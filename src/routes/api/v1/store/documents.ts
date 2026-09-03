import { createFileRoute } from "@tanstack/react-router";

import { ApiError, json, run, requireStore } from "#/lib/api/v1.ts";
import { ALLOWED_DOCUMENT_TYPES, MAX_DOCUMENT_BYTES } from "#/lib/storage/documents.ts";
import { documentsFor, ReviewError, saveDocument } from "#/lib/store/review.ts";
import { storeDocumentKindSchema } from "#/lib/store/schema.ts";

/**
 * The papers a business uploads for review.
 *
 * The body is the file itself rather than multipart: the app has one file and knows its
 * type, and a Worker parsing a multipart envelope to recover bytes it was already sent is
 * work for nothing. `?kind=` says which slot it fills.
 */
export const Route = createFileRoute("/api/v1/store/documents")({
  server: {
    handlers: {
      GET: ({ request }) =>
        run(async () => {
          const context = await requireStore(request);
          return json({ documents: await documentsFor(context.store.id) });
        }),

      POST: ({ request }) =>
        run(async () => {
          const context = await requireStore(request);

          const url = new URL(request.url);
          const kind = storeDocumentKindSchema.parse(url.searchParams.get("kind"));
          const mimeType = (request.headers.get("content-type") ?? "").split(";")[0]!.trim();

          if (
            !ALLOWED_DOCUMENT_TYPES.includes(mimeType as (typeof ALLOWED_DOCUMENT_TYPES)[number])
          ) {
            throw new ApiError(
              415,
              "unsupported_type",
              "Send a photo or a PDF of the page. Other files cannot be read.",
            );
          }

          const declared = Number(request.headers.get("content-length") ?? 0);
          if (declared > MAX_DOCUMENT_BYTES) {
            throw new ApiError(
              413,
              "too_large",
              "That file is over 8 MB. A photo of the page is enough.",
            );
          }

          const body = await request.arrayBuffer();

          try {
            const document = await saveDocument({
              storeId: context.store.id,
              kind,
              body,
              mimeType,
              fileName: url.searchParams.get("name") ?? undefined,
            });
            return json({ document }, 201);
          } catch (error) {
            if (error instanceof ReviewError) {
              throw new ApiError(error.code === "too_large" ? 413 : 400, error.code, error.message);
            }
            throw error;
          }
        }),
    },
  },
});
