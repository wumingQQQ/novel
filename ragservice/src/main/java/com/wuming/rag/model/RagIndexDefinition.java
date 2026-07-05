package com.wuming.rag.model;

import com.wuming.api.rag.dto.IndexFieldSpec;
import lombok.Data;

import java.util.List;

/**
 * 保存创建过的index 结构
 */
@Data
public class RagIndexDefinition {
    private String indexName;
    private String keyPrefix;
    private Integer dimension;
    private List<IndexFieldSpec> metadataFields;
}
