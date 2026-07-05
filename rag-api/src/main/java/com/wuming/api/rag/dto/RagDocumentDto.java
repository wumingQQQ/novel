package com.wuming.api.rag.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class RagDocumentDto implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String documentId;
    private String text;
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
