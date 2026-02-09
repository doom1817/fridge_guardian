-- ==================================================
-- 冰箱守卫者 (Fridge Guardian) 完整数据库脚本
-- 版本: V2.0 (含日志表 & 2026年测试数据)
-- ==================================================

-- --------------------------------------------------
-- 1. 初始化数据库
-- --------------------------------------------------
DROP DATABASE IF EXISTS fridge_guardian;
CREATE DATABASE fridge_guardian CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE fridge_guardian;

-- --------------------------------------------------
-- 2. 用户表 (User)
-- --------------------------------------------------
CREATE TABLE `user` (
                        `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                        `username` VARCHAR(50) NOT NULL COMMENT '用户名',
                        `password` VARCHAR(255) NOT NULL COMMENT '密码',
                        `email` VARCHAR(100) COMMENT '邮箱',
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
-- 6. AI 接口调用日志表 (新增)
-- --------------------------------------------------
CREATE TABLE `ai_api_log` (
                              `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                              `user_id` BIGINT NOT NULL COMMENT '调用用户ID',
                              `model` VARCHAR(50) DEFAULT 'deepseek-chat' COMMENT '调用的模型',
                              `request_type` VARCHAR(50) DEFAULT 'RECIPE' COMMENT '请求类型: 菜谱生成/其他',

    -- 消耗统计
                              `prompt_tokens` INT DEFAULT 0 COMMENT '提问消耗Token',
                              `completion_tokens` INT DEFAULT 0 COMMENT '回答消耗Token',
                              `total_tokens` INT DEFAULT 0 COMMENT '总消耗Token',

    -- 性能与状态
                              `latency_ms` BIGINT COMMENT '接口耗时(毫秒)',
                              `status_code` INT COMMENT 'HTTP状态码 (200为成功)',
                              `is_success` TINYINT(1) DEFAULT 1 COMMENT '业务是否成功 1-是 0-否',
                              `error_msg` TEXT COMMENT '如果失败，记录错误信息',

                              `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='AI接口调用日志';


-- ==================================================
-- 数据初始化 (基于 2026-02-09 的模拟时间)
-- ==================================================

-- 1. 插入测试用户
-- 密码明文: 123456 (注意: 生产环境请务必使用 BCrypt 加密后的密文)
-- 如果您的代码开启了 BCrypt，请使用: $2a$10$N.zmdr9k7uOCQb376Noe8uNoRElr.eC9FzF.Qf.t.w/i.. (这里先存明文方便您测试，或者您自己运行 Test 生成)
INSERT INTO `user` (username, password, email) VALUES
    ('小明', '$2a$10$x8/2.0/..123456..HASH_PLACEHOLDER..', 'xiaoming@example.com');
-- 注意：如果您还没集成 BCrypt，这里 password 请改成 '123456'
UPDATE `user` SET password = '123456' WHERE username = '小明';


-- 2. 插入食材分类
INSERT INTO `category` (name, default_expiry_days, icon) VALUES
                                                             ('新鲜蔬菜', 3, 'leaf'),
                                                             ('肉类禽蛋', 5, 'drumstick'),
                                                             ('水产海鲜', 2, 'fish'),
                                                             ('时令水果', 7, 'apple'),
                                                             ('乳品烘焙', 10, 'cheese'),
                                                             ('零食干货', 60, 'cookie');

-- 3. 插入食材库存 (关键：日期已调整为 2026年2月)
-- 假设当前日期为 2026-02-09

INSERT INTO `food_item` (user_id, category_id, name, quantity, unit, purchase_date, expiry_date, storage_location, status) VALUES

-- [场景A：非常紧急] 今天(2.9)就过期
(1, 5, '鲜牛奶', 1, '盒', '2026-02-01', '2026-02-09', 'FRIDGE', 0),

-- [场景B：临期预警] 明天(2.10)过期 (剩余1天 -> 红色/橙色高亮)
(1, 1, '菠菜', 2, '把', '2026-02-07', '2026-02-10', 'FRIDGE', 0),

-- [场景C：临期预警] 后天(2.11)过期 (剩余2天 -> 黄色提醒)
(1, 3, '基围虾', 500, '克', '2026-02-08', '2026-02-11', 'FRIDGE', 0),

-- [场景D：状态良好] 还有很久过期 (绿色)
(1, 2, '澳洲和牛', 500, '克', '2026-02-05', '2026-02-20', 'FREEZER', 0),
(1, 4, '红富士苹果', 6, '个', '2026-02-08', '2026-02-28', 'FRIDGE', 0),
(1, 6, '坚果礼盒', 1, '箱', '2026-01-20', '2026-05-20', 'PANTRY', 0),

-- [场景E：历史数据 - 已吃完] (用于ECharts统计：健康食用)
(1, 1, '西红柿', 3, '个', '2026-01-15', '2026-01-20', 'FRIDGE', 1),
(1, 2, '鸡胸肉', 2, '块', '2026-01-18', '2026-01-21', 'FREEZER', 1),
(1, 4, '草莓', 1, '盒', '2026-02-01', '2026-02-03', 'FRIDGE', 1),

-- [场景F：历史数据 - 已浪费] (用于ECharts统计：遗憾浪费)
(1, 5, '切片面包', 1, '袋', '2026-01-01', '2026-01-07', 'PANTRY', 2);

-- 4. 菜谱记录与日志表保持为空，等待您亲自测试生成