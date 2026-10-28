import "server-only";
import { API_BASE, getAuthFetchOptions } from "@/lib/api/client";
import { NextResponse } from "next/server";
import {
  previewErrorCopy,
  renderPreviewErrorHtml,
  type PreviewErrorCopy,
} from "@/lib/invoice-preview-error";
import type { ProblemDetail } from "@/lib/types";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const INVALID_LINK_COPY: PreviewErrorCopy = {
  title: "Preview unavailable",
  body: "This preview link is not valid.",
};

function errorPage(status: number, copy: PreviewErrorCopy) {
  return new NextResponse(renderPreviewErrorHtml(copy), {
    status,
    headers: { "Content-Type": "text/html; charset=utf-8" },
  });
}

/**
 * Gateway proxy for the invoice HTML preview, with a standalone HTML error page.
 *
 * The backend already enforces @RequiresCapability("INVOICING") — no need
 * for a redundant fetchMyCapabilities() call here. Removing it fixes the
 * 401 that occurs when the preview is opened in a new browser tab
 * (OBS-5006).
 *
 * The preview opens in a new tab, outside the app shell, so every error (a missing session or any
 * non-OK upstream response, e.g. 409 invoice.preview.no_template, or this route's own malformed-id
 * 400) renders a readable, escaped HTML page with the status preserved, never plain text or the raw
 * ProblemDetail JSON (Phase 88 626F, R-2).
 */
export async function GET(_request: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;

  if (!UUID_RE.test(id)) {
    return errorPage(400, INVALID_LINK_COPY);
  }

  let authOptions: { headers: Record<string, string>; credentials?: RequestCredentials };
  try {
    authOptions = await getAuthFetchOptions("GET");
  } catch {
    return errorPage(401, previewErrorCopy(401, null));
  }

  const response = await fetch(`${API_BASE}/api/invoices/${id}/preview`, {
    headers: authOptions.headers,
    credentials: authOptions.credentials,
  });

  if (!response.ok) {
    let problem: ProblemDetail | null = null;
    if ((response.headers.get("content-type") ?? "").includes("json")) {
      problem = (await response.json().catch(() => null)) as ProblemDetail | null;
    }
    return errorPage(response.status, previewErrorCopy(response.status, problem));
  }

  const html = await response.text();
  return new NextResponse(html, {
    status: 200,
    headers: { "Content-Type": "text/html" },
  });
}
