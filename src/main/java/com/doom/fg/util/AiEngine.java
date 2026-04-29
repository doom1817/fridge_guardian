package com.doom.fg.util;

import com.doom.fg.service.impl.AiServiceImpl;

import java.util.List;

public interface AiEngine {
    AiResponse chat(String apiKey, String baseUrl, String model, String systemPrompt,
                    List<AiServiceImpl.AiMessage> history, String userPrompt);
}
