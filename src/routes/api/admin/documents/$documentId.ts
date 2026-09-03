import { createFileRoute } from "@tanstack/react-router";
import { eq } from "drizzle-orm";

import { isAdminPhone } from "#/lib/admin/middleware.ts";
import { auth } from "#/lib/auth/auth.ts";
import { db } from "#/lib/db/index.ts";
import { storeDocument } from "#/lib/db/schema/index.ts";
import { getDocument } from "#/lib/storage/documents.ts";

/**
 * A business's uploaded paper, streamed to a reviewer.
 *
 * The bucket is private and stays that way: these are somebody's tax documents, so the
 * bytes only ever leave through here, against a session that is on the reviewer list. No
 * signed URL, nothing to forward, nothing that outlives the request.
 */
export const Route = createFileRoute("/api/admin/documents/$documentId")({
  server: {
    handlers: {
      GET: async ({ request, params }) => {
        const session = await auth.api.getSession({ headers: request.headers });
        if (!isAdminPhone(session?.user.phoneNumber)) {
          // 404 rather than 403: the endpoint does not confirm what it holds to anyone
          // who is not allowed to read it.
          return new Response("Not found", { status: 404 });
        }

        const [row] = await db
          .select()
          .from(storeDocument)
          .where(eq(storeDocument.id, params.documentId));
        if (!row) return new Response("Not found", { status: 404 });

        const object = await getDocument(row.key);
        if (!object) return new Response("Not found", { status: 404 });

        return new Response(object.body, {
          headers: {
            "content-type": row.mimeType,
            "content-length": String(row.sizeBytes),
            // Inline: a reviewer looks at it in the page rather than collecting a folder
            // of somebody else's certificates in their downloads.
            "content-disposition": `inline; filename="${row.kind}"`,
            "cache-control": "private, no-store",
          },
        });
      },
    },
  },
});
