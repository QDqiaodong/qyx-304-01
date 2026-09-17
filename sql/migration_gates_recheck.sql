-- 证件时效 + 活动在办两道闸门：对已存在数据库的增量迁移（幂等，可重复执行）。
-- 全新部署由 init.sql 直接建出该列，无需执行本脚本。

USE volunteer_approval;

DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_column_if_missing(
    IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl VARCHAR(512))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN ', ddl);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

CALL add_column_if_missing('registrations', 'block_reason',
    '`block_reason` VARCHAR(20) COMMENT ''停在能力校验失败的原因：THRESHOLD门槛/CERT证件过期/ACTIVITY活动散场'' AFTER `resume_node`');

DROP PROCEDURE IF EXISTS add_column_if_missing;
