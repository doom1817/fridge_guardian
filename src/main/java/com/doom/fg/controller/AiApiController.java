package com.doom.fg.controller;

import com.doom.fg.common.Result;
import com.doom.fg.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiApiController {
    @Autowired
    private AiService aiService;

    @PostMapping("/generate-recipe")
    public Result<Map<String, String>> generateRecipe(@RequestBody List<Long> foodIds) {
        Map<String, String> recipe = aiService.getAiRecipe(foodIds);
        return Result.success(recipe);
    }
}
