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

    /**
     * 根据元信息与用户输入构造rag召回结果
     * @param jobId 元信息，主要用于指示角色
     * @param query 用户输入
     * @return rag召回结果
     */
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

    /**
     * 过滤掉分数低于阈值的，选择topN文档
     * @param rerankedDocuments 重排序后的文档列表
     * @param documentMap 文档id到文档的映射
     * @param config 配置类，包含分数阈值、单场景最大字符数、topN
     * @return 选择完毕的rag上下文
     */
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
                    config.getMaxContextCharsPerScene()
            );

            contexts.add(toContext(doc, content, document.score()));

        }
        return contexts;
    }

    /**
     * 限制单个场景的最大字数
     * @param content
     * @param maxChars
     * @return
     */
    private String limitText(String content, int maxChars){
        if (content == null) {
            return "";
        }
        if(content.length() > maxChars){
            return content.substring(0, maxChars);
        }
        return content;
    }

    /**
     * 利用参数构造RagContext
     * @param doc 从向量库中检索出来的文档
     * @param content 限制长度后的文档内容
     * @param score 重排序后的分数
     * @return RagContext
     */
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

    /**
     * 将Object对象转为long类型
     * * @param value
     * @return
     */
    private Long longValue(Object value){
        if(value instanceof Number number){
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }

    /**
     * 将Object对象转为int类型
     * @param value
     * @return
     */
    private Integer intValue(Object value){
        if(value instanceof Number number){
            return number.intValue();
        }
        return value == null ? null : Integer.valueOf(value.toString());
    }

}
