package com.doom.fg.exception;

import com.doom.fg.common.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AiException.class)
    public Result<String> handleAiException(AiException e) {
        Result<String> result = new Result<>();
        result.setCode(500);
        result.setMessage(e.getErrorCode());
        result.setData(e.getUserMessage());
        return result;
    }

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        e.printStackTrace();
        return Result.error("服务器开小差了：" + e.getMessage());
    }
}
