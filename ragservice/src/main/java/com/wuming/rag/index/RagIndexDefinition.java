package com.wuming.rag.index;

import com.wuming.api.rag.enums.MetadataFieldType;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class RagIndexDefinition {
    private String indexName;
    private String keyPrefix;
    private Integer vectorDimension;
    private Map<String, MetadataFieldType> metadataFields = new LinkedHashMap<>();
}
