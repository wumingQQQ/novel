package com.wuming.api.rag.dto;

import com.wuming.api.rag.enums.MetadataFieldType;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class IndexMetadataFieldDto implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private MetadataFieldType type;
}
