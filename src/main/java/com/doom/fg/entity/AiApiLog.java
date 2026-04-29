package com.doom.fg.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_api_log")
public class AiApiLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String model;
    private String requestType;
    private String promptVersion;
    private String scenarioType;

    private Integer promptTokens = 0;
    private Integer completionTokens = 0;
    private Integer totalTokens = 0;
    private Integer foodCount = 0;

    private Long latencyMs;
    private Integer statusCode;
    private Integer isSuccess;
    private String errorType;
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
