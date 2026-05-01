-- src/main/resources/db/schema.sql
-- 餐厅管理系统 - 数据库初始化脚本 (最終修正版 v6.0)
-- 說明：專為新建數據庫設計，無欄位遷移 ALTER 語句，支持外賣配送區分

-- ============== 无依赖的表 ==============
-- 1. 业务状态表
CREATE TABLE IF NOT EXISTS business_status (
                                               status_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '業務狀態 ID',
                                               business_date DATE NOT NULL UNIQUE COMMENT '營業日期',
                                               is_open BOOLEAN DEFAULT true COMMENT '是否營業',
                                               next_call_number INT DEFAULT 1 COMMENT '下一個叫號',
                                               daily_total_customers INT DEFAULT 0 COMMENT '當日總顧客數',
                                               daily_revenue DECIMAL(10, 2) DEFAULT 0.00 COMMENT '當日總收入',
    daily_takeout_count INT DEFAULT 0 COMMENT '當日外賣訂單數',
    daily_cancelled_prepaid_amount DECIMAL(10, 2) DEFAULT 0.00 COMMENT '當日取消預定沒收的定金',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '創建時間'
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='營業狀態表';

-- 2. 顾客组表
CREATE TABLE IF NOT EXISTS customer_groups (
                                               group_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '顾客组 ID',
                                               call_number INT NOT NULL UNIQUE COMMENT '排队号码',
                                               group_size INT NOT NULL COMMENT '顾客组人数',
                                               start_time DATETIME NOT NULL COMMENT '入队/入座时间',
                                               is_assigned BOOLEAN DEFAULT FALSE COMMENT '是否已分配餐桌',
                                               shown_wait_message BOOLEAN DEFAULT FALSE COMMENT '是否已显示等待提示',
                                               table_id INT NULL COMMENT '分配的餐桌 ID',
                                               INDEX idx_call_number (call_number),
    INDEX idx_table_id (table_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='顾客组表';

-- ============== 菜单相关表 ==============
-- 3. 菜单品类表
CREATE TABLE IF NOT EXISTS menu_categories (
                                               category_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '分类 ID',
                                               name VARCHAR(20) NOT NULL UNIQUE COMMENT '分类名称',
    prefix CHAR(1) NOT NULL UNIQUE COMMENT '菜品前缀：A/B/C/D'
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单分类';

-- 4. 菜品表
CREATE TABLE IF NOT EXISTS menu_items (
                                          item_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '菜品 ID',
                                          item_code VARCHAR(10) NOT NULL UNIQUE COMMENT '菜品编号：A1, B2 等',
    name VARCHAR(50) NOT NULL COMMENT '菜品名称',
    price DECIMAL(8,2) NOT NULL COMMENT '价格',
    category_id INT NOT NULL COMMENT '所属分类 ID',
    is_active BOOLEAN DEFAULT true COMMENT '是否可用',
    FOREIGN KEY (category_id) REFERENCES menu_categories(category_id) ON DELETE CASCADE,
    INDEX idx_category (category_id),
    INDEX idx_item_code (item_code)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品表';

-- ============== 菜品季度销售统计表 ==============
CREATE TABLE IF NOT EXISTS item_quarterly_sales (
                                                    sales_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '销售记录 ID',
                                                    item_code VARCHAR(10) NOT NULL COMMENT '销售时的菜品代码快照',
    item_name VARCHAR(50) NOT NULL COMMENT '销售时的菜品名称快照',
    sale_price DECIMAL(8,2) NOT NULL COMMENT '销售时的实际单价',
    quantity_sold INT NOT NULL DEFAULT 1 COMMENT '本次销售数量',
    sale_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '销售时间戳',
    year INT NOT NULL COMMENT '年份',
    quarter ENUM('Q1', 'Q2', 'Q3', 'Q4') NOT NULL COMMENT '季度',
    INDEX idx_item_quarter (item_code, year, quarter),
    INDEX idx_sale_timestamp (sale_timestamp),
    INDEX idx_year_quarter (year, quarter)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品季度销售明细表';

-- 季度汇总视图
CREATE OR REPLACE VIEW quarterly_item_summary AS
SELECT
    item_code, item_name, year, quarter,
    SUM(quantity_sold) as total_quantity,
    COUNT(DISTINCT DATE(sale_timestamp)) as active_days,
    SUM(quantity_sold * sale_price) as total_revenue,
    AVG(sale_price) as avg_price,
    MIN(sale_price) as min_price,
    MAX(sale_price) as max_price
FROM item_quarterly_sales
GROUP BY item_code, item_name, year, quarter;

-- ============== 新增預定記錄表 (到店即刪，無 ARRIVED 狀態) ==============
CREATE TABLE IF NOT EXISTS table_reservations (
                                                  reservation_id VARCHAR(30) PRIMARY KEY COMMENT '自定义预约号：R+ 年月日 + 手机尾号 + 顺序号',
    customer_name VARCHAR(50) NOT NULL COMMENT '客人姓名',
    customer_phone VARCHAR(20) NOT NULL COMMENT '客人电话',
    reservation_time DATETIME NOT NULL COMMENT '预定入座时间',
    rescheduled_time DATETIME NULL COMMENT '延迟/改期后的新预约时间（原 reservation_time 无效时使用）',
    table_count INT NOT NULL COMMENT '预定桌子数量',
    table_config_desc VARCHAR(100) NOT NULL COMMENT '桌子配置描述',
    group_type ENUM('MAIN', 'MERGED', 'GROUP') DEFAULT 'MAIN' COMMENT '餐桌类型',
    reserved_table_ids VARCHAR(50) NULL COMMENT '本单预定桌号',
    table_selection_mode ENUM('MANUAL', 'QUANTITY') DEFAULT 'QUANTITY' COMMENT '餐桌选择方式',
    manual_table_numbers VARCHAR(100) NULL COMMENT '手动输入的餐桌号',
    status ENUM('PRE_CONFIRMED', 'CONFIRMED', 'NO_SHOW', 'DELAYED', 'COMPLETED'),
    within_15h BOOLEAN DEFAULT FALSE COMMENT '是否 1.5 小时内到店',
    pre_order BOOLEAN DEFAULT FALSE COMMENT '是否预点餐',
    is_prepaid BOOLEAN DEFAULT FALSE COMMENT '是否预付定金',
    prepaid_amount DECIMAL(10,2) DEFAULT 0.00 COMMENT '预付金额数目',
    notes TEXT NULL COMMENT '备注',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_time (reservation_time),
    INDEX idx_status (status),
    INDEX idx_phone (customer_phone),
    INDEX idx_reserved_table_ids (reserved_table_ids),
    INDEX idx_reservation_code (reservation_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='餐桌预定记录表';

-- ═══════════════════════════════════════════════════════════
-- 🔧【新增】没收定金记录表（取消预约时记录）
-- 位置说明：紧跟 table_reservations，因为它是预约的关联表
-- ═══════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS forfeited_deposits (
                                                  record_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '没收定金记录 ID',
                                                  reservation_id VARCHAR(30) NOT NULL COMMENT '关联的预约 ID',
    customer_name VARCHAR(50) NOT NULL COMMENT '客户姓名',
    customer_phone VARCHAR(20) NOT NULL COMMENT '客户电话',
    reservation_time DATETIME NOT NULL COMMENT '原定预约时间',
    forfeited_amount DECIMAL(10, 2) NOT NULL COMMENT '没收定金金额',
    cancellation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '取消时间',
    cancellation_reason VARCHAR(255) NULL COMMENT '取消原因',
    INDEX idx_reservation_id (reservation_id),
    INDEX idx_customer_phone (customer_phone),
    INDEX idx_cancellation_time (cancellation_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='取消预约的没收定金记录表';

-- ============== 餐桌核心表 (已更新支持預定) ==============
CREATE TABLE IF NOT EXISTS restaurant_tables (
                                                 table_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '餐桌 ID',
                                                 display_id VARCHAR(10) NOT NULL UNIQUE COMMENT '显示 ID，如 7 或 7a',
    base_id INT NOT NULL COMMENT '基础桌号',
    capacity INT NOT NULL COMMENT '当前可用容量',
    physical_capacity INT NOT NULL COMMENT '物理容量',

    -- 狀態增加 RESERVED
    status ENUM('VACANT', 'OCCUPIED', 'SETTING_UP', 'SPLITTING', 'RESERVED') DEFAULT 'VACANT' COMMENT '餐桌状态',
    -- 類型增加 GROUPED
    table_type ENUM('MAIN', 'MERGED', 'SUBTABLE', 'GROUPED') DEFAULT 'MAIN' COMMENT '餐桌类型',

    start_time DATETIME COMMENT '用餐开始时间',
    end_time DATETIME COMMENT '用餐结束时间',
    is_split BOOLEAN DEFAULT FALSE COMMENT '是否处于拆分状态',
    sub_table_suffix VARCHAR(2) COMMENT '子桌后缀',
    main_table_id INT COMMENT '主桌 ID',
    actual_seats INT DEFAULT 0 COMMENT '实际入座人数',
    current_group_id INT NULL COMMENT '当前顾客组 ID',
    merged_with VARCHAR(10) COMMENT '合并伙伴的 display_id',

    -- 預定相關字段
    reserved_time DATETIME NULL COMMENT '下次預定時間 (用於 1.5 小時鎖定)',
    group_with VARCHAR(50) NULL COMMENT '組合預定關聯桌號 (如 7,8，9 要三桌或以上)',

    -- 🔧【新增】当前关联的预定ID（入座时设置，离店后清空）
    current_reservation_id VARCHAR(30) NULL COMMENT '当前关联的预定记录ID',

    FOREIGN KEY (main_table_id) REFERENCES restaurant_tables(table_id) ON DELETE SET NULL,
    FOREIGN KEY (current_reservation_id) REFERENCES table_reservations(reservation_id) ON DELETE SET NULL,

    INDEX idx_display_id (display_id),
    INDEX idx_base_id (base_id),
    INDEX idx_status (status),
    INDEX idx_reserved_time (reserved_time),
    INDEX idx_current_reservation_id (current_reservation_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='餐桌表';



-- 7. 桌台订单主表（修正版 - 移除行尾內聯註釋）
CREATE TABLE IF NOT EXISTS table_orders (
                                            order_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '訂單 ID',
                                            order_number VARCHAR(30) NULL UNIQUE COMMENT '外卖订单号（格式：P-20260305-001 / D-20260305-001）',
    table_id INT NULL COMMENT '餐桌 ID，外賣或預定時為 NULL',

    -- 🔧【关键修改】reservation_id 改为 VARCHAR(30)，支持自定义格式 R20260322-1234-1
    reservation_id VARCHAR(30) NULL COMMENT '預定 ID，預定訂單時填寫 (到店後保留關聯或置空)',
    order_type ENUM('DINE_IN', 'TAKEOUT', 'RESERVATION') DEFAULT 'DINE_IN' COMMENT '訂單類型',
    -- 外賣配送方式 (僅當 order_type='TAKEOUT' 時有效)
    delivery_method ENUM('PICKUP', 'DELIVERY') NULL COMMENT '外賣方式：PICKUP=自取，DELIVERY=配送',

    -- 🔧【修改】配送狀態：僅當 delivery_method='DELIVERY' 時有效，其他情況必須為 NULL
    delivery_status ENUM('NOT_DELIVERED', 'DELIVERING', 'DELIVERED') NULL
    COMMENT '配送狀態：僅配送訂單有效，其他訂單類型為 NULL',

    -- 配送地址 (僅當 delivery_method='DELIVERY' 時有效)
    delivery_address VARCHAR(255) NULL COMMENT '配送地址',
    customer_phone VARCHAR(20) NULL COMMENT '客戶電話 (外賣必填)',

    order_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '下單時間',

    -- 重新点单时间（首次下单为 NULL，重单时更新）
    reorder_time DATETIME NULL COMMENT '重新点单时间（首次下单为 NULL，重单时更新）',

    status ENUM('NO_ORDER', 'ORDERED', 'CHECKED_OUT') DEFAULT 'ORDERED' COMMENT '訂單狀態',
    -- 三个金额字段
    items_total DECIMAL(10,2) DEFAULT 0.00 COMMENT '菜品总金额（不含配送费）',
    delivery_fee DECIMAL(10,2) DEFAULT 0.00 COMMENT '配送费（仅配送订单有效）',
    total_amount DECIMAL(10,2) DEFAULT 0.00 COMMENT '最终总金额（菜品总价 + 配送费）',

    is_checked_out BOOLEAN DEFAULT FALSE COMMENT '是否已結賬',

    -- 預付字段
    is_prepaid BOOLEAN DEFAULT FALSE COMMENT '是否預付金額',
    prepaid_amount DECIMAL(10,2) DEFAULT 0.00 COMMENT '預付金額數目',

    -- 🔧【关键修改】外键引用 table_reservations.reservation_id (VARCHAR)
    FOREIGN KEY (table_id) REFERENCES restaurant_tables(table_id) ON DELETE SET NULL,
    FOREIGN KEY (reservation_id) REFERENCES table_reservations(reservation_id) ON DELETE SET NULL,

    -- 🔧【核心修复】CHECK 约束：正确处理 delivery_method 为 NULL 的情况
    CONSTRAINT chk_delivery_status_valid CHECK (
(delivery_method = 'DELIVERY' AND delivery_status IS NOT NULL) OR
((delivery_method IS NULL OR delivery_method != 'DELIVERY') AND delivery_status IS NULL)
    ),

    -- 🔧【索引】确保 reservation_id 查询效率
    INDEX idx_table_status (table_id, status),
    INDEX idx_reservation (reservation_id),
    INDEX idx_order_type (order_type),
    INDEX idx_order_time (order_time),
    INDEX idx_reorder_time (reorder_time),
    INDEX idx_delivery_status (delivery_status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='訂單主表';


-- 8. 订单明细表
CREATE TABLE IF NOT EXISTS order_items (
                                           order_item_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '订单明细 ID',
                                           order_id INT NOT NULL COMMENT '订单 ID',
                                           item_id INT NOT NULL COMMENT '菜品 ID',
                                           quantity INT NOT NULL DEFAULT 1 COMMENT '总数量',
                                           served_quantity INT NOT NULL DEFAULT 0 COMMENT '已上菜数量',
                                           prepared_quantity INT NOT NULL DEFAULT 0 COMMENT '已准备数量',
                                           status ENUM('UNSERVED','PREPARING', 'PREPARED', 'PARTIALLY_SERVED', 'SERVED') DEFAULT 'UNSERVED' COMMENT '上菜状态',
    price_at_order DECIMAL(8,2) NOT NULL COMMENT '下单时价格',
    assigned_table_display_id VARCHAR(10) NULL COMMENT '分配的餐桌显示ID',
    served_table_display_id VARCHAR(50) NULL COMMENT '实际上菜的餐桌ID列表（如 "16,17"）',
    -- 🔧 新增：聚餐桌数量分布记录 (JSON 格式)
    quantity_distribution JSON NULL COMMENT '各桌分配数量分布，如 {"13":4,"14":4,"15":3,"16":4}',
    FOREIGN KEY (order_id) REFERENCES table_orders(order_id) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES menu_items(item_id) ON DELETE RESTRICT,

    INDEX idx_order_id (order_id),
    INDEX idx_item_id (item_id),
    INDEX idx_status (status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细';


CREATE TABLE IF NOT EXISTS order_cancellations (
                                                   cancellation_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '撤销记录 ID',

    -- ===== 撤销类型区分 =====
                                                   cancellation_type ENUM('ITEM', 'ORDER') NOT NULL COMMENT '撤销类型：ITEM=菜品，ORDER=整单',

    -- ===== 关联订单信息（整单撤销必填，菜品撤销可选）=====
    order_id INT NULL COMMENT '订单 ID',
    order_number VARCHAR(30) NULL COMMENT '订单号',

    -- ===== 关联菜品信息（菜品撤销必填，整单撤销为 NULL）=====
    item_code VARCHAR(10) NULL COMMENT '菜品编号',
    cancelled_quantity INT NULL COMMENT '撤销数量（整单撤销为 NULL）',
    before_status ENUM('UNSERVED', 'PARTIALLY_SERVED', 'SERVED') NULL COMMENT '撤销前状态（整单撤销为 NULL）',

    -- ===== 金额信息（整单撤销必填，菜品撤销可选）=====
    cancelled_amount DECIMAL(10,2) NULL COMMENT '撤销金额（整单=订单总额，菜品=菜品小计）',

    -- ===== 通用信息 =====
    cancellation_reason VARCHAR(255) NOT NULL COMMENT '撤销原因',
    cancellation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '撤销时间',

    -- ===== 索引 =====
    INDEX idx_order_id (order_id),
    INDEX idx_order_number (order_number),
    INDEX idx_item_code (item_code),
    INDEX idx_cancellation_type (cancellation_type),
    INDEX idx_cancellation_time (cancellation_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单撤销记录表（合并菜品/整单）';

-- 10. 队列表
CREATE TABLE IF NOT EXISTS queues (
                                      queue_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '队列 ID',
                                      queue_type ENUM('2_SEAT', '4_SEAT', '6_SEAT') NOT NULL COMMENT '队列类型',
    group_id INT NOT NULL COMMENT '顾客组 ID',
    position INT NOT NULL COMMENT '队列位置',
    join_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '入队时间',
    FOREIGN KEY (group_id) REFERENCES customer_groups(group_id) ON DELETE CASCADE,
    UNIQUE KEY unique_position (queue_type, position),
    INDEX idx_queue_type_position (queue_type, position),
    INDEX idx_join_time (join_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排队队列';

-- ============== 循环依赖外键（延迟添加） ==============
-- 注意：由於 restaurant_tables 和 customer_groups 互相引用，必須在表創建後添加外鍵
-- 這屬於結構約束，非欄位遷移
ALTER TABLE restaurant_tables
    ADD CONSTRAINT fk_current_group
        FOREIGN KEY (current_group_id)
            REFERENCES customer_groups(group_id)
            ON DELETE SET NULL;

ALTER TABLE customer_groups
    ADD CONSTRAINT fk_assigned_table
        FOREIGN KEY (table_id)
            REFERENCES restaurant_tables(table_id)
            ON DELETE SET NULL;

-- ============== 索引 ==============
CREATE INDEX idx_order_time_status ON table_orders(order_time, status);
CREATE INDEX idx_cg_table_time ON customer_groups(table_id, start_time);
CREATE INDEX idx_sales_date ON item_quarterly_sales(sale_timestamp);
CREATE INDEX idx_sales_year_quarter ON item_quarterly_sales(year, quarter);
CREATE INDEX idx_bs_date ON business_status(business_date);
