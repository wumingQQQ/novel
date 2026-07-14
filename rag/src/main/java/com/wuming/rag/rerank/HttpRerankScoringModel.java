package com.wuming.rag.rerank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wuming.rag.config.RagProperties;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HttpRerankScoringModel implements ScoringModel {
    private final RagProperties ragProperties;
    private final RestClient.Builder clientBuilder;

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        if (segments == null || segments.isEmpty()) {
            return Response.from(List.of());
        }
        List<Double> scores = new ArrayList<>(segments.stream().map(ignored -> 0.0).toList());
        if (query == null || query.isBlank()) {
            return Response.from(scores);
        }

        RagProperties.Reranker config = ragProperties.getReranker();
        if (!config.isEnabled()) {
            log.debug("跳过RAG重排序请求，重排序配置未启用，documentCount: {}", segments.size());
            return Response.from(scores);
        }

        RerankRequest request = new RerankRequest(
                config.getModel(),
                query,
                segments.stream().map(TextSegment::text).toList(),
                segments.size()
        );

        RerankResponse response;
        try {
            response = clientBuilder
                    .baseUrl(config.getBaseUrl())
                    .build()
                    .post()
                    .uri(config.getPath())
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .body(request)
                    .retrieve()
                    .body(RerankResponse.class);
        } catch (RuntimeException ex) {
            log.warn("RAG重排序请求失败，使用向量召回顺序降级，model: {}, documentCount: {}",
                    config.getModel(), segments.size(), ex);
            return Response.from(scores);
        }

        if (response == null || response.results() == null) {
            log.warn("RAG重排序响应为空，使用向量召回顺序降级，model: {}, documentCount: {}",
                    config.getModel(), segments.size());
            return Response.from(scores);
        }

        for (RerankResult result : response.results()) {
            if (result.index() >= 0 && result.index() < scores.size()) {
                scores.set(result.index(), result.score());
            }
        }
        log.debug("RAG重排序请求完成，model: {}, documentCount: {}, resultCount: {}",
                config.getModel(), segments.size(), response.results().size());
        return Response.from(scores);
    }

    record RerankRequest(String model,
                         String query,
                         List<String> documents,
                         @JsonProperty("top_n") Integer topN) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RerankResult(int index, @JsonProperty("relevance_score") double score) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RerankResponse(List<RerankResult> results) {
    }
}
