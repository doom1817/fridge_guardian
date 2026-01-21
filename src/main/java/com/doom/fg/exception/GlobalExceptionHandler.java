package com.doom.fg.exception;

import com.doom.fg.common.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/01/21/17:11
 * @Description:
 *  如果 AI 接口断开或数据库报错，目前前端会直接看到 500 错误。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        e.printStackTrace();
        return Result.error("服务器开小差了：" + e.getMessage());
    }
}
