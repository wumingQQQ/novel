package com.wuming.novel.llm.checker;

import com.wuming.novel.domain.entity.Scene;
import com.wuming.novel.domain.enums.PoolType;
import com.wuming.novel.domain.llmresponse.ScenePoolResponse;
import com.wuming.novel.domain.llmresponse.ScenePoolResponseWrapper;
import com.wuming.novel.exception.LLMResponseEmptyException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ScenePoolResponseChecker {
    public List<ScenePoolResponse> check(Long jobId, Scene scene, ScenePoolResponseWrapper responseWrapper) {
        if (responseWrapper == null || responseWrapper.pools() == null || responseWrapper.pools().isEmpty()) {
            throw new LLMResponseEmptyException("任务" + jobId + "场景" + scene.getId() + "分池时llm响应为空");
        }

        Set<String> poolCodes = new HashSet<>();
        for (ScenePoolResponse response : responseWrapper.pools()) {
            checkPoolResponse(jobId, scene, response, poolCodes);
        }
        if(poolCodes.size() != PoolType.values().length){
            throw new LlmResponseCheckException("任务" + jobId + "场景" + scene.getId() +"缺少某池响应");
        }
        return responseWrapper.pools();
    }

    private void checkPoolResponse(Long jobId, Scene scene, ScenePoolResponse response, Set<String> poolCodes) {
        if (response == null) {
            throw new LlmResponseCheckException("任务" + jobId + "场景" + scene.getId() + "分池结果为空");
        }
        if (response.code() == null || response.code().isBlank()) {
            throw new LlmResponseCheckException("任务" + jobId + "场景" + scene.getId() + "分池编码为空");
        }
        PoolType.fromCode(response.code());
        if (!poolCodes.add(response.code())) {
            throw new LlmResponseCheckException("任务" + jobId + "场景" + scene.getId() + "分池编码重复，code: " + response.code());
        }
        if (response.confidence() < 0 || response.confidence() > 1) {
            throw new LlmResponseCheckException("任务" + jobId
                    + "场景" + scene.getId()
                    + "分池置信度越界，code: " + response.code()
                    + ", confidence: " + response.confidence());
        }
    }
}
