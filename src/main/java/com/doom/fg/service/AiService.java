package com.doom.fg.service;

import java.util.List;
import java.util.Map;

public interface AiService {
    Map<String, Object> getAiRecipe(List<Long> foodIds);

    String chatWithHistory(String message);

    void submitRecipeFeedback(Long recipeRecordId, String feedbackStatus, String feedbackReason);

    void clearHistory();
}
