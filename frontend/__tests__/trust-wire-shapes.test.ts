import { describe, it, expect } from "vitest";
// ADR-365 / Phase 92 646E (WIRE-PIN): static, relative import — a missing or renamed JSON fails
// typecheck and vitest. Path: __tests__ → frontend → repo root.
import contract from "../../backend/src/test/resources/wire/trust-contract.json";
import type { TrustPositionResponse } from "@/lib/dashboard-types";
import type {
  AllocateUnidentifiedRequest,
  BankStatementLineResponse,
  BankStatementResponse,
  CreateTrustAccountRequest,
  InvestmentBasis,
  RecordFromLineResponse,
  TotalBalance,
  TrustAccount,
  TrustAccountSection,
  TrustAccountStatus,
  TrustAccountType,
  TrustCreditorBreakdown,
  TrustLedger,
  TrustReconciliationResponse,
  TrustReversalRule,
  TrustTransaction,
  TrustTransactionDirection,
  PayToFundRequest,
  PlaceInvestmentRequest,
  TrustInvestment,
  TrustTransactionTypeInfo,
  UnidentifiedDeposit,
  WithdrawInvestmentRequest,
} from "@/lib/types/trust";

const { records, enums } = contract;

const componentNames = (components: { name: string }[]) => components.map((c) => c.name);

