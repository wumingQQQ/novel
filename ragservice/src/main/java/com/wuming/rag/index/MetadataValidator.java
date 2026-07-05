package com.wuming.rag.index;

import com.wuming.api.rag.dto.RagDocumentDto;
import com.wuming.api.rag.enums.MetadataFieldType;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Component
public class MetadataValidator {

    public void validateDefinition(RagIndexDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("索引定义不能为空");
        }
        if (definition.getIndexName() == null || definition.getIndexName().isBlank()) {
            throw new IllegalArgumentException("indexName不能为空");
        }
        if (definition.getKeyPrefix() == null || definition.getKeyPrefix().isBlank()) {
            throw new IllegalArgumentException("keyPrefix不能为空");
        }
        if (definition.getVectorDimension() == null || definition.getVectorDimension() <= 0) {
            throw new IllegalArgumentException("vectorDimension必须大于0");
        }
        if (definition.getMetadataFields() == null) {
            throw new IllegalArgumentException("metadataFields不能为空");
        }

        for (Map.Entry<String, MetadataFieldType> entry : definition.getMetadataFields().entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("metadata字段名不能为空");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("metadata字段类型不能为空: " + entry.getKey());
            }
        }
    }

    public void validateDocument(RagIndexDefinition definition, RagDocumentDto document) {
        validateDefinition(definition);
        if (document == null) {
            throw new IllegalArgumentException("document不能为空");
        }
        if (document.getDocumentId() == null || document.getDocumentId().isBlank()) {
            throw new IllegalArgumentException("documentId不能为空");
        }
        if (document.getText() == null || document.getText().isBlank()) {
            throw new IllegalArgumentException("document.text不能为空");
        }
        if (document.getMetadata() == null) {
            throw new IllegalArgumentException("metadata不能为空");
        }

        for (Map.Entry<String, Object> entry : document.getMetadata().entrySet()) {
            MetadataFieldType fieldType = definition.getMetadataFields().get(entry.getKey());
            if (fieldType == null) {
                throw new IllegalArgumentException("metadata字段未在索引中声明: " + entry.getKey());
            }
            validateMetadataValue(entry.getKey(), fieldType, entry.getValue());
        }
    }

    private void validateMetadataValue(String fieldName, MetadataFieldType fieldType, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
            throw new IllegalArgumentException("metadata字段只允许标量值: " + fieldName);
        }
        if (fieldType == MetadataFieldType.NUMERIC && !(value instanceof Number)) {
            throw new IllegalArgumentException("metadata字段必须是数字: " + fieldName);
        }
    }
}
