package com.doom.fg.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.doom.fg.service.impl.AiServiceImpl;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/02/16/21:33
 * @Description:
 * 实现 DeepSeek/OpenAI 兼容适配器
 */
@Component
public class OpenAiCompatibleEngine implements AiEngine{
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    @Override
    public String chat(String apiKey, String baseUrl, String model, String systemPrompt, List<AiServiceImpl.AiMessage> history, String userPrompt) {
        JSONArray messages = new JSONArray();

        // 1. 注入系统提示词 (维持厨师助手人设和锁定逻辑)
        messages.add(new JSONObject().fluentPut("role", "system").fluentPut("content", systemPrompt));

        // 2. 注入历史记录 (如果是 getRecipe 初始生成，此处为 null)
        if (history != null && !history.isEmpty()) {
            for (AiServiceImpl.AiMessage msg : history) {
                messages.add(new JSONObject().fluentPut("role", msg.getRole()).fluentPut("content", msg.getContent()));
            }
        }

        // 3. 注入当前请求
        messages.add(new JSONObject().fluentPut("role", "user").fluentPut("content", userPrompt));

        JSONObject body = new JSONObject()
                .fluentPut("model", model)
                .fluentPut("messages", messages);

        // 处理 URL 路径拼接
        String apiUrl = baseUrl;
        if (!apiUrl.endsWith("/chat/completions")) {
            apiUrl = apiUrl.endsWith("/") ? apiUrl + "chat/completions" : apiUrl + "/chat/completions";
        }

        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(MediaType.parse("application/json"), body.toJSONString()))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return "Error: API 返回错误码 " + response.code();
            String resStr = response.body().string();
            JSONObject resObj = JSON.parseObject(resStr);
            return resObj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
