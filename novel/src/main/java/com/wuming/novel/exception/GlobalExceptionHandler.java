package com.wuming.novel.exception;

import com.wuming.common.web.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FileTooLargeException.class)
    public ApiResponse<Void> handleException(FileTooLargeException ex) {
        return ApiResponse.error(413, ex.getMessage());
    }

    @ExceptionHandler(FileNotSupportException.class)
    public ApiResponse<Void> handleException(FileNotSupportException ex) {
        return ApiResponse.error(400, ex.getMessage());
    }


    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        return ApiResponse.error(500, ex.getMessage());
    }

}
