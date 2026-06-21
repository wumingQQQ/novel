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
import com.wuming.novel.exception.LLMResponseEmptyException;
import com.wuming.novel.mapper.EvidenceMapper;
import com.wuming.novel.service.IEvidenceService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.ILayerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EvidenceService extends ServiceImpl<EvidenceMapper, Evidence> implements IEvidenceService {
    private final RecallService recallService;
    private final ILayerService layerService;
    private final IJobService jobService;
    private final PromptConfig promptConfig;
    private final ChatClient chatClient;

    @Lazy
    @Autowired
    private EvidenceService self;

    public EvidenceService(RecallService recallService, ILayerService layerService, IJobService jobService, PromptConfig promptConfig, ChatModel chatModel) {
        this.recallService = recallService;
        this.layerService = layerService;
        this.jobService = jobService;
        this.promptConfig = promptConfig;
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public boolean extractEvidence(Long jobId) {
        Job job = jobService.getById(jobId);
        if(job.getStage().getCode() >= JobStage.EVIDENCE_EXTRACT.getCode()){
            log.info("任务{}已经完成了阶段{}", jobId, JobStage.EVIDENCE_EXTRACT);
            return true;
        }

        Long novelId = job.getNovelId();
        String targetName = job.getTargetName();
        // TODO 后续考虑同小说不同人物
        List<Layer> layers = layerService.lambdaQuery().eq(Layer::getNovelId, novelId).orderByAsc(Layer::getLayerIndex).list();

        AtomicBoolean allSuccess = new AtomicBoolean(true);

        for (Layer layer : layers) {
            for(PoolType poolType : PoolType.values()){
                // 判断该层该池是否处理完毕
                if(lambdaQuery()
                        .eq(Evidence::getLayerId, layer.getId())
                        .eq(Evidence::getJobId, jobId)
                        .eq(Evidence::getPoolType, poolType)
                        .count() >0
                ){
                    continue;
                }

                List<Scene> scenes = recallService.recallScenes(
                        novelId, jobId, poolType,
                        layer.getStartChapterSequence(),
                        layer.getEndChapterSequence()
                );
                log.debug("job: {} layer: {} pool: {} 召回场景数: {}", jobId, layer.getId(), poolType, scenes.size());
                if(scenes.isEmpty()){
                    continue;
                }

                List<List<Scene>> partitions = Lists.partition(scenes, batchSize);
                log.debug("job: {} layer: {} pool: {} 证据提取批次数: {}", jobId, layer.getId(), poolType, partitions.size());
                List<CompletableFuture<Void>> futures = partitions.stream()
                        .map(list -> self.doMultiExtractEvidence(list, jobId, poolType, layer, targetName))
                        .toList();

                futures.forEach(future -> {
                    try {
                        future.join();
                    }
                    catch (Exception e) {
                        allSuccess.set(false);
                    }
                });
            }
        }
        return allSuccess.get();

    }

    // TODO 暂时定为确定数值，后续考虑按照章节连贯性在范围内波动
    @Value("${novel.analysis.batch-size}")
    private int batchSize;

    @Async("evidenceExtractExecutor")
    protected CompletableFuture<Void> doMultiExtractEvidence(List<Scene> scenes, Long jobId, PoolType poolType, Layer layer, String targetName) {
        try{

            EvidenceExtractResponse[] responses = chatClient.prompt()
                    .user(u -> u.text(promptConfig.getEvidenceExtractPrompt())
                            .param("targetName", targetName)
                            .param("poolTypeName", poolType.name())
                            .param("layerName", layer.getLayerName())
                            .param("sceneCount", scenes.size())
                            .param("scenes", concatScenes(scenes))
                            .param("poolDescription", promptConfig.getPoolDescription(poolType))
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
                throw new LLMResponseEmptyException("证据提取：layer-" + layer.getId() +", pool-" + poolType);
            }

            List<Evidence> evidences = new ArrayList<>();
            for (EvidenceExtractResponse response : responses) {
                Evidence evidence = new Evidence();
                evidence.setNovelId(layer.getNovelId());
                evidence.setLayerId(layer.getId());
                evidence.setJobId(jobId);
                evidence.setPoolType(poolType);
                evidence.setConclusion(response.conclusion());
                evidence.setConfidence(response.confidence());
                evidence.setSupportingQuotes(response.supportingQuotes());
                evidence.setSceneIds(response.sceneIds());

                evidences.add(evidence);
            }

            self.saveBatch(evidences);
            log.debug("job: {} layer: {} pool: {} 证据提取完成，输入场景数: {}, 证据数: {}", jobId, layer.getId(), poolType, scenes.size(), evidences.size());

            return CompletableFuture.completedFuture(null);
        }
        catch (Exception e){
            log.error("layer:{}, pool: {}证据解析时出现异常", layer.getId(), poolType, e);
            return CompletableFuture.failedFuture(e);
        }
    }


    private String concatScenes(List<Scene> scenes) {
        return scenes.stream()
                .map(scene -> "[" + scene.getId() + "]: \n" + scene.getContent())
                .collect(Collectors.joining("\n\n"));
    }
}
