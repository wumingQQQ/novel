package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.config.PassageSplitProperties;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.infrastructure.mapper.NovelPassageMapper;
import com.wuming.novel.integration.rpc.rag.NovelPassageVectorIndexService;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.INovelPassageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 小说检索文本块基础服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NovelPassageService extends ServiceImpl<NovelPassageMapper, NovelPassage>
        implements INovelPassageService {
    private static final int CHAPTER_SEQUENCE_STEP = 100;
    private static final String VECTOR_PENDING = "PENDING";

    private final IChapterService chapterService;
    private final NovelPassageVectorIndexService passageVectorIndexService;
    private final PassageSplitProperties passageSplitProperties;

    /**
     * 按章节内容切分单章Passage，替换该章节旧Passage，并在事务提交后同步刷新向量索引。
     *
     * @param jobId 任务id
     * @param chapterId 章节id
     * @return 本次切分后保存的新Passage列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<NovelPassage> splitPassage(Long jobId, Long chapterId) {
        Chapter chapter = chapterService.getById(chapterId);
        if (chapter == null) {
            throw new IllegalArgumentException("章节不存在: " + chapterId);
        }
        List<Long> oldPassageIds = cleanOldPassages(chapterId);

        List<NovelPassage> passages = splitOneChapter(chapter);
        if (passages.isEmpty()) {
            syncPassageIndexAfterCommit(jobId, chapter, oldPassageIds, List.of());
            log.info("章节没有可切分的Passage，jobId: {}, chapterId: {}", jobId, chapterId);
            return List.of();
        }

        saveBatch(passages);
        syncPassageIndexAfterCommit(jobId, chapter, oldPassageIds, passages.stream()
                .map(NovelPassage::getId)
                .toList());
        log.info("章节Passage处理完成，jobId: {}, novelId: {}, chapterId: {}, passageCount: {}",
                jobId, chapter.getNovelId(), chapterId, passages.size());
        return passages;
    }

    /**
     * 清理指定章节下已存在的Passage，并返回旧Passage id，用于后续删除旧向量。
     *
     * @param chapterId 章节id
     * @return 被清理的旧Passage id列表
     */
    private List<Long> cleanOldPassages(Long chapterId) {
        List<Long> oldPassageIds = list(new LambdaQueryWrapper<NovelPassage>()
                .eq(NovelPassage::getChapterId, chapterId))
                .stream()
                .map(NovelPassage::getId)
                .toList();
        if (!oldPassageIds.isEmpty()) {
            log.debug("章节存在旧Passage，chapterId: {}, oldPassageCount: {}", chapterId, oldPassageIds.size());
        }
        remove(new LambdaQueryWrapper<NovelPassage>()
                .eq(NovelPassage::getChapterId, chapterId));
        return oldPassageIds;
    }

    /**
     * 将单章内容切分为多个Passage实体。
     *
     * @param chapter 章节实体
     * @return 待保存的Passage列表
     */
    private List<NovelPassage> splitOneChapter(Chapter chapter) {
        List<String> paragraphs = paragraphs(chapter.getContent());
        if (paragraphs.isEmpty()) {
            return List.of();
        }
        List<Range> ranges = ranges(paragraphs.size(), chapter.getSceneBoundaries());
        log.debug("章节Passage切分完成，chapterId: {}, splitStrategy: {}, paragraphCount: {}, passageCount: {}",
                chapter.getId(), passageSplitProperties.getSplitStrategy(), paragraphs.size(), ranges.size());
        List<NovelPassage> passages = new ArrayList<>(ranges.size());
        for (int i = 0; i < ranges.size(); i++) {
            Range range = ranges.get(i);
            NovelPassage passage = new NovelPassage();
            passage.setNovelId(chapter.getNovelId());
            passage.setChapterId(chapter.getId());
            passage.setContent(String.join("\n", paragraphs.subList(range.start() - 1, range.end())));
            passage.setSequence(chapter.getSequence() * CHAPTER_SEQUENCE_STEP + i + 1);
            passage.setInnerSequence(i + 1);
            passage.setStartParagraph(range.start());
            passage.setEndParagraph(range.end());
            passage.setVectorStatus(VECTOR_PENDING);
            passages.add(passage);
        }
        return passages;
    }

    /**
     * 将章节内容按换行切分并进行trim
     *
     * @param content 章节正文
     * @return 段落内容的列表
     */
    private List<String> paragraphs(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> paragraphs = new ArrayList<>();
        for (String line : content.split("\\R")) {
            String paragraph = line.trim();
            if (!paragraph.isEmpty()) {
                paragraphs.add(paragraph);
            }
        }
        return paragraphs;
    }

    /**
     * 根据当前切分策略生成段落范围。
     *
     * @param paragraphCount 章节段落数量
     * @param sceneBoundaries LLM分析出的场景起始段落
     * @return Passage覆盖的段落范围列表
     */
    private List<Range> ranges(int paragraphCount, List<Integer> sceneBoundaries) {
        if (passageSplitProperties.getSplitStrategy() == PassageSplitProperties.SplitStrategy.SLIDING_WINDOW) {
            return slidingWindowRanges(paragraphCount);
        }
        if (sceneBoundaries == null || sceneBoundaries.isEmpty()) {
            log.debug("章节未提供场景边界，降级使用滑动窗口切分，paragraphCount: {}", paragraphCount);
            return slidingWindowRanges(paragraphCount);
        }
        List<Integer> starts = new ArrayList<>();
        starts.add(1);
        for (Integer boundary : sceneBoundaries) {
            if (boundary != null && boundary > 1 && boundary <= paragraphCount) {
                starts.add(boundary);
            }
        }
        return toRanges(starts, paragraphCount);
    }

    /**
     * 使用滑动窗口生成段落范围。
     *
     * @param paragraphCount 章节段落数量
     * @return 滑动窗口段落范围列表
     */
    private List<Range> slidingWindowRanges(int paragraphCount) {
        List<Range> ranges = new ArrayList<>();
        int windowSize = passageSplitProperties.getWindowSize();
        int overlapSize = passageSplitProperties.getOverlapSize();
        if (windowSize <= 0) {
            throw new IllegalStateException("novel.passage.window-size 必须大于0");
        }
        if (overlapSize < 0 || overlapSize >= windowSize) {
            throw new IllegalStateException("novel.passage.overlap-size 必须大于等于0且小于window-size");
        }
        int step = windowSize - overlapSize;
        for (int start = 1; start <= paragraphCount; start += step) {
            int end = Math.min(start + windowSize - 1, paragraphCount);
            ranges.add(new Range(start, end));
            if (end == paragraphCount) {
                break;
            }
        }
        return ranges;
    }

    /**
     * 将场景起始段落转换为连续、不重叠的段落范围。
     *
     * @param starts 场景起始段落列表
     * @param paragraphCount 章节段落数量
     * @return 场景范围列表
     */
    private List<Range> toRanges(List<Integer> starts, int paragraphCount) {
        List<Integer> sortedStarts = starts.stream()
                .distinct()
                .sorted()
                .toList();
        List<Range> ranges = new ArrayList<>();
        for (int i = 0; i < sortedStarts.size(); i++) {
            int start = sortedStarts.get(i);
            int end = i + 1 < sortedStarts.size()
                    ? sortedStarts.get(i + 1) - 1
                    : paragraphCount;
            if (start <= end) {
                ranges.add(new Range(start, end));
            }
        }
        return ranges;
    }

    /**
     * 在事务提交后同步刷新Passage向量索引。
     *
     * @param jobId 任务id
     * @param chapter 章节实体
     * @param oldPassageIds 待删除向量的旧Passage id
     * @param passageIds 待写入向量的新Passage id
     */
    private void syncPassageIndexAfterCommit(Long jobId,
                                             Chapter chapter,
                                             List<Long> oldPassageIds,
                                             List<Long> passageIds) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncPassageIndex(jobId, chapter, oldPassageIds, passageIds);
                }
            });
            return;
        }
        syncPassageIndex(jobId, chapter, oldPassageIds, passageIds);
    }

    /**
     * 删除旧Passage向量并写入新Passage向量。
     *
     * @param jobId 任务id
     * @param chapter 章节实体
     * @param oldPassageIds 待删除向量的旧Passage id
     * @param passageIds 待写入向量的新Passage id
     */
    private void syncPassageIndex(Long jobId, Chapter chapter, List<Long> oldPassageIds, List<Long> passageIds) {
        int deletedCount = passageVectorIndexService.deleteByIds(oldPassageIds);
        requireRagSuccess("删除旧Passage向量", deletedCount);
        int indexedCount = passageVectorIndexService.indexByIds(passageIds);
        requireRagSuccess("索引Passage向量", indexedCount);
        log.info("Passage向量同步索引完成，jobId: {}, novelId: {}, chapterId: {}, deletedCount: {}, indexedCount: {}",
                jobId, chapter.getNovelId(), chapter.getId(), deletedCount, indexedCount);
    }

    /**
     * 检查RAG调用结果，负数代表远程服务降级。
     *
     * @param action 当前动作
     * @param result RAG调用返回值
     */
    private void requireRagSuccess(String action, int result) {
        if (result < 0) {
            throw new IllegalStateException(action + "失败：RAG服务降级");
        }
    }

    private record Range(int start, int end) {
    }
}
