import { portalGet } from "@/lib/api-client";
import type { PortalPresignDownload } from "@/lib/types";

/**
 * Portal documents API client.
 *
 * `GET /portal/documents/{documentId}/presign-download` returns a presigned
 * URL for a document the client may see. Anything else (unshared, not
 * uploaded, another client's) is a 404; a 401 is handled by `portalFetch`.
 */
export async function fetchDocumentDownloadUrl(
  documentId: string,
): Promise<string> {
  const data = await portalGet<PortalPresignDownload>(
    `/portal/documents/${documentId}/presign-download`,
  );
  return data.presignedUrl;
}
