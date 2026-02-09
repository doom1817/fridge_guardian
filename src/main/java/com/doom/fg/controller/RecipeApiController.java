package com.doom.fg.controller;

import com.doom.fg.common.Result;
import com.doom.fg.context.UserContext;
import com.doom.fg.entity.RecipeRecord;
import com.doom.fg.service.RecipeRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recipe")
public class RecipeApiController {
    @Autowired
    private RecipeRecordService recipeRecordService;

    @GetMapping("/history")
    public Result<List<RecipeRecord>> getHistory() {
        Long userId = UserContext.getUserId();
        List<RecipeRecord> list = recipeRecordService.getUserRecipes(userId);
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<RecipeRecord> getById(@PathVariable Long id) {
        RecipeRecord record = recipeRecordService.getById(id);
        return Result.success(record);
    }
}
