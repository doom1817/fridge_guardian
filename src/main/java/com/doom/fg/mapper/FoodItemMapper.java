package com.doom.fg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.doom.fg.entity.FoodItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FoodItemMapper extends BaseMapper<FoodItem> {
}
