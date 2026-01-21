package com.doom.fg.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/01/21/15:21
 * @Description:
 *  这个类主要用于在前端下拉框展示，以及自动计算保质期。
 */
@Data
@TableName("category")
public class Category {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;               // 蔬菜, 肉类, 水果...
    private Integer defaultExpiryDays; // 默认保质期天数
    private String icon;               // 图标样式名（如 leaf, apple）
}
