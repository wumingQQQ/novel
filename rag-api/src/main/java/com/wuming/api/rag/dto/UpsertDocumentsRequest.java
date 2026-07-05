package com.wuming.api.rag.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class UpsertDocumentsRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String indexName;
    private List<RagDocumentDto> documents = new ArrayList<>();
}
