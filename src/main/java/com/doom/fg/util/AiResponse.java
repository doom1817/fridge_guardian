package com.doom.fg.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiResponse {
    private boolean success;
    private String content;
    private AiErrorType errorType;
    private String rawError;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    public static AiResponse success(String content) {
        return new AiResponse(true, content, null, null, null, null, null);
    }

    public static AiResponse success(String content, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        return new AiResponse(true, content, null, null, promptTokens, completionTokens, totalTokens);
    }

    public static AiResponse failure(AiErrorType errorType, String rawError) {
        return new AiResponse(false, null, errorType, rawError, null, null, null);
    }
}
