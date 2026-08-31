CREATE TABLE "device" (
	"id" text PRIMARY KEY,
	"store_id" text NOT NULL,
	"user_id" text NOT NULL,
	"name" text NOT NULL,
	"platform" text NOT NULL,
	"app_version" text,
	"push_token" text,
	"last_seen_at" timestamp with time zone DEFAULT now() NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "invoice_number_lease" (
	"id" text PRIMARY KEY,
	"store_id" text NOT NULL,
	"device_id" text NOT NULL,
	"fiscal_year" text NOT NULL,
	"invoice_type" text NOT NULL,
	"start_sequence" integer NOT NULL,
	"end_sequence" integer NOT NULL,
	"used_through" integer DEFAULT 0 NOT NULL,
	"status" text DEFAULT 'open' NOT NULL,
	"close_reason" text,
	"issued_at" timestamp with time zone DEFAULT now() NOT NULL,
	"expires_at" timestamp with time zone NOT NULL,
	"closed_at" timestamp with time zone
);
--> statement-breakpoint
CREATE TABLE "saved_bill" (
	"id" text PRIMARY KEY,
	"user_id" text NOT NULL,
	"invoice_id" text NOT NULL,
	"saved_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "user" ADD COLUMN "phone_number" text;--> statement-breakpoint
ALTER TABLE "user" ADD COLUMN "phone_number_verified" boolean;--> statement-breakpoint
ALTER TABLE "invoice" ADD COLUMN "device_id" text;--> statement-breakpoint
ALTER TABLE "invoice" ADD COLUMN "lease_id" text;--> statement-breakpoint
ALTER TABLE "invoice" ADD COLUMN "queued_at" timestamp with time zone;--> statement-breakpoint
ALTER TABLE "invoice" ADD COLUMN "share_token" text;--> statement-breakpoint
UPDATE "invoice" SET "share_token" = replace(gen_random_uuid()::text, '-', '') WHERE "share_token" IS NULL;--> statement-breakpoint
ALTER TABLE "invoice" ALTER COLUMN "share_token" SET NOT NULL;--> statement-breakpoint
ALTER TABLE "user" ADD CONSTRAINT "user_phone_number_key" UNIQUE("phone_number");--> statement-breakpoint
CREATE INDEX "device_store_idx" ON "device" ("store_id");--> statement-breakpoint
CREATE INDEX "device_user_idx" ON "device" ("user_id");--> statement-breakpoint
CREATE UNIQUE INDEX "lease_series_start_uidx" ON "invoice_number_lease" ("store_id","fiscal_year","invoice_type","start_sequence");--> statement-breakpoint
CREATE INDEX "lease_device_open_idx" ON "invoice_number_lease" ("device_id","status");--> statement-breakpoint
CREATE INDEX "lease_store_idx" ON "invoice_number_lease" ("store_id","fiscal_year");--> statement-breakpoint
CREATE UNIQUE INDEX "saved_bill_user_invoice_uidx" ON "saved_bill" ("user_id","invoice_id");--> statement-breakpoint
CREATE INDEX "saved_bill_user_idx" ON "saved_bill" ("user_id","saved_at");--> statement-breakpoint
CREATE UNIQUE INDEX "invoice_share_token_uidx" ON "invoice" ("share_token");--> statement-breakpoint
ALTER TABLE "device" ADD CONSTRAINT "device_store_id_store_id_fkey" FOREIGN KEY ("store_id") REFERENCES "store"("id") ON DELETE CASCADE;--> statement-breakpoint
ALTER TABLE "device" ADD CONSTRAINT "device_user_id_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "user"("id") ON DELETE CASCADE;--> statement-breakpoint
ALTER TABLE "invoice_number_lease" ADD CONSTRAINT "invoice_number_lease_store_id_store_id_fkey" FOREIGN KEY ("store_id") REFERENCES "store"("id") ON DELETE CASCADE;--> statement-breakpoint
ALTER TABLE "invoice_number_lease" ADD CONSTRAINT "invoice_number_lease_device_id_device_id_fkey" FOREIGN KEY ("device_id") REFERENCES "device"("id") ON DELETE CASCADE;--> statement-breakpoint
ALTER TABLE "saved_bill" ADD CONSTRAINT "saved_bill_user_id_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "user"("id") ON DELETE CASCADE;--> statement-breakpoint
ALTER TABLE "saved_bill" ADD CONSTRAINT "saved_bill_invoice_id_invoice_id_fkey" FOREIGN KEY ("invoice_id") REFERENCES "invoice"("id") ON DELETE CASCADE;--> statement-breakpoint
ALTER TABLE "invoice" ADD CONSTRAINT "invoice_device_id_device_id_fkey" FOREIGN KEY ("device_id") REFERENCES "device"("id") ON DELETE RESTRICT;--> statement-breakpoint
ALTER TABLE "invoice" ADD CONSTRAINT "invoice_lease_id_invoice_number_lease_id_fkey" FOREIGN KEY ("lease_id") REFERENCES "invoice_number_lease"("id") ON DELETE RESTRICT;