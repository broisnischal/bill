CREATE TABLE "account" (
	"id" text PRIMARY KEY,
	"issuer" text NOT NULL,
	"account_id" text NOT NULL,
	"provider_id" text NOT NULL,
	"user_id" text NOT NULL,
	"access_token" text,
	"refresh_token" text,
	"id_token" text,
	"access_token_expires_at" timestamp,
	"refresh_token_expires_at" timestamp,
	"scope" text,
	"password" text,
	"created_at" timestamp DEFAULT now() NOT NULL,
	"updated_at" timestamp NOT NULL
);
--> statement-breakpoint
CREATE TABLE "session" (
	"id" text PRIMARY KEY,
	"expires_at" timestamp NOT NULL,
	"token" text NOT NULL UNIQUE,
	"created_at" timestamp DEFAULT now() NOT NULL,
	"updated_at" timestamp NOT NULL,
	"ip_address" text,
	"user_agent" text,
	"user_id" text NOT NULL
);
--> statement-breakpoint
CREATE TABLE "user" (
	"id" text PRIMARY KEY,
	"name" text NOT NULL,
	"email" text NOT NULL UNIQUE,
	"email_verified" boolean DEFAULT false NOT NULL,
	"image" text,
	"created_at" timestamp DEFAULT now() NOT NULL,
	"updated_at" timestamp DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "verification" (
	"id" text PRIMARY KEY,
	"identifier" text NOT NULL,
	"value" text NOT NULL,
	"expires_at" timestamp NOT NULL,
	"created_at" timestamp DEFAULT now() NOT NULL,
	"updated_at" timestamp DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "invoice" (
	"id" text PRIMARY KEY,
	"store_id" text NOT NULL,
	"fiscal_year" text NOT NULL,
	"invoice_type" text DEFAULT 'tax_invoice' NOT NULL,
	"sequence" integer NOT NULL,
	"invoice_number" text NOT NULL,
	"ref_invoice_id" text,
	"ref_invoice_number" text,
	"reason" text,
	"customer_id" text,
	"buyer_name" text NOT NULL,
	"buyer_pan" text,
	"buyer_address" text,
	"buyer_phone" text,
	"issued_at" timestamp with time zone NOT NULL,
	"miti" text NOT NULL,
	"sub_total_paisa" bigint NOT NULL,
	"discount_paisa" bigint DEFAULT 0 NOT NULL,
	"taxable_amount_paisa" bigint NOT NULL,
	"non_taxable_amount_paisa" bigint DEFAULT 0 NOT NULL,
	"vat_rate_bp" integer DEFAULT 1300 NOT NULL,
	"vat_amount_paisa" bigint DEFAULT 0 NOT NULL,
	"round_off_paisa" bigint DEFAULT 0 NOT NULL,
	"total_paisa" bigint NOT NULL,
	"amount_in_words" text NOT NULL,
	"payment_method" text DEFAULT 'cash' NOT NULL,
	"notes" text,
	"status" text DEFAULT 'active' NOT NULL,
	"cancelled_at" timestamp with time zone,
	"cancelled_by" text,
	"print_count" integer DEFAULT 0 NOT NULL,
	"first_printed_at" timestamp with time zone,
	"last_printed_at" timestamp with time zone,
	"entered_by_id" text,
	"entered_by_name" text NOT NULL,
	"ird_sync_status" text DEFAULT 'pending' NOT NULL,
	"ird_synced_at" timestamp with time zone,
	"ird_sync_attempts" integer DEFAULT 0 NOT NULL,
	"ird_last_error" text,
	"ird_response" jsonb,
	"is_realtime" boolean DEFAULT true NOT NULL,
	"pdf_key" text,
	"pdf_sha256" text,
	"pdf_bytes" integer,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "invoice_audit" (
	"id" text PRIMARY KEY,
	"invoice_id" text NOT NULL,
	"store_id" text NOT NULL,
	"action" text NOT NULL,
	"actor_id" text,
	"actor_name" text,
	"at" timestamp with time zone DEFAULT now() NOT NULL,
	"ip_address" text,
	"user_agent" text,
	"meta" jsonb
);
--> statement-breakpoint
CREATE TABLE "invoice_counter" (
	"store_id" text,
	"fiscal_year" text,
	"invoice_type" text,
	"next_sequence" integer DEFAULT 1 NOT NULL,
	CONSTRAINT "invoice_counter_pkey" PRIMARY KEY("store_id","fiscal_year","invoice_type")
);
--> statement-breakpoint
CREATE TABLE "invoice_item" (
	"id" text PRIMARY KEY,
	"invoice_id" text NOT NULL,
	"line_no" integer NOT NULL,
	"item_id" text,
	"description" text NOT NULL,
	"hs_code" text,
	"unit" text DEFAULT 'pcs' NOT NULL,
	"quantity_milli" bigint NOT NULL,
	"unit_price_paisa" bigint NOT NULL,
	"discount_paisa" bigint DEFAULT 0 NOT NULL,
	"vat_applicable" boolean DEFAULT true NOT NULL,
	"line_total_paisa" bigint NOT NULL
);
--> statement-breakpoint
CREATE TABLE "customer" (
	"id" text PRIMARY KEY,
	"store_id" text NOT NULL,
	"name" text NOT NULL,
	"pan" text,
	"address" text,
	"phone" text,
	"email" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "item" (
	"id" text PRIMARY KEY,
	"store_id" text NOT NULL,
	"name" text NOT NULL,
	"description" text,
	"hs_code" text,
	"sku" text,
	"unit" text DEFAULT 'pcs' NOT NULL,
	"unit_price_paisa" bigint DEFAULT 0 NOT NULL,
	"vat_applicable" boolean DEFAULT true NOT NULL,
	"active" boolean DEFAULT true NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "store" (
	"id" text PRIMARY KEY,
	"owner_id" text NOT NULL,
	"name" text NOT NULL,
	"name_nepali" text,
	"trade_name" text,
	"pan" text NOT NULL,
	"taxpayer_type" text DEFAULT 'vat' NOT NULL,
	"registration_date" date NOT NULL,
	"registration_date_bs" text NOT NULL,
	"registration_number" text,
	"business_type" text DEFAULT 'sole_proprietorship' NOT NULL,
	"tax_office" text,
	"address" text NOT NULL,
	"ward" integer,
	"municipality" text,
	"district" text,
	"province" text,
	"country" text DEFAULT 'Nepal' NOT NULL,
	"phone" text,
	"email" text,
	"website" text,
	"logo_key" text,
	"invoice_prefix" text DEFAULT '' NOT NULL,
	"vat_rate_bp" integer DEFAULT 1300 NOT NULL,
	"print_footer_note" text,
	"bank_details" text,
	"cbms_enabled" boolean DEFAULT false NOT NULL,
	"cbms_username" text,
	"cbms_password_encrypted" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "store_member" (
	"id" text PRIMARY KEY,
	"store_id" text NOT NULL,
	"user_id" text NOT NULL,
	"role" text DEFAULT 'cashier' NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX "account_issuer_accountId_uidx" ON "account" ("issuer","account_id");--> statement-breakpoint
CREATE INDEX "account_userId_idx" ON "account" ("user_id");--> statement-breakpoint
CREATE INDEX "session_userId_idx" ON "session" ("user_id");--> statement-breakpoint
CREATE INDEX "verification_identifier_idx" ON "verification" ("identifier");--> statement-breakpoint
CREATE UNIQUE INDEX "invoice_store_number_uidx" ON "invoice" ("store_id","invoice_number");--> statement-breakpoint
CREATE UNIQUE INDEX "invoice_series_uidx" ON "invoice" ("store_id","fiscal_year","invoice_type","sequence");--> statement-breakpoint
CREATE INDEX "invoice_store_issued_idx" ON "invoice" ("store_id","issued_at");--> statement-breakpoint
CREATE INDEX "invoice_store_fy_idx" ON "invoice" ("store_id","fiscal_year");--> statement-breakpoint
CREATE INDEX "invoice_sync_idx" ON "invoice" ("ird_sync_status");--> statement-breakpoint
CREATE INDEX "invoice_ref_idx" ON "invoice" ("ref_invoice_id");--> statement-breakpoint
CREATE INDEX "invoice_audit_invoice_idx" ON "invoice_audit" ("invoice_id");--> statement-breakpoint
CREATE INDEX "invoice_audit_store_at_idx" ON "invoice_audit" ("store_id","at");--> statement-breakpoint
CREATE UNIQUE INDEX "invoice_item_line_uidx" ON "invoice_item" ("invoice_id","line_no");--> statement-breakpoint
CREATE INDEX "invoice_item_invoice_idx" ON "invoice_item" ("invoice_id");--> statement-breakpoint
CREATE INDEX "customer_store_idx" ON "customer" ("store_id");--> statement-breakpoint
CREATE INDEX "customer_store_name_idx" ON "customer" ("store_id","name");--> statement-breakpoint
CREATE INDEX "item_store_idx" ON "item" ("store_id");--> statement-breakpoint
CREATE UNIQUE INDEX "item_store_sku_uidx" ON "item" ("store_id","sku");--> statement-breakpoint
CREATE UNIQUE INDEX "store_pan_uidx" ON "store" ("pan");--> statement-breakpoint
CREATE INDEX "store_owner_idx" ON "store" ("owner_id");--> statement-breakpoint
CREATE UNIQUE INDEX "store_member_store_user_uidx" ON "store_member" ("store_id","user_id");--> statement-breakpoint
CREATE INDEX "store_member_user_idx" ON "store_member" ("user_id");--> statement-breakpoint
ALTER TABLE "account" ADD CONSTRAINT "account_user_id_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "user"("id") ON DELETE CASCADE;--> statement-breakpoint
ALTER TABLE "session" ADD CONSTRAINT "session_user_id_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "user"("id") ON DELETE CASCADE;--> statement-breakpoint
ALTER TABLE "invoice" ADD CONSTRAINT "invoice_store_id_store_id_fkey" FOREIGN KEY ("store_id") REFERENCES "store"("id") ON DELETE RESTRICT;--> statement-breakpoint
ALTER TABLE "invoice" ADD CONSTRAINT "invoice_customer_id_customer_id_fkey" FOREIGN KEY ("customer_id") REFERENCES "customer"("id") ON DELETE SET NULL;--> statement-breakpoint
ALTER TABLE "invoice" ADD CONSTRAINT "invoice_cancelled_by_user_id_fkey" FOREIGN KEY ("cancelled_by") REFERENCES "user"("id") ON DELETE SET NULL;--> statement-breakpoint
ALTER TABLE "invoice" ADD CONSTRAINT "invoice_entered_by_id_user_id_fkey" FOREIGN KEY ("entered_by_id") REFERENCES "user"("id") ON DELETE SET NULL;--> statement-breakpoint
ALTER TABLE "invoice_audit" ADD CONSTRAINT "invoice_audit_invoice_id_invoice_id_fkey" FOREIGN KEY ("invoice_id") REFERENCES "invoice"("id") ON DELETE RESTRICT;--> statement-breakpoint
ALTER TABLE "invoice_audit" ADD CONSTRAINT "invoice_audit_store_id_store_id_fkey" FOREIGN KEY ("store_id") REFERENCES "store"("id") ON DELETE RESTRICT;--> statement-breakpoint
ALTER TABLE "invoice_audit" ADD CONSTRAINT "invoice_audit_actor_id_user_id_fkey" FOREIGN KEY ("actor_id") REFERENCES "user"("id") ON DELETE SET NULL;--> statement-breakpoint
ALTER TABLE "invoice_counter" ADD CONSTRAINT "invoice_counter_store_id_store_id_fkey" FOREIGN KEY ("store_id") REFERENCES "store"("id") ON DELETE CASCADE;--> statement-breakpoint
ALTER TABLE "invoice_item" ADD CONSTRAINT "invoice_item_invoice_id_invoice_id_fkey" FOREIGN KEY ("invoice_id") REFERENCES "invoice"("id") ON DELETE RESTRICT;--> statement-breakpoint
ALTER TABLE "invoice_item" ADD CONSTRAINT "invoice_item_item_id_item_id_fkey" FOREIGN KEY ("item_id") REFERENCES "item"("id") ON DELETE SET NULL;--> statement-breakpoint
ALTER TABLE "customer" ADD CONSTRAINT "customer_store_id_store_id_fkey" FOREIGN KEY ("store_id") REFERENCES "store"("id") ON DELETE CASCADE;--> statement-breakpoint
ALTER TABLE "item" ADD CONSTRAINT "item_store_id_store_id_fkey" FOREIGN KEY ("store_id") REFERENCES "store"("id") ON DELETE CASCADE;--> statement-breakpoint
ALTER TABLE "store" ADD CONSTRAINT "store_owner_id_user_id_fkey" FOREIGN KEY ("owner_id") REFERENCES "user"("id") ON DELETE RESTRICT;--> statement-breakpoint
ALTER TABLE "store_member" ADD CONSTRAINT "store_member_store_id_store_id_fkey" FOREIGN KEY ("store_id") REFERENCES "store"("id") ON DELETE CASCADE;--> statement-breakpoint
ALTER TABLE "store_member" ADD CONSTRAINT "store_member_user_id_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "user"("id") ON DELETE CASCADE;