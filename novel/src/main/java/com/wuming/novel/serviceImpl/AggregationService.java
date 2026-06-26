package com.wuming.novel.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.wuming.novel.config.PromptConfig;
import com.wuming.novel.config.llm.LlmClientFactory;
import com.wuming.novel.domain.dto.FullPortraitDto;
import com.wuming.novel.domain.entity.Evidence;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Layer;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.enums.PoolType;
import com.wuming.novel.domain.llmresponse.AggregationResponse;
import com.wuming.novel.llm.parser.LlmJsonResponseParser;
import com.wuming.novel.llm.checker.AggregationResponseChecker;
import com.wuming.novel.service.IEvidenceService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.ILayerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class AggregationService {
    private final IEvidenceService evidenceService;
    private final ILayerService layerService;
    private final IJobService jobService;
    private final FullPortraitPersistenceService fullPortraitPersistenceService;
    private final PromptConfig promptConfig;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AggregationResponseChecker aggregationResponseChecker;
    private final LlmJsonResponseParser llmJsonResponseParser;


    public AggregationService(
            IEvidenceService evidenceService,
            ILayerService layerService,
            IJobService jobService,
            FullPortraitPersistenceService fullPortraitPersistenceService,
            PromptConfig promptConfig,
            LlmClientFactory clientFactory,
            ObjectMapper objectMapper,
            AggregationResponseChecker aggregationResponseChecker,
            LlmJsonResponseParser llmJsonResponseParser
    ) {
        this.evidenceService = evidenceService;
        this.layerService = layerService;
        this.jobService = jobService;
        this.fullPortraitPersistenceService = fullPortraitPersistenceService;
        this.promptConfig = promptConfig;
        this.chatClient = clientFactory.taskClient(LlmClientFactory.TASK_AGGREGATION);
        this.objectMapper = objectMapper;
        this.aggregationResponseChecker = aggregationResponseChecker;
        this.llmJsonResponseParser = llmJsonResponseParser;
    }


    public void aggregation(Long jobId) {
        Job job = jobService.getById(jobId);
        if(job.getStage().getCode() >= JobStage.PROFILE_AGGREGATION.getCode()){
            log.info("任务{}已经完成了阶段{}", jobId, JobStage.PROFILE_AGGREGATION);
            return;
        }

        Long novelId = job.getNovelId();
        String protagonistName = job.getProtagonistName();
        String targetName = job.getTargetName();

        try {
            List<Layer> layers = layerService.lambdaQuery().eq(Layer::getNovelId, novelId).orderByAsc(Layer::getLayerIndex).list();
            FullPortraitDto fullPortrait = new FullPortraitDto();
            fullPortrait.getCharacterProfile().getBasicSetting().setCharacterName(targetName);
            fullPortrait.getInteractionProfile().setProtagonistName(protagonistName);
            for(Layer layer : layers) {
                for(PoolType poolType : PoolType.values()) {
                    List<Evidence> evidences = evidenceService.lambdaQuery()
                            .eq(Evidence::getNovelId, novelId)
                            .eq(Evidence::getLayerId, layer.getId())
                            .eq(Evidence::getPoolType, poolType)
                            .eq(Evidence::getJobId, job.getId())
                            .list();

                    if(evidences.isEmpty()) {
                        continue;
                    }

                    log.debug("job: {} layer: {} pool: {} 聚合证据数: {}", jobId, layer.getId(), poolType, evidences.size());
                    List<List<Evidence>> partition = Lists.partition(evidences, 20);
                    for(List<Evidence> evidenceList : partition) {
                        AggregationResponse response = aggregationPool(evidenceList, layer, fullPortrait);
                        response = aggregationResponseChecker.check(jobId, response);

                        copyAggregationResponse(fullPortrait, response);
                        log.debug("job: {} layer: {} pool: {} 画像聚合批次完成，批次证据数: {}", jobId, layer.getId(), poolType, evidenceList.size());
                    }
                }
            }
            fullPortraitPersistenceService.replace(jobId, fullPortrait);
        } catch (Exception e) {
            log.error("job:{}：画像聚合失败",  jobId, e);
            throw new RuntimeException("画像聚合失败", e);
        }
    }


    private AggregationResponse aggregationPool(List<Evidence> evidences, Layer layer, FullPortraitDto fullPortrait) {

        String rawContent = chatClient.prompt()
                .user(u -> {
                            try {
                                u.text(promptConfig.getAggregationPrompt())
                                        .param("layerName", layer.getLayerName())
                                        .param("poolType", evidences.get(0).getPoolType().name())
                                        .param("evidences", formatEvidences(evidences))
                                        // 将之前的画像填入
                                        .param("currentProfile", objectMapper.writeValueAsString(fullPortrait));
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        }
                )
                .call()
                .content();
        return llmJsonResponseParser.parse(rawContent, AggregationResponse.class);
    }

    private void copyAggregationResponse(FullPortraitDto fullPortrait, AggregationResponse aggregationResponse) {
        fullPortrait.setCharacterProfile(aggregationResponse.characterProfile());
        fullPortrait.setInteractionProfile(aggregationResponse.interactionProfile());
    }

    private String formatEvidences(List<Evidence> evidences) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < evidences.size(); i++) {
            Evidence e = evidences.get(i);
            sb.append("【证据").append(i + 1).append("】\n");
            sb.append("结论：").append(e.getConclusion()).append("\n");
            sb.append("置信度：").append(String.format("%.2f", e.getConfidence())).append("\n");
            sb.append("原文支撑：\n");
            for (String quote : e.getSupportingQuotes()) {
                sb.append("  > \"").append(quote).append("\"\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

}
