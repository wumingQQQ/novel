package com.wuming.novel.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.wuming.novel.config.PromptConfig;
import com.wuming.novel.config.llm.LlmClientFactory;
import com.wuming.novel.domain.dto.FullPortraitDto;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Scene;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.enums.PoolType;
import com.wuming.novel.domain.llmresponse.AggregationResponse;
import com.wuming.novel.llm.parser.LlmJsonResponseParser;
import com.wuming.novel.llm.checker.AggregationResponseChecker;
import com.wuming.novel.service.IJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProfileDetailEnhanceService {
    private final IJobService jobService;
    private final RecallService recallService;
    private final FullPortraitPersistenceService fullPortraitPersistenceService;
    private final PromptConfig promptConfig;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AggregationResponseChecker aggregationResponseChecker;
    private final LlmJsonResponseParser llmJsonResponseParser;
    private final Environment environment;

    @Value("${novel.profile-enhance.threshold:0.7}")
    private double threshold;

    @Value("${novel.profile-enhance.default-top-k:2}")
    private int defaultTopKPerPool;

    @Value("${novel.profile-enhance.batch-size:5}")
    private int batchSize;

    public ProfileDetailEnhanceService(
            IJobService jobService,
            RecallService recallService,
            FullPortraitPersistenceService fullPortraitPersistenceService,
            PromptConfig promptConfig,
            LlmClientFactory clientFactory,
            ObjectMapper objectMapper,
            AggregationResponseChecker aggregationResponseChecker,
            LlmJsonResponseParser llmJsonResponseParser,
            Environment environment
    ) {
        this.jobService = jobService;
        this.recallService = recallService;
        this.fullPortraitPersistenceService = fullPortraitPersistenceService;
        this.promptConfig = promptConfig;
        this.chatClient = clientFactory.taskClient(
                LlmClientFactory.TASK_PROFILE_DETAIL_ENHANCE
        );
        this.objectMapper = objectMapper;
        this.aggregationResponseChecker = aggregationResponseChecker;
        this.llmJsonResponseParser = llmJsonResponseParser;
        this.environment = environment;
    }

    public void enhance(Long jobId) {
        Job job = jobService.getById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("该job不存在，请创建后重试");
        }
        if (job.getStage().getCode() >= JobStage.PROFILE_DETAIL_ENHANCE.getCode()) {
            log.info("任务{}已经完成了阶段{}", jobId, JobStage.PROFILE_DETAIL_ENHANCE);
            return;
        }

        FullPortraitDto currentProfile = fullPortraitPersistenceService.getByJobId(jobId);
        if (currentProfile == null) {
            throw new IllegalStateException("job: " + jobId + " 初版画像不存在，无法进行细节增强");
        }

        List<Scene> scenes = recallSupplementScenes(job);
        if (scenes.isEmpty()) {
            log.info("job: {} 未召回画像增强场景，跳过细节增强", jobId);
            return;
        }
        int effectiveBatchSize = Math.max(batchSize, 1);
        List<List<Scene>> partitions = Lists.partition(scenes, effectiveBatchSize);
        log.debug(
                "job: {} 画像细节增强召回总场景数: {}, 批次数: {}, batchSize: {}",
                jobId,
                scenes.size(),
                partitions.size(),
                effectiveBatchSize
        );

        try {
            for (List<Scene> partition : partitions) {
                log.debug(
                        "job: {} 画像细节增强开始，批次场景数: {}, 当前画像已生成",
                        jobId,
                        partition.size()
                );
                AggregationResponse response = requestEnhance(job, currentProfile, partition);
                response = aggregationResponseChecker.check(jobId, response);
                currentProfile.setCharacterProfile(response.characterProfile());
                currentProfile.setInteractionProfile(response.interactionProfile());
                log.debug("job: {} 画像细节增强完成，批次场景数: {}", jobId, partition.size());
            }
            fullPortraitPersistenceService.replace(jobId, currentProfile);
        } catch (Exception e) {
            log.error("job: {} 画像细节增强失败", jobId, e);
            throw new RuntimeException("画像细节增强失败", e);
        }
    }

    private List<Scene> recallSupplementScenes(Job job) {
        Map<Long, Scene> sceneMap = new LinkedHashMap<>();
        for (PoolType poolType : PoolType.values()) {
            int topK = topKFor(poolType);
            List<Scene> scenes = recallService.recallTopScenes(
                    job.getNovelId(),
                    job.getId(),
                    poolType,
                    threshold,
                    topK
            );
            scenes.forEach(scene -> sceneMap.putIfAbsent(scene.getId(), scene));
            log.debug(
                    "job: {} pool: {} 画像增强召回场景数: {}, topK: {}",
                    job.getId(),
                    poolType,
                    scenes.size(),
                    topK
            );
        }
        return sceneMap.values().stream().toList();
    }

    private int topKFor(PoolType poolType) {
        Integer configuredTopK = environment.getProperty(
                "novel.profile-enhance.top-k." + poolType.name(),
                Integer.class
        );
        if (configuredTopK == null || configuredTopK < 0) {
            return defaultTopKPerPool;
        }
        return configuredTopK;
    }

    private AggregationResponse requestEnhance(
            Job job,
            FullPortraitDto currentProfile,
            List<Scene> scenes
    ) {
        String rawContent = chatClient.prompt()
                .user(u -> {
                            try {
                                u.text(promptConfig.getProfileDetailEnhancePrompt())
                                        .param("protagonistName", job.getProtagonistName())
                                        .param("targetName", job.getTargetName())
                                        .param("currentProfile", objectMapper.writeValueAsString(currentProfile))
                                        .param("scenes", concatScenes(scenes));
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        }
                )
                .call()
                .content();
        return llmJsonResponseParser.parse(rawContent, AggregationResponse.class);
    }

    private String concatScenes(List<Scene> scenes) {
        return scenes.stream()
                .map(scene -> "[" + scene.getId() + "]:\n" + scene.getContent())
                .collect(Collectors.joining("\n\n"));
    }
}
