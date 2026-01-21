package com.doom.fg.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.doom.fg.entity.RecipeRecord;
import com.doom.fg.mapper.RecipeRecordMapper;
import com.doom.fg.service.RecipeRecordService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecipeRecordServiceImpl extends ServiceImpl<RecipeRecordMapper, RecipeRecord> implements RecipeRecordService {

    @Override
    public List<RecipeRecord> getUserRecipes(Long userId) {
        return this.lambdaQuery()
                .eq(RecipeRecord::getUserId, userId)
                .orderByDesc(RecipeRecord::getCreateTime)
                .list();
    }
}
