package com.wuming.novel.llm.checker;

import com.wuming.novel.domain.entity.Layer;
import com.wuming.novel.domain.entity.Scene;
import com.wuming.novel.domain.enums.PoolType;
import com.wuming.novel.domain.llmresponse.EvidenceExtractResponse;
import com.wuming.novel.domain.llmresponse.EvidenceExtractResponseWrapper;
import com.wuming.novel.exception.LLMResponseEmptyException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EvidenceExtractResponseChecker {

    public List<EvidenceExtractResponse> check(List<Scene> inputScenes, Long jobId, Layer layer, PoolType poolType, EvidenceExtractResponseWrapper responseWrapper) {
        if (responseWrapper == null || responseWrapper.evidences() == null || responseWrapper.evidences().isEmpty()) {
            throw new LLMResponseEmptyException("证据提取：layer-" + layer.getId() + ", pool-" + poolType);
        }

        Set<Long> validSceneIds = inputScenes.stream()
                .map(Scene::getId)
                .collect(Collectors.toSet());
        for (int i = 0; i < responseWrapper.evidences().size(); i++) {
            checkEvidence(validSceneIds, jobId, layer, poolType, responseWrapper.evidences().get(i), i);
        }
        return responseWrapper.evidences();
    }

    private void checkEvidence(
            Set<Long> validSceneIds,
            Long jobId,
            Layer layer,
            PoolType poolType,
            EvidenceExtractResponse response,
            int index
    ) {
        if (response == null) {
            throw new LlmResponseCheckException("job: " + jobId + " layer: " + layer.getId() + " pool: " + poolType + " 证据为空，index: " + index);
        }
        if (response.conclusion() == null || response.conclusion().isBlank()) {
            throw new LlmResponseCheckException("job: " + jobId + " layer: " + layer.getId() + " pool: " + poolType + " 证据结论为空，index: " + index);
        }
        if (response.confidence() == null || response.confidence() < 0 || response.confidence() > 1) {
            throw new LlmResponseCheckException("job: " + jobId
                    + " layer: " + layer.getId()
                    + " pool: " + poolType
                    + " 证据置信度非法，index: " + index
                    + ", confidence: " + response.confidence());
        }
        if (response.sceneIds() == null || response.sceneIds().isEmpty()) {
            throw new LlmResponseCheckException("job: " + jobId + " layer: " + layer.getId() + " pool: " + poolType + " 证据sceneIds为空，index: " + index);
        }
        for (Long sceneId : response.sceneIds()) {
            if (sceneId == null || !validSceneIds.contains(sceneId)) {
                throw new LlmResponseCheckException("job: " + jobId
                        + " layer: " + layer.getId()
                        + " pool: " + poolType
                        + " 证据sceneId不在输入场景中，index: " + index
                        + ", sceneId: " + sceneId);
            }
        }
        if (response.supportingQuotes() == null || response.supportingQuotes().isEmpty()) {
            throw new LlmResponseCheckException("job: " + jobId + " layer: " + layer.getId() + " pool: " + poolType + " 支撑原文为空，index: " + index);
        }
        for (String quote : response.supportingQuotes()) {
            checkQuote(jobId, layer, poolType, quote, index);
        }
    }

    private void checkQuote(Long jobId, Layer layer, PoolType poolType, String quote, int index) {
        if (quote == null || quote.isBlank()) {
            throw new LlmResponseCheckException("job: " + jobId + " layer: " + layer.getId() + " pool: " + poolType + " 支撑原文为空白，index: " + index);
        }
    }
}
