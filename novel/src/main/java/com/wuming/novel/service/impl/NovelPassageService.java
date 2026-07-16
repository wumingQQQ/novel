package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Novel;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.infrastructure.mapper.NovelPassageMapper;
import com.wuming.novel.integration.rpc.rag.NovelPassageVectorIndexService;
import com.wuming.novel.passage.split.PassageSlice;
import com.wuming.novel.passage.split.PassageSplitStrategy;
import com.wuming.novel.passage.split.PassageSplitStrategyRouter;
import com.wuming.novel.passage.split.PassageSplitStrategyType;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.INovelPassageService;
import com.wuming.novel.service.INovelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final INovelService novelService;
    private final NovelPassageVectorIndexService passageVectorIndexService;
    private final PassageSplitStrategyRouter passageSplitStrategyRouter;
    private final TransactionTemplate transactionTemplate;

    /**
     * 按章节内容切分单章Passage，替换该章节旧Passage，并在事务外同步刷新向量索引。
     *
     * @param jobId 任务id
     * @param chapterId 章节id
     * @return 本次切分后保存的新Passage列表
     */
    @Override
    public List<NovelPassage> splitPassage(Long jobId, Long chapterId) {
        Chapter chapter = chapterService.getById(chapterId);
        if (chapter == null) {
            throw new IllegalArgumentException("章节不存在: " + chapterId);
        }

        PersistedPassages persisted = transactionTemplate.execute(status -> {
            List<Long> oldIds = cleanOldPassages(chapterId);
            List<NovelPassage> newPassages = splitOneChapter(chapter);
            if (!newPassages.isEmpty()) {
                saveBatch(newPassages);
            }
            return new PersistedPassages(oldIds, newPassages);
        });

        syncPassageIndex(jobId, chapter, persisted.oldPassageIds(),
                persisted.passages().stream().map(NovelPassage::getId).toList());

        List<NovelPassage> passages = persisted.passages();
        if (passages.isEmpty()) {
            log.debug("章节没有可切分的Passage，jobId: {}, novelId: {}, chapterId: {}",
                    jobId, chapter.getNovelId(), chapterId);
        } else {
            log.debug("章节Passage处理完成，jobId: {}, novelId: {}, chapterId: {}, passageCount: {}",
                    jobId, chapter.getNovelId(), chapterId, passages.size());
        }
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
        PassageSplitStrategy splitStrategy = splitStrategy(chapter);
        List<PassageSlice> slices = splitStrategy.split(chapter.getContent());
        log.debug("章节Passage切分完成，chapterId: {}, splitStrategy: {}, passageCount: {}",
                chapter.getId(), splitStrategy.type(), slices.size());

        List<NovelPassage> passages = new ArrayList<>(slices.size());
        for (PassageSlice slice : slices) {
            NovelPassage passage = new NovelPassage();
            passage.setNovelId(chapter.getNovelId());
            passage.setChapterId(chapter.getId());
            passage.setContent(slice.content());
            passage.setSequence(chapter.getSequence() * CHAPTER_SEQUENCE_STEP + slice.sequence());
            passage.setInnerSequence(slice.sequence());
            passage.setStart(slice.start());
            passage.setEnd(slice.end());
            passage.setVectorStatus(VECTOR_PENDING);
            passages.add(passage);
        }
        return passages;
    }

    /**
     * 优先使用小说表中已记录的策略；缺失时兼容旧数据，按当前配置临时解析。
     */
    private PassageSplitStrategy splitStrategy(Chapter chapter) {
        Novel novel = novelService.getById(chapter.getNovelId());
        String recordedStrategy = novel == null ? null : novel.getPassageSplitStrategy();
        if (recordedStrategy != null && !recordedStrategy.isBlank()) {
            return passageSplitStrategyRouter.current(PassageSplitStrategyType.valueOf(recordedStrategy));
        }
        return passageSplitStrategyRouter.current(
                chapter.getNovelId(),
                passageSplitStrategyRouter.requiresNovelSamples() ? listNovelChapters(chapter.getNovelId()) : List.of());
    }

    /**
     * 读取同一本小说的章节，用于AUTO模式下判断整本书适合的Passage切分策略。
     */
    private List<Chapter> listNovelChapters(Long novelId) {
        if (novelId == null) {
            return List.of();
        }
        return chapterService.list(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getNovelId, novelId)
                .orderByAsc(Chapter::getSequence));
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
        int deletedCount = passageVectorIndexService.deleteByIds(chapter.getNovelId(), oldPassageIds);
        requireRagSuccess("删除旧Passage向量", deletedCount);
        int indexedCount = passageVectorIndexService.indexByIds(passageIds);
        requireRagSuccess("索引Passage向量", indexedCount);
        log.debug("Passage向量同步索引完成，jobId: {}, novelId: {}, chapterId: {}, deletedCount: {}, indexedCount: {}",
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

    /**
     * 事务内持久化结果，用于事务外同步向量索引。
     */
    private record PersistedPassages(List<Long> oldPassageIds, List<NovelPassage> passages) {
    }

}
