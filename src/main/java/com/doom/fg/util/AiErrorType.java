package com.doom.fg.util;

public enum AiErrorType {
    AI_CONFIG_MISSING("AI_CONFIG_MISSING", "请先配置 AI 模型信息"),
    AI_API_UNAUTHORIZED("AI_API_UNAUTHORIZED", "API Key 不可用，请检查 AI 配置"),
    AI_API_TIMEOUT("AI_API_TIMEOUT", "AI 服务响应超时，请稍后重试"),
    AI_BAD_RESPONSE_FORMAT("AI_BAD_RESPONSE_FORMAT", "AI 返回格式异常，请重新生成"),
    AI_EMPTY_RESPONSE("AI_EMPTY_RESPONSE", "AI 没有返回有效内容，请重新生成"),
    AI_UNKNOWN_ERROR("AI_UNKNOWN_ERROR", "AI 服务暂时不可用，请稍后再试");

    private final String code;
    private final String userMessage;

    AiErrorType(String code, String userMessage) {
        this.code = code;
        this.userMessage = userMessage;
    }

    public String getCode() {
        return code;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
