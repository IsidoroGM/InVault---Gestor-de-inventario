ALTER TABLE `audit_logs`
    ADD COLUMN `before_data` VARCHAR(4000) NULL;

ALTER TABLE `audit_logs`
    ADD COLUMN `after_data` VARCHAR(4000) NULL;
