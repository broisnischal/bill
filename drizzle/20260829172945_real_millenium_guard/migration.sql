CREATE TABLE "web_login_request" (
	"id" text PRIMARY KEY,
	"code" text NOT NULL,
	"poll_token" text NOT NULL,
	"status" text DEFAULT 'pending' NOT NULL,
	"approved_by_user_id" text,
	"approved_at" timestamp with time zone,
	"attempts" integer DEFAULT 0 NOT NULL,
	"user_agent" text,
	"ip_address" text,
	"expires_at" timestamp with time zone NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX "web_login_code_uidx" ON "web_login_request" ("code");--> statement-breakpoint
CREATE UNIQUE INDEX "web_login_poll_uidx" ON "web_login_request" ("poll_token");--> statement-breakpoint
CREATE INDEX "web_login_expiry_idx" ON "web_login_request" ("expires_at");--> statement-breakpoint
ALTER TABLE "web_login_request" ADD CONSTRAINT "web_login_request_approved_by_user_id_user_id_fkey" FOREIGN KEY ("approved_by_user_id") REFERENCES "user"("id") ON DELETE CASCADE;