# AI 低成本优化计划与任务清单

## 1. 文档目标

本文档用于规划 `fridge_guardian` 项目的 AI 低成本优化方案。

本轮目标：

- 不引入新的重型 Agent 框架
- 尽量复用现有 `Spring Boot + Redis + MySQL + OpenAI-compatible API` 架构
- 在一天内完成一轮可见、可用、可维护的 AI 能力增强

## 2. 当前约束

当前项目是个人独立项目，本轮优化以“一天内落地”为前提，因此默认接受以下约束：

- `application.yaml` 中的默认 `api-key` 暂时保留并继续使用
- 该 Key 预计在一天后过期，仅用于当前开发联调
- 本轮不把“移除默认 Key”作为阻塞项
- 前端配置回显仍需要做脱敏

数据库处理约定：

- 数据库变更只先落到 [fridge_guardian.sql](D:/LongTime/fridge_guardian/docs/mysql/fridge_guardian.sql)
- 由用户检查并自行执行 SQL
- 不由代码直接更新线上或本地数据库

## 3. 优化原则

- 优先做收益高、改动小的事项
- 优先补齐规范和协议，再增强功能
- 保持现有页面交互习惯，不做大范围重构
- 所有优化围绕“减少浪费、提升食材利用率”展开

## 4. 分阶段计划

### Phase 1：输出规范化

目标：

- 统一提示词模板
- 统一 AI 输出协议
- 降低模型自由发挥导致的不稳定结果

交付项：

- 菜谱生成 Prompt 模板
- 多轮追问 Prompt 模板
- 结构化输出协议（JSON）
- Prompt 版本号机制
- 结构化响应对象

### Phase 2：体验增强

目标：

- 让用户更容易持续使用 AI 功能
- 降低自由输入门槛

交付项：

- 快捷追问按钮
- 同食材再生成
- 最小用户偏好注入
- 结构化摘要卡
- 错误分类与友好提示

### Phase 3：观测闭环

目标：

- 让 AI 功能可分析、可比较、可迭代

交付项：

- 扩展 AI 日志字段
- 用户反馈闭环
- 统计字段补充

### Phase 4：安全补强

目标：

- 降低配置泄露和误配置风险

交付项：

- 配置回显脱敏
- API Key 保留策略说明
- `baseUrl / model` 基础校验

## 5. 任务清单

### A. Prompt 与输出协议

- [x] 新增统一 Prompt 模板类
- [x] 将“菜谱生成”和“追问对话”拆成两套 Prompt
- [x] 增加 `promptVersion`
- [x] 约束菜谱输出为固定 JSON 结构
- [x] 新增结构化响应对象 `AiRecipeResponse`
- [x] 保留 Markdown 展示能力，兼容现有页面

建议输出字段：

- `title`
- `summary`
- `useExpiringFoodFirst`
- `ingredients`
- `steps`
- `tips`
- `nutrition`
- `estimatedTimeMinutes`
- `difficulty`
- `markdown`

涉及文件：

- [AiServiceImpl.java](D:/LongTime/fridge_guardian/src/main/java/com/doom/fg/service/impl/AiServiceImpl.java)
- [OpenAiCompatibleEngine.java](D:/LongTime/fridge_guardian/src/main/java/com/doom/fg/util/OpenAiCompatibleEngine.java)
- [AiPrompts.java](D:/LongTime/fridge_guardian/src/main/java/com/doom/fg/util/AiPrompts.java)
- [AiRecipeResponse.java](D:/LongTime/fridge_guardian/src/main/java/com/doom/fg/dto/AiRecipeResponse.java)

### B. 最小用户偏好增强

- [x] 复用 `user.ai_config` 存储偏好
- [x] 生成菜谱时注入口味偏好、饮食目标、忌口、烹饪时长
- [x] 查询配置时支持偏好字段回显
- [x] 保存配置时增加基础校验

建议偏好字段：

- `tastePreference`
- `dietGoal`
- `taboos`
- `cookingTimePreference`

涉及文件：

- [AiApiController.java](D:/LongTime/fridge_guardian/src/main/java/com/doom/fg/controller/AiApiController.java)
- [AiServiceImpl.java](D:/LongTime/fridge_guardian/src/main/java/com/doom/fg/service/impl/AiServiceImpl.java)
- [ai_recipe.js](D:/LongTime/fridge_guardian/src/main/resources/static/js/ai_recipe.js)
- [ai_recipe.html](D:/LongTime/fridge_guardian/src/main/resources/templates/ai_recipe.html)

### C. 错误分类与用户提示

- [x] 固定 AI 错误类型
- [x] 后端返回可识别错误码
- [x] 前端按错误类型展示友好提示
- [x] 区分配置缺失、超时、鉴权失败、格式错误、空响应

错误类型：

- `AI_CONFIG_MISSING`
- `AI_API_UNAUTHORIZED`
- `AI_API_TIMEOUT`
- `AI_BAD_RESPONSE_FORMAT`
- `AI_EMPTY_RESPONSE`
- `AI_UNKNOWN_ERROR`

涉及文件：

