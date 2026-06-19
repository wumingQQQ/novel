package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.config.PromptConfig;
import com.wuming.novel.domain.entity.Scene;
import com.wuming.novel.domain.entity.rel.ScenePool;
import com.wuming.novel.domain.enums.PoolType;
import com.wuming.novel.domain.llmresponse.ScenePoolResponse;
import com.wuming.novel.mapper.ScenePoolMapper;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.IScenePoolService;
import com.wuming.novel.service.ISceneService;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class ScenePoolService extends ServiceImpl<ScenePoolMapper, ScenePool> implements IScenePoolService {
    private final PromptConfig promptConfig;
    private final ISceneService sceneService;
    private final IJobService jobService;
    private final ChatClient chatClient;

    public ScenePoolService(PromptConfig promptConfig, ISceneService sceneService, IJobService jobService, ChatModel chatModel) {
        this.promptConfig = promptConfig;
        this.sceneService = sceneService;
        this.jobService = jobService;
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Value("${novel.sample.protagonistName}")
    private String protagonistName;
    @Value("${novel.sample.targetName}")
    private String targetName;

    @Override
    public void divideSceneIntoPool(int jobId) {
        int novelId = jobService.getById(jobId).getNovelId();
        // 幂等校验
        Long count = sceneService.lambdaQuery().eq(Scene::getNovelId, novelId).count();
        if(count == 0){
            System.out.println("不存在该小说的章节，请先进行分章处理");
            return;
        }

        List<Scene> scenes = sceneService.lambdaQuery().eq(Scene::getNovelId, novelId).list();
        Scene sample = scenes.get(0);
        int sceneId = sample.getId();
        count = lambdaQuery().eq(ScenePool::getSceneId, sceneId).count();
        if(count > 0){
            System.out.println("场景已经做过分池处理");
            return;
        }

        List<CompletableFuture<String>> futures = scenes.stream().map(this::doSimpleClassify).toList();

        // 便于测试，等待任务完成
        futures.forEach(CompletableFuture::join);
    }


    @Async("poolClassifyExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected CompletableFuture<String> doSimpleClassify(Scene scene){
        try {
            var converter = new BeanOutputConverter<>(ScenePoolResponse[].class);

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
                    .entity(converter);

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
