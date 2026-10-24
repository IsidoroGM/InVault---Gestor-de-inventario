ALTER TABLE `units` ADD COLUMN `identity_locked` BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE `products` ADD COLUMN `unit_locked` BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE `units` u SET u.identity_locked = TRUE
WHERE EXISTS (SELECT 1 FROM `products` p WHERE p.unit_id = u.id);

UPDATE `products` p SET p.unit_locked = TRUE
WHERE EXISTS (SELECT 1 FROM `batches` b WHERE b.product_id = p.id);
