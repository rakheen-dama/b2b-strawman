import { describe, it, expect } from "vitest";
import { transactionTypeLabel } from "@/lib/types/trust";

describe("transactionTypeLabel", () => {
  it("preserves the LPFF acronym", () => {
    expect(transactionTypeLabel("INTEREST_LPFF")).toBe("Interest LPFF");
  });

  it("title-cases multi-word enums", () => {
    expect(transactionTypeLabel("FEE_TRANSFER")).toBe("Fee Transfer");
    expect(transactionTypeLabel("INTEREST_CREDIT")).toBe("Interest Credit");
    expect(transactionTypeLabel("TRANSFER_IN")).toBe("Transfer In");
    expect(transactionTypeLabel("TRANSFER_OUT")).toBe("Transfer Out");
    expect(transactionTypeLabel("DISBURSEMENT_PAYMENT")).toBe("Disbursement Payment");
  });

  it("handles single-word enums", () => {
    expect(transactionTypeLabel("DEPOSIT")).toBe("Deposit");
    expect(transactionTypeLabel("PAYMENT")).toBe("Payment");
    expect(transactionTypeLabel("REFUND")).toBe("Refund");
    expect(transactionTypeLabel("REVERSAL")).toBe("Reversal");
  });
});
