import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

// Phase 88 626F (U9 / R-2): the fee-note Preview opens /api/invoices/{id}/preview in a new tab. Any
// non-OK upstream answer must render a readable, HTML-escaped page (status preserved) — never the
// raw ProblemDetail JSON. The 409 invoice.preview.no_template path is not reachable through the UI
// on legal-za, so this test is its proof.

vi.mock("server-only", () => ({}));

const mockGetAuthFetchOptions = vi.fn();
vi.mock("@/lib/api/client", () => ({
  API_BASE: "http://backend.test",
  getAuthFetchOptions: (...args: unknown[]) => mockGetAuthFetchOptions(...args),
}));

import { GET } from "@/app/api/invoices/[id]/preview/route";

const ID = "852969e7-8cc1-4bca-83dd-7de636e74d8d";
const fetchMock = vi.fn();

function call(id = ID) {
  return GET(new Request(`http://localhost/api/invoices/${id}/preview`), {
    params: Promise.resolve({ id }),
  });
}

function problem(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify({ status, ...body }), {
    status,
    headers: { "Content-Type": "application/problem+json" },
  });
}

beforeEach(() => {
  mockGetAuthFetchOptions.mockResolvedValue({ headers: { Authorization: "Bearer t" } });
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("GET /api/invoices/[id]/preview", () => {
  it("passes a 200 preview through unchanged", async () => {
    const html = "<html><body><p>Fee note R1 200,00</p></body></html>";
    fetchMock.mockResolvedValue(
      new Response(html, { status: 200, headers: { "Content-Type": "text/html" } })
    );

    const res = await call();

    expect(fetchMock).toHaveBeenCalledWith(`http://backend.test/api/invoices/${ID}/preview`, {
      headers: { Authorization: "Bearer t" },
      credentials: undefined,
    });
    expect(res.status).toBe(200);
    expect(res.headers.get("content-type")).toBe("text/html");
    expect(await res.text()).toBe(html);
  });

  it("409 invoice.preview.no_template renders the friendly no-template page", async () => {
    fetchMock.mockResolvedValue(
      problem(409, {
        title: "No fee note template",
        detail:
          "No active fee note template is available to preview this fee note. Check Settings › Documents › Templates.",
        code: "invoice.preview.no_template",
      })
    );

    const res = await call();
    const body = await res.text();

    expect(res.status).toBe(409);
    expect(res.headers.get("content-type")).toBe("text/html; charset=utf-8");
    expect(body).toMatch(/^<!doctype html>/i);
    expect(body).toContain("<h1>No fee note template</h1>");
    expect(body).toContain("Settings › Documents › Templates");
    expect(body).not.toContain('"code"');
    expect(body).not.toContain("invoice.preview.no_template");
  });

  it("other ProblemDetails show 'Preview unavailable' with the escaped detail", async () => {
    fetchMock.mockResolvedValue(
      problem(404, {
        title: "Invoice not found",
        detail: 'No invoice found with id <script>alert("x")</script> & co',
      })
    );

    const res = await call();
    const body = await res.text();

    expect(res.status).toBe(404);
    expect(res.headers.get("content-type")).toBe("text/html; charset=utf-8");
    expect(body).toContain("<h1>Preview unavailable</h1>");
    expect(body).toContain(
      "No invoice found with id &lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt; &amp; co"
    );
    expect(body).not.toContain("<script>");
  });

  it("a 409 with another code is not mistaken for the no-template case", async () => {
    fetchMock.mockResolvedValue(
      problem(409, { title: "Conflict", detail: "Something else conflicted." })
    );

    const body = await (await call()).text();

    expect(body).toContain("<h1>Preview unavailable</h1>");
    expect(body).toContain("Something else conflicted.");
    expect(body).not.toContain("No fee note template");
  });

  it("a non-JSON error body shows the fallback text, never the raw body", async () => {
    fetchMock.mockResolvedValue(
      new Response("<html>gateway exploded <b>stack</b></html>", {
        status: 502,
        headers: { "Content-Type": "text/html" },
      })
    );

    const res = await call();
    const body = await res.text();

    expect(res.status).toBe(502);
    expect(body).toContain("<h1>Preview unavailable</h1>");
    expect(body).toContain("The fee note preview could not be loaded (HTTP 502).");
    expect(body).not.toContain("gateway exploded");
  });

  it("an upstream 401 asks the user to sign in again", async () => {
    fetchMock.mockResolvedValue(new Response("", { status: 401 }));

    const res = await call();
    const body = await res.text();

    expect(res.status).toBe(401);
    expect(res.headers.get("content-type")).toBe("text/html; charset=utf-8");
    expect(body).toContain("Your session has expired. Sign in again, then reopen the preview.");
  });

  it("no session (auth options throw) renders the same sign-in page with 401", async () => {
    mockGetAuthFetchOptions.mockRejectedValue(new Error("no token"));

    const res = await call();
    const body = await res.text();

    expect(res.status).toBe(401);
    expect(res.headers.get("content-type")).toBe("text/html; charset=utf-8");
    expect(body).toContain("Your session has expired. Sign in again, then reopen the preview.");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects a non-UUID id with 400 before calling the backend", async () => {
    const res = await call("not-a-uuid");

    expect(res.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
