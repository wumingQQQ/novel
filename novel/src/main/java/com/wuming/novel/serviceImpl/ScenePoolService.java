package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Sets;
import com.wuming.novel.config.PromptConfig;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Scene;
import com.wuming.novel.domain.entity.rel.ScenePool;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.enums.PoolType;
import com.wuming.novel.domain.llmresponse.ScenePoolResponse;
import com.wuming.novel.exception.LLMResponseEmptyException;
import com.wuming.novel.mapper.ScenePoolMapper;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.IScenePoolService;
import com.wuming.novel.service.ISceneService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class ScenePoolService extends ServiceImpl<ScenePoolMapper, ScenePool> implements IScenePoolService {
    private final PromptConfig promptConfig;
    private final ISceneService sceneService;
    private final IJobService jobService;
    private final ScenePoolMapper scenePoolMapper;
    private final ChatClient chatClient;

    @Lazy
    @Autowired
    private ScenePoolService self;

    public ScenePoolService(PromptConfig promptConfig, ISceneService sceneService, IJobService jobService, ScenePoolMapper scenePoolMapper, ChatModel chatModel) {
        this.promptConfig = promptConfig;
        this.sceneService = sceneService;
        this.jobService = jobService;
        this.scenePoolMapper = scenePoolMapper;
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public boolean divideSceneIntoPool(Long jobId) {
        Job job = jobService.getById(jobId);
        // 幂等校验
        if(job.getStage().getCode() >= JobStage.POOL_CLASSIFY.getCode()){
            log.info("任务{}已经完成了阶段{}", jobId, JobStage.POOL_CLASSIFY);
            return true;
        }

        Long novelId = job.getNovelId();
        List<Long> finishedSceneIds = queryFinishedSceneIds(novelId, jobId);
        Set<Long> unfinishedSceneIds = computeUnfinishedSceneIds(novelId, finishedSceneIds);


        List<Scene> scenes = sceneService.listByIds(unfinishedSceneIds);
        log.debug("job: {} 小说{}开始场景分池，已完成场景数: {}, 待处理场景数: {}", jobId, novelId, finishedSceneIds.size(), scenes.size());

        List<CompletableFuture<Void>> futures = scenes.stream()
                .map(scene -> self.doSimpleClassify(scene, jobId))
                .toList();

        // 便于测试，等待任务完成
        AtomicBoolean allSuccess = new AtomicBoolean(true);
        futures.forEach(future -> {
            try {
                future.join();
            }
            catch (Exception e) {
                allSuccess.set(false);
            }
        });
        return allSuccess.get();
    }

    private List<Long> queryFinishedSceneIds(Long novelId, Long jobId) {
        QueryWrapper<ScenePool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("novel_id", novelId)
                .eq("job_id", jobId)
                .select("scene_id")
                .groupBy("scene_id");
        return scenePoolMapper.selectList(queryWrapper)
                .stream()
                .map(ScenePool::getSceneId)
                .toList();
    }

    private Set<Long> computeUnfinishedSceneIds(Long novelId, List<Long> finishedSceneIds){
        List<Long> allSceneIds = sceneService.lambdaQuery()
                .eq(Scene::getNovelId, novelId)
                .select(Scene::getId)
                .list()
                .stream()
                .map(Scene::getId)
                .toList();

        return Sets.difference(
                new HashSet<>(allSceneIds),
                new HashSet<>(finishedSceneIds)
        );
    }


    @Async("poolClassifyExecutor")
    protected CompletableFuture<Void> doSimpleClassify(Scene scene, Long jobId){
        try {
            Job job = jobService.getById(jobId);

            String protagonistName = job.getProtagonistName();
            String targetName = job.getTargetName();

            ScenePoolResponse[] responses = chatClient.prompt()
                    .user(u -> u.text(promptConfig.getScenePoolPrompt())
                            .param("protagonistName", protagonistName)
                            .param("targetName", targetName)
                            .param("sceneContent", scene.getContent())
                    )
                    .options(OpenAiChatOptions.builder()
                            .responseFormat(ResponseFormat.builder()
                                    .type(ResponseFormat.Type.JSON_OBJECT)
                                    .build())
                            .build()
                    )
                    .call()
                    .entity(ScenePoolResponse[].class);

            if(responses == null || responses.length == 0){
                throw new LLMResponseEmptyException("任务" + jobId + "场景" + scene.getId() +"分池时llm响应为空");
            }

            List<ScenePool> scenePools = new ArrayList<>();

            for(ScenePoolResponse response : responses){
                PoolType poolType = PoolType.fromCode(response.code());

                ScenePool scenePool = new ScenePool();
                scenePool.setSceneId(scene.getId());
                scenePool.setNovelId(scene.getNovelId());
                scenePool.setJobId(jobId);
                scenePool.setPoolType(poolType);
                scenePool.setConfidence(response.confidence());
                scenePools.add(scenePool);
            }

            self.saveBatch(scenePools);
            log.debug("job: {} scene: {} 分池完成，池数量: {}", jobId, scene.getId(), scenePools.size());

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("处理任务：{}, scene{}分池出现异常", jobId, scene.getId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

}
