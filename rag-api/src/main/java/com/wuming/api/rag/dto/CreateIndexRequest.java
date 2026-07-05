package com.wuming.api.rag.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 创建向量库的请求dto
 */
@Data
public class CreateIndexRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String indexName;
    private String keyPrefix;
    private List<IndexFieldSpec> metadataFields;
}
