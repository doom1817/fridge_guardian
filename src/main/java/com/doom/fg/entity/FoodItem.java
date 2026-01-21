package com.doom.fg.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/01/21/15:22
 * @Description:
 */
@Data
@TableName("food_item")
public class FoodItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;     // 所属用户ID
    private Integer categoryId; // 分类ID

    private String name;     // 食材名称
    private Double quantity; // 数量
    private String unit;     // 单位 (个, 斤, 盒)

    private LocalDate purchaseDate; // 购买日期
    private LocalDate expiryDate;   // 过期日期

    private String storageLocation; // FRIDGE(冷藏), FREEZER(冷冻), PANTRY(常温)

    /**
     * 状态: 0-在库, 1-已吃完, 2-已过期浪费
     */
    private Integer status;

    @TableLogic
    private Integer isDeleted; // 逻辑删除 (0:未删, 1:已删)

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // 非数据库字段，用于在前端显示剩余天数
    @TableField(exist = false)
    private Long daysLeft;
}
