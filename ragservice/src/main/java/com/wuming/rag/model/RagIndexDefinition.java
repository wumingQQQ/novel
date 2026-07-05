package com.wuming.rag.model;

import com.wuming.api.rag.dto.CreateIndexRequest;
import com.wuming.api.rag.dto.IndexFieldSpec;
import lombok.Data;
import lombok.NoArgsConstructor;
import redis.clients.jedis.search.schemafields.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 保存创建过的index 结构
 */
@Data
@NoArgsConstructor
public class RagIndexDefinition {
    private String indexName;
    private String keyPrefix;
    private Integer dimension = 1024;
    private List<IndexFieldSpec> metadataFields;

    public RagIndexDefinition(CreateIndexRequest request){
        this.indexName = request.getIndexName();
        this.keyPrefix = request.getKeyPrefix();
        this.dimension = request.getVectorDimension();
        this.metadataFields = request.getMetadataFields();
    }

    private SchemaField toSchemaField(IndexFieldSpec field){
        return switch (field.getType()){
            case TAG -> TagField.of(field.getName());
            case TEXT -> TextField.of(field.getName());
            case NUMERIC -> NumericField.of(field.getName());
            default -> throw new IllegalArgumentException("Unsupported field type: " + field.getType());
        };
    }

    public List<SchemaField> buildFullSchemaFields() {
        // 动态构建向量字段配置
        Map<String, Object> vectorAttrs = new HashMap<>();
        vectorAttrs.put("TYPE", "FLOAT32");
        vectorAttrs.put("DIM", this.dimension);
        vectorAttrs.put("DISTANCE_METRIC", "COSINE"); // 也可根据配置动态传入

        SchemaField vectorField = new VectorField(
                "embedding",
                VectorField.VectorAlgorithm.HNSW,
                vectorAttrs
        );
        SchemaField textField = TextField.of("text");
        List<SchemaField> fields = new ArrayList<>(this.metadataFields.stream()
                .map(this::toSchemaField).toList());
        fields.add(vectorField);
        fields.add(textField);
        return fields;
    }
}
