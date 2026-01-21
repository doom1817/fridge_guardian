package com.doom.fg.service;

import java.util.List;
import java.util.Map;

public interface AiService {
    Map<String, String> getAiRecipe(List<Long> foodIds);
}
