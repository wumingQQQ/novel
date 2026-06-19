package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.wuming.novel.config.PromptConfig;
import com.wuming.novel.domain.entity.Evidence;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Layer;
import com.wuming.novel.domain.entity.Scene;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.enums.PoolType;
import com.wuming.novel.domain.llmresponse.EvidenceExtractResponse;
import com.wuming.novel.mapper.EvidenceMapper;
import com.wuming.novel.service.IEvidenceService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.ILayerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EvidenceService extends ServiceImpl<EvidenceMapper, Evidence> implements IEvidenceService {
    private final RecallService recallService;
    private final ILayerService layerService;
    private final IJobService jobService;
    private final PromptConfig promptConfig;
    private final ChatClient chatClient;

    public EvidenceService(RecallService recallService, ILayerService layerService, IJobService jobService, PromptConfig promptConfig, ChatModel chatModel) {
        this.recallService = recallService;
        this.layerService = layerService;
        this.jobService = jobService;
        this.promptConfig = promptConfig;
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public void extractEvidence(int jobId) {
        Job job = jobService.getById(jobId);
        if(job.getStage().getCode() >= JobStage.EVIDENCE_EXTRACT.getCode()){
            log.info("任务{}已经完成了阶段{}", jobId, JobStage.EVIDENCE_EXTRACT);
            return;
        }

        int novelId = job.getNovelId();
        String targetName = job.getTargetName();
        // TODO 后续考虑同小说不同人物


        List<Layer> layers = layerService.lambdaQuery().eq(Layer::getNovelId, novelId).orderByAsc(Layer::getLayerIndex).list();

        for (Layer layer : layers) {
            for(PoolType poolType : PoolType.values()){
                // 判断该层该池是否处理完毕
                if(lambdaQuery()
                        .eq(Evidence::getLayerId, layer.getId())
                        .eq(Evidence::getPoolType, poolType)
                        .count() >0
                ){
                    continue;
                }

                List<Scene> scenes = recallService.recallScenes(
                        novelId, poolType,
                        layer.getStartChapterSequence(),
                        layer.getEndChapterSequence()
                );
                if(scenes.isEmpty()){
                    continue;
                }
                List<List<Scene>> partitions = Lists.partition(scenes, batchSize);
                List<CompletableFuture<String>> futures = partitions.stream()
                        .map(list -> doMultiExtractEvidence(list, poolType, layer, targetName))
                        .toList();

                futures.forEach(CompletableFuture::join);
            }
        }

    }

    // TODO 暂时定为确定数值，后续考虑按照章节连贯性在范围内波动
    @Value("${novel.analysis.batch-size}")
    private int batchSize;

    @Async("evidenceExtractExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected CompletableFuture<String> doMultiExtractEvidence(List<Scene> scenes, PoolType poolType, Layer layer, String targetName) {
        try{

            EvidenceExtractResponse[] responses = chatClient.prompt()
                    .user(u -> u.text(promptConfig.getEvidenceExtractPrompt(poolType))
                            .param("targetName", targetName)
                            .param("poolTypeName", poolType.name())
                            .param("layerName", layer.getLayerName())
                            .param("sceneCount", scenes.size())
                            .param("scenes", concatScenes(scenes))
                    )
                    .options(OpenAiChatOptions.builder()
                            .responseFormat(ResponseFormat.builder()
                                    .type(ResponseFormat.Type.JSON_OBJECT)
                                    .build()
                            )
                            .build()
                    )
                    .call()
                    .entity(EvidenceExtractResponse[].class);

            if(responses == null || responses.length == 0){
                System.out.println("证据解析时llm响应为空");
                return CompletableFuture.completedFuture("fail");
            }

            List<Evidence> evidences = new ArrayList<>();
            for (EvidenceExtractResponse response : responses) {
                Evidence evidence = new Evidence();
                evidence.setNovelId(layer.getNovelId());
                evidence.setLayerId(layer.getId());
                evidence.setPoolType(poolType);
                evidence.setConclusion(response.conclusion());
                evidence.setConfidence(response.confidence());
                evidence.setSupportQuotes(response.supportingQuotes());
                evidence.setSceneIndices(response.sceneIndices());

                evidences.add(evidence);
            }

            saveBatch(evidences);

            return CompletableFuture.completedFuture("success");
        }
        catch (Exception e){
            System.out.println("抛出异常" + e.getMessage());
            return CompletableFuture.completedFuture("error");
        }
    }


    private String concatScenes(List<Scene> scenes) {
        return scenes.stream()
                .map(scene -> "[" + scene.getId() + "]: \n" + scene.getContent())
                .collect(Collectors.joining("\n\n"));
    }
}
