package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.wuming.novel.config.PromptConfig;
import com.wuming.novel.config.llm.LlmClientFactory;
import com.wuming.novel.domain.entity.Evidence;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Layer;
import com.wuming.novel.domain.entity.Scene;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.enums.PoolType;
import com.wuming.novel.domain.llmresponse.EvidenceExtractResponse;
import com.wuming.novel.domain.llmresponse.EvidenceExtractResponseWrapper;
import com.wuming.novel.llm.checker.EvidenceExtractResponseChecker;
import com.wuming.novel.mapper.EvidenceMapper;
import com.wuming.novel.pipeline.RedisStageFailureStore;
import com.wuming.novel.service.IEvidenceService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.ILayerService;
import com.wuming.novel.sse.JobProgressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final RedisStageFailureStore redisStageFailureStore;
    private final JobProgressService jobProgressService;
    private final EvidenceExtractResponseChecker evidenceExtractResponseChecker;

    @Lazy
    @Autowired
    private EvidenceService self;

    public EvidenceService(RecallService recallService, ILayerService layerService, IJobService jobService, PromptConfig promptConfig, LlmClientFactory clientFactory, RedisStageFailureStore redisStageFailureStore, JobProgressService jobProgressService, EvidenceExtractResponseChecker evidenceExtractResponseChecker) {
        this.recallService = recallService;
        this.layerService = layerService;
        this.jobService = jobService;
        this.promptConfig = promptConfig;
        this.chatClient = clientFactory.defaultClient();
        this.redisStageFailureStore = redisStageFailureStore;
        this.jobProgressService = jobProgressService;
        this.evidenceExtractResponseChecker = evidenceExtractResponseChecker;
    }

    @Override
    public void extractEvidence(Long jobId) {
        Job job = jobService.getById(jobId);
        if(job.getStage().getCode() >= JobStage.EVIDENCE_EXTRACT.getCode()){
            log.info("任务{}已经完成了阶段{}", jobId, JobStage.EVIDENCE_EXTRACT);
            return;
        }

        Long novelId = job.getNovelId();
        String targetName = job.getTargetName();
        List<Layer> layers = layerService.lambdaQuery().eq(Layer::getNovelId, novelId).orderByAsc(Layer::getLayerIndex).list();
        // 失败的层池，对应layerId:poolType
        Set<String> failedItems = new HashSet<>(redisStageFailureStore.consumeFailedItems(jobId, JobStage.EVIDENCE_EXTRACT));
        boolean retryFailedOnly = !failedItems.isEmpty();   // false意味着没有失败项或者没有跑过

        List<LayerPoolEvidenceTask> tasks = new ArrayList<>();

        // 召回场景
        for (Layer layer : layers) {
            for(PoolType poolType : PoolType.values()){
                String itemKey = evidenceItemKey(layer.getId(), poolType);
                if (retryFailedOnly && !failedItems.contains(itemKey)) {
                    // 没有该层池的key
                    continue;
                }
                if (retryFailedOnly) {
                    cleanEvidenceItem(jobId, layer.getId(), poolType);
                } else if (hasEvidenceItem(jobId, layer.getId(), poolType)) {
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
                tasks.add(new LayerPoolEvidenceTask(partitions, poolType, layer, itemKey));
            }
        }
        jobProgressService.setStageTotalItems(jobId, JobStage.EVIDENCE_EXTRACT, tasks.size());

        AtomicBoolean hasEvidence = new AtomicBoolean(false);
        List<CompletableFuture<Void>> layerPoolFutures = new ArrayList<>();
        for (LayerPoolEvidenceTask task : tasks) {
            CompletableFuture<?>[] partitionFutures = task.partitions().stream()
                    .map(scenes -> self.doMultiExtractEvidence(scenes, jobId, task.poolType(), task.layer(), targetName, task.itemKey()))
                    .toArray(CompletableFuture[]::new);

            CompletableFuture<Void> layerPoolFuture = CompletableFuture.allOf(partitionFutures)
                    .thenRun(() -> {
                        jobProgressService.recordItemSuccess(jobId, JobStage.EVIDENCE_EXTRACT);
                        hasEvidence.set(true);
                    })
                    .exceptionally(e -> {
                        jobProgressService.recordItemFailure(jobId, JobStage.EVIDENCE_EXTRACT);
                        log.debug("job: {} 证据提取层池子任务失败，等待统一重试", jobId, e);
                        return null;
                    });
            layerPoolFutures.add(layerPoolFuture);
        }

        CompletableFuture.allOf(layerPoolFutures.toArray(new CompletableFuture[0])).join();
        if(!hasEvidence.get()){
            throw new IllegalStateException("job: " + jobId + " 证据提取未生成任何证据，请检查场景召回结果或场景分池置信度");
        }

    }

    // TODO 暂时定为确定数值，后续考虑按照章节连贯性在范围内波动
    @Value("${novel.analysis.batch-size}")
    private int batchSize;

    @Async("evidenceExtractExecutor")
    protected CompletableFuture<Void> doMultiExtractEvidence(List<Scene> scenes, Long jobId, PoolType poolType, Layer layer, String targetName, String itemKey) {
        try{

            EvidenceExtractResponseWrapper responseWrapper = chatClient.prompt()
                    .user(u -> u.text(promptConfig.getEvidenceExtractPrompt())
                            .param("targetName", targetName)
                            .param("poolTypeName", poolType.name())
                            .param("layerName", layer.getLayerName())
                            .param("sceneCount", scenes.size())
                            .param("scenes", concatScenes(scenes))
                            .param("poolDescription", promptConfig.getPoolDescription(poolType))
                    )
                    .call()
                    .entity(EvidenceExtractResponseWrapper.class);

            List<EvidenceExtractResponse> responses = evidenceExtractResponseChecker.check(scenes, jobId, layer, poolType, responseWrapper);

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
            redisStageFailureStore.recordFailure(jobId, JobStage.EVIDENCE_EXTRACT, itemKey);
            log.error("layer:{}, pool: {}证据解析时出现异常", layer.getId(), poolType, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private String evidenceItemKey(Long layerId, PoolType poolType) {
        return layerId + ":" + poolType.name();
    }

    private void cleanEvidenceItem(Long jobId, Long layerId, PoolType poolType) {
        lambdaUpdate()
                .eq(Evidence::getJobId, jobId)
                .eq(Evidence::getLayerId, layerId)
                .eq(Evidence::getPoolType, poolType)
                .remove();
    }

    private boolean hasEvidenceItem(Long jobId, Long layerId, PoolType poolType) {
        return lambdaQuery()
                .eq(Evidence::getJobId, jobId)
                .eq(Evidence::getLayerId, layerId)
                .eq(Evidence::getPoolType, poolType)
                .count() > 0;
    }


    private String concatScenes(List<Scene> scenes) {
        return scenes.stream()
                .map(scene -> "[" + scene.getId() + "]: \n" + scene.getContent())
                .collect(Collectors.joining("\n\n"));
    }

    private record LayerPoolEvidenceTask(List<List<Scene>> partitions, PoolType poolType, Layer layer, String itemKey) {
    }
}
