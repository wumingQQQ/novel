package com.wuming.api.rag.dto;

import com.wuming.api.rag.enums.RagIndexType;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class DeleteDocumentsRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private RagIndexType indexType;
    private List<String> documentIds = new ArrayList<>();
}
