CREATE TABLE `stock_movement_requests` (
    `id` VARCHAR(64) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `request_hash` VARCHAR(64) NOT NULL,
    `movement_id` BIGINT NULL,
    `response_body` VARCHAR(4000) NULL,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_request_movement` UNIQUE (`movement_id`),
    CONSTRAINT `fk_request_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_request_movement` FOREIGN KEY (`movement_id`) REFERENCES `stock_movements` (`id`)
) ENGINE=InnoDB;
