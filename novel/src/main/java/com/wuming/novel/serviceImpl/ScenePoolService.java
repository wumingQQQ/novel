package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.config.PromptConfig;
import com.wuming.novel.config.llm.LlmClientFactory;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Scene;
import com.wuming.novel.domain.entity.rel.ScenePool;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.enums.PoolType;
import com.wuming.novel.domain.llmresponse.ScenePoolResponse;
import com.wuming.novel.domain.llmresponse.ScenePoolResponseWrapper;
import com.wuming.novel.exception.LLMResponseEmptyException;
import com.wuming.novel.mapper.ScenePoolMapper;
import com.wuming.novel.pipeline.RedisStageFailureStore;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.IScenePoolService;
import com.wuming.novel.service.ISceneService;
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
public class ScenePoolService extends ServiceImpl<ScenePoolMapper, ScenePool> implements IScenePoolService {
    private final PromptConfig promptConfig;
    private final ISceneService sceneService;
    private final IJobService jobService;
    private final ChatClient chatClient;
    private final RedisStageFailureStore redisStageFailureStore;

    @Lazy
    @Autowired
    private ScenePoolService self;

    public ScenePoolService(PromptConfig promptConfig, ISceneService sceneService, IJobService jobService, LlmClientFactory clientFactory, RedisStageFailureStore redisStageFailureStore) {
        this.promptConfig = promptConfig;
        this.sceneService = sceneService;
        this.jobService = jobService;
        this.chatClient = clientFactory.defaultClient();
        this.redisStageFailureStore = redisStageFailureStore;
    }

    @Override
    public void divideSceneIntoPool(Long jobId) {
        Job job = jobService.getById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("该job不存在，请创建后重试");
        }
        // 幂等校验
        if(job.getStage().getCode() >= JobStage.POOL_CLASSIFY.getCode()){
            log.info("任务{}已经完成了阶段{}", jobId, JobStage.POOL_CLASSIFY);
            return;
        }

        Long novelId = job.getNovelId();
        String protagonistName = job.getProtagonistName();
        String targetName = job.getTargetName();
        List<Long> targetSceneIds = queryTargetSceneIds(jobId, novelId);


        List<Scene> scenes = sceneService.listByIds(targetSceneIds);
        log.debug("job: {} 小说{}开始场景分池，待处理场景数: {}", jobId, novelId, scenes.size());

        List<CompletableFuture<Void>> futures = scenes.stream()
                .map(scene -> self.doSimpleClassify(scene, jobId, protagonistName, targetName))
                .toList();

        // 便于测试，等待任务完成
        futures.forEach(future -> {
            try {
                future.join();
            }
            catch (Exception e) {
                log.debug("job: {} 场景分池子任务失败，等待统一重试", jobId, e);
            }
        });
    }

    private List<Long> queryTargetSceneIds(Long jobId, Long novelId) {
        List<Long> failedSceneIds = redisStageFailureStore.consumeFailedLongItems(jobId, JobStage.POOL_CLASSIFY);
        if (!failedSceneIds.isEmpty()) {
            return failedSceneIds;
        }
        return sceneService.lambdaQuery()
                .eq(Scene::getNovelId, novelId)
                .select(Scene::getId)
                .list()
                .stream()
                .map(Scene::getId)
                .toList();
    }


    @Async("poolClassifyExecutor")
    protected CompletableFuture<Void> doSimpleClassify(Scene scene, Long jobId, String protagonistName, String targetName){
        try {
            ScenePoolResponseWrapper responseWrapper = chatClient.prompt()
                    .user(u -> u.text(promptConfig.getScenePoolPrompt())
                            .param("protagonistName", protagonistName)
                            .param("targetName", targetName)
                            .param("sceneContent", scene.getContent())
                    )
                    .call()
                    .entity(ScenePoolResponseWrapper.class);

            if(responseWrapper == null || responseWrapper.pools() == null || responseWrapper.pools().isEmpty()){
                throw new LLMResponseEmptyException("任务" + jobId + "场景" + scene.getId() +"分池时llm响应为空");
            }
            List<ScenePoolResponse> responses = responseWrapper.pools();

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
            redisStageFailureStore.recordFailure(jobId, JobStage.POOL_CLASSIFY, scene.getId());
            log.error("处理任务：{}, scene{}分池出现异常", jobId, scene.getId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

}
