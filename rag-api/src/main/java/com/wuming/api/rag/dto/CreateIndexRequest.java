package com.wuming.api.rag.dto;

import com.wuming.api.rag.enums.RagIndexType;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class CreateIndexRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private RagIndexType indexType;
}
