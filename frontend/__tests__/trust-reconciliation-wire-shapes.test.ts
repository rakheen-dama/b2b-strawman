import { describe, it, expect, vi, beforeEach } from "vitest";

// G89-F17 (ADR-365 wire pin): TrustReconciliationController returns a plain JSON array
// (`ResponseEntity<List<…>>`) for both list endpoints, not a Spring `Page` envelope. Reading
// `result.page.*` threw, so the Reconciliation list always showed "Failed to load reconciliations".
// These tests drive the real server actions against a mocked `api.get` that returns an array.

vi.mock("server-only", () => ({}));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }));

vi.mock("@/lib/api", () => ({
  api: { get: (...args: unknown[]) => mockGet(...args), post: vi.fn(), put: vi.fn() },
  API_BASE: "http://backend",
  getAuthFetchOptions: vi.fn(),
}));

import {
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
