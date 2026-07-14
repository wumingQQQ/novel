package com.wuming.rag.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;

import java.util.List;

public class RagContentRetriever implements ContentRetriever {

    private final EmbeddingStore<TextSegment>  embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final Filter filter;
    private final int topK;

    public RagContentRetriever(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel, Filter filter, int topK) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.filter = filter;
        this.topK = topK;
    }

    @Override
    public List<Content> retrieve(Query query) {
        Embedding queryEmbedding = embeddingModel.embed(query.text()).content();

        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder builder = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK);

        if(filter != null){
            builder.filter(filter);
        }

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(builder.build());
        return result.matches().stream()
                .map(this::toContent)
                .toList();
    }

    private Content toContent(EmbeddingMatch<TextSegment> match){
        TextSegment segment = TextSegment.from(
                match.embedded().text(),
                match.embedded().metadata()
                        .put("document_id", match.embeddingId())
                        .put("vector_score", match.score())
        );
        return Content.from(segment);
    }
}
