package com.wuming.user.exception;

import com.wuming.common.exception.BaseExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {
}
