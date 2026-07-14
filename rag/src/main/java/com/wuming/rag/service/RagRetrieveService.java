package com.wuming.rag.service;

import com.wuming.api.rag.dto.SearchHit;
import com.wuming.rag.model.RagQueryTransformCommand;
import com.wuming.rag.model.RagRetrievalCommand;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagRetrieveService {
    private final EmbeddingStoreRegistry registry;
    private final EmbeddingModel embeddingModel;
    private final ScoringModel scoringModel;
    private final RagQueryTransformer queryTransformer;

    public List<SearchHit> retrieve(RagRetrievalCommand command){
        EmbeddingStore<TextSegment> store = registry.getRequired(command.indexName());

        ContentRetriever retriever = new RagContentRetriever(
                store,
                embeddingModel,
                command.filter(),
                command.topK()
        );

        Collection<Query> queries = resolveQueries(command);

        Map<Query, Collection<List<Content>>> queryToContents = new LinkedHashMap<>();
        for (Query query : queries) {
            queryToContents.put(query, List.of(retriever.retrieve(query)));
        }

        ContentAggregator aggregator = ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)
                .maxResults(command.topN())
                .minScore(0.6)
                .build();

        List<SearchHit> hits = aggregator.aggregate(queryToContents).stream()
                .map(this::toSearchHit)
                .toList();
        log.debug("RAG召回聚合完成，indexName: {}, queryCount: {}, topK: {}, topN: {}, hitCount: {}",
                command.indexName(), queries.size(), command.topK(), command.topN(), hits.size());
        return hits;
    }

    private Collection<Query> resolveQueries(RagRetrievalCommand command) {
        Collection<Query> queries = queryTransformer.transform(new RagQueryTransformCommand(
                command.query(),
                command.multiQuery()
        ));
        log.debug("RAG查询重写完成，originalQuery: {}, transformedQueryCount: {}",
                command.query(), queries.size());
        return queries;
    }

    private SearchHit toSearchHit(Content content) {
        TextSegment segment = content.textSegment();
        Map<String, Object> metadata = new LinkedHashMap<>(segment.metadata().toMap());

        Object documentId = metadata.remove("document_id");
        Object vectorScore = metadata.remove("vector_score");

        Object rerankedScore = content.metadata().get(ContentMetadata.RERANKED_SCORE);

        SearchHit hit = new SearchHit();
        hit.setDocumentId(documentId == null ? null : documentId.toString());
        hit.setContent(segment.text());
        hit.setMetadata(metadata);
        if(rerankedScore instanceof Number number){
            hit.setScore(number.doubleValue());
        }
        else if(vectorScore instanceof Number number){
            hit.setScore(number.doubleValue());
        }
        return hit;
    }
}