- [OpenAiCompatibleEngine.java](D:/LongTime/fridge_guardian/src/main/java/com/doom/fg/util/OpenAiCompatibleEngine.java)
- [AiErrorType.java](D:/LongTime/fridge_guardian/src/main/java/com/doom/fg/util/AiErrorType.java)
- [AiServiceImpl.java](D:/LongTime/fridge_guardian/src/main/java/com/doom/fg/service/impl/AiServiceImpl.java)
- [ai_recipe.js](D:/LongTime/fridge_guardian/src/main/resources/static/js/ai_recipe.js)

### D. 快捷交互与再生成

- [x] 增加快捷追问按钮
- [x] 增加“同食材再来一道”
- [x] 增加“更快做法 / 减脂友好 / 更清淡些 / 优先临期 / 步骤更简”
- [x] 保持与现有聊天区域兼容

涉及文件：

- [ai_recipe.html](D:/LongTime/fridge_guardian/src/main/resources/templates/ai_recipe.html)
- [ai_recipe.js](D:/LongTime/fridge_guardian/src/main/resources/static/js/ai_recipe.js)
- [ai_recipe.css](D:/LongTime/fridge_guardian/src/main/resources/static/css/ai_recipe.css)

### E. 日志与统计增强

- [x] 扩展 `AiApiLog` 字段
- [x] 记录 `promptVersion`
- [x] 记录 `scenarioType`
- [x] 记录 `errorType`
- [x] 记录 `foodCount`
- [x] 优先解析模型真实 `usage`
- [x] 当前保留长度估算作为兜底

新增字段：

- `prompt_version`
- `scenario_type`
- `error_type`
- `food_count`

涉及文件：

- [AiApiLog.java](D:/LongTime/fridge_guardian/src/main/java/com/doom/fg/entity/AiApiLog.java)
- [AiServiceImpl.java](D:/LongTime/fridge_guardian/src/main/java/com/doom/fg/service/impl/AiServiceImpl.java)
- [fridge_guardian.sql](D:/LongTime/fridge_guardian/docs/mysql/fridge_guardian.sql)

### F. 用户反馈闭环

- [x] 为生成结果增加轻量反馈入口
- [x] 支持“有帮助 / 不太合适”
- [x] 支持可选原因补充
- [x] 反馈与菜谱记录关联

建议原因方向：

- 太复杂
- 不贴合食材
- 不符合口味
- 不够实用

涉及文件：

- [ai_recipe.html](D:/LongTime/fridge_guardian/src/main/resources/templates/ai_recipe.html)
- [ai_recipe.js](D:/LongTime/fridge_guardian/src/main/resources/static/js/ai_recipe.js)
- [AiApiController.java](D:/LongTime/fridge_guardian/src/main/java/com/doom/fg/controller/AiApiController.java)
- [AiServiceImpl.java](D:/LongTime/fridge_guardian/src/main/java/com/doom/fg/service/impl/AiServiceImpl.java)
- [RecipeRecord.java](D:/LongTime/fridge_guardian/src/main/java/com/doom/fg/entity/RecipeRecord.java)
- [fridge_guardian.sql](D:/LongTime/fridge_guardian/docs/mysql/fridge_guardian.sql)

### G. 安全与配置治理

- [x] 保留 `application.yaml` 中的默认 `api-key` 作为本轮临时方案
- [x] 在文档中明确短期联调策略
- [x] 配置回显时对 Key 脱敏
- [x] 对 `baseUrl` 和 `model` 做基础校验
- [x] 保持默认配置与用户自定义配置的兼容逻辑

涉及文件：

- [AiApiController.java](D:/LongTime/fridge_guardian/src/main/java/com/doom/fg/controller/AiApiController.java)
- [AiServiceImpl.java](D:/LongTime/fridge_guardian/src/main/java/com/doom/fg/service/impl/AiServiceImpl.java)
- [ai_recipe.js](D:/LongTime/fridge_guardian/src/main/resources/static/js/ai_recipe.js)

## 6. 当前执行进度

### 已完成

- `Phase 1` 全部完成
- `Phase 2` 全部完成
- `Phase 3` 主体完成
- `Phase 4` 本轮目标完成

### 未完成但可留到下一轮

- 首页 AI 看板继续补更多可视化指标
- 若后续引入 Spring AI，可单独做 `Flux` 流式响应与 Tool Calling PoC

## 7. 推荐实施顺序

按性价比排序：

1. Prompt 模板化与版本化
2. 结构化输出协议
3. 错误分类与友好提示
4. 快捷追问与再生成
5. 最小用户偏好注入
6. 日志字段扩展
7. 用户反馈闭环
8. 配置脱敏与基础治理

## 8. 验收标准

- AI 输出格式稳定，后端可正确解析
- 用户不必长输入，也能完成常见菜谱调整
- 日志可区分主要失败原因
- 反馈链路可正常写入菜谱记录
- 配置回显不暴露完整明文密钥

## 9. 本轮交付范围总结

本轮已经完成的最小可用增强包括：

- Prompt 模板化
- 结构化 JSON 输出
- 错误分类
- 快捷追问按钮
- 同食材再生成
- 最小偏好注入
- AI 日志增强
- 用户反馈闭环
- 配置脱敏与基础校验

这意味着当前项目的 AI 模块已经从“能调用模型”提升到“具备基础产品化能力”。

## 10. 后续文档建议

下一轮可以继续补充：

- `docs/ai-prompt-spec.md`
- `docs/ai-output-schema.md`
- `docs/ai-feedback-design.md`
- `docs/ai-metrics-dashboard.md`
