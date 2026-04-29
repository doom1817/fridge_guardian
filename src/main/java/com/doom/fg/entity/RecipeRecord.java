package com.doom.fg.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recipe_record")
public class RecipeRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String foodNames;
    private String title;
    private String content;
    private String feedbackStatus;
    private String feedbackReason;
    private LocalDateTime feedbackTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
