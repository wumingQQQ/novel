package com.wuming.novel.exception;

import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;

public class FileNotSupportException extends BusinessException {
    public FileNotSupportException(String message) {
        super(ErrorCode.NOVEL_FILE_UNSUPPORTED, message);
    }
}
