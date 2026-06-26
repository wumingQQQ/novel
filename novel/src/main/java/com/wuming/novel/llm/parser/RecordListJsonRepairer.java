package com.wuming.novel.llm.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class RecordListJsonRepairer {
    private static final Pattern JSON_KEY_PATTERN = Pattern.compile(
            "\"([A-Za-z][A-Za-z0-9_]*)\"\\s*:",
            Pattern.DOTALL
    );

    private final ObjectMapper objectMapper;

    String repair(String json, Class<?> targetType) {
        ListWrapperInfo wrapperInfo = resolveListWrapper(targetType);
        if (wrapperInfo == null || !wrapperInfo.elementType().isRecord()) {
            return json;
        }

        String arrayBody = findArrayBody(json, wrapperInfo.fieldName());
        if (arrayBody == null) {
            return json;
        }

        List<String> fields = List.of(
                wrapperInfo.elementType().getRecordComponents()
        ).stream().map(RecordComponent::getName).toList();
        List<Map<String, String>> items = extractRecordItems(
                arrayBody,
                fields,
                Set.copyOf(fields)
        );

        if (items.isEmpty()
                || items.stream().anyMatch(item -> !item.keySet()
                .containsAll(fields))) {
            return json;
        }

        return buildWrapperJson(wrapperInfo.fieldName(), fields, items);
    }

    private ListWrapperInfo resolveListWrapper(Class<?> targetType) {
        if (!targetType.isRecord()) {
            return null;
        }

        RecordComponent[] components = targetType.getRecordComponents();
        if (components.length != 1 || components[0].getType() != List.class) {
            return null;
        }

        Type genericType = components[0].getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return null;
        }

        Type actualType = parameterizedType.getActualTypeArguments()[0];
        if (!(actualType instanceof Class<?> elementType)) {
            return null;
        }

        return new ListWrapperInfo(components[0].getName(), elementType);
    }

    private String findArrayBody(String json, String fieldName) {
        String quotedFieldName = "\"" + fieldName + "\"";
        int fieldIndex = json.indexOf(quotedFieldName);
        if (fieldIndex < 0) {
            return null;
        }

        int arrayStart = json.indexOf('[', fieldIndex + quotedFieldName.length());
        int arrayEnd = json.lastIndexOf(']');
        if (arrayStart < 0 || arrayEnd <= arrayStart) {
            return null;
        }

        return json.substring(arrayStart + 1, arrayEnd);
    }

    private List<Map<String, String>> extractRecordItems(
            String arrayBody,
            List<String> fields,
            Set<String> fieldSet
    ) {
        String firstField = fields.get(0);
        List<Map<String, String>> items = new ArrayList<>();
        Map<String, String> current = new LinkedHashMap<>();
        int index = 0;

        while (index < arrayBody.length()) {
            KeyMatch keyMatch = findNextField(arrayBody, index, fieldSet);
            if (keyMatch == null) {
                break;
            }

            if (shouldStartNextItem(keyMatch.name(), firstField, current)) {
                items.add(current);
                current = new LinkedHashMap<>();
            }

            ValueMatch valueMatch = readValue(arrayBody, keyMatch.valueStart());
            if (valueMatch == null) {
                break;
            }

            current.put(keyMatch.name(), valueMatch.value());
            index = valueMatch.end();
        }

        if (!current.isEmpty()) {
            items.add(current);
        }
        return items;
    }

    private boolean shouldStartNextItem(
            String fieldName,
            String firstField,
            Map<String, String> current
    ) {
        return !current.isEmpty()
                && (fieldName.equals(firstField)
                || current.containsKey(fieldName));
    }

    private KeyMatch findNextField(
            String text,
            int start,
            Set<String> fieldSet
    ) {
        Matcher matcher = JSON_KEY_PATTERN.matcher(text);
        matcher.region(start, text.length());
        while (matcher.find()) {
            String field = matcher.group(1);
            if (fieldSet.contains(field)) {
                return new KeyMatch(field, matcher.end());
            }
        }
        return null;
    }

    private ValueMatch readValue(String text, int start) {
        int valueStart = skipWhitespace(text, start);
        if (valueStart >= text.length()) {
            return null;
        }

        char first = text.charAt(valueStart);
        if (first == '"') {
            return readQuotedValue(text, valueStart);
        }
        if (first == '[' || first == '{') {
            return readBalancedValue(text, valueStart);
        }
        return readScalarValue(text, valueStart);
    }

    private ValueMatch readQuotedValue(String text, int start) {
        boolean escaped = false;
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return new ValueMatch(text.substring(start, i + 1), i + 1);
            }
        }
        return null;
    }

    private ValueMatch readBalancedValue(String text, int start) {
        ArrayDeque<Character> expectedClosers = new ArrayDeque<>();
        expectedClosers.push(text.charAt(start) == '[' ? ']' : '}');
        boolean inString = false;
        boolean escaped = false;

        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                expectedClosers.push('}');
            } else if (c == '[') {
                expectedClosers.push(']');
            } else if (!expectedClosers.isEmpty()
                    && c == expectedClosers.peek()) {
                expectedClosers.pop();
                if (expectedClosers.isEmpty()) {
                    return new ValueMatch(
                            text.substring(start, i + 1),
                            i + 1
                    );
                }
            }
        }
        return null;
    }

    private ValueMatch readScalarValue(String text, int start) {
        int end = start;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (c == ',' || c == '}' || c == ']') {
                break;
            }
            if (c == '"' && JSON_KEY_PATTERN.matcher(text.substring(end)).find()) {
                break;
            }
            end++;
        }

        String value = text.substring(start, end).trim();
        return value.isEmpty() ? null : new ValueMatch(value, end);
    }

    private int skipWhitespace(String text, int start) {
        int index = start;
        while (index < text.length()
                && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private String buildWrapperJson(
            String wrapperField,
            List<String> fields,
            List<Map<String, String>> items
    ) {
        String itemJson = items.stream()
                .map(item -> fields.stream()
                        .map(field -> toJsonString(field) + ":" + item.get(field))
                        .collect(Collectors.joining(",", "{", "}")))
                .collect(Collectors.joining(","));
        return "{\"" + wrapperField + "\":[" + itemJson + "]}";
    }

    private String toJsonString(String value) {
        try {
            String unescaped = objectMapper.readValue(
                    "\"" + value + "\"",
                    String.class
            );
            return objectMapper.writeValueAsString(unescaped);
        } catch (JsonProcessingException e) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException ignored) {
                return "\"\"";
            }
        }
    }

    private record ListWrapperInfo(String fieldName, Class<?> elementType) {
    }

    private record KeyMatch(String name, int valueStart) {
    }

    private record ValueMatch(String value, int end) {
    }
}
