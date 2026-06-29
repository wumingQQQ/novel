package com.wuming.chat.rag.redis;

import com.wuming.api.scene.dto.SceneDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SceneVectorStoreService {
    private static final int FIRST_CHUNK_INDEX = 0;
    private final VectorStore vectorStore;

    /**
     * 插入单个scene，支持幂等
     * @param jobId
     * @param scene
     */
    public void upsertScene(Long jobId, SceneDto scene) {
        deleteScene(jobId, scene.getSceneId());

        Document doc = toDocument(jobId, scene, FIRST_CHUNK_INDEX);
        vectorStore.add(List.of(doc));

        log.debug("场景向量写入完成，jobId: {}, sceneId: {}, chunkIndex: {}",
                jobId, scene.getSceneId(), FIRST_CHUNK_INDEX);
    }

    /**
     * 删除指定key的内容，用于实现幂等
     * @param jobId
     * @param sceneId
     */
    public void deleteScene(Long jobId, Long sceneId){
        String documentId = documentId(jobId, sceneId, FIRST_CHUNK_INDEX);
        vectorStore.delete(List.of(documentId));
    }

    /**
     * 根据查询寻找语义相近的内容
     * @param jobId 元信息过滤
     * @param query 查询
     * @param topK 召回数量
     * @return 召回的文档列表
     */
    public List<Document> search(Long jobId, String query, int topK){
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .filterExpression("jobId == " + jobId)
                        .build()
        );
    }

    /**
     * 将给定信息构造为document
     * @param jobId 用于构造元信息与键
     * @param scene 核心内容与元信息
     * @param chunkIndex 该场景的第几块
     * @return 构造完成的document
     */
    private Document toDocument(Long jobId, SceneDto scene, int chunkIndex) {
        String content = scene.getContent();

        return new Document(
                documentId(jobId, scene.getSceneId(), chunkIndex),
                content,
                Map.of(
                        "jobId", jobId,
                        "novelId",  scene.getNovelId(),
                        "chapterId", scene.getChapterId(),
                        "sceneId", scene.getSceneId(),
                        "chapterSequence", scene.getChapterSequence(),
                        "sceneSequence", scene.getSceneSequence(),
                        "chunkIndex", chunkIndex
                )
        );
    }

    /**
     * 根据参数构造向量文档id
     * @param jobId
     * @param sceneId
     * @param chunkIndex
     * @return 向量文档id
     */
    private String documentId(Long jobId, Long sceneId, int chunkIndex){
        return String.format("%d:%d:%d", jobId, sceneId, chunkIndex);
    }

    /**
     * 可用于后面在元信息中判断内容重复，跳过与内容变化重建
     * @throws NoSuchAlgorithmException
     */
    private String sha256(String content) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    }
}
