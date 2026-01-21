-- --------------------------------------------------
-- 1. 创建数据库
-- --------------------------------------------------
CREATE DATABASE IF NOT EXISTS fridge_guardian CHARACTER SET utf8mb4;
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


-- 1. 插入测试用户 (密码均为123456，实际开发建议用BCrypt)
INSERT INTO `user` (username, password, email) VALUES
    ('小明', '123456', 'xiaoming@example.com');

-- 2. 插入初始化分类
INSERT INTO `category` (name, default_expiry_days, icon) VALUES
                                                             ('新鲜蔬菜', 3, 'leaf'),
                                                             ('肉类禽蛋', 5, 'drumstick'),
                                                             ('水产海鲜', 2, 'fish'),
                                                             ('时令水果', 7, 'apple'),
                                                             ('乳品烘焙', 10, 'cheese'),
                                                             ('零食干货', 60, 'cookie');

-- 3. 插入食材库存 (模拟各种场景)
-- 假设今天是 2024-05-20 (请根据你测试时的实际日期微调)

INSERT INTO `food_item` (user_id, category_id, name, quantity, unit, purchase_date, expiry_date, storage_location, status) VALUES
-- 场景：即将过期的食材 (用于首页红色高亮提醒)
(1, 1, '菠菜', 1, '把', '2024-05-18', '2024-05-21', 'FRIDGE', 0),
(1, 5, '鲜牛奶', 1, '盒', '2024-05-12', '2024-05-22', 'FRIDGE', 0),

-- 场景：正常的食材
(1, 2, '澳洲和牛', 500, '克', '2024-05-19', '2024-05-24', 'FREEZER', 0),
(1, 4, '红富士苹果', 5, '个', '2024-05-20', '2024-05-30', 'FRIDGE', 0),

-- 场景：已经吃完的食材 (用于统计图表)
(1, 1, '西红柿', 3, '个', '2024-05-10', '2024-05-13', 'FRIDGE', 1),
(1, 3, '三文鱼', 200, '克', '2024-05-10', '2024-05-12', 'FRIDGE', 1),

-- 场景：不小心放过期浪费了的食材 (用于浪费率统计)
(1, 5, '切片面包', 1, '袋', '2024-05-01', '2024-05-07', 'PANTRY', 2);

-- 4. 插入一条模拟 AI 菜谱历史
INSERT INTO `recipe_record` (user_id, food_names, title, content) VALUES
    (1, '菠菜, 牛肉', '元气菠菜炒牛肉', '### 烹饪步骤\n1. 将牛肉切片腌制...\n2. 菠菜焯水...\n3. 大火快炒...');