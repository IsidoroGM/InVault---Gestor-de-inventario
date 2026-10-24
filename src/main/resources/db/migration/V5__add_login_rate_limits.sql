CREATE TABLE `login_rate_limits` (
    `id` VARCHAR(80) NOT NULL,
    `attempts` INT NOT NULL,
    `window_expires_at` BIGINT NOT NULL,
    `reported` BOOLEAN NOT NULL,
    PRIMARY KEY (`id`),
    INDEX `ix_login_rate_limits_expiry` (`window_expires_at`)
) ENGINE=InnoDB;
