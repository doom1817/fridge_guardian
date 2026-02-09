package com.doom.fg.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

        LambdaQueryWrapper<AiApiLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiApiLog::getUserId, userId);

        List<AiApiLog> logs = aiApiLogMapper.selectList(wrapper);

        long totalCalls = logs.size();
        long successCalls = logs.stream().filter(log -> log.getIsSuccess() == 1).count();
        long failedCalls = totalCalls - successCalls;
        double successRate = totalCalls > 0 ? (successCalls * 100.0 / totalCalls) : 0;

        int totalTokens = logs.stream().mapToInt(AiApiLog::getTotalTokens).sum();
        int promptTokens = logs.stream().mapToInt(AiApiLog::getPromptTokens).sum();
        int completionTokens = logs.stream().mapToInt(AiApiLog::getCompletionTokens).sum();

        long avgLatency = logs.stream().filter(log -> log.getLatencyMs() != null)
                .mapToLong(AiApiLog::getLatencyMs).sum();
        avgLatency = totalCalls > 0 ? avgLatency / totalCalls : 0;

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

        LambdaQueryWrapper<AiApiLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiApiLog::getUserId, userId)
                .ge(AiApiLog::getCreateTime, startDate.atStartOfDay())
                .le(AiApiLog::getCreateTime, endDate.plusDays(1).atStartOfDay())
                .orderByAsc(AiApiLog::getCreateTime);

        List<AiApiLog> logs = aiApiLogMapper.selectList(wrapper);

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

        for (AiApiLog log : logs) {
            String dateKey = log.getCreateTime().toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Map<String, Object> data = dailyData.get(dateKey);
            if (data != null) {
                data.put("totalTokens", (Integer) data.get("totalTokens") + log.getTotalTokens());
                data.put("promptTokens", (Integer) data.get("promptTokens") + log.getPromptTokens());
                data.put("completionTokens", (Integer) data.get("completionTokens") + log.getCompletionTokens());
                data.put("callCount", (Integer) data.get("callCount") + 1);
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

        LambdaQueryWrapper<AiApiLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiApiLog::getUserId, userId)
                .ge(AiApiLog::getCreateTime, startDate.atStartOfDay())
                .le(AiApiLog::getCreateTime, endDate.plusDays(1).atStartOfDay())
                .orderByAsc(AiApiLog::getCreateTime);

        List<AiApiLog> logs = aiApiLogMapper.selectList(wrapper);

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

        for (AiApiLog log : logs) {
            String dateKey = log.getCreateTime().toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Map<String, Object> data = dailyData.get(dateKey);
            if (data != null) {
                data.put("totalCalls", (Integer) data.get("totalCalls") + 1);
                if (log.getIsSuccess() == 1) {
                    data.put("successCalls", (Integer) data.get("successCalls") + 1);
                } else {
                    data.put("failedCalls", (Integer) data.get("failedCalls") + 1);
                }
                int total = (Integer) data.get("totalCalls");
                int success = (Integer) data.get("successCalls");
                data.put("successRate", total > 0 ? Math.round(success * 10000.0 / total) / 100.0 : 0.0);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(dailyData.values());
        return Result.success(result);
    }
}
