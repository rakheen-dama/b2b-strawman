import { describe, it, expect } from "vitest";
// ADR-365 / Phase 92 646E (WIRE-PIN): static, relative import — a missing or renamed JSON fails
// typecheck and vitest. Path: __tests__ → frontend → repo root.
import contract from "../../backend/src/test/resources/wire/trust-contract.json";
import type {
  TrustLedger,
  TrustReversalRule,
  TrustTransaction,
  TrustTransactionDirection,
  TrustTransactionTypeInfo,
} from "@/lib/types/trust";

const { records, enums } = contract;

const componentNames = (components: { name: string }[]) => components.map((c) => c.name);

// Each TS string union as an exhaustive record: `satisfies Record<Union, true>` fails
// `pnpm typecheck` on a missing or extra member, so the key list IS the union's member list.
const TS_UNIONS: Record<keyof typeof enums, string[]> = {
  TrustTransactionDirection: Object.keys({
    IN: true,
    OUT: true,
    NEUTRAL: true,
  } satisfies Record<TrustTransactionDirection, true>),
  TrustLedger: Object.keys({
    CLIENT: true,
    CLIENT_INVESTMENT: true,
    NON_CLIENT: true,
    INTER_ACCOUNT: true,
  } satisfies Record<TrustLedger, true>),
  "TrustTransactionType.Origin": Object.keys({
    FIRM: true,
    BANK: true,
  } satisfies Record<NonNullable<TrustTransactionTypeInfo["origin"]>, true>),
  "TrustTransactionType.Reversal": Object.keys({
    IMMEDIATE: true,
    NEEDS_APPROVAL: true,
    PAIR: true,
    VIA_PAIR: true,
    OWNER: true,
    NONE: true,
  } satisfies Record<TrustReversalRule, true>),
};

// Each literal is typed Required<T>: a missing or extra key fails `pnpm typecheck`; the runtime
// assertion fails when the Java record changes and the literal was not updated. Literal key ORDER
// must equal the Java record's declaration order (toEqual on arrays is order-sensitive).
describe("trust wire shapes match the backend contract (ADR-365)", () => {
  it("pins exactly the trust records", () => {
    expect(Object.keys(records).sort()).toEqual([
      "TrustTransactionResponse",
      "TrustTransactionTypeResponse",
    ]);
  });

  it("TrustTransaction ↔ TrustTransactionService.TrustTransactionResponse", () => {
    const literal: Required<TrustTransaction> = {
      id: "",
      trustAccountId: "",
      transactionType: "",
      typeLabel: "",
      direction: "IN",
      ledger: "CLIENT",
      amount: 0,
      clientId: "",
      matterId: null,
      creditorAccountId: null,
      investmentId: null,
      counterpartyClientId: null,
      invoiceId: null,
      reference: "",
      description: null,
      transactionDate: "",
      status: "RECORDED",
      postedAt: null,
      approvedBy: null,
      approvedAt: null,
      secondApprovedBy: null,
      secondApprovedAt: null,
      rejectedBy: null,
      rejectedAt: null,
      rejectionReason: null,
      reversalOf: null,
      reversedById: null,
      reversalPending: false,
      bankStatementLineId: null,
      recordedBy: "",
      createdAt: "",
      runningBalance: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.TrustTransactionResponse));
  });

  it("TrustTransactionTypeInfo ↔ TrustTransactionService.TrustTransactionTypeResponse", () => {
    const literal: Required<TrustTransactionTypeInfo> = {
      code: "",
      label: "",
      direction: "NEUTRAL",
      ledger: null,
      origin: null,
      requiresApproval: false,
      reversal: "NONE",
      clientVisible: false,
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.TrustTransactionTypeResponse));
  });

  it("pins exactly the enums the trust records carry", () => {
    expect(Object.keys(enums).sort()).toEqual(Object.keys(TS_UNIONS).sort());
  });

  it.each(Object.keys(TS_UNIONS) as (keyof typeof enums)[])(
    "the %s TS union members equal the backend enum constants",
    (name) => {
      expect([...TS_UNIONS[name]].sort()).toEqual([...enums[name]].sort());
    }
  );
});
