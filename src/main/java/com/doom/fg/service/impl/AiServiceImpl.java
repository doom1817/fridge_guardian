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
        String redisKey = HISTORY_KEY_PREFIX + userId;

        // 1. 【关键】新生成意味着新会话，必须清空旧的历史记忆
        redisTemplate.delete(redisKey);

        // 2. 构建提示词
        String foodList = String.join("、", foods);

        // --- System Prompt (设定严格边界) ---
        String systemPrompt = "你是一个专业的'冰箱守卫者'主厨。你的任务是基于用户提供的食材清单设计菜谱。\n" +
                "【核心规则】\n" +
                "1. 严禁引入清单中不存在的主食材（肉类、蔬菜、蛋奶等）。你可以假设用户拥有基础调料（油盐酱醋糖、葱姜蒜、辣椒等）。\n" +
                "2. 如果用户提供的食材无法组合成常规菜肴，请发挥创意进行混搭，或者礼貌告知无法生成。\n" +
                "3. 输出格式要求：Markdown格式，包含菜名、食材表、详细步骤、营养贴士。\n" +
                "4. 这一步是确立“食材范围”的关键，后续对话将严格限制在这个范围内。";

        String userPrompt = "我冰箱里有这些食材：" + foodList + "。请帮我设计一道美味的家常菜。";

        // 3. 准备请求消息链
        List<AiMessage> messages = new ArrayList<>();
        messages.add(new AiMessage("system", systemPrompt));
        messages.add(new AiMessage("user", userPrompt));

        // 4. 调用 AI
        String aiReply = callDeepSeekApi(messages);

        // 5. 【关键】构建初始记忆上下文
        // 只有调用成功才存入 Redis，这样后续的 chatWithHistory 才能读到这些食材信息
        if (aiReply != null && !aiReply.startsWith("Error")) {
            // 存入 User: "我有A,B,C..."
            saveToRedis(redisKey, new AiMessage("user", userPrompt));
            // 存入 AI: "推荐菜谱X..."
            saveToRedis(redisKey, new AiMessage("assistant", aiReply));
        }

        return aiReply;
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
    @Override
    public String chatWithHistory(String userMessage) {
        Long userId = UserContext.getUserId();
        String redisKey = HISTORY_KEY_PREFIX + userId;

        // 1. 获取历史记录
        List<AiMessage> history = getHistoryFromRedis(redisKey);

        // 如果 Redis 里没数据，说明用户没先生成菜谱直接发请求，或者缓存过期了
        if (history == null || history.isEmpty()) {
            return "抱歉，我的记忆好像断片了。请先在左侧勾选食材并点击“生成菜谱”，让我先了解您有哪些食材。";
        }

        // 2. 构建请求消息链
        List<AiMessage> messages = new ArrayList<>();

        // --- System Prompt (强化锁定逻辑) ---
        // 这里的提示词专门针对“后续对话”，强调不能偏题
        String systemPrompt = "你是一个贴心的厨师助手。用户正在根据之前生成的菜谱（见上下文）提出调整需求。\n" +
                "【严格指令】\n" +
                "1. 你的回答必须 **完全基于上下文中的食材清单**。严禁在建议中引入新的主食材（如肉、菜），除非用户显式补充了新食材。\n" +
                "2. 用户的调整（如'太淡了'、'不想炸'、'做成汤'）只能通过调整调料、烹饪方式或去除某些现有食材来实现。\n" +
                "3. 如果用户的要求在现有食材下无法实现（例如只有'鸡蛋'却要求'做红烧肉'），请礼貌拒绝并解释原因，建议用户重新录入食材。";

        messages.add(new AiMessage("system", systemPrompt));

        // 加入历史 (包含了之前的食材声明和菜谱)
        messages.addAll(history);

        // 加入当前用户的调整指令
        messages.add(new AiMessage("user", userMessage));

        // 3. 调用 AI
        String aiReply = callDeepSeekApi(messages);

        // 4. 保存新一轮对话到 Redis (滑动窗口)
        if (aiReply != null && !aiReply.startsWith("Error")) {
            saveToRedis(redisKey, new AiMessage("user", userMessage));
            saveToRedis(redisKey, new AiMessage("assistant", aiReply));
        }

        return aiReply;
    }
    /**
     * 存入 Redis 并维护长度
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

        // 2.滑动窗口：只保留最近 10 条消息 (5轮对话)，避免 Token 消耗过大
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
