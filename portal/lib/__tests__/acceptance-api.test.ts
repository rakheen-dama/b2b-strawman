import { describe, it, expect, vi, beforeEach } from "vitest";

const mockPublicFetch = vi.fn();
vi.mock("@/lib/api-client", () => ({
  publicFetch: (...args: unknown[]) => mockPublicFetch(...args),
}));

import { declineAcceptance, DeclineConflictError } from "@/lib/api/acceptance";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("declineAcceptance (Phase 88 U23)", () => {
  beforeEach(() => {
    mockPublicFetch.mockReset();
  });

  it("POSTs {reason} (trimmed) to the encoded decline endpoint", async () => {
    mockPublicFetch.mockResolvedValue(
      jsonResponse(200, {
        status: "DECLINED",
        declinedAt: "2026-10-22T14:10:02Z",
      }),
    );

    const result = await declineAcceptance("tok/en 1", "  Price too high  ");

    expect(mockPublicFetch).toHaveBeenCalledWith(
      "/api/portal/acceptance/tok%2Fen%201/decline",
      { method: "POST", body: JSON.stringify({ reason: "Price too high" }) },
    );
    expect(result).toEqual({
      status: "DECLINED",
      declinedAt: "2026-10-22T14:10:02Z",
    });
  });

  it("sends an empty JSON object when there is no reason (backend body is required)", async () => {
    mockPublicFetch.mockResolvedValue(
      jsonResponse(200, { status: "DECLINED", declinedAt: "x" }),
    );

    await declineAcceptance("tok", "   ");

    expect(mockPublicFetch).toHaveBeenCalledWith(
      "/api/portal/acceptance/tok/decline",
      { method: "POST", body: "{}" },
    );
  });

  it("maps 409 to DeclineConflictError with fixed copy", async () => {
    mockPublicFetch.mockResolvedValue(
      jsonResponse(409, {
        title: "Invalid status transition",
        detail: "This request can no longer be declined",
      }),
    );

    const error = await declineAcceptance("tok", "").catch((e: unknown) => e);
    expect(error).toBeInstanceOf(DeclineConflictError);
    expect((error as Error).message).toBe(
      "This request can no longer be declined.",
    );
  });

  it("maps any other failure to a generic retry error", async () => {
    mockPublicFetch.mockResolvedValue(jsonResponse(500, {}));

    const error = await declineAcceptance("tok", "").catch((e: unknown) => e);
    expect(error).not.toBeInstanceOf(DeclineConflictError);
    expect((error as Error).message).toBe(
      "Failed to decline. Please try again.",
    );
  });
});
