package com.wuming.novel.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuming.novel.domain.llmresponse.SceneSplitResponseWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmJsonResponseParser {
    private static final int RAW_LOG_LIMIT = 1200;
    private static final Pattern SCENE_ITEM_PATTERN = Pattern.compile(
            "\"sequence\"\\s*:\\s*(\\d+)\\s*,?\\s*\"anchor\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
            Pattern.DOTALL
    );
    private static final Pattern ANCHOR_VALUE_PATTERN = Pattern.compile(
            "\"((?:\\\\.|[^\"\\\\])*)\"",
            Pattern.DOTALL
    );

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
                        targetType.getSimpleName()
                );
                return result;
            } catch (JsonProcessingException ignored) {
                // 继续尝试下面已知的场景数组结构修复。
            }

            String repairedSceneJson = repairSceneArrayItems(json, targetType);
            if (!repairedSceneJson.equals(json)) {
                try {
                    T result = objectMapper.readValue(repairedSceneJson, targetType);
                    log.warn(
                            "LLM JSON解析失败后场景数组结构修复成功，targetType={}",
                            targetType.getSimpleName()
                    );
                    return result;
                } catch (JsonProcessingException ignored) {
                    // 只修复已知的 scenes 数组对象缺失问题，失败后保留原始异常。
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
    }

    private String repairSceneArrayItems(String json, Class<?> targetType) {
        if (targetType != SceneSplitResponseWrapper.class) {
            return json;
        }

        Matcher matcher = SCENE_ITEM_PATTERN.matcher(json);
        StringBuilder anchors = new StringBuilder();
        int count = 0;
        while (matcher.find()) {
            if (count > 0) {
                anchors.append(',');
            }
            anchors.append(toJsonString(matcher.group(2)));
            count++;
        }
        if (count > 0) {
            return "{\"anchors\":[" + anchors + "]}";
        }

        if (!json.contains("\"anchors\"")) {
            return json;
        }
        int arrayStart = json.indexOf('[');
        int arrayEnd = json.lastIndexOf(']');
        if (arrayStart < 0 || arrayEnd <= arrayStart) {
            return json;
        }

        matcher = ANCHOR_VALUE_PATTERN.matcher(json.substring(arrayStart + 1, arrayEnd));
        while (matcher.find()) {
            if (count > 0) {
                anchors.append(',');
            }
            anchors.append(toJsonString(matcher.group(1)));
            count++;
        }
        return count == 0 ? json : "{\"anchors\":[" + anchors + "]}";
    }

    private String toJsonString(String value) {
        try {
            String unescaped = objectMapper.readValue("\"" + value + "\"", String.class);
            return objectMapper.writeValueAsString(unescaped);
        } catch (JsonProcessingException e) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException ignored) {
                return "\"\"";
            }
        }
    }

    private String abbreviate(String text) {
        if (text == null || text.length() <= RAW_LOG_LIMIT) {
            return text;
        }
        return text.substring(0, RAW_LOG_LIMIT) + "...";
    }
}
