package com.wuming.novel.exception;

public class LlmResponseCheckException extends RuntimeException {
    public LlmResponseCheckException(String message) {
        super(message);
    }
}
