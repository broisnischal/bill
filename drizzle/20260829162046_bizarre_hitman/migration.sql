CREATE TABLE "invoice_payment" (
	"id" text PRIMARY KEY,
	"invoice_id" text NOT NULL,
	"store_id" text NOT NULL,
	"amount_paisa" bigint NOT NULL,
	"method" text DEFAULT 'cash' NOT NULL,
	"received_at" timestamp with time zone NOT NULL,
	"miti" text NOT NULL,
	"note" text,
	"recorded_by_id" text,
	"device_id" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "invoice" ADD COLUMN "paid_at_issue_paisa" bigint DEFAULT 0 NOT NULL;--> statement-breakpoint
ALTER TABLE "invoice" ADD COLUMN "due_miti" text;--> statement-breakpoint
CREATE INDEX "invoice_payment_invoice_idx" ON "invoice_payment" ("invoice_id");--> statement-breakpoint
CREATE INDEX "invoice_payment_store_idx" ON "invoice_payment" ("store_id","received_at");--> statement-breakpoint
ALTER TABLE "invoice_payment" ADD CONSTRAINT "invoice_payment_invoice_id_invoice_id_fkey" FOREIGN KEY ("invoice_id") REFERENCES "invoice"("id") ON DELETE RESTRICT;--> statement-breakpoint
ALTER TABLE "invoice_payment" ADD CONSTRAINT "invoice_payment_store_id_store_id_fkey" FOREIGN KEY ("store_id") REFERENCES "store"("id") ON DELETE RESTRICT;--> statement-breakpoint
ALTER TABLE "invoice_payment" ADD CONSTRAINT "invoice_payment_recorded_by_id_user_id_fkey" FOREIGN KEY ("recorded_by_id") REFERENCES "user"("id") ON DELETE SET NULL;