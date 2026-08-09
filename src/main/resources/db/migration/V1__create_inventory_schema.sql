CREATE TABLE `roles` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(50) NOT NULL,
    `description` VARCHAR(255) NULL,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_roles_name` UNIQUE (`name`)
) ENGINE=InnoDB;

CREATE TABLE `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL,
    `email` VARCHAR(120) NOT NULL,
    `password_hash` VARCHAR(255) NOT NULL,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    `must_change_password` BOOLEAN NOT NULL DEFAULT FALSE,
    `last_login_at` DATETIME(6) NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_users_username` UNIQUE (`username`),
    CONSTRAINT `uk_users_email` UNIQUE (`email`)
) ENGINE=InnoDB;

CREATE TABLE `user_roles` (
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    PRIMARY KEY (`user_id`, `role_id`),
    CONSTRAINT `uk_user_roles_user_role` UNIQUE (`user_id`, `role_id`),
    CONSTRAINT `fk_user_roles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_user_roles_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `categories` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    `description` VARCHAR(255) NULL,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_categories_name` UNIQUE (`name`)
) ENGINE=InnoDB;

CREATE TABLE `locations` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    `description` VARCHAR(255) NULL,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_locations_name` UNIQUE (`name`)
) ENGINE=InnoDB;

CREATE TABLE `units` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(30) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `symbol` VARCHAR(20) NULL,
    `description` VARCHAR(255) NULL,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_units_code` UNIQUE (`code`),
    CONSTRAINT `uk_units_name` UNIQUE (`name`)
) ENGINE=InnoDB;

CREATE TABLE `suppliers` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(150) NOT NULL,
    `contact_name` VARCHAR(120) NULL,
    `phone` VARCHAR(30) NULL,
    `email` VARCHAR(120) NULL,
    `notes` VARCHAR(255) NULL,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_suppliers_name` UNIQUE (`name`)
) ENGINE=InnoDB;

CREATE TABLE `products` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `sku` VARCHAR(80) NOT NULL,
    `name` VARCHAR(150) NOT NULL,
    `description` VARCHAR(500) NULL,
    `category_id` BIGINT NOT NULL,
    `location_id` BIGINT NOT NULL,
    `unit_id` BIGINT NOT NULL,
    `minimum_stock` DECIMAL(15,3) NOT NULL DEFAULT 0,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_products_sku` UNIQUE (`sku`),
    CONSTRAINT `fk_products_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`),
    CONSTRAINT `fk_products_location` FOREIGN KEY (`location_id`) REFERENCES `locations` (`id`),
    CONSTRAINT `fk_products_unit` FOREIGN KEY (`unit_id`) REFERENCES `units` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `batches` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `product_id` BIGINT NOT NULL,
    `batch_code` VARCHAR(100) NOT NULL,
    `quantity` DECIMAL(15,3) NOT NULL DEFAULT 0,
    `status` VARCHAR(30) NOT NULL,
    `notes` VARCHAR(255) NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_batches_batch_code` UNIQUE (`batch_code`),
    CONSTRAINT `fk_batches_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
    INDEX `idx_batches_product_status` (`product_id`, `status`)
) ENGINE=InnoDB;

CREATE TABLE `stock_movements` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `product_id` BIGINT NOT NULL,
    `batch_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `supplier_id` BIGINT NULL,
    `movement_type` VARCHAR(40) NOT NULL,
    `quantity` DECIMAL(15,3) NOT NULL,
    `previous_batch_quantity` DECIMAL(15,3) NOT NULL,
    `new_batch_quantity` DECIMAL(15,3) NOT NULL,
    `reason` VARCHAR(255) NULL,
    `movement_date` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_movements_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
    CONSTRAINT `fk_movements_batch` FOREIGN KEY (`batch_id`) REFERENCES `batches` (`id`),
    CONSTRAINT `fk_movements_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_movements_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`),
    INDEX `idx_movements_product_date` (`product_id`, `movement_date`),
    INDEX `idx_movements_batch_date` (`batch_id`, `movement_date`),
    INDEX `idx_movements_type_date` (`movement_type`, `movement_date`)
) ENGINE=InnoDB;

CREATE TABLE `audit_logs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NULL,
    `action` VARCHAR(50) NOT NULL,
    `entity_name` VARCHAR(100) NOT NULL,
    `entity_id` BIGINT NULL,
    `details` VARCHAR(500) NULL,
    `client_ip` VARCHAR(60) NULL,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_audit_logs_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    INDEX `idx_audit_action_created` (`action`, `created_at`),
    INDEX `idx_audit_entity_created` (`entity_name`, `created_at`),
    INDEX `idx_audit_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB;
