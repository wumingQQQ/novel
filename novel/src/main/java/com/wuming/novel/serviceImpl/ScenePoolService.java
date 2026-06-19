package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Sets;
import com.wuming.novel.config.PromptConfig;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Scene;
import com.wuming.novel.domain.entity.rel.ScenePool;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.enums.PoolType;
import com.wuming.novel.domain.llmresponse.ScenePoolResponse;
import com.wuming.novel.mapper.ScenePoolMapper;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.IScenePoolService;
import com.wuming.novel.service.ISceneService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class ScenePoolService extends ServiceImpl<ScenePoolMapper, ScenePool> implements IScenePoolService {
    private final PromptConfig promptConfig;
    private final ISceneService sceneService;
    private final IJobService jobService;
    private final ScenePoolMapper scenePoolMapper;
    private final ChatClient chatClient;

    public ScenePoolService(PromptConfig promptConfig, ISceneService sceneService, IJobService jobService, ScenePoolMapper scenePoolMapper, ChatModel chatModel) {
        this.promptConfig = promptConfig;
        this.sceneService = sceneService;
        this.jobService = jobService;
        this.scenePoolMapper = scenePoolMapper;
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public void divideSceneIntoPool(int jobId) {
        Job job = jobService.getById(jobId);
        // 幂等校验
        if(job.getStage().getCode() >= JobStage.POOL_CLASSIFY.getCode()){
            log.info("任务{}已经完成了阶段{}", jobId, JobStage.POOL_CLASSIFY);
            return;
        }

        int novelId = job.getNovelId();
        List<Integer> finishedSceneIds = queryFinishedSceneIds(novelId);
        Set<Integer> unfinishedSceneIds = computeUnfinishedSceneIds(novelId, finishedSceneIds);


        List<Scene> scenes = sceneService.listByIds(unfinishedSceneIds);

        List<CompletableFuture<String>> futures = scenes.stream()
                .map(scene -> doSimpleClassify(scene, jobId))
                .toList();

        // 便于测试，等待任务完成
        futures.forEach(CompletableFuture::join);
    }

    private List<Integer> queryFinishedSceneIds(int novelId){
        QueryWrapper<ScenePool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("novel_id", novelId)
                .select("scene_id")
                .groupBy("scene_id");
        return scenePoolMapper.selectList(queryWrapper)
                .stream()
                .map(ScenePool::getSceneId)
                .toList();
    }

    private Set<Integer> computeUnfinishedSceneIds(int novelId, List<Integer> finishedSceneIds){
        List<Integer> allSceneIds = sceneService.lambdaQuery()
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected CompletableFuture<String> doSimpleClassify(Scene scene, int jobId){
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
                System.out.printf("scene%d分池时llm响应为空", scene.getId());
                return CompletableFuture.completedFuture("error");
            }

            List<ScenePool> scenePools = new ArrayList<>();

            for(ScenePoolResponse response : responses){
                PoolType poolType = PoolType.fromCode(response.code());

                ScenePool scenePool = new ScenePool();
                scenePool.setSceneId(scene.getId());
                scenePool.setNovelId(scene.getNovelId());
                scenePool.setPoolType(poolType);
                scenePool.setConfidence(response.confidence());
                scenePools.add(scenePool);
            }

            saveBatch(scenePools);

            return CompletableFuture.completedFuture("success");
        } catch (Exception e) {
            System.out.printf("处理scene%d出现异常：%s", scene.getId(), e.getMessage());
            return CompletableFuture.completedFuture("error");
        }
    }

    // TODO 后续考虑做同章场景批量切分，配置场景切分服务使用


}
