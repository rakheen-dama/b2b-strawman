import { describe, it, expect } from "vitest";
// ADR-365 / Phase 92 646E (WIRE-PIN): static, relative import — a missing or renamed JSON fails
// typecheck and vitest. Path: __tests__ → frontend → repo root.
import contract from "../../backend/src/test/resources/wire/trust-contract.json";
import type { TrustTransaction, TrustTransactionTypeInfo } from "@/lib/types/trust";

const componentNames = (components: { name: string }[]) => components.map((c) => c.name);

// Each literal is typed Required<T>: a missing or extra key fails `pnpm typecheck`; the runtime
// assertion fails when the Java record changes and the literal was not updated. Literal key ORDER
// must equal the Java record's declaration order (toEqual on arrays is order-sensitive).
describe("trust wire shapes match the backend contract (ADR-365)", () => {
  it("pins exactly the trust records", () => {
    expect(Object.keys(contract).sort()).toEqual([
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
    expect(Object.keys(literal)).toEqual(componentNames(contract.TrustTransactionResponse));
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
    expect(Object.keys(literal)).toEqual(componentNames(contract.TrustTransactionTypeResponse));
  });

  it("the direction and ledger vocabularies match the backend enums", () => {
    const types = Object.fromEntries(
      contract.TrustTransactionTypeResponse.map((c) => [c.name, c.type])
    );
    expect(types.direction).toBe(
      "io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionDirection"
    );
    expect(types.ledger).toBe(
      "io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedger"
    );
  });
});
