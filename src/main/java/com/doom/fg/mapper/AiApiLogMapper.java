package com.doom.fg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.doom.fg.entity.AiApiLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/02/09/16:25
 * @Description:
 */
@Mapper
public interface AiApiLogMapper extends BaseMapper<AiApiLog> {

    @Select("SELECT " +
            "COUNT(*) as totalCalls, " +
            "SUM(CASE WHEN is_success = 1 THEN 1 ELSE 0 END) as successCalls, " +
            "SUM(CASE WHEN is_success = 0 THEN 1 ELSE 0 END) as failedCalls, " +
            "COALESCE(SUM(total_tokens), 0) as totalTokens, " +
            "COALESCE(SUM(prompt_tokens), 0) as promptTokens, " +
            "COALESCE(SUM(completion_tokens), 0) as completionTokens, " +
            "COALESCE(AVG(latency_ms), 0) as avgLatency " +
            "FROM ai_api_log " +
            "WHERE user_id = #{userId}")
    Map<String, Object> getStatisticsByUserId(@Param("userId") Long userId);

    @Select("SELECT " +
            "DATE(create_time) as date, " +
            "COALESCE(SUM(total_tokens), 0) as totalTokens, " +
            "COALESCE(SUM(prompt_tokens), 0) as promptTokens, " +
            "COALESCE(SUM(completion_tokens), 0) as completionTokens, " +
            "COUNT(*) as callCount " +
            "FROM ai_api_log " +
            "WHERE user_id = #{userId} " +
            "AND create_time >= #{startDate} " +
            "AND create_time < #{endDate} " +
            "GROUP BY DATE(create_time) " +
            "ORDER BY DATE(create_time)")
    List<Map<String, Object>> getTokenTrendByUserId(@Param("userId") Long userId, 
                                                     @Param("startDate") LocalDateTime startDate,
                                                     @Param("endDate") LocalDateTime endDate);

    @Select("SELECT " +
            "DATE(create_time) as date, " +
            "COUNT(*) as totalCalls, " +
            "SUM(CASE WHEN is_success = 1 THEN 1 ELSE 0 END) as successCalls, " +
            "SUM(CASE WHEN is_success = 0 THEN 1 ELSE 0 END) as failedCalls " +
            "FROM ai_api_log " +
            "WHERE user_id = #{userId} " +
            "AND create_time >= #{startDate} " +
            "AND create_time < #{endDate} " +
            "GROUP BY DATE(create_time) " +
            "ORDER BY DATE(create_time)")
    List<Map<String, Object>> getSuccessRateTrendByUserId(@Param("userId") Long userId,
                                                          @Param("startDate") LocalDateTime startDate,
                                                          @Param("endDate") LocalDateTime endDate);
}
