package com.doom.fg.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.doom.fg.context.UserContext;
import com.doom.fg.entity.FoodItem;
import com.doom.fg.entity.RecipeRecord;
import com.doom.fg.service.AiService;
import com.doom.fg.service.FoodItemService;
import com.doom.fg.service.RecipeRecordService;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AiServiceImpl implements AiService {

    @Autowired
    private FoodItemService foodItemService;

    @Autowired
    private RecipeRecordService recipeRecordService;

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.api-url}")
    private String apiUrl;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    @Override
    public Map<String, String> getAiRecipe(List<Long> foodIds) {
        List<FoodItem> foodItems = foodItemService.listByIds(foodIds);
        List<String> foodNames = foodItems.stream()
                .map(FoodItem::getName)
                .collect(Collectors.toList());

        String foodNamesStr = String.join("、", foodNames);
        String recipeContent = getRecipe(foodNames);

        Map<String, String> result = new HashMap<>();
        result.put("title", "AI 生成的菜谱");
        result.put("content", recipeContent);

        saveRecipeRecord(foodNamesStr, recipeContent);

        return result;
    }

    private void saveRecipeRecord(String foodNames, String content) {
        Long userId = UserContext.getUserId();
        
        if (userId == null) {
            throw new RuntimeException("用户未登录，无法保存菜谱记录");
        }
        
        RecipeRecord record = new RecipeRecord();
        record.setUserId(userId);
        record.setFoodNames(foodNames);
        record.setTitle("AI 生成的菜谱");
        record.setContent(content);
        recipeRecordService.save(record);
    }

    public String getRecipe(List<String> foods) {
        String foodList = String.join("、", foods);
        String systemPrompt = "你是一个专业的\"智能冰箱菜谱助手\"。\n" +
                "你的职责是：仅且只能根据用户提供的食材给出菜谱建议。\n" +
                "严格规则：\n" +
                "如果用户提供的食材可以组合成菜肴，请按 Markdown 格式提供菜名、食材和步骤。\n" +
                "如果用户输入的不是食材，或者询问的问题与\"做菜、食材处理、菜谱推荐\"无关（例如问天气、写代码、聊政治、讲笑话等），你必须直接且仅回复\"抱歉！\"，不要带任何解释。\n" +
                "即使食材包含不能吃的东西，也要识别并拒绝。";

        String userPrompt = "我现在冰箱里有这些食材：" + foodList + "。请帮我设计一个菜谱。";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek-chat");
        requestBody.put("temperature", 0.3);

        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);

        requestBody.put("messages", List.of(systemMessage, userMessage));

        String json = JSON.toJSONString(requestBody);

        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            System.out.println("AI API Response Code: " + response.code());
            System.out.println("AI API Response Body: " + responseBody);
            
            if (response.isSuccessful()) {
                JSONObject resObj = JSON.parseObject(responseBody);
                return resObj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
            } else {
                System.err.println("AI API Error: " + response.code() + " - " + responseBody);
                return "API 调用失败（状态码：" + response.code() + "），请检查配置。";
            }
        } catch (IOException e) {
            System.err.println("AI API Exception: " + e.getMessage());
            e.printStackTrace();
            return "网络错误：" + e.getMessage();
        }
    }
}
