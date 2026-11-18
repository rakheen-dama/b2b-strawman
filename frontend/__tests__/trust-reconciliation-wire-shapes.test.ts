import { describe, it, expect, vi, beforeEach } from "vitest";

// G89-F17 (ADR-365 wire pin): TrustReconciliationController returns a plain JSON array
// (`ResponseEntity<List<…>>`) for both list endpoints, not a Spring `Page` envelope. Reading
// `result.page.*` threw, so the Reconciliation list always showed "Failed to load reconciliations".
// These tests drive the real server actions against a mocked `api.get` that returns an array.

vi.mock("server-only", () => ({}));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

const { mockGet, mockPost, MockApiError } = vi.hoisted(() => {
  // Stands in for lib/api/client's ApiError: the message is the ProblemDetail's `detail || title`.
  class MockApiError extends Error {
    constructor(
      public status: number,
      message: string,
      public detail?: { title?: string; detail?: string }
    ) {
      super(message);
    }
  }
  return { mockGet: vi.fn(), mockPost: vi.fn(), MockApiError };
});

vi.mock("@/lib/api", () => ({
  api: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    put: vi.fn(),
  },
  API_BASE: "http://backend",
  ApiError: MockApiError,
  getAuthFetchOptions: vi.fn(),
}));

import {
  completeReconciliation,
  fetchBankStatements,
  fetchReconciliations,
} from "@/app/(app)/org/[slug]/trust-accounting/reconciliation/actions";

function rec(id: string) {
  return { id, periodEnd: "2026-10-31", status: "DRAFT" };
}

describe("trust reconciliation actions accept the backend JSON array (G89-F17)", () => {
  beforeEach(() => mockGet.mockReset());

  it("fetchReconciliations maps a JSON array to the page shape", async () => {
    mockGet.mockResolvedValue([rec("r1"), rec("r2"), rec("r3")]);

    const page = await fetchReconciliations("acc-1");

    expect(mockGet).toHaveBeenCalledWith("/api/trust-accounts/acc-1/reconciliations");
    expect(page.content.map((r) => r.id)).toEqual(["r1", "r2", "r3"]);
    expect(page.totalElements).toBe(3);
    expect(page.totalPages).toBe(1);
    expect(page.pageNumber).toBe(0);
    expect(page.pageSize).toBe(20);
  });

  it("fetchReconciliations pages the array client-side", async () => {
    mockGet.mockResolvedValue(Array.from({ length: 5 }, (_, i) => rec(`r${i}`)));

    const page = await fetchReconciliations("acc-1", { page: 1, size: 2 });

    expect(page.content.map((r) => r.id)).toEqual(["r2", "r3"]);
    expect(page.totalElements).toBe(5);
    expect(page.totalPages).toBe(3);
    expect(page.pageNumber).toBe(1);
  });

  it("fetchReconciliations returns an empty page for an empty array", async () => {
    mockGet.mockResolvedValue([]);

    const page = await fetchReconciliations("acc-1");

    expect(page.content).toEqual([]);
    expect(page.totalElements).toBe(0);
    expect(page.totalPages).toBe(0);
  });

  it("fetchBankStatements returns the JSON array as-is", async () => {
    mockGet.mockResolvedValue([{ id: "s1" }, { id: "s2" }]);

    const statements = await fetchBankStatements("acc-1");

    expect(mockGet).toHaveBeenCalledWith("/api/trust-accounts/acc-1/bank-statements");
    expect(statements.map((s) => s.id)).toEqual(["s1", "s2"]);
  });
});

// O-OBS-2: the completion gate's refusal puts the refused condition in the ProblemDetail title and
// its sentences in the detail; the action returns both so the wizard banner can show the title.
describe("completeReconciliation refusal (O-OBS-2)", () => {
  // Braces: a beforeEach that returns a function runs it as teardown (it would call mockPost).
  beforeEach(() => {
    mockPost.mockReset();
  });

  it("returns the ProblemDetail title alongside the detail sentences", async () => {
    const detail =
      "Adjusted bank balance R100,00 differs from the cashbook R1 415,00 by R1 315,00 as at 18 November 2026. 1 statement line is neither matched, recorded nor excluded.";
    mockPost.mockRejectedValue(
      new MockApiError(400, detail, { title: "Reconciliation does not balance", detail })
    );

    const result = await completeReconciliation("recon-1");

    expect(mockPost).toHaveBeenCalledWith("/api/trust-reconciliations/recon-1/complete");
    expect(result).toEqual({
      success: false,
      error: detail,
      errorTitle: "Reconciliation does not balance",
    });
  });

  it("omits the title when it is the message itself (a ProblemDetail without a detail)", async () => {
    mockPost.mockRejectedValue(
      new MockApiError(409, "Reconciliation already completed", {
        title: "Reconciliation already completed",
      })
    );

    expect(await completeReconciliation("recon-1")).toEqual({
      success: false,
      error: "Reconciliation already completed",
    });
  });

  it("returns only the message for a non-API error", async () => {
    mockPost.mockRejectedValue(new Error("fetch failed"));

    expect(await completeReconciliation("recon-1")).toEqual({
      success: false,
      error: "fetch failed",
    });
  });
});
