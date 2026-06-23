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
import com.wuming.novel.exception.LLMResponseEmptyException;
import com.wuming.novel.mapper.SceneMapper;
import com.wuming.novel.pipeline.RedisStageFailureStore;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.ISceneService;
import com.wuming.novel.sse.JobProgressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SceneService extends ServiceImpl<SceneMapper, Scene> implements ISceneService {
    private final IChapterService chapterService;
    private final PromptConfig promptConfig;
    private final IJobService jobService;
    private final ChatClient chatClient;
    private final RedisStageFailureStore redisStageFailureStore;
    private final JobProgressService jobProgressService;

    @Lazy
    @Autowired
    private SceneService self;

    public SceneService(IChapterService chapterService, PromptConfig promptConfig, IJobService jobService, LlmClientFactory clientFactory, RedisStageFailureStore redisStageFailureStore, JobProgressService jobProgressService) {
        this.chapterService = chapterService;
        this.promptConfig = promptConfig;
        this.jobService = jobService;
        this.chatClient = clientFactory.defaultClient();
        this.redisStageFailureStore = redisStageFailureStore;
        this.jobProgressService = jobProgressService;
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

    private String normalize(String chapterContent){
        if (chapterContent == null) {
            return "";
        }

        return chapterContent
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u3000', ' ')
                .replace('\u00A0', ' ')
                .replace("\uFEFF", "")
                .replace("\u200B", "")
                .lines()
                .map(line -> line.trim().replaceAll("[ \\t\\f]+", " "))
                .collect(Collectors.joining("\n"))
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }


    @Async("sceneSplitExecutor")
    protected CompletableFuture<Void> splitOneChapter(Chapter chapter, Long jobId) {

        try {
            SceneSplitResponseWrapper responseWrapper = chatClient.prompt()
                    .user(u -> u.text(promptConfig.getSceneSplitPrompt())
                            .param("chapterTitle", chapter.getTitle())
                            .param("chapterContent", normalize(chapter.getContent()))
                    )
                    .call()
                    .entity(SceneSplitResponseWrapper.class);


            List<Scene> scenes = extractSceneFromChapter(chapter, responseWrapper);

            self.saveBatch(scenes);
            log.debug("小说{}章节{}切分成功，chapterId: {}, 场景数: {}", chapter.getNovelId(), chapter.getSequence(), chapter.getId(), scenes.size());
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            redisStageFailureStore.recordFailure(jobId, JobStage.SCENE_SPLIT, chapter.getId());
            log.error("小说{}的章节{}处理失败", chapter.getNovelId(), chapter.getSequence(), e);
            return CompletableFuture.failedFuture(e);
        }


    }

    private List<Scene> extractSceneFromChapter(Chapter chapter, SceneSplitResponseWrapper responseWrapper){
        Long novelId = chapter.getNovelId();
        if(responseWrapper == null || responseWrapper.scenes() == null || responseWrapper.scenes().isEmpty()){
            throw new LLMResponseEmptyException("小说" + novelId +"章节" +chapter.getId() +"分场景时llm响应为空");
        }
        List<SceneSplitResponse> responses = responseWrapper.scenes();

        List<Scene> scenes= new ArrayList<>();

        String content = normalize(chapter.getContent());

        for(int i = 0; i < responses.size(); i++){
            SceneSplitResponse current = responses.get(i);

            Scene scene = new Scene();
            scene.setNovelId(chapter.getNovelId());
            scene.setChapterId(chapter.getId());
            scene.setSequence(i + 1);

            // 定位原文位置
            int startIndex = content.indexOf(current.anchor());
            int endIndex;
            if(i < responses.size() -1){
                SceneSplitResponse next = responses.get(i + 1);
                endIndex = content.indexOf(next.anchor());
            }
            else{
                // 如果是最后一个场景，则结束位置为章节末尾
                endIndex = content.length();
            }

            // 边界保护
            if(startIndex != -1 && endIndex != -1 && startIndex < endIndex){
                String sceneContent = content.substring(startIndex, endIndex);
                scene.setContent(sceneContent);
                scenes.add(scene);
            }
            else{
                // 可能发生幻觉，原文位置找不到，或者顺序错乱
                String nextAnchor = i < responses.size() - 1 ? responses.get(i + 1).anchor() : null;
                log.warn("小说{}章节{}锚点匹配失败，chapterId: {}, sceneSequence: {},  anchor: {}, nextAnchor: {}",
                        chapter.getNovelId(),
                        chapter.getSequence(),
                        chapter.getId(),
                        current.sequence(),
                        current.anchor(),
                        nextAnchor);
                throw new RuntimeException("llm返回锚点解析失败");
            }
        }
        return scenes;
    }
}
