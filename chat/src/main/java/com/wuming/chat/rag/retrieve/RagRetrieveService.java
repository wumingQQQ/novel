package com.wuming.chat.rag.retrieve;

import com.wuming.chat.config.llm.RagProperties;
import com.wuming.chat.rag.redis.SceneVectorStoreService;
import com.wuming.chat.rag.rerank.RerankDocument;
import com.wuming.chat.rag.rerank.RerankService;
import com.wuming.chat.rag.rerank.RerankedDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrieveService {
    private final RagProperties ragProperties;
    private final SceneVectorStoreService vectorStoreService;
    private final RerankService rerankService;

    public RagRetrieveResult retrieve(Long jobId, String query){
        RagProperties.Retrieve config = ragProperties.getRetrieve();

        List<Document> documents = vectorStoreService.search(jobId, query, config.getVectorTopK());
        if(documents == null || documents.isEmpty()){
            return RagRetrieveResult.empty(query);
        }

        Map<String, Document> documentMap = documents.stream()
                .collect(Collectors.toMap(Document::getId, doc -> doc));

        List<RerankDocument> rerankDocuments = documents.stream()
                .map(doc -> new RerankDocument(
                        doc.getId(),
                        doc.getText()
                ))
                .toList();

        List<RerankedDocument> rerankedDocuments = rerankService.rerank(query, rerankDocuments);

        if(rerankedDocuments.isEmpty()){
            return RagRetrieveResult.empty(query);
        }

        List<RagContext> contexts = selectContexts(
                rerankedDocuments,
                documentMap,
                config
        );

        return contexts.isEmpty()
                ? RagRetrieveResult.empty(query)
                : new RagRetrieveResult(query, contexts);
    }

    private List<RagContext>  selectContexts(
            List<RerankedDocument> rerankedDocuments,
            Map<String, Document> documentMap,
            RagProperties.Retrieve config
    ){
        List<RagContext> contexts = new ArrayList<>();

        for(RerankedDocument document : rerankedDocuments){
            if(document.score() < config.getMinScore()){
                continue;
            }

            if(contexts.size() >= config.getContextTopN()){
                break;
            }

            Document doc = documentMap.get(document.documentId());
            if(doc == null){
                continue;
            }

            String content = limitText(
                    document.content(),
                    config.getMaxContextChars()
            );

            contexts.add(toContext(doc, content, document.score()));

        }
        return contexts;
    }

    private String limitText(String content, int maxChars){
        if (content == null) {
            return "";
        }
        if(content.length() > maxChars){
            return content.substring(0, maxChars);
        }
        return content;
    }

    private RagContext toContext(Document doc, String content, double score){
        Map<String, Object> metadata = doc.getMetadata();
        return new RagContext(
                doc.getId(),
                longValue(metadata.get("sceneId")),
                intValue(metadata.get("chapterSequence")),
                intValue(metadata.get("sceneSequence")),
                content,
                score
        );
    }

    private Long longValue(Object value){
        if(value instanceof Number number){
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }

    private Integer intValue(Object value){
        if(value instanceof Number number){
            return number.intValue();
        }
        return value == null ? null : Integer.valueOf(value.toString());
    }

}
