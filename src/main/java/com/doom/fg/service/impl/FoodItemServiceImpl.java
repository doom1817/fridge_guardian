package com.doom.fg.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.doom.fg.entity.Category;
import com.doom.fg.entity.FoodItem;
import com.doom.fg.mapper.FoodItemMapper;
import com.doom.fg.service.CategoryService;
import com.doom.fg.service.FoodItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class FoodItemServiceImpl extends ServiceImpl<FoodItemMapper, FoodItem> implements FoodItemService {

    @Autowired
    private CategoryService categoryService;

    @Override
    public boolean saveFoodWithExpiry(FoodItem food, Integer defaultDays) {
        if (food.getExpiryDate() == null && food.getPurchaseDate() != null) {
            if (defaultDays != null) {
                food.setExpiryDate(food.getPurchaseDate().plusDays(defaultDays));
            } else if (food.getCategoryId() != null) {
                Category category = categoryService.getById(food.getCategoryId());
                if (category != null && category.getDefaultExpiryDays() != null) {
                    food.setExpiryDate(food.getPurchaseDate().plusDays(category.getDefaultExpiryDays()));
                }
            }
        }
        return this.save(food);
    }

    @Override
    public void calculateDaysLeft(FoodItem food) {
        if (food.getExpiryDate() != null) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), food.getExpiryDate());
            food.setDaysLeft(days);
        } else {
            food.setDaysLeft(0L);
        }
    }

    @Override
    public List<FoodItem> getExpiringSoon(int days) {
        LocalDate threshold = LocalDate.now().plusDays(days);
        List<FoodItem> list = this.lambdaQuery()
                .eq(FoodItem::getStatus, 0)
                .le(FoodItem::getExpiryDate, threshold)
                .orderByAsc(FoodItem::getExpiryDate)
                .list();
        list.forEach(this::calculateDaysLeft);
        return list;
    }
}
