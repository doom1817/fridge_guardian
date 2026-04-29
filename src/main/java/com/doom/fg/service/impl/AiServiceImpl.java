package com.doom.fg.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.doom.fg.context.UserContext;
import com.doom.fg.dto.AiRecipeResponse;
import com.doom.fg.entity.AiApiLog;
import com.doom.fg.entity.FoodItem;
import com.doom.fg.entity.RecipeRecord;
import com.doom.fg.entity.User;
import com.doom.fg.exception.AiException;
import com.doom.fg.mapper.AiApiLogMapper;
import com.doom.fg.service.AiService;
import com.doom.fg.service.FoodItemService;
import com.doom.fg.service.RecipeRecordService;
import com.doom.fg.service.UserService;
import com.doom.fg.util.AiEngine;
import com.doom.fg.util.AiErrorType;
import com.doom.fg.util.AiPrompts;
import com.doom.fg.util.AiResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiServiceImpl implements AiService {
    private static final String HISTORY_KEY_PREFIX = "ai:chat:history:";
    private static final int MAX_HISTORY_SIZE = 10;
    private static final long HISTORY_EXPIRE_MINUTES = 30;
    private static final String DEFAULT_RECIPE_TITLE = "AI 生成的家常菜";

    @Autowired
    private FoodItemService foodItemService;

    @Autowired
    private RecipeRecordService recipeRecordService;

    @Autowired
    private AiApiLogMapper aiApiLogMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AiEngine aiEngine;

    @Autowired
    private UserService userService;

    @Value("${ai.api-key}")
    private String defaultApiKey;

    @Value("${ai.api-url}")
    private String defaultApiUrl;

    @Value("${ai.model:deepseek-chat}")
    private String defaultModel;

    @Override
    public Map<String, Object> getAiRecipe(List<Long> foodIds) {
        List<FoodItem> foodItems = foodItemService.listByIds(foodIds);
        List<String> foodNames = foodItems.stream()
                .map(FoodItem::getName)
                .filter(StringUtils::hasText)
                .toList();

        if (foodNames.isEmpty()) {
            throw new AiException(AiErrorType.AI_BAD_RESPONSE_FORMAT.getCode(), "请先选择至少一种食材");
        }

        AiRecipeResult recipeResult = generateRecipe(foodNames);
        AiRecipeResponse recipe = recipeResult.getRecipe();
        String recipeMarkdown = recipe.getMarkdown();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", recipe.getTitle());
        result.put("summary", recipe.getSummary());
        result.put("content", recipeMarkdown);
        result.put("markdown", recipeMarkdown);
        result.put("structured", recipe);
        result.put("promptVersion", AiPrompts.PROMPT_VERSION);
        result.put("recipeRecordId", recipeResult.getRecipeRecordId());
        return result;
    }

    @Override
    public String chatWithHistory(String userMessage) {
        Long userId = UserContext.getUserId();
        String redisKey = HISTORY_KEY_PREFIX + userId;
        AiConfig config = getEffectiveConfig();

        List<AiMessage> history = getHistoryFromRedis(redisKey);
        if (history.isEmpty()) {
            throw new AiException(AiErrorType.AI_BAD_RESPONSE_FORMAT.getCode(), "请先选择食材生成菜谱，再继续追问");
        }

        List<FoodItem> expiringFoods = foodItemService.getExpiringSoon(3);
        String systemPrompt = AiPrompts.buildChatSystemPrompt(expiringFoods);

        long startTime = System.currentTimeMillis();
        AiResponse aiResponse = aiEngine.chat(
                config.getApiKey(),
                config.getBaseUrl(),
                config.getModel(),
                systemPrompt,
                history,
                userMessage
        );
        long latency = System.currentTimeMillis() - startTime;

        String reply = extractChatReply(aiResponse);
        saveAiLog(userId, config.getModel(), "CHAT", "RECIPE_CHAT",
                systemPrompt, userMessage, reply, aiResponse, latency, 0, null);

        saveToRedis(redisKey, new AiMessage("user", userMessage));
        saveToRedis(redisKey, new AiMessage("assistant", reply));
        return reply;
    }

    @Override
    public void submitRecipeFeedback(Long recipeRecordId, String feedbackStatus, String feedbackReason) {
        if (recipeRecordId == null) {
            throw new AiException(AiErrorType.AI_BAD_RESPONSE_FORMAT.getCode(), "缺少菜谱记录 ID");
        }
        if (!StringUtils.hasText(feedbackStatus)) {
            throw new AiException(AiErrorType.AI_BAD_RESPONSE_FORMAT.getCode(), "请选择反馈结果");
        }

        Long userId = UserContext.getUserId();
        RecipeRecord record = recipeRecordService.getById(recipeRecordId);
        if (record == null || !userId.equals(record.getUserId())) {
            throw new AiException(AiErrorType.AI_BAD_RESPONSE_FORMAT.getCode(), "未找到对应的菜谱记录");
        }

        record.setFeedbackStatus(feedbackStatus);
        record.setFeedbackReason(StringUtils.hasText(feedbackReason) ? feedbackReason.trim() : null);
        record.setFeedbackTime(LocalDateTime.now());
        recipeRecordService.updateById(record);
    }

    @Override
    public void clearHistory() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        redisTemplate.delete(HISTORY_KEY_PREFIX + userId);
    }

    private AiRecipeResult generateRecipe(List<String> foods) {
        Long userId = UserContext.getUserId();
        String redisKey = HISTORY_KEY_PREFIX + userId;
        AiConfig config = getEffectiveConfig();

        redisTemplate.delete(redisKey);

        String systemPrompt = AiPrompts.buildRecipeSystemPrompt(buildPreferencesPrompt(config));
        String userPrompt = AiPrompts.buildRecipeUserPrompt(foods);

        long startTime = System.currentTimeMillis();
        AiResponse aiResponse = aiEngine.chat(
                config.getApiKey(),
                config.getBaseUrl(),
                config.getModel(),
                systemPrompt,
                null,
                userPrompt
        );
        long latency = System.currentTimeMillis() - startTime;

        try {
            AiRecipeResponse recipe = extractRecipe(aiResponse);
            Long recipeRecordId = saveRecipeRecord(String.join("、", foods), recipe.getTitle(), recipe.getMarkdown());
            saveAiLog(userId, config.getModel(), "RECIPE", "RECIPE_GENERATE",
                    systemPrompt, userPrompt, recipe.getMarkdown(), aiResponse, latency, foods.size(), null);
            saveToRedis(redisKey, new AiMessage("user", userPrompt));
            saveToRedis(redisKey, new AiMessage("assistant", recipe.getMarkdown()));
            return new AiRecipeResult(recipe, recipeRecordId);
        } catch (AiException e) {
            saveAiLog(userId, config.getModel(), "RECIPE", "RECIPE_GENERATE",
                    systemPrompt, userPrompt, null, aiResponse, latency, foods.size(), e.getErrorCode());
            throw e;
        }
    }

    private String buildPreferencesPrompt(AiConfig config) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(config.getTastePreference())) {
            parts.add("口味偏好：" + config.getTastePreference());
        }
        if (StringUtils.hasText(config.getDietGoal())) {
            parts.add("饮食目标：" + config.getDietGoal());
        }
        if (StringUtils.hasText(config.getTaboos())) {
            parts.add("忌口说明：" + config.getTaboos());
        }
        if (StringUtils.hasText(config.getCookingTimePreference())) {
            parts.add("烹饪时长偏好：" + config.getCookingTimePreference());
        }
        return parts.isEmpty()
                ? "未提供额外偏好，请按通用家常菜思路生成。"
                : String.join("；", parts) + "。";
    }

    private AiRecipeResponse extractRecipe(AiResponse aiResponse) {
        if (!aiResponse.isSuccess()) {
            AiErrorType errorType = aiResponse.getErrorType() == null ? AiErrorType.AI_UNKNOWN_ERROR : aiResponse.getErrorType();
            throw new AiException(errorType.getCode(), errorType.getUserMessage());
        }

        try {
            String rawContent = sanitizeJson(aiResponse.getContent());
            AiRecipeResponse recipe = JSON.parseObject(rawContent, AiRecipeResponse.class);
            normalizeRecipe(recipe);
            return recipe;
        } catch (Exception e) {
            log.warn("Failed to parse recipe response: {}", aiResponse.getContent(), e);
            throw new AiException(AiErrorType.AI_BAD_RESPONSE_FORMAT.getCode(), AiErrorType.AI_BAD_RESPONSE_FORMAT.getUserMessage());
        }
    }

    private String extractChatReply(AiResponse aiResponse) {
        if (!aiResponse.isSuccess()) {
            AiErrorType errorType = aiResponse.getErrorType() == null ? AiErrorType.AI_UNKNOWN_ERROR : aiResponse.getErrorType();
            throw new AiException(errorType.getCode(), errorType.getUserMessage());
        }
        return aiResponse.getContent();
    }

    private void normalizeRecipe(AiRecipeResponse recipe) {
        if (!StringUtils.hasText(recipe.getTitle())) {
            recipe.setTitle(DEFAULT_RECIPE_TITLE);
        }
        if (recipe.getIngredients() == null) {
            recipe.setIngredients(new ArrayList<>());
        }
        if (recipe.getSteps() == null) {
            recipe.setSteps(new ArrayList<>());
        }
        if (recipe.getTips() == null) {
            recipe.setTips(new ArrayList<>());
        }
        if (!StringUtils.hasText(recipe.getDifficulty())) {
            recipe.setDifficulty("easy");
        }
        if (recipe.getEstimatedTimeMinutes() == null || recipe.getEstimatedTimeMinutes() < 0) {
            recipe.setEstimatedTimeMinutes(20);
        }
        if (!StringUtils.hasText(recipe.getSummary())) {
            recipe.setSummary("优先利用现有食材完成的一道家常菜。");
        }
        if (!StringUtils.hasText(recipe.getNutrition())) {
            recipe.setNutrition("营养信息根据当前食材组合做了简要估算。");
        }
        if (!StringUtils.hasText(recipe.getMarkdown())) {
            recipe.setMarkdown(buildMarkdown(recipe));
        }
        if (recipe.getUseExpiringFoodFirst() == null) {
            recipe.setUseExpiringFoodFirst(Boolean.FALSE);
        }
    }

    private String buildMarkdown(AiRecipeResponse recipe) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(recipe.getTitle()).append("\n\n");
        builder.append("**简介：** ").append(recipe.getSummary()).append("\n\n");
        builder.append("## 食材\n");
        for (AiRecipeResponse.IngredientItem ingredient : recipe.getIngredients()) {
            String amount = StringUtils.hasText(ingredient.getAmount()) ? " - " + ingredient.getAmount() : "";
            builder.append("- ").append(ingredient.getName()).append(amount).append("\n");
        }
        builder.append("\n## 步骤\n");
        for (int i = 0; i < recipe.getSteps().size(); i++) {
            builder.append(i + 1).append(". ").append(recipe.getSteps().get(i)).append("\n");
        }
        if (!recipe.getTips().isEmpty()) {
            builder.append("\n## 小贴士\n");
            for (String tip : recipe.getTips()) {
                builder.append("- ").append(tip).append("\n");
            }
        }
        builder.append("\n## 营养说明\n").append(recipe.getNutrition()).append("\n");
        return builder.toString();
    }

    private Long saveRecipeRecord(String foodNames, String title, String content) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return null;
        }

        RecipeRecord record = new RecipeRecord();
        record.setUserId(userId);
        record.setFoodNames(foodNames);
        record.setTitle(StringUtils.hasText(title) ? title : DEFAULT_RECIPE_TITLE);
        record.setContent(content);
        recipeRecordService.save(record);
        return record.getId();
    }

    private void saveAiLog(Long userId, String model, String requestType, String scenarioType,
                           String systemPrompt, String userMessage, String reply,
                           AiResponse aiResponse, long latency, int foodCount, String overrideErrorType) {
        AiApiLog logEntity = new AiApiLog();
        logEntity.setUserId(userId);
        logEntity.setModel(model);
        logEntity.setRequestType(requestType);
        logEntity.setPromptVersion(AiPrompts.PROMPT_VERSION);
        logEntity.setScenarioType(scenarioType);
        logEntity.setFoodCount(foodCount);
        logEntity.setLatencyMs(latency);
        logEntity.setIsSuccess(aiResponse.isSuccess() ? 1 : 0);

        String resolvedErrorType = overrideErrorType;
        if (!StringUtils.hasText(resolvedErrorType) && aiResponse.getErrorType() != null) {
            resolvedErrorType = aiResponse.getErrorType().getCode();
        }
        logEntity.setErrorType(resolvedErrorType);

        if (!aiResponse.isSuccess() || StringUtils.hasText(resolvedErrorType)) {
            String rawError = aiResponse.getRawError();
            if (!StringUtils.hasText(rawError) && StringUtils.hasText(resolvedErrorType)) {
                rawError = resolvedErrorType;
            }
            logEntity.setErrorMsg(rawError);
        }

        int estimatedPromptTokens = safeLength(systemPrompt) + safeLength(userMessage);
        int estimatedCompletionTokens = safeLength(reply);

        int promptTokens = aiResponse.getPromptTokens() != null ? aiResponse.getPromptTokens() : estimatedPromptTokens;
        int completionTokens = aiResponse.getCompletionTokens() != null ? aiResponse.getCompletionTokens() : estimatedCompletionTokens;
        int totalTokens = aiResponse.getTotalTokens() != null ? aiResponse.getTotalTokens() : promptTokens + completionTokens;

        logEntity.setPromptTokens(promptTokens);
        logEntity.setCompletionTokens(completionTokens);
        logEntity.setTotalTokens(totalTokens);
        aiApiLogMapper.insert(logEntity);
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private AiConfig getEffectiveConfig() {
        Long userId = UserContext.getUserId();
        User user = userService.getById(userId);

        String apiKey = defaultApiKey;
        String baseUrl = defaultApiUrl;
        String model = defaultModel;
        String tastePreference = "";
        String dietGoal = "";
        String taboos = "";
        String cookingTimePreference = "";
        boolean isCustom = false;

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
                    tastePreference = userConfig.getString("tastePreference");
                    dietGoal = userConfig.getString("dietGoal");
                    taboos = userConfig.getString("taboos");
                    cookingTimePreference = userConfig.getString("cookingTimePreference");
                }
            } catch (Exception e) {
                log.error("User {} AI config parse failed, fallback to default", userId, e);
            }
        }

        if (isCustom) {
            log.info("Using custom AI config. model={}, baseUrl={}", model, baseUrl);
        } else {
            log.info("Using default AI config. model={}, baseUrl={}", model, baseUrl);
        }

        if (!StringUtils.hasText(apiKey)) {
            throw new AiException(AiErrorType.AI_CONFIG_MISSING.getCode(), AiErrorType.AI_CONFIG_MISSING.getUserMessage());
        }
        return new AiConfig(apiKey, baseUrl, model, tastePreference, dietGoal, taboos, cookingTimePreference);
    }

    private List<AiMessage> getHistoryFromRedis(String key) {
        List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
        if (jsonList == null || jsonList.isEmpty()) {
            return new ArrayList<>();
        }
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

    private String sanitizeJson(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7).trim();
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3).trim();
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }
        return trimmed;
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
        private String tastePreference;
        private String dietGoal;
        private String taboos;
        private String cookingTimePreference;
    }

    @Data
    @AllArgsConstructor
    private static class AiRecipeResult {
        private AiRecipeResponse recipe;
        private Long recipeRecordId;
    }
}
