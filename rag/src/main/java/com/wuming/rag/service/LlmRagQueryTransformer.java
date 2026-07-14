package com.wuming.rag.service;

import com.wuming.rag.config.RagProperties;
import com.wuming.rag.model.RagQueryTransformCommand;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
public class LlmRagQueryTransformer implements RagQueryTransformer {

    private final ChatModel chatModel;
    private final RagProperties.QueryRewrite properties;

    public LlmRagQueryTransformer(ChatModel chatModel, RagProperties.QueryRewrite properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    @Override
    public List<Query> transform(RagQueryTransformCommand command) {
        if (command.multiQuery()) {
            return multiQuery(command.query());
        }
        return singleQuery(command.query());
    }

    private List<Query> singleQuery(String query) {
        try {
            String rewritten = normalize(chatModel.chat(createPrompt(properties.getPromptTemplate(), query)));
            if (!hasText(rewritten)) {
                log.warn("RAG单路查询改写返回空内容，使用原始查询，query: {}", query);
                return List.of(Query.from(query));
            }
            return List.of(Query.from(rewritten));
        } catch (Exception ex) {
            log.warn("RAG单路查询改写失败，使用原始查询，query: {}, error: {}", query, ex.getMessage());
            log.debug("RAG单路查询改写失败堆栈", ex);
            return List.of(Query.from(query));
        }
    }

    private List<Query> multiQuery(String query) {
        try {
            String response = chatModel.chat(createPrompt(properties.getMultiQueryPromptTemplate(), query));
            List<String> rewrittenQueries = parseMultiQueryResponse(response);
            if (rewrittenQueries.isEmpty()) {
                log.warn("RAG多路查询改写返回空内容，使用原始查询，query: {}", query);
                return List.of(Query.from(query));
            }

            LinkedHashSet<String> distinctQueries = new LinkedHashSet<>();
            distinctQueries.add(query.trim());
            distinctQueries.addAll(rewrittenQueries);

            return distinctQueries.stream()
                    .filter(this::hasText)
                    .limit(Math.max(1, properties.getMultiQueryMaxCount()))
                    .map(Query::from)
                    .toList();
        } catch (Exception ex) {
            log.warn("RAG多路查询改写失败，使用原始查询，query: {}, error: {}", query, ex.getMessage());
            log.debug("RAG多路查询改写失败堆栈", ex);
            return List.of(Query.from(query));
        }
    }

    private String createPrompt(String template, String query) {
        return PromptTemplate.from(template)
                .apply(Map.of(
                        "query", query,
                        "multiQueryMaxCount", properties.getMultiQueryMaxCount()
                ))
                .text();
    }

    private List<String> parseMultiQueryResponse(String response) {
        if (!hasText(response)) {
            return List.of();
        }

        List<String> queries = new ArrayList<>();
        for (String line : response.split("\\R")) {
            String normalized = normalize(line)
                    .replaceFirst("^[-*•]\\s*", "")
                    .replaceFirst("^\\d+[.、)]\\s*", "")
                    .trim();
            if (hasText(normalized)) {
                queries.add(normalized);
            }
        }
        return queries;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```[a-zA-Z]*\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }
        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
