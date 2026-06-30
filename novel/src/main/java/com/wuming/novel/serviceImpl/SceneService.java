package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.config.PromptConfig;
import com.wuming.novel.config.llm.LlmClientFactory;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Scene;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.llmresponse.SceneSplitResponseWrapper;
import com.wuming.novel.llm.parser.LlmJsonResponseParser;
import com.wuming.novel.llm.checker.SceneSplitResponseChecker;
import com.wuming.novel.infrastructure.mapper.SceneMapper;
import com.wuming.novel.integration.message.EventPublisher;
import com.wuming.novel.integration.message.scenesplit.ChapterSceneSplitCompleteEvent;
import com.wuming.novel.pipeline.RedisStageFailureStore;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.ISceneService;
import com.wuming.novel.sse.JobProgressService;
import com.wuming.novel.text.NovelTextNormalizer;
import com.wuming.novel.text.TextAnchorMatcher;
import com.wuming.novel.text.TextMatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class SceneService extends ServiceImpl<SceneMapper, Scene> implements ISceneService {
    private final IChapterService chapterService;
    private final PromptConfig promptConfig;
    private final IJobService jobService;
    private final ChatClient chatClient;
    private final RedisStageFailureStore redisStageFailureStore;
    private final JobProgressService jobProgressService;
    private final SceneSplitResponseChecker sceneSplitResponseChecker;
    private final NovelTextNormalizer textNormalizer;
    private final TextAnchorMatcher textAnchorMatcher;
    private final LlmJsonResponseParser llmJsonResponseParser;
    private final EventPublisher<ChapterSceneSplitCompleteEvent> eventPublisher;

    public SceneService(
            IChapterService chapterService,
            PromptConfig promptConfig,
            IJobService jobService,
            LlmClientFactory clientFactory,
            RedisStageFailureStore redisStageFailureStore,
            JobProgressService jobProgressService,
            SceneSplitResponseChecker sceneSplitResponseChecker,
            NovelTextNormalizer textNormalizer,
            TextAnchorMatcher textAnchorMatcher,
            LlmJsonResponseParser llmJsonResponseParser,
            EventPublisher<ChapterSceneSplitCompleteEvent> eventPublisher
            ) {
        this.chapterService = chapterService;
        this.promptConfig = promptConfig;
        this.jobService = jobService;
        this.chatClient = clientFactory.taskClient(LlmClientFactory.TASK_SCENE_SPLIT);
        this.redisStageFailureStore = redisStageFailureStore;
        this.jobProgressService = jobProgressService;
        this.sceneSplitResponseChecker = sceneSplitResponseChecker;
        this.textNormalizer = textNormalizer;
        this.textAnchorMatcher = textAnchorMatcher;
        this.llmJsonResponseParser = llmJsonResponseParser;
        this.eventPublisher = eventPublisher;
    }

    @Lazy
    @Autowired
    private SceneService self;

    /**
     * 章节切分环节的入口
     * @param jobId 画像任务的id
     */
    @Override
    public void splitScene(Long jobId) {
        Job job = jobService.getById(jobId);
        if (job.getStage().getCode() >= JobStage.SCENE_SPLIT.getCode()) {
            log.info("任务{}已经完成了阶段{}", jobId, JobStage.SCENE_SPLIT);
            return;
        }

        Long novelId = job.getNovelId();
        List<Chapter> chapters = queryTargetChapters(jobId, novelId);

        jobProgressService.setStageTotalItems(
                jobId,
                JobStage.SCENE_SPLIT,
                chapters.size()
        );
        log.debug("job: {} 小说{}开始场景切分，待处理章节数: {}", jobId, novelId, chapters.size());

        List<CompletableFuture<Void>> futures = chapters.stream()
                .map(chapter -> submitChapterSplit(chapter, jobId))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private List<Chapter> queryTargetChapters(Long jobId, Long novelId) {
        List<Long> failedChapterIds = redisStageFailureStore.consumeFailedLongItems(
                jobId,
                JobStage.SCENE_SPLIT
        );

        if (!failedChapterIds.isEmpty()) {
            return chapterService.listByIds(failedChapterIds);
        }

        // 如果查询结果为空则表示是首次进入该阶段，全量处理
        List<Long> chapterIds = chapterService.lambdaQuery()
                .eq(Chapter::getNovelId, novelId)
                .select(Chapter::getId)
                .list()
                .stream()
                .map(Chapter::getId)
                .toList();
        return chapterService.listByIds(chapterIds);
    }

    /**
     * 单章切分的异步任务完成之后的收尾工作：记录异步任务是否成功
     * @return 异步任务CompletableFuture，便于主方法进行同步
     */
    private CompletableFuture<Void> submitChapterSplit(Chapter chapter, Long jobId) {
        // 使用代理调用，否则异步注解不会生效
        return self.splitOneChapter(chapter, jobId)
                .thenRun(() -> jobProgressService.recordItemSuccess(
                        jobId,
                        JobStage.SCENE_SPLIT
                ))
                .exceptionally(e -> {
                    jobProgressService.recordItemFailure(jobId, JobStage.SCENE_SPLIT);
                    return null;
                });
    }

    /**
     * 异步切分单章的核心方法
     * @return 任务顺利完成则返回，异常结束则将异常章节保存到redis，等待后续重试，同时抛出异常
     */
    @Async("sceneSplitExecutor")
    protected CompletableFuture<Void> splitOneChapter(Chapter chapter, Long jobId) {
        try {
            String normalizedContent = textNormalizer.normalizeForPrompt(chapter.getContent());
            SceneSplitResponseWrapper responseWrapper = requestSceneSplit(
                    chapter,
                    normalizedContent
            );
            List<Scene> scenes = extractSceneFromChapter(chapter, normalizedContent, responseWrapper);

            self.saveBatch(scenes);
            log.debug(
                    "小说{}章节{}切分成功，chapterId: {}, 场景数: {}",
                    chapter.getNovelId(),
                    chapter.getSequence(),
                    chapter.getId(),
                    scenes.size()
            );

            // 必须在保存场景后发布事件
            publishSceneSplitCompleteEvent(jobId, scenes.size(), chapter);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            redisStageFailureStore.recordFailure(
                    jobId,
                    JobStage.SCENE_SPLIT,
                    chapter.getId()
            );
            log.error("小说{}的章节{}处理失败", chapter.getNovelId(), chapter.getSequence(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 调用llm进行实际切分
     * @param chapter   负责给提示词模板注入元信息
     * @param normalizedContent   使用NovelTextNormalizer规范化后的章节内容，便于llm返回准确的内容
     * @return  针对字符串数组的包装类，内部为切分锚点的数组
     */
    private SceneSplitResponseWrapper requestSceneSplit(
            Chapter chapter,
            String normalizedContent
    ) {
        String rawContent = chatClient.prompt()
                .user(u -> u.text(promptConfig.getSceneSplitPrompt())
                        .param("chapterTitle", chapter.getTitle())
                        .param("chapterContent", normalizedContent)
                )
                .call()
                .content();
        return llmJsonResponseParser.parse(rawContent, SceneSplitResponseWrapper.class);
    }

    private List<Scene> extractSceneFromChapter(
            Chapter chapter,
            String content,
            SceneSplitResponseWrapper responseWrapper
    ) {
        // 检验llm返回结果
        List<String> anchors = sceneSplitResponseChecker.check(
                chapter,
                content,
                responseWrapper
        );
        List<Scene> scenes = new ArrayList<>();
        List<Integer> startIndexes = new ArrayList<>();
        startIndexes.add(0);
        for (String anchor : anchors) {
            TextMatch match = textAnchorMatcher.find(content, anchor)
                    .orElseThrow(() -> new IllegalStateException(
                            "场景锚点未匹配，anchor: " + anchor
                    ));
            startIndexes.add(match.startIndex());
        }

        for (int i = 0; i < startIndexes.size(); i++) {
            int startIndex = startIndexes.get(i);
            int endIndex = content.length();
            if (i < startIndexes.size() - 1) {
                endIndex = startIndexes.get(i + 1);
            }

            Scene scene = new Scene();
            scene.setNovelId(chapter.getNovelId());
            scene.setChapterId(chapter.getId());
            scene.setSequence(i + 1);
            scene.setContent(content.substring(startIndex, endIndex));
            scenes.add(scene);
        }
        return scenes;
    }

    /**
     * 发布单章切分完毕事件
     */
    private void publishSceneSplitCompleteEvent(Long jobId, int sceneSize, Chapter chapter){
        ChapterSceneSplitCompleteEvent event = new ChapterSceneSplitCompleteEvent();
        event.setJobId(jobId);
        event.setNovelId(chapter.getNovelId());
        event.setChapterId(chapter.getId());
        event.setChapterSequence(chapter.getSequence());
        event.setSceneCount(sceneSize);
        try {
            eventPublisher.publish(event);
        }
        catch (RuntimeException e) {
            log.warn("job章节切分完毕事件发布失败，jobId: {}, chapterId: {}", jobId, chapter.getId(), e);
        }

    }
}
