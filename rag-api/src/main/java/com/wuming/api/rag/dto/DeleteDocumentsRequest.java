package com.wuming.api.rag.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class DeleteDocumentsRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String indexName;
    private List<String> documentIds = new ArrayList<>();
}
