import { describe, it, expect } from "vitest";
// ADR-365 / Phase 92 646E (WIRE-PIN): static, relative import — a missing or renamed JSON fails
// typecheck and vitest. Path: __tests__ → frontend → repo root.
import contract from "../../backend/src/test/resources/wire/trust-contract.json";
import type {
  TrustIntegrityFlagCount,
  TrustIntegritySummary,
  TrustPositionResponse,
} from "@/lib/dashboard-types";
import type {
  AllocateUnidentifiedRequest,
  BankPosition,
  BankStatementLineResponse,
  BankStatementResponse,
  ClientLedgerCard,
  CreateTrustAccountRequest,
  CreditorLine,
  CreditorLineKind,
  CreditorMatterBalance,
  ExpectedStatementRequest,
  FirmIntegrityReport,
  FlagSubject,
  IntegrityFlag,
  InterestMemo,
  InterestPresentation,
  InvestmentBasis,
  InvestmentInterest,
  MonthlyList,
  MonthlyListAdjustmentEntry,
  MonthlyListAdjustments,
  MonthlyListSummary,
  RecordFromLineRequest,
  RecordFromLineResponse,
  RecordInvestmentInterestRequest,
  SaveInterestMemoRequest,
  StatementLineClassification,
  TotalBalance,
  TrustAccount,
  TrustAccountSection,
  TrustAccountStatus,
  TrustAccountType,
  TrustCreditorBreakdown,
  TrustIntegrityReport,
  TrustLedger,
  TrustPosition,
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
  // 650A: what a Rule 54.15 creditor line is a balance of
  "CreditorLine.Kind": Object.keys({
    CLIENT: true,
    CLIENT_INVESTMENT: true,
    FUND: true,
    UNIDENTIFIED: true,
    INTER_ACCOUNT: true,
  } satisfies Record<CreditorLineKind, true>),
  // 648B: the interest entry's presentation and the record-from-statement classification
  InterestPresentation: Object.keys({
    GROSS: true,
    NET: true,
  } satisfies Record<InterestPresentation, true>),
  StatementLineClassification: Object.keys({
    FUND_INTEREST: true,
    FUND_SWEEP: true,
    BANK_CHARGE: true,
    CHARGE_REFUND: true,
    UNIDENTIFIED_DEPOSIT: true,
    INVESTMENT_INTEREST: true,
  } satisfies Record<StatementLineClassification, true>),
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
      "AdjustmentEntry",
      "AdjustmentsSinceLock",
      "AllocateRequest",
      "BankPosition",
      "BankStatementLineResponse",
      "BankStatementResponse",
      "ClientLedgerCardResponse",
      "CreateTrustAccountRequest",
      "CreditorBreakdown",
      "CreditorLine",
      "ExpectedStatementRequest",
      "FirmIntegrityReport",
      "FlagCount",
      "FlagSubject",
      "IntegrityFlag",
      "IntegritySummary",
      "InterestMemoResponse",
      "InvestmentInterestResponse",
      "MatterBalance",
      "MonthlyListResponse",
      "MonthlyListSummary",
      "PayToFundRequest",
      "PlaceInvestmentRequest",
      "RecordFromLineRequest",
      "RecordFromLineResponse",
      "RecordInvestmentInterestRequest",
      "SaveInterestMemoRequest",
      "TotalBalanceResponse",
      "TrustAccountResponse",
      "TrustIntegrityReport",
      "TrustInvestmentResponse",
      "TrustPosition",
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
      integrityFlags: [],
      status: "DRAFT",
      completedBy: null,
      completedAt: null,
      monthlyListId: null,
      notes: null,
      createdAt: "",
      updatedAt: "",
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.TrustReconciliationResponse));
  });

  it("CreditorLine ↔ books.CreditorLine (650A)", () => {
    const literal: Required<CreditorLine> = {
      kind: "CLIENT",
      clientId: null,
      name: null,
      matters: [],
      balance: 0,
      unidentifiedDepositId: null,
      receivedDate: null,
      bankDescription: null,
      totalDeposits: null,
      totalPayments: null,
      totalFeeTransfers: null,
      lastTransactionDate: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.CreditorLine));
  });

  it("CreditorMatterBalance ↔ books.CreditorLine.MatterBalance (650A)", () => {
    const literal: Required<CreditorMatterBalance> = { matterId: null, name: null, balance: 0 };
    expect(Object.keys(literal)).toEqual(componentNames(records.MatterBalance));
  });

  it("MonthlyListSummary ↔ TrustMonthlyListService.MonthlyListSummary (650A)", () => {
    const literal: Required<MonthlyListSummary> = {
      id: "",
      trustAccountId: "",
      periodEnd: "",
      reconciliationId: "",
      adjustedBankBalance: 0,
      cashbookBalance: 0,
      creditorTotal: 0,
      difference: 0,
      lockedBy: "",
      lockedByName: null,
      lockedAt: "",
      adjustmentCount: 0,
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.MonthlyListSummary));
  });

  it("MonthlyListAdjustmentEntry ↔ TrustMonthlyListService.AdjustmentEntry (650A)", () => {
    const literal: Required<MonthlyListAdjustmentEntry> = {
      transactionId: "",
      transactionDate: "",
      postedAt: "",
      transactionType: "",
      typeLabel: "",
      ledger: "CLIENT",
      clientId: null,
      clientName: null,
      reference: null,
      description: null,
      cashbookEffect: 0,
      ledgerEffect: 0,
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.AdjustmentEntry));
  });

  it("MonthlyListAdjustments ↔ TrustMonthlyListService.AdjustmentsSinceLock (650A)", () => {
    const literal: Required<MonthlyListAdjustments> = {
      entries: [],
      cashbook: 0,
      clientLedgers: 0,
      investmentCreditors: 0,
      fundLedger: 0,
      unidentifiedDeposits: 0,
      interAccount: 0,
      liveCreditorTotal: 0,
      changeInCreditorTotal: 0,
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.AdjustmentsSinceLock));
  });

  it("MonthlyList ↔ TrustMonthlyListService.MonthlyListResponse (650A)", () => {
    const literal: Required<MonthlyList> = {
      id: "",
      trustAccountId: "",
      trustAccountName: null,
      periodEnd: "",
      reconciliationId: "",
      bankBalance: 0,
      outstandingDeposits: 0,
      outstandingPayments: 0,
      adjustedBankBalance: 0,
      cashbookBalance: 0,
      clientLedgerTotal: 0,
      investmentCreditorTotal: 0,
      fundLedgerBalance: 0,
      unidentifiedDepositTotal: 0,
      interAccountBalance: 0,
      creditorTotal: 0,
      difference: 0,
      lines: [],
      integrityFlags: [],
      lockedBy: "",
      lockedByName: null,
      lockedAt: "",
      adjustmentsSinceLock: {
        entries: [],
        cashbook: 0,
        clientLedgers: 0,
        investmentCreditors: 0,
        fundLedger: 0,
        unidentifiedDeposits: 0,
        interAccount: 0,
        liveCreditorTotal: 0,
        changeInCreditorTotal: 0,
      },
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.MonthlyListResponse));
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
      integrity: { balanced: true, flags: [] },
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.TrustPositionResponse));
  });

  it("TrustIntegritySummary ↔ TrustPositionResponse.IntegritySummary (649C)", () => {
    const literal: Required<TrustIntegritySummary> = { balanced: true, flags: [] };
    expect(Object.keys(literal)).toEqual(componentNames(records.IntegritySummary));
  });

  it("TrustIntegrityFlagCount ↔ TrustPositionResponse.FlagCount (649C)", () => {
    const literal: Required<TrustIntegrityFlagCount> = {
      code: "FUND_LEDGER_IN_DEBIT",
      severity: "WARNING",
      count: 1,
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.FlagCount));
  });

  it("IntegrityFlag ↔ books.IntegrityFlag (649C)", () => {
    const literal: Required<IntegrityFlag> = {
      code: "BANK_DIFFERENCE",
      severity: "BLOCKING",
      message: "",
      amount: null,
      subjects: [],
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.IntegrityFlag));
  });

  it("FlagSubject ↔ books.FlagSubject (649C)", () => {
    const literal: Required<FlagSubject> = { kind: "CLIENT", id: null, name: null, amount: null };
    expect(Object.keys(literal)).toEqual(componentNames(records.FlagSubject));
  });

  const position: Required<TrustPosition> = {
    trustAccountId: "",
    asAt: "",
    cashbook: 0,
    clientLedgers: 0,
    investmentCreditors: 0,
    fundLedger: 0,
    unidentifiedDeposits: 0,
    otherNonClient: 0,
    interAccount: 0,
    creditorTotal: 0,
    difference: 0,
  };

  it("TrustPosition ↔ TrustBooksService.TrustPosition (649C)", () => {
    expect(Object.keys(position)).toEqual(componentNames(records.TrustPosition));
  });

  it("BankPosition ↔ TrustBooksService.BankPosition (649C)", () => {
    const literal: Required<BankPosition> = {
      bankBalance: 0,
      outstandingDeposits: 0,
      outstandingPayments: 0,
      adjustedBankBalance: 0,
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.BankPosition));
  });

  it("TrustIntegrityReport ↔ TrustIntegrityService.TrustIntegrityReport (649C)", () => {
    const literal: Required<TrustIntegrityReport> = {
      trustAccountId: "",
      asAt: "",
      position,
      bank: null,
      balanced: true,
      flags: [],
      lastCompletedReconciliationId: null,
      lastReconciledThrough: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.TrustIntegrityReport));
  });

  it("FirmIntegrityReport ↔ TrustIntegrityService.FirmIntegrityReport (649C)", () => {
    const literal: Required<FirmIntegrityReport> = {
      asAt: "",
      balanced: true,
      cashbook: 0,
      clientLedgers: 0,
      investmentCreditors: 0,
      fundLedger: 0,
      unidentifiedDeposits: 0,
      otherNonClient: 0,
      interAccount: 0,
      creditorTotal: 0,
      difference: 0,
      flags: [],
      accounts: [],
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.FirmIntegrityReport));
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
      // FU-6b: who, when and why a statement was retired; the statement that replaced it
      retiredAt: null,
      retiredBy: null,
      retiredReason: null,
      replacedByStatementId: null,
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

  it("TrustAccount ↔ TrustAccountService.TrustAccountResponse (648C, 650D)", () => {
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
      lockedThrough: null,
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
      grossInterest: 0,
      fundShare: 0,
      netInterest: 0,
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

  it("InvestmentInterest ↔ TrustInvestmentService.InvestmentInterestResponse (648B)", () => {
    const literal: Required<InvestmentInterest> = {
      id: "",
      investmentId: "",
      entryDate: "",
      grossAmount: 0,
      fundShareAmount: 0,
      netAmount: 0,
      presentation: "NET",
      status: "POSTED",
      netTransactionId: "",
      fundShareTransactionId: null,
      bankStatementLineId: null,
      clientTaxReference: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.InvestmentInterestResponse));
  });

  it("RecordInvestmentInterestRequest ↔ TrustInvestmentService.RecordInvestmentInterestRequest (648B)", () => {
    const literal: Required<RecordInvestmentInterestRequest> = {
      entryDate: "",
      grossAmount: 0,
      fundShareAmount: null,
      presentation: "GROSS",
      bankStatementLineId: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.RecordInvestmentInterestRequest));
  });

  it("RecordFromLineRequest ↔ StatementLinePostingService.RecordFromLineRequest (648B)", () => {
    const literal: Required<RecordFromLineRequest> = {
      classification: "INVESTMENT_INTEREST",
      chargeRecoverable: null,
      chargeVatAmount: null,
      investmentId: null,
      grossAmount: null,
      fundShareAmount: null,
      presentation: null,
      notes: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.RecordFromLineRequest));
  });

  it("ClientLedgerCard ↔ ClientLedgerService.ClientLedgerCardResponse (648E)", () => {
    const literal: Required<ClientLedgerCard> = {
      id: "",
      trustAccountId: "",
      clientId: "",
      clientName: "",
      balance: 0,
      totalDeposits: 0,
      totalPayments: 0,
      totalFeeTransfers: 0,
      totalRefunds: 0,
      totalTransfersIn: 0,
      totalTransfersOut: 0,
      totalInvestmentPlacements: 0,
      totalInvestmentReturns: 0,
      investedBalance: 0,
      lastTransactionDate: null,
      createdAt: "",
      updatedAt: "",
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.ClientLedgerCardResponse));
  });

  it("InterestMemo ↔ TrustInterestMemoService.InterestMemoResponse (650B)", () => {
    const literal: Required<InterestMemo> = {
      id: "",
      trustAccountId: "",
      periodMonth: "",
      grossInterest: 0,
      grossCharges: 0,
      chargesVat: 0,
      sourceNote: "",
      recordedBy: "",
      recordedByName: null,
      createdAt: "",
      updatedAt: "",
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.InterestMemoResponse));
  });

  it("SaveInterestMemoRequest ↔ TrustInterestMemoService.SaveInterestMemoRequest (650B)", () => {
    const literal: Required<SaveInterestMemoRequest> = {
      grossInterest: 0,
      grossCharges: 0,
      chargesVat: 0,
      sourceNote: "",
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.SaveInterestMemoRequest));
  });

  it("ExpectedStatementRequest ↔ TrustReconciliationService.ExpectedStatementRequest (652C)", () => {
    const literal: Required<ExpectedStatementRequest> = {
      expectedBankStatementId: "",
    };
    expect(Object.keys(literal)).toEqual(componentNames(records.ExpectedStatementRequest));
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
