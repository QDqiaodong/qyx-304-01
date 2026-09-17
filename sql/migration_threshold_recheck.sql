-- 门槛改写重检 + 退回重提：对已存在数据库的增量迁移（幂等，可重复执行）。
-- 全新部署由 init.sql 直接建出这些列，无需执行本脚本。
-- MySQL 8.0 的 ADD COLUMN 不支持 IF NOT EXISTS，故用存储过程判列是否存在。

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

CALL add_column_if_missing('positions', 'requirement_version',
    '`requirement_version` INT DEFAULT 1 COMMENT ''门槛版本：技能/证书/时长每次加严或改写加1'' AFTER `max_count`');

CALL add_column_if_missing('registrations', 'requirement_version_at_apply',
    '`requirement_version_at_apply` INT DEFAULT 1 AFTER `check_pass`');
CALL add_column_if_missing('registrations', 'recheck_result',
    '`recheck_result` TEXT COMMENT ''门槛改写后的最新复核结果JSON'' AFTER `requirement_version_at_apply`');
CALL add_column_if_missing('registrations', 'recheck_pass',
    '`recheck_pass` TINYINT COMMENT ''最新复核是否通过：1是 0否 NULL未复核'' AFTER `recheck_result`');
CALL add_column_if_missing('registrations', 'resume_node',
    '`resume_node` TINYINT COMMENT ''门槛收紧被卡前的原审批节点，放宽后恢复'' AFTER `recheck_pass`');

-- 老数据兜底：已存在的岗位/报名门槛版本按 1 计
UPDATE positions SET requirement_version = 1 WHERE requirement_version IS NULL;
UPDATE registrations SET requirement_version_at_apply = 1 WHERE requirement_version_at_apply IS NULL;

DROP PROCEDURE IF EXISTS add_column_if_missing;
