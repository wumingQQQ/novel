package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.config.PromptConfig;
import com.wuming.novel.config.llm.LlmClientFactory;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Scene;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.llmresponse.SceneSplitResponse;
import com.wuming.novel.domain.llmresponse.SceneSplitResponseWrapper;
import com.wuming.novel.llm.checker.SceneSplitResponseChecker;
import com.wuming.novel.mapper.SceneMapper;
import com.wuming.novel.pipeline.RedisStageFailureStore;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.ISceneService;
import com.wuming.novel.sse.JobProgressService;
import com.wuming.novel.text.NovelTextNormalizer;
import com.wuming.novel.text.TextAnchorMatcher;
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

    @Lazy
    @Autowired
    private SceneService self;

    public SceneService(IChapterService chapterService, PromptConfig promptConfig, IJobService jobService, LlmClientFactory clientFactory, RedisStageFailureStore redisStageFailureStore, JobProgressService jobProgressService, SceneSplitResponseChecker sceneSplitResponseChecker, NovelTextNormalizer textNormalizer, TextAnchorMatcher textAnchorMatcher) {
        this.chapterService = chapterService;
        this.promptConfig = promptConfig;
        this.jobService = jobService;
        this.chatClient = clientFactory.defaultClient();
        this.redisStageFailureStore = redisStageFailureStore;
        this.jobProgressService = jobProgressService;
        this.sceneSplitResponseChecker = sceneSplitResponseChecker;
        this.textNormalizer = textNormalizer;
        this.textAnchorMatcher = textAnchorMatcher;
    }

    @Override
    public void splitScene(Long jobId) {
        Job job = jobService.getById(jobId);
        if(job.getStage().getCode() >= JobStage.SCENE_SPLIT.getCode()){
            log.info("任务{}已经完成了阶段{}", jobId, JobStage.SCENE_SPLIT);
            return;
        }
        Long novelId = job.getNovelId();
        List<Long> targetChapterIds = queryTargetChapterIds(jobId, novelId);

        List<Chapter> chapters = chapterService.listByIds(targetChapterIds);
        jobProgressService.setStageTotalItems(jobId, JobStage.SCENE_SPLIT, chapters.size());
        log.debug("job: {} 小说{}开始场景切分，待处理章节数: {}", jobId, novelId, chapters.size());
        List<CompletableFuture<Void>> futures = chapters.stream()
                .map(chapter -> self.splitOneChapter(chapter, jobId)     // 使用代理，否则异步注解失效
                        .thenRun(() -> jobProgressService.recordItemSuccess(jobId, JobStage.SCENE_SPLIT))
                        .exceptionally(e -> {
                            jobProgressService.recordItemFailure(jobId, JobStage.SCENE_SPLIT);
                            return null;
                        }))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private List<Long> queryTargetChapterIds(Long jobId, Long novelId) {
        List<Long> failedChapterIds = redisStageFailureStore.consumeFailedLongItems(jobId, JobStage.SCENE_SPLIT);

        if (!failedChapterIds.isEmpty()) {
            return failedChapterIds;
        }
        // 如果查询结果为空则表示是首次进入该阶段，全量处理
        return chapterService.lambdaQuery()
                .eq(Chapter::getNovelId, novelId)
                .select(Chapter::getId)
                .list()
                .stream()
                .map(Chapter::getId)
                .toList();
    }

    @Async("sceneSplitExecutor")
    protected CompletableFuture<Void> splitOneChapter(Chapter chapter, Long jobId) {

        try {
            String normalizedContent = textNormalizer.normalizeForPrompt(chapter.getContent());
            SceneSplitResponseWrapper responseWrapper = chatClient.prompt()
                    .user(u -> u.text(promptConfig.getSceneSplitPrompt())
                            .param("chapterTitle", chapter.getTitle())
                            .param("chapterContent", normalizedContent)
                    )
                    .call()
                    .entity(SceneSplitResponseWrapper.class);


            List<Scene> scenes = extractSceneFromChapter(chapter, normalizedContent, responseWrapper);

            self.saveBatch(scenes);
            log.debug("小说{}章节{}切分成功，chapterId: {}, 场景数: {}", chapter.getNovelId(), chapter.getSequence(), chapter.getId(), scenes.size());
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            redisStageFailureStore.recordFailure(jobId, JobStage.SCENE_SPLIT, chapter.getId());
            log.error("小说{}的章节{}处理失败", chapter.getNovelId(), chapter.getSequence(), e);
            return CompletableFuture.failedFuture(e);
        }


    }

    private List<Scene> extractSceneFromChapter(Chapter chapter, String content, SceneSplitResponseWrapper responseWrapper){
        List<SceneSplitResponse> responses = sceneSplitResponseChecker.check(chapter, content, responseWrapper);

        List<Scene> scenes= new ArrayList<>();

        for(int i = 0; i < responses.size(); i++){
            SceneSplitResponse current = responses.get(i);

            Scene scene = new Scene();
            scene.setNovelId(chapter.getNovelId());
            scene.setChapterId(chapter.getId());
            scene.setSequence(i + 1);

            // 定位原文位置
            int startIndex = textAnchorMatcher.indexOf(content, current.anchor());
            int endIndex;
            if(i < responses.size() -1){
                SceneSplitResponse next = responses.get(i + 1);
                endIndex = textAnchorMatcher.indexOf(content, next.anchor());
            }
            else{
                // 如果是最后一个场景，则结束位置为章节末尾
                endIndex = content.length();
            }

            scene.setContent(content.substring(startIndex, endIndex));
            scenes.add(scene);
        }
        return scenes;
    }
}
