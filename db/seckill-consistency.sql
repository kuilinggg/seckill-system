-- 订单一致性字段扩展
ALTER TABLE t_order
    ADD COLUMN IF NOT EXISTS order_no VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务订单号',
    ADD COLUMN IF NOT EXISTS count INT NOT NULL DEFAULT 1 COMMENT '购买数量',
    ADD COLUMN IF NOT EXISTS payment_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '支付状态',
    ADD COLUMN IF NOT EXISTS create_time DATETIME NULL COMMENT '创建时间',
    ADD COLUMN IF NOT EXISTS update_time DATETIME NULL COMMENT '更新时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_t_order_order_no ON t_order(order_no);
CREATE INDEX IF NOT EXISTS idx_t_order_user_product ON t_order(user_id, product_id);

-- 库存总表
CREATE TABLE IF NOT EXISTS t_inventory (
    product_id BIGINT PRIMARY KEY,
    available_stock INT NOT NULL DEFAULT 0,
    frozen_stock INT NOT NULL DEFAULT 0,
    update_time DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存总表';

-- 库存冻结流水
CREATE TABLE IF NOT EXISTS t_inventory_reservation (
    id BIGINT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    product_id BIGINT NOT NULL,
    reserve_count INT NOT NULL,
    status INT NOT NULL COMMENT '0-初始化 1-已冻结 2-冻结失败 3-已确认售卖 4-已释放',
    reason VARCHAR(128) NULL,
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    UNIQUE KEY uk_inventory_reservation_order_no (order_no),
    KEY idx_inventory_reservation_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存冻结记录';
