package com.doom.fg.entity;
import com.baomidou.mybatisplus.annotation.*;
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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
