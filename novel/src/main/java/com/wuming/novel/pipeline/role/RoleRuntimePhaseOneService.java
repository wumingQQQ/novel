package com.wuming.novel.pipeline.role;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuming.novel.domain.dto.ChapterAnalysisResult;
import com.wuming.novel.domain.dto.PassageCharacterResult;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.domain.entity.PassageCharacter;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.infrastructure.observability.TraceContext;
import com.wuming.novel.integration.rpc.rag.NovelPassageVectorIndexService;
import com.wuming.novel.llm.role.ChapterAnalysisService;
import com.wuming.novel.llm.role.PassageCharacterRecognitionService;
import com.wuming.novel.pipeline.RedisStageFailureStore;
import com.wuming.novel.pipeline.role.support.NovelPassageSplitter;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.INovelPassageService;
import com.wuming.novel.service.IPassageCharacterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class RoleRuntimePhaseOneService {
    private static final String ANALYSIS_DONE = "DONE";
    private static final String ANALYSIS_FAILED = "FAILED";
    private static final String VECTOR_PENDING = "PENDING";
    private static final String VECTOR_INDEXED = "INDEXED";
    private static final String VECTOR_FAILED = "FAILED";

    private final IJobService jobService;
    private final IChapterService chapterService;
    private final INovelPassageService novelPassageService;
    private final IPassageCharacterService passageCharacterService;
    private final ChapterAnalysisService chapterAnalysisService;
    private final PassageCharacterRecognitionService characterRecognitionService;
    private final NovelPassageVectorIndexService vectorIndexService;
    private final NovelPassageSplitter passageSplitter;
    private final RedisStageFailureStore failureStore;
    private final ObjectMapper objectMapper;

    private final Executor roleRuntimeExecutor;

    @Value("${novel.rag.vector-batch-size:32}")
    private int vectorBatchSize;

    public RoleRuntimePhaseOneService(
            IJobService jobService,
            IChapterService chapterService,
            INovelPassageService novelPassageService,
            IPassageCharacterService passageCharacterService,
            ChapterAnalysisService chapterAnalysisService,
            PassageCharacterRecognitionService characterRecognitionService,
            NovelPassageVectorIndexService vectorIndexService,
            NovelPassageSplitter passageSplitter,
            RedisStageFailureStore failureStore,
            ObjectMapper objectMapper,
            @Qualifier("roleRuntimeExecutor") Executor roleRuntimeExecutor
    ) {
        this.jobService = jobService;
        this.chapterService = chapterService;
        this.novelPassageService = novelPassageService;
        this.passageCharacterService = passageCharacterService;
        this.chapterAnalysisService = chapterAnalysisService;
        this.characterRecognitionService = characterRecognitionService;
        this.vectorIndexService = vectorIndexService;
        this.passageSplitter = passageSplitter;
        this.failureStore = failureStore;
        this.objectMapper = objectMapper;
        this.roleRuntimeExecutor = roleRuntimeExecutor;
    }

    public void build(Long jobId) {
        Job job = jobService.getById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("job不存在: " + jobId);
        }
        failureStore.consumeFailedItems(jobId, JobStage.CHAPTER_SPLIT);

        long start = System.currentTimeMillis();
        Long novelId = job.getNovelId();
        log.info("阶段1开始，jobId: {}, novelId: {}", jobId, novelId);

        chapterService.splitChapter(jobId);
        List<Chapter> chapters = listChapters(novelId);
        log.info("阶段1章节切分完成，jobId: {}, novelId: {}, chapterCount: {}",
                jobId, novelId, chapters.size());

        analyzeChapters(jobId, novelId, chapters);
        List<NovelPassage> passages = buildPassages(novelId, chapters);
        recognizeCharacters(jobId, novelId, passages);
        indexPassages(jobId, novelId, passages);

        log.info("阶段1完成，jobId: {}, novelId: {}, chapterCount: {}, passageCount: {}, costMs: {}",
                jobId, novelId, chapters.size(), passages.size(), System.currentTimeMillis() - start);
    }

    private List<Chapter> listChapters(Long novelId) {
        return chapterService.list(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getNovelId, novelId)
                .orderByAsc(Chapter::getSequence));
    }

    private void analyzeChapters(Long jobId, Long novelId, List<Chapter> chapters) {
        long start = System.currentTimeMillis();
        log.info("章节分析开始，jobId: {}, novelId: {}, chapterCount: {}", jobId, novelId, chapters.size());
        List<CompletableFuture<Void>> futures = chapters.stream()
                .map(chapter -> CompletableFuture.runAsync(
                        () -> analyzeOneChapter(jobId, novelId, chapter),
                        roleRuntimeExecutor
                ))
                .toList();
        waitAll(futures);
        long doneCount = chapters.stream()
                .filter(chapter -> ANALYSIS_DONE.equals(chapterService.getById(chapter.getId()).getAnalysisStatus()))
                .count();
        log.info("章节分析完成，jobId: {}, novelId: {}, doneCount: {}, failedCount: {}, costMs: {}",
                jobId, novelId, doneCount, chapters.size() - doneCount, System.currentTimeMillis() - start);
    }

    private void analyzeOneChapter(Long jobId, Long novelId, Chapter chapter) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(jobId);
             TraceContext.MdcScope ignoredNovel = TraceContext.putNovelId(novelId);
             TraceContext.MdcScope ignoredChapter = TraceContext.putChapterId(chapter.getId())) {
            long start = System.currentTimeMillis();
            log.debug("章节分析开始，chapterId: {}, sequence: {}", chapter.getId(), chapter.getSequence());
            try {
                ChapterAnalysisResult result = chapterAnalysisService.analyze(chapter);
                chapter.setSummary(result.summary());
                chapter.setMainCharacters(String.join(",", safeList(result.mainCharacters())));
                chapter.setSceneBoundaries(toJson(safeList(result.sceneBoundaries())));
                chapter.setAnalysisStatus(ANALYSIS_DONE);
                chapter.setAnalysisError(null);
                chapter.setAnalyzedTime(LocalDateTime.now());
                chapterService.updateById(chapter);
                log.debug("章节分析成功，chapterId: {}, costMs: {}",
                        chapter.getId(), System.currentTimeMillis() - start);
            } catch (RuntimeException e) {
                chapter.setAnalysisStatus(ANALYSIS_FAILED);
                chapter.setAnalysisError(e.getMessage());
                chapter.setAnalyzedTime(LocalDateTime.now());
                chapterService.updateById(chapter);
                log.warn("章节分析失败，chapterId: {}, costMs: {}",
                        chapter.getId(), System.currentTimeMillis() - start, e);
            }
        }
    }

    private List<NovelPassage> buildPassages(Long novelId, List<Chapter> chapters) {
        long start = System.currentTimeMillis();
        cleanOldPassages(novelId);

        int nextSequence = 1;
        List<NovelPassage> passages = new ArrayList<>();
        for (Chapter chapter : listChapters(novelId)) {
            List<NovelPassage> chapterPassages = passageSplitter.split(chapter, nextSequence);
            passages.addAll(chapterPassages);
            nextSequence += chapterPassages.size();
        }
        if (!passages.isEmpty()) {
            novelPassageService.saveBatch(passages);
        }
        List<NovelPassage> savedPassages = listPassages(novelId);
        log.info("Passage切分完成，novelId: {}, chapterCount: {}, passageCount: {}, costMs: {}",
                novelId, chapters.size(), savedPassages.size(), System.currentTimeMillis() - start);
        return savedPassages;
    }

    private void cleanOldPassages(Long novelId) {
        List<Long> oldPassageIds = listPassages(novelId).stream()
                .map(NovelPassage::getId)
                .toList();
        if (!oldPassageIds.isEmpty()) {
            boolean characterRemoved = passageCharacterService.remove(
                    new LambdaQueryWrapper<PassageCharacter>()
                            .in(PassageCharacter::getPassageId, oldPassageIds)
            );
            log.debug("清理旧Passage人物映射，novelId: {}, passageCount: {}, removed: {}",
                    novelId, oldPassageIds.size(), characterRemoved);
        }
        boolean passageRemoved = novelPassageService.remove(new LambdaQueryWrapper<NovelPassage>()
                .eq(NovelPassage::getNovelId, novelId));
        log.debug("清理旧Passage，novelId: {}, removed: {}", novelId, passageRemoved);
    }

    private List<NovelPassage> listPassages(Long novelId) {
        return novelPassageService.list(new LambdaQueryWrapper<NovelPassage>()
                .eq(NovelPassage::getNovelId, novelId)
                .orderByAsc(NovelPassage::getSequence));
    }

    private void recognizeCharacters(Long jobId, Long novelId, List<NovelPassage> passages) {
        long start = System.currentTimeMillis();
        log.info("Passage人物识别开始，jobId: {}, novelId: {}, passageCount: {}",
                jobId, novelId, passages.size());
        List<CompletableFuture<Void>> futures = passages.stream()
                .map(passage -> CompletableFuture.runAsync(
                        () -> recognizeOnePassage(jobId, novelId, passage),
                        roleRuntimeExecutor
                ))
                .toList();
        waitAll(futures);
        log.info("Passage人物识别完成，jobId: {}, novelId: {}, passageCount: {}, costMs: {}",
                jobId, novelId, passages.size(), System.currentTimeMillis() - start);
    }

    private void recognizeOnePassage(Long jobId, Long novelId, NovelPassage passage) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(jobId);
             TraceContext.MdcScope ignoredNovel = TraceContext.putNovelId(novelId)) {
            long start = System.currentTimeMillis();
            log.debug("Passage人物识别开始，passageId: {}, sequence: {}",
                    passage.getId(), passage.getSequence());
            try {
                PassageCharacterResult result = characterRecognitionService.recognize(passage);
                List<PassageCharacter> characters = safeList(result.characters()).stream()
                        .map(String::trim)
                        .filter(name -> !name.isBlank())
                        .distinct()
                        .map(name -> passageCharacter(passage.getId(), name))
                        .toList();
                if (!characters.isEmpty()) {
                    passageCharacterService.saveBatch(characters);
                }
                log.debug("Passage人物识别成功，passageId: {}, characterCount: {}, costMs: {}",
                        passage.getId(), characters.size(), System.currentTimeMillis() - start);
            } catch (RuntimeException e) {
                log.warn("Passage人物识别失败，passageId: {}, costMs: {}",
                        passage.getId(), System.currentTimeMillis() - start, e);
            }
        }
    }

    private PassageCharacter passageCharacter(Long passageId, String characterName) {
        PassageCharacter passageCharacter = new PassageCharacter();
        passageCharacter.setPassageId(passageId);
        passageCharacter.setCharacterName(characterName);
        return passageCharacter;
    }

    private void indexPassages(Long jobId, Long novelId, List<NovelPassage> passages) {
        long start = System.currentTimeMillis();
        List<List<NovelPassage>> batches = batches(passages, Math.max(1, vectorBatchSize));
        log.info("Passage向量化开始，jobId: {}, novelId: {}, passageCount: {}, batchCount: {}",
                jobId, novelId, passages.size(), batches.size());
        List<CompletableFuture<Void>> futures = batches.stream()
                .map(batch -> CompletableFuture.runAsync(
                        () -> indexOneBatch(jobId, novelId, batch),
                        roleRuntimeExecutor
                ))
                .toList();
        waitAll(futures);
        log.info("Passage向量化完成，jobId: {}, novelId: {}, passageCount: {}, costMs: {}",
                jobId, novelId, passages.size(), System.currentTimeMillis() - start);
    }

    private void indexOneBatch(Long jobId, Long novelId, List<NovelPassage> batch) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(jobId);
             TraceContext.MdcScope ignoredNovel = TraceContext.putNovelId(novelId)) {
            long start = System.currentTimeMillis();
            try {
                int upsertCount = vectorIndexService.upsert(batch);
                batch.forEach(passage -> {
                    passage.setVectorStatus(VECTOR_INDEXED);
                    passage.setVectorError(null);
                    passage.setIndexedTime(LocalDateTime.now());
                });
                novelPassageService.updateBatchById(batch);
                log.debug("Passage向量批次写入成功，batchSize: {}, upsertCount: {}, costMs: {}",
                        batch.size(), upsertCount, System.currentTimeMillis() - start);
            } catch (RuntimeException e) {
                batch.forEach(passage -> {
                    passage.setVectorStatus(VECTOR_FAILED);
                    passage.setVectorError(e.getMessage());
                    novelPassageService.updateById(passage);
                    failureStore.recordFailure(jobId, JobStage.CHAPTER_SPLIT, passage.getId());
                });
                log.warn("Passage向量批次写入失败，batchSize: {}, costMs: {}",
                        batch.size(), System.currentTimeMillis() - start, e);
            }
        }
    }

    private void waitAll(List<CompletableFuture<Void>> futures) {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON序列化失败", e);
        }
    }

    private List<List<NovelPassage>> batches(List<NovelPassage> passages, int batchSize) {
        List<List<NovelPassage>> result = new ArrayList<>();
        for (int i = 0; i < passages.size(); i += batchSize) {
            result.add(passages.subList(i, Math.min(i + batchSize, passages.size())));
        }
        return result;
    }
}
