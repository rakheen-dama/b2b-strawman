import type { ProblemDetail } from "@/lib/types";

// Phase 88 626F (U9): the fee-note Preview opens /api/invoices/{id}/preview in a new tab, so when
// the backend answers with an error there is no app shell around it. The route handler renders this
// minimal standalone page instead of the raw ProblemDetail JSON. Pure (no "server-only") so the copy
// and escaping are unit-testable; the inline styles are a self-contained document outside Tailwind.

export const NO_TEMPLATE_CODE = "invoice.preview.no_template";

export interface PreviewErrorCopy {
  title: string;
  body: string;
}

/** User-facing copy for a non-OK preview response. `problem` is null for non-JSON bodies. */
export function previewErrorCopy(status: number, problem: ProblemDetail | null): PreviewErrorCopy {
  if (status === 401) {
    return {
      title: "Session expired",
      body: "Your session has expired. Sign in again, then reopen the preview.",
    };
  }
  if (status === 409 && problem?.code === NO_TEMPLATE_CODE) {
    return {
      title: "No fee note template",
      body: "Kazi can't preview this fee note because no fee note template is available. An owner or admin can check Settings › Documents › Templates.",
    };
  }
  const detail = typeof problem?.detail === "string" ? problem.detail.trim() : "";
  return {
    title: "Preview unavailable",
    body: detail || `The fee note preview could not be loaded (HTTP ${status}).`,
  };
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/** A standalone HTML document; every interpolated value is escaped. */
export function renderPreviewErrorHtml({ title, body }: PreviewErrorCopy): string {
  return (
    `<!doctype html><html lang="en"><head><meta charset="utf-8">` +
    `<meta name="viewport" content="width=device-width, initial-scale=1">` +
    `<title>${escapeHtml(title)}</title>` +
    `<style>body{font-family:system-ui,sans-serif;background:#f1f5f9;color:#0f172a;margin:0;padding:48px 16px}` +
    `main{max-width:560px;margin:0 auto;background:#fff;border:1px solid #e2e8f0;border-radius:8px;padding:24px}` +
    `h1{font-size:18px;margin:0 0 8px}p{font-size:14px;line-height:1.5;color:#334155;margin:0}</style>` +
    `</head><body><main role="alert"><h1>${escapeHtml(title)}</h1><p>${escapeHtml(body)}</p></main></body></html>`
  );
}
