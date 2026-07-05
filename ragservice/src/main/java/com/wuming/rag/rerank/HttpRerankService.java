package com.wuming.rag.rerank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wuming.rag.config.RagServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HttpRerankService implements RerankService {
    private final RagServiceProperties properties;
    private final RestClient.Builder clientBuilder;

    @Override
    public List<RerankedDocument> rerank(String query, List<RerankDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        RagServiceProperties.Reranker config = properties.getReranker();
        RerankRequest request = new RerankRequest(
                config.getModel(),
                query,
                documents.stream().map(RerankDocument::content).toList()
        );
        RerankResponse response = clientBuilder
                .baseUrl(config.getBaseUrl())
                .build()
                .post()
                .uri(config.getPath())
                .header("Authorization", "Bearer " + config.getApiKey())
                .body(request)
                .retrieve()
                .body(RerankResponse.class);
        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream()
                .filter(result -> result.index() >= 0)
                .filter(result -> result.index() < documents.size())
                .sorted(Comparator.comparingDouble(RerankResult::score).reversed())
                .limit(config.getTopN())
                .map(result -> {
                    RerankDocument source = documents.get(result.index());
                    return new RerankedDocument(source.documentId(), source.content(), result.score());
                })
                .toList();
    }

    record RerankRequest(String model, String query, List<String> documents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RerankResult(int index, @JsonProperty("relevance_score") double score) {
    }

    record RerankResponse(List<RerankResult> results) {
    }
}
