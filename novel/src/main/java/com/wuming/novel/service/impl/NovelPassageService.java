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

import java.util.ArrayList;
import java.time.LocalDateTime;
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
    private static final String VECTOR_INDEXED = "INDEXED";
    private static final String VECTOR_FAILED = "FAILED";

    private final IChapterService chapterService;
    private final NovelPassageVectorIndexService vectorIndexService;
    private final PassageSplitProperties passageSplitProperties;

    /**
     * 按章节分析结果切分单章 Passage，保存 Passage，并写入向量索引。
     *
     * @param jobId 任务id
     * @param chapterId 章节id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void splitPassage(Long jobId, Long chapterId) {
        Chapter chapter = chapterService.getById(chapterId);
        if (chapter == null) {
            throw new IllegalArgumentException("章节不存在: " + chapterId);
        }
        cleanOldPassages(chapterId);

        List<NovelPassage> passages = splitOneChapter(chapter);
        if (passages.isEmpty()) {
            log.info("章节没有可切分的Passage，jobId: {}, chapterId: {}", jobId, chapterId);
            return;
        }

        saveBatch(passages);
        indexPassages(jobId, chapterId, passages);
        log.info("章节Passage处理完成，jobId: {}, novelId: {}, chapterId: {}, passageCount: {}",
                jobId, chapter.getNovelId(), chapterId, passages.size());
    }

    // TODO 移除向量库中的passage
    private void cleanOldPassages(Long chapterId) {
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
    }

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
     * 将passage插入索引库
     */
    @Transactional(rollbackFor = Exception.class)
    protected void indexPassages(Long jobId, Long chapterId, List<NovelPassage> passages) {
        try {
            int indexedCount = vectorIndexService.upsert(passages);
            passages.forEach(passage -> {
                passage.setVectorStatus(VECTOR_INDEXED);
                passage.setVectorError(null);
                passage.setIndexedTime(LocalDateTime.now());
            });
            updateBatchById(passages);
            log.info("章节Passage向量索引完成，jobId: {}, chapterId: {}, indexedCount: {}",
                    jobId, chapterId, indexedCount);
        } catch (RuntimeException e) {
            passages.forEach(passage -> {
                passage.setVectorStatus(VECTOR_FAILED);
                passage.setVectorError(e.getMessage());
            });
            updateBatchById(passages);
            log.warn("章节Passage向量索引失败，jobId: {}, chapterId: {}, passageCount: {}",
                    jobId, chapterId, passages.size(), e);
        }
    }

    private record Range(int start, int end) {
    }
}
