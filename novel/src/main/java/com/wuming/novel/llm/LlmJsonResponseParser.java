package com.wuming.novel.llm;

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

    public <T> T parse(String rawContent, Class<T> targetType) {
        String json = rawContent == null ? "" : rawContent.trim();
        try {
            return objectMapper.readValue(json, targetType);
        } catch (JsonProcessingException firstException) {
            String repairedJson = json + "}";
            try {
                T result = objectMapper.readValue(repairedJson, targetType);
                log.warn(
                        "LLM JSON解析失败后追加右括号修复成功，targetType={}",
                        targetType.getSimpleName(),
                        firstException
                );
                return result;
            } catch (JsonProcessingException ignored) {
                // 暂时只处理末尾缺少一个 } 的情况，其他错误保留原始异常，后续按真实样例补充。
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
    }

    private String abbreviate(String text) {
        if (text == null || text.length() <= RAW_LOG_LIMIT) {
            return text;
        }
        return text.substring(0, RAW_LOG_LIMIT) + "...";
    }
}
