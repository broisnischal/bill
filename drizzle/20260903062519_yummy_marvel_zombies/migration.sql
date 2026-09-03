CREATE TABLE `store_document` (
	`id` text PRIMARY KEY,
	`store_id` text NOT NULL,
	`kind` text NOT NULL,
	`key` text NOT NULL,
	`file_name` text,
	`mime_type` text NOT NULL,
	`size_bytes` integer NOT NULL,
	`uploaded_at` integer NOT NULL,
	CONSTRAINT `fk_store_document_store_id_store_id_fk` FOREIGN KEY (`store_id`) REFERENCES `store`(`id`) ON DELETE CASCADE
);
--> statement-breakpoint
ALTER TABLE `store` ADD `status` text DEFAULT 'pending' NOT NULL;--> statement-breakpoint
ALTER TABLE `store` ADD `reviewed_at` integer;--> statement-breakpoint
ALTER TABLE `store` ADD `reviewed_by_id` text REFERENCES user(id) ON DELETE SET NULL;--> statement-breakpoint
ALTER TABLE `store` ADD `review_note` text;--> statement-breakpoint
CREATE INDEX `store_status_idx` ON `store` (`status`);--> statement-breakpoint
CREATE UNIQUE INDEX `store_document_kind_uidx` ON `store_document` (`store_id`,`kind`);--> statement-breakpoint
CREATE INDEX `store_document_store_idx` ON `store_document` (`store_id`);--> statement-breakpoint
-- Businesses that existed before review did are approved, not held. They were billing
-- yesterday and a schema change must not stop them this morning; review applies from
-- here on.
UPDATE `store` SET `status` = 'approved', `reviewed_at` = unixepoch() WHERE `status` = 'pending';
