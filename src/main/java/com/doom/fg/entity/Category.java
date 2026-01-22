package com.doom.fg.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

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
public class Category implements Serializable {
    // 序列化
    // 这个字段用于序列化，确保不同JVM间对象序列化的兼容性
    // 如果没有这个字段，Java会根据类的结构自动生成一个，
    // 但在类结构变化时可能导致反序列化失败，加上它可以明确指定版本号
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;               // 蔬菜, 肉类, 水果...
    private Integer defaultExpiryDays; // 默认保质期天数
    private String icon;               // 图标样式名（如 leaf, apple）
}
