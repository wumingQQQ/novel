package com.wuming.novel.exception;

import com.wuming.novel.domain.dto.ApiResonse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FileTooLargeException.class)
    public ApiResonse<Void> handleException(FileTooLargeException ex) {
        return ApiResonse.error(413, ex.getMessage());
    }

    @ExceptionHandler(FileNotSupportException.class)
    public ApiResonse<Void> handleException(FileNotSupportException ex) {
        return ApiResonse.error(400, ex.getMessage());
    }


    @ExceptionHandler(Exception.class)
    public ApiResonse<Void> handleException(Exception ex) {
        return ApiResonse.error(500, ex.getMessage());
    }

}
