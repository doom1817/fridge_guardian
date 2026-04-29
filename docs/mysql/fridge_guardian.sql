-- ==================================================
-- 冰箱守卫者 (Fridge Guardian) 完整数据库脚本
-- 版本: V2.1 (2026-02-16 同步版)
-- ==================================================

-- --------------------------------------------------
-- 1. 初始化数据库
-- --------------------------------------------------
DROP DATABASE IF EXISTS fridge_guardian;
CREATE DATABASE fridge_guardian CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE fridge_guardian;

-- --------------------------------------------------
-- 2. 用户表 (User) - 已包含 AI 插件化配置字段
-- --------------------------------------------------
CREATE TABLE `user` (
                        `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                        `username` VARCHAR(50) NOT NULL COMMENT '用户名',
                        `password` VARCHAR(255) NOT NULL COMMENT '密码',
                        `email` VARCHAR(100) COMMENT '邮箱',
                        `ai_config` TEXT NULL COMMENT 'AI引擎配置(JSON格式: engineType, apiKey, model)',
                        `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                        `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='用户表';

-- --------------------------------------------------
-- 3. 食材分类表 (Category)
-- --------------------------------------------------
CREATE TABLE `category` (
                            `id` INT PRIMARY KEY AUTO_INCREMENT,
                            `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
                            `default_expiry_days` INT DEFAULT 3 COMMENT '该类默认保质天数',
                            `icon` VARCHAR(50) COMMENT '前端展示图标代码'
) ENGINE=InnoDB COMMENT='食材分类表';

