package com.doom.fg.util;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/02/16/21:01
 * @Description:
 */
@Component
public class DifyClient {
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    public String chat(String apiKey, String baseUrl, String query, JSONObject inputs) {
        JSONObject body = new JSONObject();
        body.put("inputs", inputs != null ? inputs : new JSONObject());
        body.put("query", query);
        body.put("response_mode", "blocking"); // 阻塞模式开发最快
        body.put("user", "fridge-guardian-admin");

        Request request = new Request.Builder()
                .url(baseUrl.endsWith("/") ? baseUrl + "chat-messages" : baseUrl + "/chat-messages")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(MediaType.parse("application/json"), body.toJSONString()))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return "Dify 响应异常代码: " + response.code();
            String resStr = response.body().string();
            JSONObject resObj = JSON.parseObject(resStr);
            return resObj.getString("answer");
        } catch (IOException e) {
            return "调用 Dify 失败: " + e.getMessage();
        }
    }
}
