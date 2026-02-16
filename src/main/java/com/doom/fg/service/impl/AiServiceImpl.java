package com.doom.fg.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.doom.fg.context.UserContext;
import com.doom.fg.entity.FoodItem;
import com.doom.fg.entity.RecipeRecord;
import com.doom.fg.entity.User;
import com.doom.fg.mapper.AiApiLogMapper;
import com.doom.fg.service.AiService;
import com.doom.fg.service.FoodItemService;
import com.doom.fg.service.RecipeRecordService;
import com.doom.fg.service.UserService;
import com.doom.fg.util.OpenAiCompatibleEngine;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
@Slf4j
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

    @Autowired
    private OpenAiCompatibleEngine aiEngine; // 注入通用适配器引擎

    @Autowired
    private UserService userService;

    // 解决错误2：从 application.yaml 读取兜底默认配置
    @Value("${ai.api-key}")
    private String defaultApiKey;

    @Value("${ai.api-url}")
    private String defaultApiUrl;

    @Value("${ai.model:deepseek-chat}")
    private String defaultModel;

    // Redis Key 前缀
    private static final String HISTORY_KEY_PREFIX = "ai:chat:history:";
    // 保留最近 10 条消息 (5轮对话)
    private static final int MAX_HISTORY_SIZE = 10;
    // 上下文过期时间
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
        if (userId == null) return;

        RecipeRecord record = new RecipeRecord();
        record.setUserId(userId);
        record.setFoodNames(foodNames);
        record.setTitle("AI 生成的菜谱");
        record.setContent(content);
        recipeRecordService.save(record);
    }

    /**
     * 初始菜谱生成：解决错误1
     */
    public String getRecipe(List<String> foods) {
        Long userId = UserContext.getUserId();
        User user = userService.getById(userId);

        String redisKey = HISTORY_KEY_PREFIX + userId;
        Boolean delete = redisTemplate.delete(redisKey);
        // 1. 获取配置路由 (解决变量缺失问题)
        AiConfig config = getEffectiveConfig();

        // 2. 清理旧对话，构建新提示词
        redisTemplate.delete(redisKey);
        String foodList = String.join("、", foods);

        String systemPrompt = "你是一个专业的'冰箱守卫者'主厨。你的任务是基于用户提供的食材清单设计菜谱。\n" +
                "【核心规则】\n" +
                "1. 严禁引入清单中不存在的主食材（肉类、蔬菜、蛋奶等）。你可以假设用户拥有基础调料（油盐酱醋糖、葱姜蒜、辣椒等）。\n" +
                "2. 如果用户提供的食材无法组合成常规菜肴，请发挥创意进行混搭，或者礼貌告知无法生成。\n" +
                "3. 输出格式要求：Markdown格式，包含菜名、食材表、详细步骤、营养贴士。\n" +
                "4. 这一步是确立“食材范围”的关键，后续对话将严格限制在这个范围内。";

        String userPrompt = "我冰箱里有这些食材：" + foodList + "。请帮我设计一道美味的家常菜。";

        // 3. 调用适配器引擎 (解决参数不匹配问题，传入6个参数)
        String aiReply = aiEngine.chat(config.getApiKey(), config.getBaseUrl(), config.getModel(), systemPrompt, null, userPrompt);

        // 4. 构建初始记忆
        if (aiReply != null && !aiReply.startsWith("Error")) {
            saveToRedis(redisKey, new AiMessage("user", userPrompt));
            saveToRedis(redisKey, new AiMessage("assistant", aiReply));
        }
        return aiReply;
    }

    /**
     * 支持上下文的对话：维持对话锁定逻辑
     */
    @Override
    public String chatWithHistory(String userMessage) {
        Long userId = UserContext.getUserId();
        User user = userService.getById(userId);
        String redisKey = HISTORY_KEY_PREFIX + userId;

        // 1. 动态获取配置
        AiConfig config = getEffectiveConfig();

        // 2. 获取历史记录
        List<AiMessage> history = getHistoryFromRedis(redisKey);
        if (history == null || history.isEmpty()) {
            return "抱歉，我的记忆断片了。请先选择食材生成菜谱。";
        }

        // 3. MCP逻辑：注入实时临期上下文
        List<FoodItem> expiringFoods = foodItemService.getExpiringSoon(3);
        String fridgeContext = expiringFoods.stream()
                .map(f -> f.getName() + "(余" + f.getDaysLeft() + "天)")
                .collect(Collectors.joining("、"));

        // 4. 构建严格的对话主题锁定提示词
        String systemPrompt = "你是一个贴心的厨师助手。用户正在根据之前生成的菜谱提出调整需求。\n" +
                "【当前冰箱实时库存】： " + (fridgeContext.isEmpty() ? "无临期食材" : fridgeContext) + "\n" +
                "【严格指令】\n" +
                "1. 你的回答必须完全基于上下文中的食材清单。严禁引入新的主食材。\n" +
                "2. 优先建议用户使用上述【临期清单】中的食材。\n" +
                "3. 用户的调整只能通过调整调料、烹饪方式实现。\n" +
                "4. 如果要求无法实现（如只有鸡蛋要求做肉），请礼貌拒绝并解释。";

        // 5. 调用适配器 (解决参数不匹配问题，传入6个参数)
        String aiReply = aiEngine.chat(config.getApiKey(), config.getBaseUrl(), config.getModel(), systemPrompt, history, userMessage);

        // 6. 维护对话滑动窗口
        if (aiReply != null && !aiReply.startsWith("Error")) {
            saveToRedis(redisKey, new AiMessage("user", userMessage));
            saveToRedis(redisKey, new AiMessage("assistant", aiReply));
        }
        return aiReply;
    }
    /**
     * 获取有效的 AI 配置 (用户配置优先 -> 系统配置兜底)
     */
    /**
     * 获取有效的 AI 配置 (用户配置优先 -> 系统配置兜底)
     */
    private AiConfig getEffectiveConfig() {
        Long userId = UserContext.getUserId();
        User user = userService.getById(userId);

        // 默认值
        String apiKey = defaultApiKey;
        String baseUrl = defaultApiUrl;
        String model = defaultModel;
        boolean isCustom = false; // 标记是否使用了自定义配置

        // 1. 尝试从用户个性化配置中覆盖
        if (user != null && StringUtils.hasText(user.getAiConfig())) {
            try {
                JSONObject userConfig = JSON.parseObject(user.getAiConfig());
                if (userConfig != null) {
                    if (StringUtils.hasText(userConfig.getString("apiKey"))) {
                        apiKey = userConfig.getString("apiKey");
                        isCustom = true;
                    }
                    if (StringUtils.hasText(userConfig.getString("baseUrl"))) {
                        baseUrl = userConfig.getString("baseUrl");
                        isCustom = true;
                    }
                    if (StringUtils.hasText(userConfig.getString("model"))) {
                        model = userConfig.getString("model");
                        isCustom = true;
                    }
                }
            } catch (Exception e) {
                log.error("用户 {} AI配置解析失败，降级使用默认配置", userId, e);
            }
        }

        // 【日志核心点】打印当前使用的配置来源
        if (isCustom) {
            log.info(">>> 使用用户 [自定义] AI配置 | Model: {} | URL: {}", model, baseUrl);
        } else {
            log.info(">>> 使用系统 [默认] AI配置 | Model: {} | URL: {}", model, baseUrl);
        }

        // 2. 最终检查
        if (!StringUtils.hasText(apiKey)) {
            log.error("用户 {} 未配置AI，且系统无默认配置", userId);
            throw new RuntimeException("AI_CONFIG_MISSING");
        }

        return new AiConfig(apiKey, baseUrl, model);
    }
    // --- 内部私有方法 (Redis维护) ---
    private List<AiMessage> getHistoryFromRedis(String key) {
        List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
        if (jsonList == null || jsonList.isEmpty()) return new ArrayList<>();
        return jsonList.stream()
                .map(json -> JSON.parseObject(json, AiMessage.class))
                .collect(Collectors.toList());
    }

    private void saveToRedis(String key, AiMessage message) {
        redisTemplate.opsForList().rightPush(key, JSON.toJSONString(message));
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > MAX_HISTORY_SIZE) {
            redisTemplate.opsForList().trim(key, size - MAX_HISTORY_SIZE, -1);
        }
        redisTemplate.expire(key, HISTORY_EXPIRE_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void clearHistory() {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new RuntimeException("用户未登录");
        redisTemplate.delete(HISTORY_KEY_PREFIX + userId);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AiMessage implements Serializable {
        private String role;
        private String content;
    }
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class AiConfig {
        private String apiKey;
        private String baseUrl;
        private String model;
    }
}