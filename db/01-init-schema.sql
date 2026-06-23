CREATE DATABASE IF NOT EXISTS seckill DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS seckill_order_0 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS seckill_order_1 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE seckill;

CREATE TABLE IF NOT EXISTS user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(128) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(128) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    stock INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_inventory (
    product_id BIGINT PRIMARY KEY,
    available_stock INT NOT NULL DEFAULT 0,
    frozen_stock INT NOT NULL DEFAULT 0,
    update_time DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_inventory_reservation (
    id BIGINT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    product_id BIGINT NOT NULL,
    reserve_count INT NOT NULL,
    status INT NOT NULL,
    reason VARCHAR(128) NULL,
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    UNIQUE KEY uk_inventory_reservation_order_no (order_no),
    KEY idx_inventory_reservation_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO product (id, title, price, stock)
VALUES (1, 'Seckill Demo Product', 99.00, 100)
ON DUPLICATE KEY UPDATE title = VALUES(title);

INSERT INTO t_inventory (product_id, available_stock, frozen_stock, update_time)
VALUES (1, 100, 0, NOW())
ON DUPLICATE KEY UPDATE update_time = VALUES(update_time);

USE seckill_order_0;

CREATE TABLE IF NOT EXISTS t_order_0 (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    count INT NOT NULL DEFAULT 1,
    status INT NOT NULL DEFAULT 0,
    payment_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    UNIQUE KEY uk_t_order_0_order_no (order_no),
    KEY idx_t_order_0_user_product (user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_order_1 (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    count INT NOT NULL DEFAULT 1,
    status INT NOT NULL DEFAULT 0,
    payment_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    UNIQUE KEY uk_t_order_1_order_no (order_no),
    KEY idx_t_order_1_user_product (user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

USE seckill_order_1;

CREATE TABLE IF NOT EXISTS t_order_0 (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    count INT NOT NULL DEFAULT 1,
    status INT NOT NULL DEFAULT 0,
    payment_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    UNIQUE KEY uk_t_order_0_order_no (order_no),
    KEY idx_t_order_0_user_product (user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_order_1 (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    count INT NOT NULL DEFAULT 1,
    status INT NOT NULL DEFAULT 0,
    payment_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    UNIQUE KEY uk_t_order_1_order_no (order_no),
    KEY idx_t_order_1_user_product (user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
