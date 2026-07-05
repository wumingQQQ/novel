package com.wuming.api.rag.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UpsertResult implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String code;
    private String message;
    private int indexedCount;

    public static UpsertResult success(int indexedCount) {
        UpsertResult result = new UpsertResult();
        result.setSuccess(true);
        result.setCode("OK");
        result.setIndexedCount(indexedCount);
        return result;
    }

    public static UpsertResult failure(String code, String message) {
        UpsertResult result = new UpsertResult();
        result.setSuccess(false);
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
