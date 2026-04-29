package com.doom.fg.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.doom.fg.service.impl.AiServiceImpl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OpenAiCompatibleEngine implements AiEngine {
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    @Override
    public AiResponse chat(String apiKey, String baseUrl, String model, String systemPrompt,
                           List<AiServiceImpl.AiMessage> history, String userPrompt) {
        JSONArray messages = new JSONArray();
        messages.add(new JSONObject().fluentPut("role", "system").fluentPut("content", systemPrompt));

        if (history != null && !history.isEmpty()) {
            for (AiServiceImpl.AiMessage message : history) {
                messages.add(new JSONObject()
                        .fluentPut("role", message.getRole())
                        .fluentPut("content", message.getContent()));
            }
        }

        messages.add(new JSONObject().fluentPut("role", "user").fluentPut("content", userPrompt));

        JSONObject body = new JSONObject()
                .fluentPut("model", model)
                .fluentPut("messages", messages);

        String apiUrl = normalizeApiUrl(baseUrl);

        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return AiResponse.failure(classifyStatus(response.code()), "HTTP_" + response.code());
            }

            String responseBody = response.body() == null ? "" : response.body().string();
            JSONObject responseJson = JSON.parseObject(responseBody);
            JSONArray choices = responseJson.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return AiResponse.failure(AiErrorType.AI_EMPTY_RESPONSE, "Missing choices");
            }

            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            if (message == null) {
                return AiResponse.failure(AiErrorType.AI_BAD_RESPONSE_FORMAT, "Missing message object");
            }

            String content = message.getString("content");
            if (content == null || content.trim().isEmpty()) {
                return AiResponse.failure(AiErrorType.AI_EMPTY_RESPONSE, "Empty content");
            }

            JSONObject usage = responseJson.getJSONObject("usage");
            Integer promptTokens = usage == null ? null : usage.getInteger("prompt_tokens");
            Integer completionTokens = usage == null ? null : usage.getInteger("completion_tokens");
            Integer totalTokens = usage == null ? null : usage.getInteger("total_tokens");
            return AiResponse.success(content, promptTokens, completionTokens, totalTokens);
        } catch (SocketTimeoutException e) {
            return AiResponse.failure(AiErrorType.AI_API_TIMEOUT, e.getMessage());
        } catch (Exception e) {
            return AiResponse.failure(AiErrorType.AI_UNKNOWN_ERROR, e.getMessage());
        }
    }

    private String normalizeApiUrl(String baseUrl) {
        if (baseUrl.endsWith("/chat/completions")) {
            return baseUrl;
        }
        return baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
    }

    private AiErrorType classifyStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return AiErrorType.AI_API_UNAUTHORIZED;
        }
        if (statusCode == 408 || statusCode == 504) {
            return AiErrorType.AI_API_TIMEOUT;
        }
        return AiErrorType.AI_UNKNOWN_ERROR;
    }
}
