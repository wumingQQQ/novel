package com.wuming.novel.exception;

import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;

public class FileTooLargeException extends BusinessException {
    public FileTooLargeException(String message) {
        super(ErrorCode.NOVEL_FILE_TOO_LARGE, message);
    }
}
