package com.wuming.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.wuming.api.rag.RagFacade;
import com.wuming.api.rag.dto.CreateIndexRequest;
import com.wuming.api.rag.dto.DeleteDocumentRequest;
import com.wuming.api.rag.dto.SearchHit;
import com.wuming.api.rag.dto.UpsertDocumentRequest;
import com.wuming.api.rag.dto.spec.PassageSearchRequest;
import com.wuming.api.rag.dto.spec.ReactionRuleSearchRequest;
import com.wuming.api.rag.dto.spec.RoleExampleSearchRequest;
import com.wuming.rag.model.RagIndexDefinition;
import com.wuming.rag.rerank.RerankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;

import java.util.List;


@Slf4j
@DubboService
@RequiredArgsConstructor
public class RagService implements RagFacade {
    private final RerankService rerankService;
    private final RedisIndexDefinitionStore definitionStore;
    private final JedisPooled jedisPool;

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
            log.error("索引序列化失败", e);
            return false;
        }
    }

    @Override
    public int upsertDocuments(UpsertDocumentRequest request) {
        return 0;
    }

    @Override
    public int deleteDocuments(DeleteDocumentRequest request) {
        return 0;
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
