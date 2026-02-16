package com.doom.fg.controller;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.doom.fg.common.Result;
import com.doom.fg.context.UserContext;
import com.doom.fg.entity.AiApiLog;
import com.doom.fg.entity.User;
import com.doom.fg.mapper.AiApiLogMapper;
import com.doom.fg.service.AiService;
import com.doom.fg.service.UserService;
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
    @Autowired
    private UserService userService;

    @PostMapping("/generate-recipe")
    public Result<Map<String, String>> generateRecipe(@RequestBody List<Long> foodIds) {
        Map<String, String> recipe = aiService.getAiRecipe(foodIds);
        return Result.success(recipe);
    }
    @PostMapping("/ai-config")
    public Result<Void> updateAiConfig(@RequestBody Map<String, String> config) {
        Long userId = UserContext.getUserId();
        User user = userService.getById(userId);
        // 将前端传来的 apiKey, baseUrl, model 序列化为 JSON
        user.setAiConfig(JSON.toJSONString(config));
        userService.updateById(user);
        return Result.success();
    }
    /**
     * 新增接口 1: 获取用户的 AI 配置信息
     * 用于前端判断用户是否已经配置过，以及回显配置
     */
    @GetMapping("/my-config")
    public Result<Map<String, String>> getUserAiConfig() {
        Long userId = UserContext.getUserId();
        User user = userService.getById(userId);

        if (user.getAiConfig() == null || user.getAiConfig().isEmpty()) {
            return Result.success(null); // 返回空，前端据此判断是否弹出配置框
        }

        // 将 JSON 字符串转为 Map 返回给前端
        Map<String, String> config = JSON.parseObject(user.getAiConfig(), Map.class);
        // 为了安全，可以对 apiKey 做脱敏处理，或者直接返回（视安全性要求而定）
        return Result.success(config);
    }
    /**
     * 新增接口 2: 保存用户的 AI 配置
     */
    @PostMapping("/config")
    public Result<Void> saveUserAiConfig(@RequestBody Map<String, String> config) {
        Long userId = UserContext.getUserId();
        User user = userService.getById(userId);

        // 简单的参数校验
        if (!config.containsKey("apiKey") || !config.containsKey("baseUrl") || !config.containsKey("model")) {
            return Result.error("配置参数不完整");
        }

        // 序列化为 JSON 字符串存储
        user.setAiConfig(JSON.toJSONString(config));
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

        // selectMaps 返回 Map 列表，我们取第一条即可
        List<Map<String, Object>> list = aiApiLogMapper.selectMaps(query);
        Map<String, Object> result = list != null && !list.isEmpty() ? list.get(0) : new HashMap<>();

        // 处理空值
        long totalCalls = result.get("totalCalls") != null ? ((Number) result.get("totalCalls")).longValue() : 0;
        long successCalls = result.get("successCalls") != null ? ((Number) result.get("successCalls")).longValue() : 0;
        long failedCalls = totalCalls - successCalls;
        double successRate = totalCalls > 0 ? (successCalls * 100.0 / totalCalls) : 0;

        // 重新封装返回
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

        // 1. 初始化完整日期结构（确保即使某天没数据，也能显示为 0）
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

        // 2. 数据库聚合查询 (只查每天的统计值)
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

        // 3. 将数据库数据填入完整日期结构中
        if (dbList != null) {
            for (Map<String, Object> record : dbList) {
                String dateKey = (String) record.get("date");
                if (dailyData.containsKey(dateKey)) {
                    // 注意：MyBatis返回的聚合结果可能是 BigDecimal 类型，需安全转换
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

        // 1. 初始化
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

        // 2. 聚合查询
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

        // 3. 填充数据
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
        if (message == null || message.trim().isEmpty()) {
            return Result.error("请输入内容");
        }
        String reply = aiService.chatWithHistory(message);
        return Result.success(reply);
    }

    @PostMapping("/clear-history")
    public Result<String> clearHistory() {
        aiService.clearHistory();
        return Result.success("记忆已清除");
    }


    // --- 辅助方法：安全转换数字类型 (放在 Controller 底部) ---
    private int toInt(Object obj) {
        return obj == null ? 0 : ((Number) obj).intValue();
    }

    private long toLong(Object obj) {
        return obj == null ? 0 : ((Number) obj).longValue();
    }
}
