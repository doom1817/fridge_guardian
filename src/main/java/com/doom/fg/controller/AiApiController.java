package com.doom.fg.controller;

import com.doom.fg.common.Result;
import com.doom.fg.context.UserContext;
import com.doom.fg.entity.AiApiLog;
import com.doom.fg.mapper.AiApiLogMapper;
import com.doom.fg.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
public class AiApiController {
    @Autowired
    private AiService aiService;

    @Autowired
    private AiApiLogMapper aiApiLogMapper;

    @PostMapping("/generate-recipe")
    public Result<Map<String, String>> generateRecipe(@RequestBody List<Long> foodIds) {
        Map<String, String> recipe = aiService.getAiRecipe(foodIds);
        return Result.success(recipe);
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        Long userId = UserContext.getUserId();

        Map<String, Object> dbResult = aiApiLogMapper.getStatisticsByUserId(userId);

        long totalCalls = dbResult != null && dbResult.get("totalCalls") != null ? ((Number) dbResult.get("totalCalls")).longValue() : 0;
        long successCalls = dbResult != null && dbResult.get("successCalls") != null ? ((Number) dbResult.get("successCalls")).longValue() : 0;
        long failedCalls = dbResult != null && dbResult.get("failedCalls") != null ? ((Number) dbResult.get("failedCalls")).longValue() : 0;
        int totalTokens = dbResult != null && dbResult.get("totalTokens") != null ? ((Number) dbResult.get("totalTokens")).intValue() : 0;
        int promptTokens = dbResult != null && dbResult.get("promptTokens") != null ? ((Number) dbResult.get("promptTokens")).intValue() : 0;
        int completionTokens = dbResult != null && dbResult.get("completionTokens") != null ? ((Number) dbResult.get("completionTokens")).intValue() : 0;
        long avgLatency = dbResult != null && dbResult.get("avgLatency") != null ? ((Number) dbResult.get("avgLatency")).longValue() : 0;

        double successRate = totalCalls > 0 ? (successCalls * 100.0 / totalCalls) : 0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCalls", totalCalls);
        stats.put("successCalls", successCalls);
        stats.put("failedCalls", failedCalls);
        stats.put("successRate", Math.round(successRate * 100.0) / 100.0);
        stats.put("totalTokens", totalTokens);
        stats.put("promptTokens", promptTokens);
        stats.put("completionTokens", completionTokens);
        stats.put("avgLatency", avgLatency);

        return Result.success(stats);
    }

    @GetMapping("/token-trend")
    public Result<List<Map<String, Object>>> getTokenTrend(@RequestParam(defaultValue = "7") int days) {
        Long userId = UserContext.getUserId();

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        List<Map<String, Object>> dbResults = aiApiLogMapper.getTokenTrendByUserId(
                userId, 
                startDate.atStartOfDay(), 
                endDate.plusDays(1).atStartOfDay()
        );

        Map<String, Map<String, Object>> dailyData = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            Map<String, Object> data = new HashMap<>();
            data.put("date", date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            data.put("totalTokens", 0);
            data.put("promptTokens", 0);
            data.put("completionTokens", 0);
            data.put("callCount", 0);
            dailyData.put(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), data);
        }

        for (Map<String, Object> dbResult : dbResults) {
            String dateKey = ((java.sql.Date) dbResult.get("date")).toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Map<String, Object> data = dailyData.get(dateKey);
            if (data != null) {
                data.put("totalTokens", dbResult.get("totalTokens") != null ? ((Number) dbResult.get("totalTokens")).intValue() : 0);
                data.put("promptTokens", dbResult.get("promptTokens") != null ? ((Number) dbResult.get("promptTokens")).intValue() : 0);
                data.put("completionTokens", dbResult.get("completionTokens") != null ? ((Number) dbResult.get("completionTokens")).intValue() : 0);
                data.put("callCount", dbResult.get("callCount") != null ? ((Number) dbResult.get("callCount")).intValue() : 0);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(dailyData.values());
        return Result.success(result);
    }

    @GetMapping("/success-rate-trend")
    public Result<List<Map<String, Object>>> getSuccessRateTrend(@RequestParam(defaultValue = "7") int days) {
        Long userId = UserContext.getUserId();

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        List<Map<String, Object>> dbResults = aiApiLogMapper.getSuccessRateTrendByUserId(
                userId, 
                startDate.atStartOfDay(), 
                endDate.plusDays(1).atStartOfDay()
        );

        Map<String, Map<String, Object>> dailyData = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            Map<String, Object> data = new HashMap<>();
            data.put("date", date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            data.put("totalCalls", 0);
            data.put("successCalls", 0);
            data.put("failedCalls", 0);
            data.put("successRate", 0.0);
            dailyData.put(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), data);
        }

        for (Map<String, Object> dbResult : dbResults) {
            String dateKey = ((java.sql.Date) dbResult.get("date")).toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Map<String, Object> data = dailyData.get(dateKey);
            if (data != null) {
                int totalCalls = dbResult.get("totalCalls") != null ? ((Number) dbResult.get("totalCalls")).intValue() : 0;
                int successCalls = dbResult.get("successCalls") != null ? ((Number) dbResult.get("successCalls")).intValue() : 0;
                int failedCalls = dbResult.get("failedCalls") != null ? ((Number) dbResult.get("failedCalls")).intValue() : 0;
                data.put("totalCalls", totalCalls);
                data.put("successCalls", successCalls);
                data.put("failedCalls", failedCalls);
                data.put("successRate", totalCalls > 0 ? Math.round(successCalls * 10000.0 / totalCalls) / 100.0 : 0.0);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(dailyData.values());
        return Result.success(result);
    }
}
