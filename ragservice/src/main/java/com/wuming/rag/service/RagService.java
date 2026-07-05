package com.wuming.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.wuming.api.rag.RagFacade;
import com.wuming.api.rag.dto.*;
import com.wuming.api.rag.dto.spec.PassageSearchRequest;
import com.wuming.api.rag.dto.spec.ReactionRuleSearchRequest;
import com.wuming.api.rag.dto.spec.RoleExampleSearchRequest;
import com.wuming.rag.model.RagIndexDefinition;
import com.wuming.rag.rerank.RerankService;
import com.wuming.rag.util.RedisByteUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.ai.embedding.EmbeddingModel;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;

import java.nio.charset.StandardCharsets;
import java.util.List;


@Slf4j
@DubboService
@RequiredArgsConstructor
public class RagService implements RagFacade {
    private final RerankService rerankService;
    private final RedisIndexDefinitionStore definitionStore;
    private final JedisPooled jedisPool;
    private final EmbeddingModel embeddingModel;

    @Override
    public boolean createIndex(CreateIndexRequest request) {
        String indexName = request.getIndexName();
        if(definitionStore.exists(indexName)){
            log.warn("索引已存在");
            return true;
        }
        RagIndexDefinition definition = new RagIndexDefinition(request);
        try {
            // 配置存储方式与索引前缀
            FTCreateParams params = FTCreateParams.createParams()
                    .on(IndexDataType.HASH)
                    .addPrefix(definition.getKeyPrefix());
            // 正式创建向量库
            jedisPool.ftCreate(indexName, params,
                    definition.buildFullSchemaFields());

            definitionStore.save(definition);
            return true;
        }
        catch (JsonProcessingException e) {
            log.error("索引定义序列化失败", e);
            return false;
        }
    }

    @Override
    public int upsertDocuments(UpsertDocumentRequest request) {
        int success = 0;
        try {
            log.info("开始插入文档");
            RagIndexDefinition definition = definitionStore.getRequired(request.getIndexName());
            if(request.getDocuments() == null || request.getDocuments().isEmpty()){
                return 0;
            }
            List<String> texts = request.getDocuments().stream()
                    .map(RagDocument::getText).toList();

            List<float[]> embeddings = embeddingModel.embed(texts);
            for(int i = 0; i < request.getDocuments().size(); i++){
                RagDocument doc = request.getDocuments().get(i);
                String redisKey = definition.getKeyPrefix() + doc.getDocumentId();

                // 由于需要保存数组，所以参数需为byte[]
                jedisPool.hset(
                    redisKey.getBytes(StandardCharsets.UTF_8),
                    RedisByteUtil.hashFields(doc, embeddings.get(i))
                );
                success++;
            }
        }
        catch (JsonProcessingException e) {
            log.error("反序列化索引定义失败", e);
            log.error("文档插入失败");
        }
        return success;
    }

    @Override
    public int deleteDocuments(DeleteDocumentRequest request) {
        try {
            log.info("开始删除文档");
            RagIndexDefinition definition = definitionStore.getRequired(request.getIndexName());
            if(request.getDocumentIds() == null || request.getDocumentIds().isEmpty()){
                return 0;
            }

            List<String> redisKeys = request.getDocumentIds().stream()
                    .map(docId -> definition.getKeyPrefix() + docId)
                    .toList();
            return (int) jedisPool.del(redisKeys.toArray(new String[0]));
        }
        catch (JsonProcessingException e) {
            log.error("反序列化索引定义时失败", e);
            log.error("删除操作失败");
            return 0;
        }
    }

    @Override
    public List<SearchHit> searchPassages(PassageSearchRequest request) {
        return List.of();
    }

    @Override
    public List<SearchHit> searchRoleExamples(RoleExampleSearchRequest request) {
        return List.of();
    }

    @Override
    public List<SearchHit> searchReactionRules(ReactionRuleSearchRequest request) {
        return List.of();
    }
}
