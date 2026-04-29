package com.doom.fg.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiRecipeResponse {
    private String title;
    private String summary;

    @JSONField(name = "useExpiringFoodFirst")
    private Boolean useExpiringFoodFirst;

    private List<IngredientItem> ingredients = new ArrayList<>();
    private List<String> steps = new ArrayList<>();
    private List<String> tips = new ArrayList<>();
    private String nutrition;
    private Integer estimatedTimeMinutes;
    private String difficulty;
    private String markdown;

    @Data
    public static class IngredientItem {
        private String name;
        private String amount;
    }
}
