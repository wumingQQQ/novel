package com.wuming.novel.exception;

public class LLMResponseEmptyException extends RuntimeException {
    public LLMResponseEmptyException(String message) {
        super(message);
    }
}
