package com.wuming.novel.llm.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmJsonResponseParser {
    private static final int RAW_LOG_LIMIT = 1200;

    private final ObjectMapper objectMapper;
    private final LlmJsonResponseRepairer responseRepairer;

    public <T> T parse(String rawContent, Class<T> targetType) {
        String json = rawContent == null ? "" : rawContent.trim();
        try {
            return objectMapper.readValue(json, targetType);
        } catch (JsonProcessingException firstException) {
            return parseWithRepair(json, targetType, firstException);
        }
    }

    private <T> T parseWithRepair(
            String json,
            Class<T> targetType,
            JsonProcessingException firstException
    ) {
        for (JsonRepairCandidate repair
                : responseRepairer.repairCandidates(json, targetType)) {
            try {
                T result = objectMapper.readValue(repair.json(), targetType);
                log.warn(
                        "LLM JSON解析失败后{}成功，targetType={}",
                        repair.description(),
                        targetType.getSimpleName()
                );
                return result;
            } catch (JsonProcessingException ignored) {
                // 当前候选修复失败，继续尝试下一个候选。
            }
        }

        log.warn(
                "LLM JSON解析失败，targetType={}, raw={}",
                targetType.getSimpleName(),
                abbreviate(json),
                firstException
        );
        throw new LlmJsonParseException(
                "LLM JSON解析失败，targetType=" + targetType.getSimpleName(),
                firstException
        );
    }

    private String abbreviate(String text) {
        if (text == null || text.length() <= RAW_LOG_LIMIT) {
            return text;
        }
        return text.substring(0, RAW_LOG_LIMIT) + "...";
    }
}
