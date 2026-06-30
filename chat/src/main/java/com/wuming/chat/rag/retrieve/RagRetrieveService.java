package com.wuming.chat.rag.retrieve;

import com.wuming.chat.config.llm.RagProperties;
import com.wuming.chat.infrastructure.observability.TraceContext;
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
     * 根据任务id和用户输入召回RAG上下文；日志只记录数量与耗时，不记录用户输入正文。
     *
     * @param jobId 角色画像对应的任务id
     * @param query 用户输入
     * @return rag召回结果
     */
    public RagRetrieveResult retrieve(Long jobId, String query) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(jobId)) {
            long start = System.currentTimeMillis();
            RagProperties.Retrieve config = ragProperties.getRetrieve();
            log.info("开始RAG场景召回，vectorTopK: {}, contextTopN: {}",
                    config.getVectorTopK(), config.getContextTopN());

            List<Document> documents = searchDocuments(jobId, query, config);
            if (documents == null || documents.isEmpty()) {
                log.info("RAG场景召回为空，costMs: {}", System.currentTimeMillis() - start);
                return RagRetrieveResult.empty(query);
            }

            List<RerankedDocument> rerankedDocuments = rerankDocuments(query, documents);
            if (rerankedDocuments.isEmpty()) {
                log.info("RAG重排序结果为空，vectorCount: {}, costMs: {}",
                        documents.size(), System.currentTimeMillis() - start);
                return RagRetrieveResult.empty(query);
            }

            List<RagContext> contexts = selectContexts(
                    rerankedDocuments,
                    documentMap(documents),
                    config
            );
            log.info("RAG召回完成，vectorCount: {}, rerankCount: {}, contextCount: {}, costMs: {}",
                    documents.size(), rerankedDocuments.size(), contexts.size(),
                    System.currentTimeMillis() - start);
            return contexts.isEmpty()
                    ? RagRetrieveResult.empty(query)
                    : new RagRetrieveResult(query, contexts);
        }
    }

    /**
     * 从向量库按任务id检索候选场景。
     */
    private List<Document> searchDocuments(Long jobId, String query,
                                           RagProperties.Retrieve config) {
        long start = System.currentTimeMillis();
        List<Document> documents = vectorStoreService.search(
                jobId,
                query,
                config.getVectorTopK()
        );
        log.debug("向量召回完成，documentCount: {}, costMs: {}",
                documents == null ? 0 : documents.size(),
                System.currentTimeMillis() - start);
        return documents;
    }

    /**
     * 将向量召回候选交给重排序模型重新打分。
     */
    private List<RerankedDocument> rerankDocuments(String query,
                                                   List<Document> documents) {
        long start = System.currentTimeMillis();
        List<RerankDocument> rerankDocuments = documents.stream()
                .map(doc -> new RerankDocument(doc.getId(), doc.getText()))
                .toList();
        List<RerankedDocument> result = rerankService.rerank(query, rerankDocuments);
        log.debug("RAG重排序完成，inputCount: {}, outputCount: {}, costMs: {}",
                rerankDocuments.size(), result.size(), System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 按文档id构造映射，便于重排序后回取原始元数据。
     */
    private Map<String, Document> documentMap(List<Document> documents) {
        return documents.stream()
                .collect(Collectors.toMap(Document::getId, doc -> doc));
    }

    /**
     * 过滤掉低于阈值的候选，并截取前N个文档作为RAG上下文。
     *
     * @param rerankedDocuments 重排序后的文档列表
     * @param documentMap 文档id到文档的映射
     * @param config 召回配置
     * @return 选择完毕的rag上下文
     */
    private List<RagContext> selectContexts(
            List<RerankedDocument> rerankedDocuments,
            Map<String, Document> documentMap,
            RagProperties.Retrieve config
    ) {
        List<RagContext> contexts = new ArrayList<>();

        for (RerankedDocument document : rerankedDocuments) {
            if (document.score() < config.getMinScore()) {
                continue;
            }

            if (contexts.size() >= config.getContextTopN()) {
                break;
            }

            Document doc = documentMap.get(document.documentId());
            if (doc == null) {
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
     * 限制单个场景注入提示词的最大字数。
     */
    private String limitText(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        if (content.length() > maxChars) {
            return content.substring(0, maxChars);
        }
        return content;
    }

    /**
     * 利用向量文档元数据和重排序分数构造RagContext。
     *
     * @param doc 从向量库中检索出来的文档
     * @param content 限制长度后的文档内容
     * @param score 重排序后的分数
     * @return RagContext
     */
    private RagContext toContext(Document doc, String content, double score) {
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
     * 将元数据值转换为Long，兼容数值与字符串两种来源。
     */
    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }

    /**
     * 将元数据值转换为Integer，兼容数值与字符串两种来源。
     */
    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.valueOf(value.toString());
    }
}
