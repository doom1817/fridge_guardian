package com.doom.fg.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/02/09/16:24
 * @Description:
 */
@Data
@TableName("ai_api_log")
public class AiApiLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String model;
    private String requestType;

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    private Long latencyMs;
    private Integer statusCode;
    private Integer isSuccess; // 1成功 0失败
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
