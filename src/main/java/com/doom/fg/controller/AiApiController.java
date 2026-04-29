package com.doom.fg.controller;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.doom.fg.common.Result;
import com.doom.fg.context.UserContext;
import com.doom.fg.entity.AiApiLog;
import com.doom.fg.entity.User;
import com.doom.fg.mapper.AiApiLogMapper;
import com.doom.fg.service.AiService;
import com.doom.fg.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiApiController {
    private static final String API_KEY_MASK = "********";

    @Autowired
    private AiService aiService;

    @Autowired
    private AiApiLogMapper aiApiLogMapper;

    @Autowired
    private UserService userService;

    @PostMapping("/generate-recipe")
    public Result<Map<String, Object>> generateRecipe(@RequestBody List<Long> foodIds) {
        return Result.success(aiService.getAiRecipe(foodIds));
    }

    @PostMapping("/recipe-feedback")
    public Result<Void> submitRecipeFeedback(@RequestBody Map<String, String> params) {
        Long recipeRecordId = params.get("recipeRecordId") == null ? null : Long.parseLong(params.get("recipeRecordId"));
        String feedbackStatus = params.get("feedbackStatus");
        String feedbackReason = params.get("feedbackReason");
        aiService.submitRecipeFeedback(recipeRecordId, feedbackStatus, feedbackReason);
        return Result.success();
    }

    @PostMapping("/ai-config")
    public Result<Void> updateAiConfig(@RequestBody Map<String, String> config) {
        Long userId = UserContext.getUserId();
        User user = userService.getById(userId);
        user.setAiConfig(JSON.toJSONString(config));
        userService.updateById(user);
        return Result.success();
    }

    @GetMapping("/my-config")
    public Result<Map<String, String>> getUserAiConfig() {
        Long userId = UserContext.getUserId();
        User user = userService.getById(userId);
        if (!StringUtils.hasText(user.getAiConfig())) {
            return Result.success(null);
        }

        Map<String, String> config = JSON.parseObject(user.getAiConfig(), Map.class);
        String apiKey = config.get("apiKey");
        if (StringUtils.hasText(apiKey)) {
            config.put("apiKey", maskApiKey(apiKey));
            config.put("apiKeyMasked", API_KEY_MASK);
        }
        return Result.success(config);
    }

    @PostMapping("/config")
    public Result<Void> saveUserAiConfig(@RequestBody Map<String, String> config) {
        if (!StringUtils.hasText(config.get("baseUrl")) || !StringUtils.hasText(config.get("model"))) {
            return Result.error("配置参数不完整");
        }
        if (!isValidUrl(config.get("baseUrl"))) {
            return Result.error("Base URL 格式不正确");
        }
        if (config.get("model").trim().length() < 2) {
            return Result.error("模型名称格式不正确");
        }

        Long userId = UserContext.getUserId();
        User user = userService.getById(userId);
        Map<String, String> mergedConfig = mergeConfig(user.getAiConfig(), config);

        if (!StringUtils.hasText(mergedConfig.get("apiKey"))) {
            return Result.error("请提供有效的 API Key");
        }

        user.setAiConfig(JSON.toJSONString(mergedConfig));
        userService.updateById(user);
        return Result.success();
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        Long userId = UserContext.getUserId();
        QueryWrapper<AiApiLog> query = new QueryWrapper<>();
        query.select(
                "COUNT(*) as totalCalls",
                "SUM(CASE WHEN is_success = 1 THEN 1 ELSE 0 END) as successCalls",
                "IFNULL(SUM(total_tokens), 0) as totalTokens",
                "IFNULL(SUM(prompt_tokens), 0) as promptTokens",
                "IFNULL(SUM(completion_tokens), 0) as completionTokens",
                "IFNULL(AVG(latency_ms), 0) as avgLatency"
        ).eq("user_id", userId);

        List<Map<String, Object>> list = aiApiLogMapper.selectMaps(query);
        Map<String, Object> result = list != null && !list.isEmpty() ? list.get(0) : new HashMap<>();

        long totalCalls = result.get("totalCalls") != null ? ((Number) result.get("totalCalls")).longValue() : 0;
        long successCalls = result.get("successCalls") != null ? ((Number) result.get("successCalls")).longValue() : 0;
        long failedCalls = totalCalls - successCalls;
        double successRate = totalCalls > 0 ? (successCalls * 100.0 / totalCalls) : 0;

        Map<String, Object> stats = new HashMap<>(result);
        stats.put("failedCalls", failedCalls);
        stats.put("successRate", Math.round(successRate * 100.0) / 100.0);
        return Result.success(stats);
    }

    @GetMapping("/token-trend")
    public Result<List<Map<String, Object>>> getTokenTrend(@RequestParam(defaultValue = "7") int days) {
        Long userId = UserContext.getUserId();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        Map<String, Map<String, Object>> dailyData = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            String dateStr = startDate.plusDays(i).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Map<String, Object> data = new HashMap<>();
            data.put("date", dateStr);
            data.put("totalTokens", 0);
            data.put("promptTokens", 0);
            data.put("completionTokens", 0);
            data.put("callCount", 0);
            dailyData.put(dateStr, data);
        }

        QueryWrapper<AiApiLog> query = new QueryWrapper<>();
        query.select(
                        "DATE_FORMAT(create_time, '%Y-%m-%d') as date",
                        "IFNULL(SUM(total_tokens), 0) as totalTokens",
                        "IFNULL(SUM(prompt_tokens), 0) as promptTokens",
                        "IFNULL(SUM(completion_tokens), 0) as completionTokens",
                        "COUNT(*) as callCount"
                )
                .eq("user_id", userId)
                .ge("create_time", startDate.atStartOfDay())
                .le("create_time", endDate.plusDays(1).atStartOfDay())
                .groupBy("DATE_FORMAT(create_time, '%Y-%m-%d')")
                .orderByAsc("date");

        List<Map<String, Object>> dbList = aiApiLogMapper.selectMaps(query);
        if (dbList != null) {
            for (Map<String, Object> record : dbList) {
                String dateKey = (String) record.get("date");
                if (dailyData.containsKey(dateKey)) {
                    Map<String, Object> target = dailyData.get(dateKey);
                    target.put("totalTokens", toInt(record.get("totalTokens")));
                    target.put("promptTokens", toInt(record.get("promptTokens")));
                    target.put("completionTokens", toInt(record.get("completionTokens")));
                    target.put("callCount", toInt(record.get("callCount")));
                }
            }
        }

        return Result.success(new ArrayList<>(dailyData.values()));
    }

    @GetMapping("/success-rate-trend")
    public Result<List<Map<String, Object>>> getSuccessRateTrend(@RequestParam(defaultValue = "7") int days) {
        Long userId = UserContext.getUserId();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        Map<String, Map<String, Object>> dailyData = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            String dateStr = startDate.plusDays(i).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Map<String, Object> data = new HashMap<>();
            data.put("date", dateStr);
            data.put("totalCalls", 0);
            data.put("successCalls", 0);
            data.put("failedCalls", 0);
            data.put("successRate", 0.0);
            dailyData.put(dateStr, data);
        }

        QueryWrapper<AiApiLog> query = new QueryWrapper<>();
        query.select(
                        "DATE_FORMAT(create_time, '%Y-%m-%d') as date",
                        "COUNT(*) as totalCalls",
                        "SUM(CASE WHEN is_success = 1 THEN 1 ELSE 0 END) as successCalls"
                )
                .eq("user_id", userId)
                .ge("create_time", startDate.atStartOfDay())
                .le("create_time", endDate.plusDays(1).atStartOfDay())
                .groupBy("DATE_FORMAT(create_time, '%Y-%m-%d')");

        List<Map<String, Object>> dbList = aiApiLogMapper.selectMaps(query);
        if (dbList != null) {
            for (Map<String, Object> record : dbList) {
                String dateKey = (String) record.get("date");
                if (dailyData.containsKey(dateKey)) {
                    Map<String, Object> target = dailyData.get(dateKey);
                    long total = toLong(record.get("totalCalls"));
                    long success = toLong(record.get("successCalls"));
                    long fail = total - success;
                    double rate = total > 0 ? (success * 100.0 / total) : 0.0;

                    target.put("totalCalls", total);
                    target.put("successCalls", success);
                    target.put("failedCalls", fail);
                    target.put("successRate", Math.round(rate * 100.0) / 100.0);
                }
            }
        }

        return Result.success(new ArrayList<>(dailyData.values()));
    }

    @PostMapping("/chat")
    public Result<String> chat(@RequestBody Map<String, String> params) {
        String message = params.get("message");
        if (!StringUtils.hasText(message)) {
            return Result.error("请输入内容");
        }
        return Result.success(aiService.chatWithHistory(message));
    }

    @PostMapping("/clear-history")
    public Result<String> clearHistory() {
        aiService.clearHistory();
        return Result.success("记忆已清除");
    }

    private Map<String, String> mergeConfig(String storedConfigJson, Map<String, String> incomingConfig) {
        Map<String, String> merged = new HashMap<>();
        if (StringUtils.hasText(storedConfigJson)) {
            merged.putAll(JSON.parseObject(storedConfigJson, Map.class));
        }
        merged.putAll(incomingConfig);

        String incomingApiKey = incomingConfig.get("apiKey");
        if (!StringUtils.hasText(incomingApiKey) || isMaskedApiKey(incomingApiKey)) {
            if (StringUtils.hasText(storedConfigJson)) {
                Map<String, String> storedConfig = JSON.parseObject(storedConfigJson, Map.class);
                merged.put("apiKey", storedConfig.get("apiKey"));
            } else {
                merged.remove("apiKey");
            }
        } else {
            merged.put("apiKey", incomingApiKey.trim());
        }
        return merged;
    }

    private boolean isMaskedApiKey(String apiKey) {
        return API_KEY_MASK.equals(apiKey) || apiKey.contains("*");
    }

    private boolean isValidUrl(String url) {
        try {
            URI uri = URI.create(url.trim());
            return StringUtils.hasText(uri.getScheme()) && StringUtils.hasText(uri.getHost());
        } catch (Exception e) {
            return false;
        }
    }

    private String maskApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey) || apiKey.length() <= 8) {
            return API_KEY_MASK;
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private int toInt(Object obj) {
        return obj == null ? 0 : ((Number) obj).intValue();
    }

    private long toLong(Object obj) {
        return obj == null ? 0 : ((Number) obj).longValue();
    }
}
