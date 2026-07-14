package com.wuming.api.rag.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 批量更新向量文档元数据字段的请求。
 */
@Data
public class UpdateDocumentMetadataRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String indexName;
    private List<DocumentMetadataPatch> patches;

    @Data
    public static class DocumentMetadataPatch implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String documentId;
        private Map<String, Object> metadata;
    }
}
