import { describe, it, expect, vi, beforeEach } from "vitest";

// Server actions: mock the API client and the cache so the request body can be asserted.
vi.mock("server-only", () => ({}));

const mockPost = vi.fn();
vi.mock("@/lib/api", () => ({
  api: { post: (...args: unknown[]) => mockPost(...args), get: vi.fn() },
}));

const mockRevalidatePath = vi.fn();
vi.mock("next/cache", () => ({
  revalidatePath: (...args: unknown[]) => mockRevalidatePath(...args),
}));

import {
  allocateUnidentifiedDeposit,
  payUnidentifiedDepositToFund,
} from "@/app/(app)/org/[slug]/trust-accounting/other-creditors/actions";

/**
 * Phase 92 647D: the two server actions send the pinned request records
 * (`AllocateUnidentifiedRequest`, `PayToFundRequest`) and return a server refusal as `error`.
 */
describe("unidentified deposit resolution actions (647D)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("maps the allocation form to the AllocateRequest body; blanks become null", async () => {
    mockPost.mockResolvedValue({});

    const result = await allocateUnidentifiedDeposit("ud-1", {
      customerId: "cust-1",
      projectId: "mat-1",
      allocationDate: "",
      notes: "",
    });

    expect(result).toEqual({ success: true });
    expect(mockPost).toHaveBeenCalledWith("/api/unidentified-deposits/ud-1/allocate", {
      clientId: "cust-1",
      matterId: "mat-1",
      allocationDate: null,
      notes: null,
    });
    expect(mockRevalidatePath).toHaveBeenCalledWith("/", "layout");

    await allocateUnidentifiedDeposit("ud-1", {
      customerId: "cust-1",
      projectId: "mat-1",
      allocationDate: "2026-11-10",
      notes: "Proof of payment on file",
    });
    expect(mockPost).toHaveBeenLastCalledWith("/api/unidentified-deposits/ud-1/allocate", {
      clientId: "cust-1",
      matterId: "mat-1",
      allocationDate: "2026-11-10",
      notes: "Proof of payment on file",
    });
  });

  it("maps the Fund form to the PayToFundRequest body and returns a refusal as error", async () => {
    mockPost.mockRejectedValue(
      new Error("This deposit already has a pending allocation or payment")
    );

    const result = await payUnidentifiedDepositToFund("ud-2", {
      reference: "  LPFF-1  ",
      transactionDate: "2026-11-17",
      notes: "",
    });

    expect(mockPost).toHaveBeenCalledWith("/api/unidentified-deposits/ud-2/pay-to-fund", {
      reference: "LPFF-1",
      transactionDate: "2026-11-17",
      notes: null,
    });
    expect(result).toEqual({
      success: false,
      error: "This deposit already has a pending allocation or payment",
    });
    expect(mockRevalidatePath).not.toHaveBeenCalled();
  });
});