// Each TS string union as an exhaustive record: `satisfies Record<Union, true>` fails
// `pnpm typecheck` on a missing or extra member, so the key list IS the union's member list.
const TS_UNIONS: Record<keyof typeof enums, string[]> = {
  // 648C: the trust account response's enums
  InvestmentBasis: Object.keys({
    FIRM_DISCRETION: true,
    CLIENT_INSTRUCTION: true,
  } satisfies Record<InvestmentBasis, true>),
  TrustAccountSection: Object.keys({
    S86_2: true,
    S86_3: true,
    S86_4: true,
  } satisfies Record<TrustAccountSection, true>),
  TrustAccountStatus: Object.keys({
    ACTIVE: true,
    CLOSED: true,
  } satisfies Record<TrustAccountStatus, true>),
  TrustAccountType: Object.keys({
    GENERAL: true,
    INVESTMENT: true,
    SECTION_86: true,
  } satisfies Record<TrustAccountType, true>),
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
      "AllocateRequest",
      "BankStatementLineResponse",
      "BankStatementResponse",
      "CreateTrustAccountRequest",
      "CreditorBreakdown",
      "PayToFundRequest",
      "PlaceInvestmentRequest",
      "RecordFromLineResponse",
      "TotalBalanceResponse",
      "TrustAccountResponse",
      "TrustInvestmentResponse",
      "TrustPositionResponse",
      "TrustReconciliationResponse",
      "TrustTransactionResponse",
      "TrustTransactionTypeResponse",
      "UnidentifiedDepositResponse",
      "WithdrawInvestmentRequest",
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
      chargeRecoverable: null,
      chargeVatAmount: null,
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

  it("TrustReconciliationResponse ↔ TrustReconciliationService.TrustReconciliationResponse (646C)", () => {
    const literal: Required<TrustReconciliationResponse> = {
      id: "",
      trustAccountId: "",
      periodEnd: "",
      bankStatementId: null,
      bankBalance: 0,
      outstandingDeposits: 0,
      outstandingPayments: 0,
      adjustedBankBalance: 0,
      cashbookBalance: 0,
      clientLedgerTotal: 0,
      creditorBreakdown: {
        clientLedgers: 0,
        investmentCreditors: 0,
        fundInterestAndCharges: 0,
        unidentifiedDeposits: 0,
        otherNonClient: 0,
        interAccount: 0,
        total: 0,
      },
      bankToCashbookDifference: 0,
      cashbookToCreditorsDifference: 0,
      difference: 0,
      excludedLinesTotal: 0,
      unresolvedLineCount: 0,
      isBalanced: false,
      status: "DRAFT",
      completedBy: null,
      completedAt: null,
      notes: null,
      createdAt: "",
      updatedAt: "",
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.TrustReconciliationResponse));
  });

  it("TrustCreditorBreakdown ↔ TrustReconciliationService.CreditorBreakdown (646C)", () => {
    const literal: Required<TrustCreditorBreakdown> = {
      clientLedgers: 0,
      investmentCreditors: 0,
      fundInterestAndCharges: 0,
      unidentifiedDeposits: 0,
      otherNonClient: 0,
      interAccount: 0,
      total: 0,
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.CreditorBreakdown));
  });

  it("TrustPositionResponse ↔ dashboard.dto.TrustPositionResponse (646D)", () => {
    const literal: Required<TrustPositionResponse> = {
      trustAccountId: "",
      accountName: "",
      bankName: "",
      cashbookBalance: 0,
      clientLedgerTotal: 0,
      creditorBreakdown: {
        clientLedgers: 0,
        investmentCreditors: 0,
        fundInterestAndCharges: 0,
        unidentifiedDeposits: 0,
        otherNonClient: 0,
        interAccount: 0,
        total: 0,
      },
      difference: 0,
      lastReconciliationPeriodEnd: null,
      lastReconciliationStatus: null,
      lastReconciliationBalanced: null,
      daysSinceReconciliation: null,
      pendingApprovalCount: 0,
      accounts: [],
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.TrustPositionResponse));
  });

  it("TotalBalance ↔ ClientLedgerService.TotalBalanceResponse (646D)", () => {
    const literal: Required<TotalBalance> = {
      balance: 0,
      creditorBreakdown: {
        clientLedgers: 0,
        investmentCreditors: 0,
        fundInterestAndCharges: 0,
        unidentifiedDeposits: 0,
        otherNonClient: 0,
        interAccount: 0,
        total: 0,
      },
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.TotalBalanceResponse));
  });

  it("BankStatementLineResponse ↔ TrustReconciliationService.BankStatementLineResponse (647A)", () => {
    const literal: Required<BankStatementLineResponse> = {
      id: "",
      lineNumber: 0,
      transactionDate: "",
      description: "",
      reference: null,
      amount: 0,
      runningBalance: null,
      matchStatus: "UNMATCHED",
      trustTransactionId: null,
      matchConfidence: null,
      classification: null,
      classificationLabel: null,
      suggestedClassification: null,
      createdTransactionIds: [],
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.BankStatementLineResponse));
  });

  it("BankStatementResponse ↔ TrustReconciliationService.BankStatementResponse (647A)", () => {
    const literal: Required<BankStatementResponse> = {
      id: "",
      trustAccountId: "",
      periodStart: "",
      periodEnd: "",
      openingBalance: 0,
      closingBalance: 0,
      fileName: "",
      format: "CSV",
      lineCount: 0,
      matchedCount: 0,
      status: "IMPORTED",
      importedBy: "",
      createdAt: "",
      updatedAt: "",
      lines: [],
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.BankStatementResponse));
  });

  it("RecordFromLineResponse ↔ StatementLinePostingService.RecordFromLineResponse (647A)", () => {
    const literal: Required<RecordFromLineResponse> = {
      line: {
        id: "",
        lineNumber: 0,
        transactionDate: "",
        description: "",
        reference: null,
        amount: 0,
        runningBalance: null,
        matchStatus: "RECORDED",
        trustTransactionId: null,
        matchConfidence: null,
        classification: null,
        classificationLabel: null,
        suggestedClassification: null,
        createdTransactionIds: [],
      },
      transactions: [],
      unidentifiedDepositId: null,
      investmentInterestId: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.RecordFromLineResponse));
  });

  it("UnidentifiedDeposit ↔ UnidentifiedDepositService.UnidentifiedDepositResponse (647B)", () => {
    const literal: Required<UnidentifiedDeposit> = {
      id: "",
      trustAccountId: "",
      receivedDate: "",
      bankDescription: "",
      bankReference: null,
      amount: 0,
      status: "HELD",
      ageDays: 0,
      olderThanOneYear: false,
      allocatedClientId: null,
      allocatedClientName: null,
      allocatedMatterId: null,
      allocatedMatterName: null,
      resolutionTransactionId: null,
      resolutionStatus: null,
      resolvedDate: null,
      bankStatementLineId: "",
      notes: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.UnidentifiedDepositResponse));
  });

  it("AllocateUnidentifiedRequest ↔ UnidentifiedDepositService.AllocateRequest (647D)", () => {
    const literal: Required<AllocateUnidentifiedRequest> = {
      clientId: "",
      matterId: "",
      allocationDate: null,
      notes: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.AllocateRequest));
  });

  it("PayToFundRequest ↔ UnidentifiedDepositService.PayToFundRequest (647D)", () => {
    const literal: Required<PayToFundRequest> = {
      reference: "",
      transactionDate: "",
      notes: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.PayToFundRequest));
  });

  it("TrustAccount ↔ TrustAccountService.TrustAccountResponse (648C)", () => {
    const literal: Required<TrustAccount> = {
      id: "",
      accountName: "",
      bankName: "",
      branchCode: "",
      accountNumber: "",
      accountType: "GENERAL",
      investmentBasis: null,
      section: "S86_2",
      isPrimary: false,
      requireDualApproval: false,
      paymentApprovalThreshold: null,
      status: "ACTIVE",
      openedDate: "",
      closedDate: null,
      notes: null,
      createdAt: "",
      updatedAt: "",
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.TrustAccountResponse));
  });

  it("CreateTrustAccountRequest ↔ TrustAccountService.CreateTrustAccountRequest (648C)", () => {
    const literal: Required<CreateTrustAccountRequest> = {
      accountName: "",
      bankName: "",
      branchCode: "",
      accountNumber: "",
      accountType: "GENERAL",
      isPrimary: false,
      requireDualApproval: false,
      paymentApprovalThreshold: null,
      openedDate: "",
      notes: "",
      investmentBasis: "FIRM_DISCRETION",
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.CreateTrustAccountRequest));
  });

  it("TrustInvestment ↔ TrustInvestmentService.TrustInvestmentResponse (648A)", () => {
    const literal: Required<TrustInvestment> = {
      id: "",
      trustAccountId: "",
      investmentAccountId: "",
      investmentAccountName: null,
      clientId: null,
      clientName: null,
      matterId: null,
      institution: "",
      accountNumber: "",
      principal: 0,
      interestRate: 0,
      depositDate: "",
      maturityDate: null,
      interestEarned: 0,
      balance: 0,
      status: "ACTIVE",
      awaitingApproval: false,
      withdrawalDate: null,
      withdrawalAmount: null,
      depositTransactionId: null,
      withdrawalTransactionId: null,
      notes: null,
      investmentBasis: "FIRM_DISCRETION",
      createdAt: "",
      updatedAt: "",
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.TrustInvestmentResponse));
  });

  it("PlaceInvestmentRequest ↔ TrustInvestmentService.PlaceInvestmentRequest (648A)", () => {
    const literal: Required<PlaceInvestmentRequest> = {
      investmentAccountId: "",
      clientId: null,
      matterId: null,
      institution: "",
      accountNumber: "",
      principal: 0,
      interestRate: 0,
      depositDate: "",
      maturityDate: null,
      notes: null,
      investmentBasis: "FIRM_DISCRETION",
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.PlaceInvestmentRequest));
  });

  it("WithdrawInvestmentRequest ↔ TrustInvestmentService.WithdrawInvestmentRequest (648A)", () => {
    const literal: Required<WithdrawInvestmentRequest> = { withdrawalDate: "" };
    expect(Object.keys(literal)).toEqual(componentNames(records.WithdrawInvestmentRequest));
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
