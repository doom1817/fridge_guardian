package com.doom.fg.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.doom.fg.entity.FoodItem;
import java.util.List;

public interface FoodItemService extends IService<FoodItem> {
    boolean saveFoodWithExpiry(FoodItem food, Integer defaultDays);

    void calculateDaysLeft(FoodItem food);

    List<FoodItem> getExpiringSoon(int days);
}
