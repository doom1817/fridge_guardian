package com.doom.fg.util;


import com.doom.fg.service.impl.AiServiceImpl;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/02/16/21:01
 * @Description:
 * AI 引擎适配器 (Adapter Pattern)
 */

public interface AiEngine {

    /**
     * 统一的 AI 对话接口
     * @param apiKey API 密钥
     * @param baseUrl API 基准地址
     * @param model 模型名称
     * @param systemPrompt 系统提示词（用于锁定对话主题）
     * @param history 历史消息记录
     * @param userPrompt 当前用户输入
     */
    String chat(String apiKey, String baseUrl, String model, String systemPrompt, List<AiServiceImpl.AiMessage> history, String userPrompt);
}
