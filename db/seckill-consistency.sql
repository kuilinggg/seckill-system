-- Order consistency migration for existing databases.
-- It upgrades the user/product idempotency index on every physical order table
-- from a normal index to a unique index so MySQL can guard one-user-one-product.

DELIMITER //

DROP PROCEDURE IF EXISTS ensure_unique_order_user_product_index//
CREATE PROCEDURE ensure_unique_order_user_product_index(
    IN p_schema_name VARCHAR(64),
    IN p_table_name VARCHAR(64),
    IN p_old_index_name VARCHAR(64),
    IN p_unique_index_name VARCHAR(64)
)
BEGIN
    DECLARE old_index_count INT DEFAULT 0;
    DECLARE unique_index_count INT DEFAULT 0;

    SELECT COUNT(*)
      INTO old_index_count
      FROM information_schema.statistics
     WHERE table_schema = p_schema_name
       AND table_name = p_table_name
       AND index_name = p_old_index_name;

    IF old_index_count > 0 THEN
        SET @drop_sql = CONCAT('ALTER TABLE `', p_schema_name, '`.`', p_table_name, '` DROP INDEX `', p_old_index_name, '`');
        PREPARE drop_stmt FROM @drop_sql;
        EXECUTE drop_stmt;
        DEALLOCATE PREPARE drop_stmt;
    END IF;

    SELECT COUNT(*)
      INTO unique_index_count
      FROM information_schema.statistics
     WHERE table_schema = p_schema_name
       AND table_name = p_table_name
       AND index_name = p_unique_index_name
       AND non_unique = 0;

    IF unique_index_count = 0 THEN
        SET @add_sql = CONCAT('ALTER TABLE `', p_schema_name, '`.`', p_table_name, '` ADD UNIQUE KEY `', p_unique_index_name, '` (`user_id`, `product_id`)');
        PREPARE add_stmt FROM @add_sql;
        EXECUTE add_stmt;
        DEALLOCATE PREPARE add_stmt;
    END IF;
END//

DELIMITER ;

CALL ensure_unique_order_user_product_index('seckill_order_0', 't_order_0', 'idx_t_order_0_user_product', 'uk_t_order_0_user_product');
CALL ensure_unique_order_user_product_index('seckill_order_0', 't_order_1', 'idx_t_order_1_user_product', 'uk_t_order_1_user_product');
CALL ensure_unique_order_user_product_index('seckill_order_1', 't_order_0', 'idx_t_order_0_user_product', 'uk_t_order_0_user_product');
CALL ensure_unique_order_user_product_index('seckill_order_1', 't_order_1', 'idx_t_order_1_user_product', 'uk_t_order_1_user_product');

DROP PROCEDURE IF EXISTS ensure_unique_order_user_product_index;
