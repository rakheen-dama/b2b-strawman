import { describe, it, expect } from "vitest";
import {
  canOfferReversal,
  reversalRuleFor,
  type TrustTransactionTypeInfo,
} from "@/lib/types/trust";

// Phase 92 646E: the local type-label map is gone (labels arrive on the DTO as `typeLabel`); the
// reversal rule comes from the registry endpoint, and a pending contra blocks Reverse (R1).

function typeInfo(code: string, reversal: TrustTransactionTypeInfo["reversal"]) {
  return {
    code,
    label: code,
    direction: "IN",
    ledger: "CLIENT",
    origin: "FIRM",
    requiresApproval: false,
    reversal,
    clientVisible: true,
  } satisfies TrustTransactionTypeInfo;
}

const TYPES: TrustTransactionTypeInfo[] = [
  typeInfo("DEPOSIT", "NEEDS_APPROVAL"),
  typeInfo("PAYMENT", "IMMEDIATE"),
  typeInfo("TRANSFER_OUT", "PAIR"),
  typeInfo("TRANSFER_IN", "VIA_PAIR"),
  typeInfo("UNIDENTIFIED_DEPOSIT", "OWNER"),
  typeInfo("REVERSAL", "NONE"),
];

function tx(overrides: Partial<Parameters<typeof canOfferReversal>[0]> = {}) {
  return {
    transactionType: "PAYMENT",
    status: "APPROVED" as const,
    reversedById: null,
    reversalPending: false,
    ...overrides,
  };
}

describe("reversalRuleFor", () => {
  it("reads the rule from the registry list", () => {
    expect(reversalRuleFor("DEPOSIT", TYPES)).toBe("NEEDS_APPROVAL");
    expect(reversalRuleFor("TRANSFER_OUT", TYPES)).toBe("PAIR");
  });

  it("treats a code the list does not carry as NONE", () => {
    expect(reversalRuleFor("INTEREST_CREDIT", TYPES)).toBe("NONE");
    expect(reversalRuleFor("DEPOSIT", [])).toBe("NONE");
  });
});

describe("canOfferReversal", () => {
  it("offers Reverse for posted rows whose rule reverses on the generic endpoint", () => {
    expect(canOfferReversal(tx({ transactionType: "PAYMENT" }), TYPES)).toBe(true);
    expect(canOfferReversal(tx({ transactionType: "DEPOSIT", status: "RECORDED" }), TYPES)).toBe(
      true
    );
    expect(
      canOfferReversal(tx({ transactionType: "TRANSFER_OUT", status: "RECORDED" }), TYPES)
    ).toBe(true);
  });

  it("refuses VIA_PAIR, OWNER and NONE rules", () => {
    for (const code of ["TRANSFER_IN", "UNIDENTIFIED_DEPOSIT", "REVERSAL", "UNKNOWN"]) {
      expect(canOfferReversal(tx({ transactionType: code, status: "RECORDED" }), TYPES)).toBe(
        false
      );
    }
  });

  it("refuses unposted and already reversed rows", () => {
    expect(canOfferReversal(tx({ status: "AWAITING_APPROVAL" }), TYPES)).toBe(false);
    expect(canOfferReversal(tx({ status: "REJECTED" }), TYPES)).toBe(false);
    expect(canOfferReversal(tx({ status: "REVERSED", reversedById: "rev-1" }), TYPES)).toBe(false);
    expect(canOfferReversal(tx({ reversedById: "rev-1" }), TYPES)).toBe(false);
  });

  // R1: the backend refuses a second reversal while a contra awaits approval
  it("refuses a row whose contra is pending approval", () => {
    expect(
      canOfferReversal(
        tx({ transactionType: "DEPOSIT", status: "RECORDED", reversalPending: true }),
        TYPES
      )
    ).toBe(false);
  });

  it("offers nothing while the registry is unavailable", () => {
    expect(canOfferReversal(tx(), [])).toBe(false);
  });
});
