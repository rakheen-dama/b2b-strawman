import { describe, it, expect, vi, beforeEach } from "vitest";

// Server actions: mock the API client and the cache so the request URL and body can be asserted.
vi.mock("server-only", () => ({}));

const mockPut = vi.fn();
const mockGet = vi.fn();
vi.mock("@/lib/api", () => ({
  api: {
    put: (...args: unknown[]) => mockPut(...args),
    get: (...args: unknown[]) => mockGet(...args),
  },
}));

const mockRevalidatePath = vi.fn();
vi.mock("next/cache", () => ({
  revalidatePath: (...args: unknown[]) => mockRevalidatePath(...args),
}));

import {
  fetchInterestMemos,
  fetchLockedMonths,
  saveInterestMemo,
} from "@/app/(app)/org/[slug]/trust-accounting/other-creditors/actions";

/**
 * Phase 92 650B (ADR-384): the memo server actions call the pinned endpoints — the PUT carries
 * the month in the path and the `SaveInterestMemoRequest` body, a refusal comes back as `error` —
 * and the locked months are the `yyyy-MM` of each locked list's period end.
 */
describe("interest and charges memo actions (650B)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("puts the month's memo with the SaveInterestMemoRequest body, the note trimmed", async () => {
    mockPut.mockResolvedValue({});

    const result = await saveInterestMemo("acc-1", {
      month: "2026-10",
      grossInterest: 1234.56,
      grossCharges: 115,
      chargesVat: 15,
      sourceNote: "  FNB advice  ",
    });

    expect(result).toEqual({ success: true });
    expect(mockPut).toHaveBeenCalledWith("/api/trust-accounts/acc-1/interest-memos/2026-10", {
      grossInterest: 1234.56,
      grossCharges: 115,
      chargesVat: 15,
      sourceNote: "FNB advice",
    });
    expect(mockRevalidatePath).toHaveBeenCalledWith("/", "layout");
  });

  it("returns the server's refusal as error and revalidates nothing", async () => {
    mockPut.mockRejectedValue(
      new Error("The VAT cannot be more than the gross charges it is included in.")
    );

    const result = await saveInterestMemo("acc-1", {
      month: "2026-10",
      grossInterest: 0,
      grossCharges: 10,
      chargesVat: 20,
      sourceNote: "FNB advice",
    });

    expect(result).toEqual({
      success: false,
      error: "The VAT cannot be more than the gross charges it is included in.",
    });
    expect(mockRevalidatePath).not.toHaveBeenCalled();
  });

  it("reads the year's memos and the locked months", async () => {
    mockGet.mockResolvedValueOnce([]);
    await fetchInterestMemos("acc-1", 2026);
    expect(mockGet).toHaveBeenCalledWith("/api/trust-accounts/acc-1/interest-memos?year=2026");

    mockGet.mockResolvedValueOnce([
      { id: "l-2", periodEnd: "2026-10-31" },
      { id: "l-1", periodEnd: "2026-02-28" },
    ]);
    expect(await fetchLockedMonths("acc-1")).toEqual(["2026-10", "2026-02"]);
    expect(mockGet).toHaveBeenLastCalledWith("/api/trust-accounts/acc-1/monthly-lists");
  });
});
