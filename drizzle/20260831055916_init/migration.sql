CREATE TABLE `account` (
	`id` text PRIMARY KEY,
	`issuer` text NOT NULL,
	`account_id` text NOT NULL,
	`provider_id` text NOT NULL,
	`user_id` text NOT NULL,
	`access_token` text,
	`refresh_token` text,
	`id_token` text,
	`access_token_expires_at` integer,
	`refresh_token_expires_at` integer,
	`scope` text,
	`password` text,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	CONSTRAINT `fk_account_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
);
--> statement-breakpoint
CREATE TABLE `session` (
	`id` text PRIMARY KEY,
	`expires_at` integer NOT NULL,
	`token` text NOT NULL UNIQUE,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	`ip_address` text,
	`user_agent` text,
	`user_id` text NOT NULL,
	CONSTRAINT `fk_session_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
);
--> statement-breakpoint
CREATE TABLE `user` (
	`id` text PRIMARY KEY,
	`name` text NOT NULL,
	`email` text NOT NULL UNIQUE,
	`email_verified` integer DEFAULT false NOT NULL,
	`image` text,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	`phone_number` text UNIQUE,
	`phone_number_verified` integer
);
--> statement-breakpoint
CREATE TABLE `verification` (
	`id` text PRIMARY KEY,
	`identifier` text NOT NULL,
	`value` text NOT NULL,
	`expires_at` integer NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL
);
--> statement-breakpoint
CREATE TABLE `dev_sms_code` (
	`phone_number` text PRIMARY KEY,
	`code` text NOT NULL,
	`sent_at` integer NOT NULL
);
--> statement-breakpoint
CREATE TABLE `device` (
	`id` text PRIMARY KEY,
	`store_id` text NOT NULL,
	`user_id` text NOT NULL,
	`name` text NOT NULL,
	`platform` text NOT NULL,
	`app_version` text,
	`push_token` text,
	`last_seen_at` integer NOT NULL,
	`created_at` integer NOT NULL,
	CONSTRAINT `fk_device_store_id_store_id_fk` FOREIGN KEY (`store_id`) REFERENCES `store`(`id`) ON DELETE CASCADE,
	CONSTRAINT `fk_device_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
);
--> statement-breakpoint
CREATE TABLE `invoice_number_lease` (
	`id` text PRIMARY KEY,
	`store_id` text NOT NULL,
	`device_id` text NOT NULL,
	`fiscal_year` text NOT NULL,
	`invoice_type` text NOT NULL,
	`start_sequence` integer NOT NULL,
	`end_sequence` integer NOT NULL,
	`used_through` integer DEFAULT 0 NOT NULL,
	`status` text DEFAULT 'open' NOT NULL,
	`close_reason` text,
	`issued_at` integer NOT NULL,
	`expires_at` integer NOT NULL,
	`closed_at` integer,
	CONSTRAINT `fk_invoice_number_lease_store_id_store_id_fk` FOREIGN KEY (`store_id`) REFERENCES `store`(`id`) ON DELETE CASCADE,
	CONSTRAINT `fk_invoice_number_lease_device_id_device_id_fk` FOREIGN KEY (`device_id`) REFERENCES `device`(`id`) ON DELETE CASCADE
);
--> statement-breakpoint
CREATE TABLE `saved_bill` (
	`id` text PRIMARY KEY,
	`user_id` text NOT NULL,
	`invoice_id` text NOT NULL,
	`saved_at` integer NOT NULL,
	CONSTRAINT `fk_saved_bill_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
	CONSTRAINT `fk_saved_bill_invoice_id_invoice_id_fk` FOREIGN KEY (`invoice_id`) REFERENCES `invoice`(`id`) ON DELETE CASCADE
);
--> statement-breakpoint
CREATE TABLE `shopper_profile` (
	`id` text PRIMARY KEY,
	`user_id` text NOT NULL,
	`token` text NOT NULL,
	`name` text NOT NULL,
	`phone` text,
	`pan` text,
	`address` text,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	CONSTRAINT `fk_shopper_profile_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
);
--> statement-breakpoint
CREATE TABLE `web_login_request` (
	`id` text PRIMARY KEY,
	`code` text NOT NULL,
	`poll_token` text NOT NULL,
	`status` text DEFAULT 'pending' NOT NULL,
	`approved_by_user_id` text,
	`approved_at` integer,
	`attempts` integer DEFAULT 0 NOT NULL,
	`user_agent` text,
	`ip_address` text,
	`expires_at` integer NOT NULL,
	`created_at` integer NOT NULL,
	CONSTRAINT `fk_web_login_request_approved_by_user_id_user_id_fk` FOREIGN KEY (`approved_by_user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
);
--> statement-breakpoint
CREATE TABLE `invoice` (
	`id` text PRIMARY KEY,
	`store_id` text NOT NULL,
	`fiscal_year` text NOT NULL,
	`invoice_type` text DEFAULT 'tax_invoice' NOT NULL,
	`sequence` integer NOT NULL,
	`invoice_number` text NOT NULL,
	`ref_invoice_id` text,
	`ref_invoice_number` text,
	`reason` text,
	`customer_id` text,
	`shopper_user_id` text,
	`buyer_name` text NOT NULL,
	`buyer_pan` text,
	`buyer_address` text,
	`buyer_phone` text,
	`issued_at` integer NOT NULL,
	`miti` text NOT NULL,
	`sub_total_paisa` integer NOT NULL,
	`discount_paisa` integer DEFAULT 0 NOT NULL,
	`taxable_amount_paisa` integer NOT NULL,
	`non_taxable_amount_paisa` integer DEFAULT 0 NOT NULL,
	`vat_rate_bp` integer DEFAULT 1300 NOT NULL,
	`vat_amount_paisa` integer DEFAULT 0 NOT NULL,
	`round_off_paisa` integer DEFAULT 0 NOT NULL,
	`total_paisa` integer NOT NULL,
	`amount_in_words` text NOT NULL,
	`payment_method` text DEFAULT 'cash' NOT NULL,
	`paid_at_issue_paisa` integer DEFAULT 0 NOT NULL,
	`due_miti` text,
	`notes` text,
	`status` text DEFAULT 'active' NOT NULL,
	`cancelled_at` integer,
	`cancelled_by` text,
	`print_count` integer DEFAULT 0 NOT NULL,
	`first_printed_at` integer,
	`last_printed_at` integer,
	`entered_by_id` text,
	`entered_by_name` text NOT NULL,
	`ird_sync_status` text DEFAULT 'pending' NOT NULL,
	`ird_synced_at` integer,
	`ird_sync_attempts` integer DEFAULT 0 NOT NULL,
	`ird_last_error` text,
	`ird_response` text,
	`is_realtime` integer DEFAULT true NOT NULL,
	`device_id` text,
	`lease_id` text,
	`queued_at` integer,
	`share_token` text NOT NULL,
	`pdf_key` text,
	`pdf_sha256` text,
	`pdf_bytes` integer,
	`created_at` integer NOT NULL,
	CONSTRAINT `fk_invoice_store_id_store_id_fk` FOREIGN KEY (`store_id`) REFERENCES `store`(`id`) ON DELETE RESTRICT,
	CONSTRAINT `fk_invoice_customer_id_customer_id_fk` FOREIGN KEY (`customer_id`) REFERENCES `customer`(`id`) ON DELETE SET NULL,
	CONSTRAINT `fk_invoice_shopper_user_id_user_id_fk` FOREIGN KEY (`shopper_user_id`) REFERENCES `user`(`id`) ON DELETE SET NULL,
	CONSTRAINT `fk_invoice_cancelled_by_user_id_fk` FOREIGN KEY (`cancelled_by`) REFERENCES `user`(`id`) ON DELETE SET NULL,
	CONSTRAINT `fk_invoice_entered_by_id_user_id_fk` FOREIGN KEY (`entered_by_id`) REFERENCES `user`(`id`) ON DELETE SET NULL,
	CONSTRAINT `fk_invoice_device_id_device_id_fk` FOREIGN KEY (`device_id`) REFERENCES `device`(`id`) ON DELETE RESTRICT,
	CONSTRAINT `fk_invoice_lease_id_invoice_number_lease_id_fk` FOREIGN KEY (`lease_id`) REFERENCES `invoice_number_lease`(`id`) ON DELETE RESTRICT
);
--> statement-breakpoint
CREATE TABLE `invoice_audit` (
	`id` text PRIMARY KEY,
	`invoice_id` text NOT NULL,
	`store_id` text NOT NULL,
	`action` text NOT NULL,
	`actor_id` text,
	`actor_name` text,
	`at` integer NOT NULL,
	`ip_address` text,
	`user_agent` text,
	`meta` text,
	CONSTRAINT `fk_invoice_audit_invoice_id_invoice_id_fk` FOREIGN KEY (`invoice_id`) REFERENCES `invoice`(`id`) ON DELETE RESTRICT,
	CONSTRAINT `fk_invoice_audit_store_id_store_id_fk` FOREIGN KEY (`store_id`) REFERENCES `store`(`id`) ON DELETE RESTRICT,
	CONSTRAINT `fk_invoice_audit_actor_id_user_id_fk` FOREIGN KEY (`actor_id`) REFERENCES `user`(`id`) ON DELETE SET NULL
);
--> statement-breakpoint
CREATE TABLE `invoice_counter` (
	`store_id` text NOT NULL,
	`fiscal_year` text NOT NULL,
	`invoice_type` text NOT NULL,
	`next_sequence` integer DEFAULT 1 NOT NULL,
	CONSTRAINT `invoice_counter_pk` PRIMARY KEY(`store_id`, `fiscal_year`, `invoice_type`),
	CONSTRAINT `fk_invoice_counter_store_id_store_id_fk` FOREIGN KEY (`store_id`) REFERENCES `store`(`id`) ON DELETE CASCADE
);
--> statement-breakpoint
CREATE TABLE `invoice_item` (
	`id` text PRIMARY KEY,
	`invoice_id` text NOT NULL,
	`line_no` integer NOT NULL,
	`item_id` text,
	`description` text NOT NULL,
	`hs_code` text,
	`unit` text DEFAULT 'pcs' NOT NULL,
	`quantity_milli` integer NOT NULL,
	`unit_price_paisa` integer NOT NULL,
	`discount_paisa` integer DEFAULT 0 NOT NULL,
	`vat_applicable` integer DEFAULT true NOT NULL,
	`line_total_paisa` integer NOT NULL,
	CONSTRAINT `fk_invoice_item_invoice_id_invoice_id_fk` FOREIGN KEY (`invoice_id`) REFERENCES `invoice`(`id`) ON DELETE RESTRICT,
	CONSTRAINT `fk_invoice_item_item_id_item_id_fk` FOREIGN KEY (`item_id`) REFERENCES `item`(`id`) ON DELETE SET NULL
);
--> statement-breakpoint
CREATE TABLE `invoice_payment` (
	`id` text PRIMARY KEY,
	`invoice_id` text NOT NULL,
	`store_id` text NOT NULL,
	`amount_paisa` integer NOT NULL,
	`method` text DEFAULT 'cash' NOT NULL,
	`received_at` integer NOT NULL,
	`miti` text NOT NULL,
	`note` text,
	`recorded_by_id` text,
	`device_id` text,
	`created_at` integer NOT NULL,
	CONSTRAINT `fk_invoice_payment_invoice_id_invoice_id_fk` FOREIGN KEY (`invoice_id`) REFERENCES `invoice`(`id`) ON DELETE RESTRICT,
	CONSTRAINT `fk_invoice_payment_store_id_store_id_fk` FOREIGN KEY (`store_id`) REFERENCES `store`(`id`) ON DELETE RESTRICT,
	CONSTRAINT `fk_invoice_payment_recorded_by_id_user_id_fk` FOREIGN KEY (`recorded_by_id`) REFERENCES `user`(`id`) ON DELETE SET NULL
);
--> statement-breakpoint
CREATE TABLE `customer` (
	`id` text PRIMARY KEY,
	`store_id` text NOT NULL,
	`name` text NOT NULL,
	`pan` text,
	`address` text,
	`phone` text,
	`email` text,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	CONSTRAINT `fk_customer_store_id_store_id_fk` FOREIGN KEY (`store_id`) REFERENCES `store`(`id`) ON DELETE CASCADE
);
--> statement-breakpoint
CREATE TABLE `item` (
	`id` text PRIMARY KEY,
	`store_id` text NOT NULL,
	`name` text NOT NULL,
	`description` text,
	`hs_code` text,
	`sku` text,
	`barcode` text,
	`unit` text DEFAULT 'pcs' NOT NULL,
	`unit_price_paisa` integer DEFAULT 0 NOT NULL,
	`vat_applicable` integer DEFAULT true NOT NULL,
	`active` integer DEFAULT true NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	CONSTRAINT `fk_item_store_id_store_id_fk` FOREIGN KEY (`store_id`) REFERENCES `store`(`id`) ON DELETE CASCADE
);
--> statement-breakpoint
CREATE TABLE `store` (
	`id` text PRIMARY KEY,
	`owner_id` text NOT NULL,
	`name` text NOT NULL,
	`name_nepali` text,
	`trade_name` text,
	`pan` text NOT NULL,
	`taxpayer_type` text DEFAULT 'vat' NOT NULL,
	`registration_date` text NOT NULL,
	`registration_date_bs` text NOT NULL,
	`registration_number` text,
	`business_type` text DEFAULT 'sole_proprietorship' NOT NULL,
	`tax_office` text,
	`address` text NOT NULL,
	`ward` integer,
	`municipality` text,
	`district` text,
	`province` text,
	`country` text DEFAULT 'Nepal' NOT NULL,
	`phone` text,
	`email` text,
	`website` text,
	`logo_key` text,
	`invoice_prefix` text DEFAULT '' NOT NULL,
	`vat_rate_bp` integer DEFAULT 1300 NOT NULL,
	`print_footer_note` text,
	`bank_details` text,
	`cbms_enabled` integer DEFAULT false NOT NULL,
	`cbms_username` text,
	`cbms_password_encrypted` text,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	CONSTRAINT `fk_store_owner_id_user_id_fk` FOREIGN KEY (`owner_id`) REFERENCES `user`(`id`) ON DELETE RESTRICT
);
--> statement-breakpoint
CREATE TABLE `store_member` (
	`id` text PRIMARY KEY,
	`store_id` text NOT NULL,
	`user_id` text NOT NULL,
	`role` text DEFAULT 'cashier' NOT NULL,
	`created_at` integer NOT NULL,
	CONSTRAINT `fk_store_member_store_id_store_id_fk` FOREIGN KEY (`store_id`) REFERENCES `store`(`id`) ON DELETE CASCADE,
	CONSTRAINT `fk_store_member_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
);
--> statement-breakpoint
CREATE UNIQUE INDEX `account_issuer_accountId_uidx` ON `account` (`issuer`,`account_id`);--> statement-breakpoint
CREATE INDEX `account_userId_idx` ON `account` (`user_id`);--> statement-breakpoint
CREATE INDEX `session_userId_idx` ON `session` (`user_id`);--> statement-breakpoint
CREATE INDEX `verification_identifier_idx` ON `verification` (`identifier`);--> statement-breakpoint
CREATE INDEX `device_store_idx` ON `device` (`store_id`);--> statement-breakpoint
CREATE INDEX `device_user_idx` ON `device` (`user_id`);--> statement-breakpoint
CREATE UNIQUE INDEX `lease_series_start_uidx` ON `invoice_number_lease` (`store_id`,`fiscal_year`,`invoice_type`,`start_sequence`);--> statement-breakpoint
CREATE INDEX `lease_device_open_idx` ON `invoice_number_lease` (`device_id`,`status`);--> statement-breakpoint
CREATE INDEX `lease_store_idx` ON `invoice_number_lease` (`store_id`,`fiscal_year`);--> statement-breakpoint
CREATE UNIQUE INDEX `saved_bill_user_invoice_uidx` ON `saved_bill` (`user_id`,`invoice_id`);--> statement-breakpoint
CREATE INDEX `saved_bill_user_idx` ON `saved_bill` (`user_id`,`saved_at`);--> statement-breakpoint
CREATE UNIQUE INDEX `shopper_profile_user_uidx` ON `shopper_profile` (`user_id`);--> statement-breakpoint
CREATE UNIQUE INDEX `shopper_profile_token_uidx` ON `shopper_profile` (`token`);--> statement-breakpoint
CREATE UNIQUE INDEX `web_login_code_uidx` ON `web_login_request` (`code`);--> statement-breakpoint
CREATE UNIQUE INDEX `web_login_poll_uidx` ON `web_login_request` (`poll_token`);--> statement-breakpoint
CREATE INDEX `web_login_expiry_idx` ON `web_login_request` (`expires_at`);--> statement-breakpoint
CREATE UNIQUE INDEX `invoice_store_number_uidx` ON `invoice` (`store_id`,`invoice_number`);--> statement-breakpoint
CREATE UNIQUE INDEX `invoice_share_token_uidx` ON `invoice` (`share_token`);--> statement-breakpoint
CREATE UNIQUE INDEX `invoice_series_uidx` ON `invoice` (`store_id`,`fiscal_year`,`invoice_type`,`sequence`);--> statement-breakpoint
CREATE INDEX `invoice_store_issued_idx` ON `invoice` (`store_id`,`issued_at`);--> statement-breakpoint
CREATE INDEX `invoice_store_fy_idx` ON `invoice` (`store_id`,`fiscal_year`);--> statement-breakpoint
CREATE INDEX `invoice_sync_idx` ON `invoice` (`ird_sync_status`);--> statement-breakpoint
CREATE INDEX `invoice_ref_idx` ON `invoice` (`ref_invoice_id`);--> statement-breakpoint
CREATE INDEX `invoice_shopper_idx` ON `invoice` (`shopper_user_id`,`issued_at`);--> statement-breakpoint
CREATE INDEX `invoice_audit_invoice_idx` ON `invoice_audit` (`invoice_id`);--> statement-breakpoint
CREATE INDEX `invoice_audit_store_at_idx` ON `invoice_audit` (`store_id`,`at`);--> statement-breakpoint
CREATE UNIQUE INDEX `invoice_item_line_uidx` ON `invoice_item` (`invoice_id`,`line_no`);--> statement-breakpoint
CREATE INDEX `invoice_item_invoice_idx` ON `invoice_item` (`invoice_id`);--> statement-breakpoint
CREATE INDEX `invoice_payment_invoice_idx` ON `invoice_payment` (`invoice_id`);--> statement-breakpoint
CREATE INDEX `invoice_payment_store_idx` ON `invoice_payment` (`store_id`,`received_at`);--> statement-breakpoint
CREATE INDEX `customer_store_idx` ON `customer` (`store_id`);--> statement-breakpoint
CREATE INDEX `customer_store_name_idx` ON `customer` (`store_id`,`name`);--> statement-breakpoint
CREATE INDEX `item_store_idx` ON `item` (`store_id`);--> statement-breakpoint
CREATE UNIQUE INDEX `item_store_sku_uidx` ON `item` (`store_id`,`sku`);--> statement-breakpoint
CREATE UNIQUE INDEX `item_store_barcode_uidx` ON `item` (`store_id`,`barcode`);--> statement-breakpoint
CREATE UNIQUE INDEX `store_pan_uidx` ON `store` (`pan`);--> statement-breakpoint
CREATE INDEX `store_owner_idx` ON `store` (`owner_id`);--> statement-breakpoint
CREATE UNIQUE INDEX `store_member_store_user_uidx` ON `store_member` (`store_id`,`user_id`);--> statement-breakpoint
CREATE INDEX `store_member_user_idx` ON `store_member` (`user_id`);