package com.doom.fg.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.doom.fg.context.UserContext;
import com.doom.fg.entity.AiApiLog;
import com.doom.fg.entity.FoodItem;
import com.doom.fg.entity.RecipeRecord;
import com.doom.fg.mapper.AiApiLogMapper;
import com.doom.fg.service.AiService;
import com.doom.fg.service.FoodItemService;
import com.doom.fg.service.RecipeRecordService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
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

    @Autowired
    private AiApiLogMapper aiApiLogMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.api-url}")
    private String apiUrl;

    @Value("${ai.model}")
    private String modelName;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    // Redis Key 前缀
    private static final String HISTORY_KEY_PREFIX = "ai:chat:history:";
    // 保留最近几轮对话 (1轮 = 1问 + 1答，10条即保留5轮)
    private static final int MAX_HISTORY_SIZE = 10;
    // 上下文过期时间 (例如 30 分钟无对话则清空)
    private static final long HISTORY_EXPIRE_MINUTES = 30;

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
        Long userId = UserContext.getUserId();
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

        AiApiLog log = new AiApiLog();
        log.setUserId(userId);
        log.setModel("deepseek-chat");
        log.setRequestType("RECIPE");

        long start = System.currentTimeMillis();

        try (Response response = client.newCall(request).execute()) {
            long end = System.currentTimeMillis();
            log.setLatencyMs(end - start);
            log.setStatusCode(response.code());

            String responseBody = response.body() != null ? response.body().string() : "";
            System.out.println("AI API Response Code: " + response.code());
            System.out.println("AI API Response Body: " + responseBody);

            if (response.isSuccessful()) {
                JSONObject resObj = JSON.parseObject(responseBody);

                if (resObj.containsKey("usage")) {
                    JSONObject usage = resObj.getJSONObject("usage");
                    log.setPromptTokens(usage.getIntValue("prompt_tokens"));
                    log.setCompletionTokens(usage.getIntValue("completion_tokens"));
                    log.setTotalTokens(usage.getIntValue("total_tokens"));
                }

                log.setIsSuccess(1);
                aiApiLogMapper.insert(log);

                return resObj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
            } else {
                log.setIsSuccess(0);
                log.setErrorMsg("API Error: " + response.code() + " - " + responseBody);
                aiApiLogMapper.insert(log);
                return "API 调用失败（状态码：" + response.code() + "），请检查配置。";
            }
        } catch (IOException e) {
            log.setLatencyMs(System.currentTimeMillis() - start);
            log.setIsSuccess(0);
            log.setStatusCode(500);
            log.setErrorMsg(e.getMessage());
            aiApiLogMapper.insert(log);

            System.err.println("AI API Exception: " + e.getMessage());
            e.printStackTrace();
            return "网络错误：" + e.getMessage();
        }
    }

    /**
     * 清空对话历史
     */
    public void clearHistory() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        String redisKey = HISTORY_KEY_PREFIX + userId;
        redisTemplate.delete(redisKey);
    }

    /**
     * 支持上下文的通用对话接口
     */
    public String chatWithHistory(String userMessage) {
        Long userId = UserContext.getUserId();
        String redisKey = HISTORY_KEY_PREFIX + userId;

        // 1. 准备 System Prompt (系统人设)
        // 注意：System Prompt 通常不存入 Redis，而是每次请求时放在第一条
        AiMessage systemMsg = new AiMessage("system", "你是一个专业的冰箱食材管理助手，请帮助用户规划菜谱和管理库存。");

        // 2. 从 Redis 获取历史记录
        List<AiMessage> history = getHistoryFromRedis(redisKey);

        // 3. 构建本次请求的消息列表 (System + History + Current User Msg)
        List<AiMessage> requestMessages = new ArrayList<>();
        requestMessages.add(systemMsg);
        requestMessages.addAll(history);
        requestMessages.add(new AiMessage("user", userMessage));

        // 4. 发送请求给 DeepSeek
        String aiReply = callDeepSeekApi(requestMessages);

        // 5. 如果调用成功，将本次对话存入 Redis (异步或同步均可)
        if (aiReply != null && !aiReply.startsWith("Error")) {
            saveToRedis(redisKey, new AiMessage("user", userMessage));
            saveToRedis(redisKey, new AiMessage("assistant", aiReply));
        }

        return aiReply;
    }
    /**
     * 存储消息到 Redis
     */
    private List<AiMessage> getHistoryFromRedis(String key) {
        // 从 Redis List 获取所有数据
        List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
        if (jsonList == null || jsonList.isEmpty()) {
            return new ArrayList<>();
        }
        return jsonList.stream()
                .map(json -> JSON.parseObject(json, AiMessage.class))
                .collect(Collectors.toList());
    }
    private void saveToRedis(String key, AiMessage message) {
        // 1. 推入新消息
        redisTemplate.opsForList().rightPush(key, JSON.toJSONString(message));

        // 2. 裁剪长度 (保持最新的 N 条)
        // 如果当前长度 > MAX，移除头部旧数据
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > MAX_HISTORY_SIZE) {
            redisTemplate.opsForList().trim(key, size - MAX_HISTORY_SIZE, -1);
        }

        // 3. 刷新过期时间
        redisTemplate.expire(key, HISTORY_EXPIRE_MINUTES, TimeUnit.MINUTES);
    }
    private String callDeepSeekApi(List<AiMessage> messages) {
        // 记录日志准备
        AiApiLog logEntity = new AiApiLog();
        logEntity.setUserId(UserContext.getUserId());
        logEntity.setModel(modelName);
        logEntity.setRequestType("CHAT");
        long start = System.currentTimeMillis();

        try {
            // 构建 JSON Body
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", modelName);
            jsonBody.put("messages", messages); // 直接放入对象数组
            jsonBody.put("stream", false);

            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"), jsonBody.toJSONString());

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                long end = System.currentTimeMillis();
                logEntity.setLatencyMs(end - start);
                logEntity.setStatusCode(response.code());

                String resStr = response.body() != null ? response.body().string() : "";

                if (response.isSuccessful()) {
                    JSONObject resObj = JSON.parseObject(resStr);

                    // 记录 Token 消耗 (您之前的逻辑)
                    if (resObj.containsKey("usage")) {
                        JSONObject usage = resObj.getJSONObject("usage");
                        logEntity.setPromptTokens(usage.getIntValue("prompt_tokens"));
                        logEntity.setCompletionTokens(usage.getIntValue("completion_tokens"));
                        logEntity.setTotalTokens(usage.getIntValue("total_tokens"));
                    }
                    logEntity.setIsSuccess(1);
                    aiApiLogMapper.insert(logEntity);

                    // 返回 AI 的回答内容
                    return resObj.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                } else {
                    logEntity.setIsSuccess(0);
                    logEntity.setErrorMsg("HTTP " + response.code());
                    aiApiLogMapper.insert(logEntity);
                    return "Error: API call failed with code " + response.code();
                }
            }
        } catch (Exception e) {
            logEntity.setLatencyMs(System.currentTimeMillis() - start);
            logEntity.setIsSuccess(0);
            logEntity.setErrorMsg(e.getMessage());
            aiApiLogMapper.insert(logEntity);
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    // 内部 DTO 类
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AiMessage implements Serializable {
        private String role;  // "user"
        private String content;
    }
}
