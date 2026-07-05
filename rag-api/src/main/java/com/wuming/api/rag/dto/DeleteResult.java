package com.wuming.api.rag.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class DeleteResult implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String code;
    private String message;
    private int deletedCount;

    public static DeleteResult success(int deletedCount) {
        DeleteResult result = new DeleteResult();
        result.setSuccess(true);
        result.setCode("OK");
        result.setDeletedCount(deletedCount);
        return result;
    }

    public static DeleteResult failure(String code, String message) {
        DeleteResult result = new DeleteResult();
        result.setSuccess(false);
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
