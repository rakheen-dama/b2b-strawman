import "server-only";
import { API_BASE, getAuthFetchOptions } from "@/lib/api/client";
import { NextResponse } from "next/server";
import { previewErrorCopy, renderPreviewErrorHtml } from "@/lib/invoice-preview-error";
import type { ProblemDetail } from "@/lib/types";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function errorPage(status: number, problem: ProblemDetail | null) {
  return new NextResponse(renderPreviewErrorHtml(previewErrorCopy(status, problem)), {
    status,
    headers: { "Content-Type": "text/html; charset=utf-8" },
  });
}

/**
 * Pure gateway proxy for invoice HTML preview.
 *
 * The backend already enforces @RequiresCapability("INVOICING") — no need
 * for a redundant fetchMyCapabilities() call here. Removing it fixes the
 * 401 that occurs when the preview is opened in a new browser tab
 * (OBS-5006).
 *
 * The preview opens in a new tab, outside the app shell, so every error (a missing session or any
 * non-OK upstream response, e.g. 409 invoice.preview.no_template) renders a readable, escaped HTML
 * page with the status preserved, never the raw ProblemDetail JSON (Phase 88 626F).
 */
export async function GET(_request: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;

  if (!UUID_RE.test(id)) {
    return new NextResponse("Bad Request", { status: 400 });
  }

  let authOptions: { headers: Record<string, string>; credentials?: RequestCredentials };
  try {
    authOptions = await getAuthFetchOptions("GET");
  } catch {
    return errorPage(401, null);
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
    return errorPage(response.status, problem);
  }

  const html = await response.text();
  return new NextResponse(html, {
    status: 200,
    headers: { "Content-Type": "text/html" },
  });
}
