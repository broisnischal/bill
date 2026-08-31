CREATE TABLE "shopper_profile" (
	"id" text PRIMARY KEY,
	"user_id" text NOT NULL,
	"token" text NOT NULL,
	"name" text NOT NULL,
	"phone" text,
	"pan" text,
	"address" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "item" ADD COLUMN "barcode" text;--> statement-breakpoint
CREATE UNIQUE INDEX "shopper_profile_user_uidx" ON "shopper_profile" ("user_id");--> statement-breakpoint
CREATE UNIQUE INDEX "shopper_profile_token_uidx" ON "shopper_profile" ("token");--> statement-breakpoint
CREATE UNIQUE INDEX "item_store_barcode_uidx" ON "item" ("store_id","barcode");--> statement-breakpoint
ALTER TABLE "shopper_profile" ADD CONSTRAINT "shopper_profile_user_id_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "user"("id") ON DELETE CASCADE;