-- --------------------------------------------------
-- 4. 食材库存表 (Food Item)
-- --------------------------------------------------
CREATE TABLE `food_item` (
                             `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                             `user_id` BIGINT NOT NULL COMMENT '所属用户',
                             `category_id` INT COMMENT '分类ID',
                             `name` VARCHAR(100) NOT NULL COMMENT '食材名称',
                             `quantity` DECIMAL(10,2) DEFAULT 1.0 COMMENT '数量',
                             `unit` VARCHAR(20) DEFAULT '个' COMMENT '单位',
                             `purchase_date` DATE NOT NULL COMMENT '购买日期',
                             `expiry_date` DATE NOT NULL COMMENT '过期日期',
                             `storage_location` VARCHAR(20) DEFAULT 'FRIDGE' COMMENT '存储位置: FRIDGE-冷藏, FREEZER-冷冻, PANTRY-常温',
                             `status` INT DEFAULT 0 COMMENT '状态: 0-在库, 1-已吃完, 2-已过期浪费',
                             `is_deleted` INT DEFAULT 0 COMMENT 'MyBatisPlus逻辑删除: 0-正常, 1-删除',
                             `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                             `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             INDEX `idx_user_status` (`user_id`, `status`),
                             INDEX `idx_expiry` (`expiry_date`)
) ENGINE=InnoDB COMMENT='食材库存表';

-- --------------------------------------------------
-- 5. AI 菜谱记录表 (Recipe Record)
-- --------------------------------------------------
CREATE TABLE `recipe_record` (
                                 `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                                 `user_id` BIGINT NOT NULL,
                                 `food_names` VARCHAR(255) NOT NULL COMMENT '使用的食材名称拼接',
                                 `title` VARCHAR(200) COMMENT '菜名',
                                 `content` TEXT COMMENT 'AI生成的Markdown格式步骤',
                                 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='AI菜谱历史';

-- --------------------------------------------------
-- 6. AI 接口调用日志表
-- --------------------------------------------------
CREATE TABLE `ai_api_log` (
                              `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                              `user_id` BIGINT NOT NULL COMMENT '调用用户ID',
                              `model` VARCHAR(50) DEFAULT 'deepseek-chat' COMMENT '调用的模型',
                              `request_type` VARCHAR(50) DEFAULT 'RECIPE' COMMENT '请求类型: 菜谱生成/DIFY对话',
                              `prompt_tokens` INT DEFAULT 0 COMMENT '提问消耗Token',
                              `completion_tokens` INT DEFAULT 0 COMMENT '回答消耗Token',
                              `total_tokens` INT DEFAULT 0 COMMENT '总消耗Token',
                              `latency_ms` BIGINT COMMENT '接口耗时(毫秒)',
                              `status_code` INT COMMENT 'HTTP状态码',
                              `is_success` TINYINT(1) DEFAULT 1 COMMENT '业务是否成功 1-是 0-否',
                              `error_msg` TEXT COMMENT '错误详情',
                              `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='AI接口调用日志';


-- ==================================================
-- 数据初始化 (基准日期: 2026-4-29)
-- ==================================================

-- 1. 插入测试用户 (密码: 123456) BCryptPasswordEncoder
INSERT INTO `user` (username, password, email, ai_config) VALUES
    ('admin', '$2a$10$yoXh/gIVkY7tHbnfGJXLAuY8HHCGc1X1JdA.JMzrhMcJeryELNCiq', 'admin@example.com', '{"engineType":"DIFY","apiKey":"sk-1f7adfd6ef99498599bf9edd01d0118c","baseUrl":"http://localhost:8100/v1"}');

-- 2. 插入食材分类
INSERT INTO `category` (name, default_expiry_days, icon) VALUES
                                                             ('新鲜蔬菜', 3, 'leaf'),
                                                             ('肉类禽蛋', 5, 'drumstick'),
                                                             ('水产海鲜', 2, 'fish'),
                                                             ('时令水果', 7, 'apple'),
                                                             ('乳品烘焙', 10, 'cheese'),
                                                             ('零食干货', 60, 'cookie');

-- 3. 插入食材库存 (模拟 2026-04-29 的真实分布)
INSERT INTO `food_item` (user_id, category_id, name, quantity, unit, purchase_date, expiry_date, storage_location, status) VALUES

-- [场景A：非常紧急] 今天(04-29)就过期 -> 列表应显示为极致高亮
(1, 5, '鲜牛奶', 1, '盒', '2026-02-10', '2026-04-29', 'FRIDGE', 0),

-- [场景B：临期预警] 明天(04-30)过期 (剩余1天)
(1, 1, '生菜', 2, '把', '2026-02-14', '2026-04-30', 'FRIDGE', 0),

-- [场景C：临期预警] 后天(04-29)过期 (剩余2天)
(1, 3, '鲜活鲈鱼', 1, '条', '2026-02-15', '2026-05-01', 'FRIDGE', 0),

-- [场景D：状态良好] 还有很久过期
(1, 2, '黑猪里脊', 500, '克', '2026-02-15', '2026-05-5', 'FREEZER', 0),
(1, 4, '砂糖橘', 1, '斤', '2026-02-15', '2026-06-05', 'FRIDGE', 0),
(1, 6, '混合坚果', 1, '罐', '2026-01-20', '2026-07-20', 'PANTRY', 0),

-- [场景E：已吃完数据] (用于饼图：健康食用)
(1, 1, '西红柿', 3, '个', '2026-02-01', '2026-02-05', 'FRIDGE', 1),
(1, 2, '鸡腿肉', 4, '只', '2026-02-05', '2026-02-10', 'FREEZER', 1),

-- [场景F：已浪费数据] (用于饼图：遗憾浪费)
(1, 5, '吐司面包', 1, '袋', '2026-02-01', '2026-02-06', 'PANTRY', 2);
-- ==================================================
-- 增量更新草案：AI 日志增强字段
-- 说明：
-- 1. 下面语句用于“已有数据库”的手动升级
-- 2. 请检查后按需执行，不要与上面的 DROP/CREATE 全量初始化混用
# ==================================================
ALTER TABLE `ai_api_log`
  ADD COLUMN `prompt_version` VARCHAR(20) DEFAULT 'v1' COMMENT 'Prompt版本' AFTER `request_type`,
  ADD COLUMN `scenario_type` VARCHAR(50) DEFAULT 'RECIPE_GENERATE' COMMENT '场景类型' AFTER `prompt_version`,
  ADD COLUMN `food_count` INT DEFAULT 0 COMMENT '本次使用食材数量' AFTER `total_tokens`,
  ADD COLUMN `error_type` VARCHAR(50) NULL COMMENT '错误类型' AFTER `is_success`;
-- --------------------------------------------------
-- AI 日志测试示例（仅作参考，执行前请先确认字段已更新）
-- 不修改现有测试用户数据
-- --------------------------------------------------
INSERT INTO `ai_api_log`
(`user_id`, `model`, `request_type`, `prompt_version`, `scenario_type`,
 `prompt_tokens`, `completion_tokens`, `total_tokens`, `food_count`,
 `latency_ms`, `status_code`, `is_success`, `error_type`, `error_msg`)
VALUES
(1, 'deepseek-chat', 'RECIPE', 'v1', 'RECIPE_GENERATE', 860, 1240, 2100, 3, 1880, 200, 1, NULL, NULL),
(1, 'deepseek-chat', 'CHAT', 'v1', 'RECIPE_CHAT', 420, 260, 680, 0, 920, 200, 1, NULL, NULL),
(1, 'deepseek-chat', 'RECIPE', 'v1', 'RECIPE_GENERATE', 520, 0, 520, 2, 3100, 401, 0, 'AI_API_UNAUTHORIZED', 'HTTP_401');
-- ==================================================
-- 增量更新草案：菜谱反馈字段
-- 说明：
-- 1. 下面语句用于“已有数据库”的手动升级
-- 2. 请检查后按需执行
-- ==================================================
ALTER TABLE `recipe_record`
  ADD COLUMN `feedback_status` VARCHAR(30) NULL COMMENT '反馈结果' AFTER `content`,
  ADD COLUMN `feedback_reason` VARCHAR(255) NULL COMMENT '反馈原因' AFTER `feedback_status`,
  ADD COLUMN `feedback_time` DATETIME NULL COMMENT '反馈时间' AFTER `feedback_reason`;

-- --------------------------------------------------
-- 菜谱反馈测试示例（仅作参考，执行前请先确认字段已更新）
-- --------------------------------------------------
UPDATE `recipe_record`
SET `feedback_status` = 'HELPFUL',
    `feedback_reason` = '步骤清晰，适合当前库存',
    `feedback_time` = NOW()
WHERE `id` = 1;
