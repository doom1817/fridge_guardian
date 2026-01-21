package com.doom.fg.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.doom.fg.entity.RecipeRecord;

import java.util.List;

public interface RecipeRecordService extends IService<RecipeRecord> {
    List<RecipeRecord> getUserRecipes(Long userId);
}
