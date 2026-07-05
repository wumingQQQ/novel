package com.wuming.api.rag.dto;

import com.wuming.api.rag.enums.MetadataFieldType;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建索引库时的metadata field
 */
@Data
public class IndexFieldSpec implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private MetadataFieldType type;
}
