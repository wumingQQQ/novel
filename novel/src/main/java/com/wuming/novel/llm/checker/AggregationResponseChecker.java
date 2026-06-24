package com.wuming.novel.llm.checker;

import com.wuming.novel.domain.dto.CharacterProfileDto;
import com.wuming.novel.domain.dto.InteractionProfileDto;
import com.wuming.novel.domain.llmresponse.AggregationResponse;
import com.wuming.novel.exception.LLMResponseEmptyException;
import org.springframework.stereotype.Component;

@Component
public class AggregationResponseChecker {
    public AggregationResponse check(Long jobId, AggregationResponse response) {
        if (response == null) {
            throw new LLMResponseEmptyException("job: " + jobId + " 聚合服务时llm返回空");
        }
        checkCharacterProfile(jobId, response.characterProfile());
        checkInteractionProfile(jobId, response.interactionProfile());
        return response;
    }

    private void checkCharacterProfile(Long jobId, CharacterProfileDto dto) {
        if (dto == null) {
            throw new LlmResponseCheckException("job: " + jobId + " 聚合角色画像为空");
        }
        if (dto.getBasicSetting() == null) {
            throw new LlmResponseCheckException("job: " + jobId + " 聚合角色基础设定为空");
        }
        if (dto.getSpeechStyle() == null) {
            throw new LlmResponseCheckException("job: " + jobId + " 聚合角色说话风格为空");
        }
    }

    private void checkInteractionProfile(Long jobId, InteractionProfileDto dto) {
        if (dto == null) {
            throw new LlmResponseCheckException("job: " + jobId + " 聚合互动画像为空");
        }
    }
}
