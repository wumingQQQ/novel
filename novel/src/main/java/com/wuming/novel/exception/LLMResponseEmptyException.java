package com.wuming.novel.exception;

import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;

public class LLMResponseEmptyException extends BusinessException {
    public LLMResponseEmptyException(String message) {
        super(ErrorCode.LLM_EMPTY_RESPONSE, message);
    }
}
