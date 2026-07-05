package com.wuming.rag.vector.redis;

import com.wuming.api.rag.dto.RagDocumentDto;
import com.wuming.api.rag.dto.RagHitDto;
import com.wuming.api.rag.enums.IndexStatus;
import com.wuming.api.rag.enums.MetadataFieldType;
import com.wuming.rag.index.MetadataValidator;
import com.wuming.rag.index.RagIndexDefinition;
import com.wuming.rag.index.RedisIndexDefinitionStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.args.SortingOrder;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.FTSearchParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RedisVectorIndexService {
    private static final String TEXT_FIELD = "text";
    private static final String VECTOR_FIELD = "embedding";
    private static final String VECTOR_PARAM = "query_vector";
    private static final String SCORE_FIELD = "score";

    private final JedisPooled jedisPooled;
    private final EmbeddingModel embeddingModel;
    private final MetadataValidator metadataValidator;
    private final RedisIndexDefinitionStore definitionStore;

    public RedisVectorIndexService(
            JedisPooled jedisPooled,
            EmbeddingModel embeddingModel,
            MetadataValidator metadataValidator,
            RedisIndexDefinitionStore definitionStore
    ) {
        this.jedisPooled = jedisPooled;
        this.embeddingModel = embeddingModel;
        this.metadataValidator = metadataValidator;
        this.definitionStore = definitionStore;
    }

    public IndexStatus createIndex(RagIndexDefinition definition) {
        metadataValidator.validateDefinition(definition);
        if (definitionStore.exists(definition.getIndexName())) {
            return IndexStatus.EXISTS;
        }

        try {
            jedisPooled.ftCreate(
                    definition.getIndexName(),
                    FTCreateParams.createParams()
                            .on(IndexDataType.HASH)
                            .prefix(definition.getKeyPrefix()),
                    schemaFields(definition)
            );
            definitionStore.save(definition);
            return IndexStatus.CREATED;
        } catch (JedisDataException e) {
            if (e.getMessage() != null && e.getMessage().contains("Index already exists")) {
                definitionStore.save(definition);
                return IndexStatus.EXISTS;
            }
            throw e;
        }
    }

    public int upsertDocuments(String indexName, List<RagDocumentDto> documents) {
        RagIndexDefinition definition = definitionStore.getRequired(indexName);
        if (documents == null || documents.isEmpty()) {
            return 0;
        }
        for (RagDocumentDto document : documents) {
            metadataValidator.validateDocument(definition, document);
        }

        List<float[]> embeddings = embeddingModel.embed(
                documents.stream().map(RagDocumentDto::getText).toList()
        );
        for (int i = 0; i < documents.size(); i++) {
            RagDocumentDto document = documents.get(i);
            jedisPooled.hset(
                    redisKey(definition, document.getDocumentId()).getBytes(StandardCharsets.UTF_8),
                    hashFields(document, embeddings.get(i))
            );
        }
        return documents.size();
    }

    public int deleteDocuments(String indexName, List<String> documentIds) {
        RagIndexDefinition definition = definitionStore.getRequired(indexName);
        if (documentIds == null || documentIds.isEmpty()) {
            return 0;
        }
        String[] keys = documentIds.stream()
                .map(documentId -> redisKey(definition, documentId))
                .toArray(String[]::new);
        return (int) jedisPooled.del(keys);
    }

    public List<RagHitDto> search(String indexName,
                                  String query,
                                  Map<String, Object> filters,
                                  int topK) {
        RagIndexDefinition definition = definitionStore.getRequired(indexName);
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query不能为空");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK必须大于0");
        }

        float[] embedding = embeddingModel.embed(query);
        SearchResult result = jedisPooled.ftSearch(
                indexName,
                searchQuery(definition, filters, topK),
                searchParams(definition, embedding, topK)
        );
        return toHits(definition, result);
    }

    private Iterable<SchemaField> schemaFields(RagIndexDefinition definition) {
        List<SchemaField> fields = new ArrayList<>();
        fields.add(TextField.of(TEXT_FIELD));
        fields.add(new VectorField(
                VECTOR_FIELD,
                VectorField.VectorAlgorithm.HNSW,
                Map.of(
                        "TYPE", "FLOAT32",
                        "DIM", definition.getVectorDimension(),
                        "DISTANCE_METRIC", "COSINE"
                )
        ));
        for (Map.Entry<String, MetadataFieldType> entry : definition.getMetadataFields().entrySet()) {
            fields.add(metadataSchemaField(entry.getKey(), entry.getValue()));
        }
        return fields;
    }

    private SchemaField metadataSchemaField(String name, MetadataFieldType type) {
        return switch (type) {
            case NUMERIC -> NumericField.of(name);
            case TEXT -> TextField.of(name);
            case TAG -> TagField.of(name);
        };
    }

    private Map<byte[], byte[]> hashFields(RagDocumentDto document, float[] embedding) {
        Map<byte[], byte[]> fields = new LinkedHashMap<>();
        fields.put(bytes(TEXT_FIELD), bytes(document.getText()));
        fields.put(bytes(VECTOR_FIELD), vectorBytes(embedding));
        for (Map.Entry<String, Object> entry : document.getMetadata().entrySet()) {
            if (entry.getValue() != null) {
                fields.put(bytes(entry.getKey()), bytes(String.valueOf(entry.getValue())));
            }
        }
        return fields;
    }

    private FTSearchParams searchParams(RagIndexDefinition definition, float[] embedding, int topK) {
        List<String> returnFields = new ArrayList<>();
        returnFields.add(TEXT_FIELD);
        returnFields.add(SCORE_FIELD);
        returnFields.addAll(definition.getMetadataFields().keySet());
        return FTSearchParams.searchParams()
                .params(Map.of(VECTOR_PARAM, vectorBytes(embedding)))
                .returnFields(returnFields.toArray(String[]::new))
                .sortBy(SCORE_FIELD, SortingOrder.ASC)
                .limit(0, topK)
                .dialect(2);
    }

    private String searchQuery(RagIndexDefinition definition, Map<String, Object> filters, int topK) {
        String filterQuery = filterQuery(definition, filters);
        return filterQuery + "=>[KNN " + topK + " @" + VECTOR_FIELD + " $" + VECTOR_PARAM
                + " AS " + SCORE_FIELD + "]";
    }

    private String filterQuery(RagIndexDefinition definition, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return "*";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            MetadataFieldType type = definition.getMetadataFields().get(entry.getKey());
            if (type == null) {
                throw new IllegalArgumentException("过滤字段未在索引中声明: " + entry.getKey());
            }
            if (entry.getValue() == null) {
                continue;
            }
            parts.add(filterPart(entry.getKey(), type, entry.getValue()));
        }
        return parts.isEmpty() ? "*" : String.join(" ", parts);
    }

    private String filterPart(String fieldName, MetadataFieldType type, Object value) {
        return switch (type) {
            case NUMERIC -> "@" + fieldName + ":[" + value + " " + value + "]";
            case TAG -> "@" + fieldName + ":{" + escapeTag(String.valueOf(value)) + "}";
            case TEXT -> "@" + fieldName + ":" + escapeText(String.valueOf(value));
        };
    }

    private List<RagHitDto> toHits(RagIndexDefinition definition, SearchResult result) {
        if (result == null || result.getDocuments() == null) {
            return List.of();
        }
        return result.getDocuments().stream()
                .map(document -> {
                    RagHitDto hit = new RagHitDto();
                    hit.setDocumentId(stripPrefix(definition, document.getId()));
                    hit.setText(document.getString(TEXT_FIELD));
                    hit.setScore(doubleValue(document.get(SCORE_FIELD)));
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    for (String fieldName : definition.getMetadataFields().keySet()) {
                        if (document.hasProperty(fieldName)) {
                            metadata.put(fieldName, document.get(fieldName));
                        }
                    }
                    hit.setMetadata(metadata);
                    return hit;
                })
                .toList();
    }

    private String redisKey(RagIndexDefinition definition, String documentId) {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId不能为空");
        }
        return definition.getKeyPrefix() + documentId;
    }

    private String stripPrefix(RagIndexDefinition definition, String redisKey) {
        if (redisKey != null && redisKey.startsWith(definition.getKeyPrefix())) {
            return redisKey.substring(definition.getKeyPrefix().length());
        }
        return redisKey;
    }

    private byte[] vectorBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        return Double.parseDouble(value.toString());
    }

    private String escapeTag(String value) {
        return value.replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace("{", "\\{")
                .replace("}", "\\}");
    }

    private String escapeText(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
