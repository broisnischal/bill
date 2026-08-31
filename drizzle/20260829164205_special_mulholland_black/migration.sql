ALTER TABLE "invoice" ADD COLUMN "shopper_user_id" text;--> statement-breakpoint
CREATE INDEX "invoice_shopper_idx" ON "invoice" ("shopper_user_id","issued_at");--> statement-breakpoint
ALTER TABLE "invoice" ADD CONSTRAINT "invoice_shopper_user_id_user_id_fkey" FOREIGN KEY ("shopper_user_id") REFERENCES "user"("id") ON DELETE SET NULL;