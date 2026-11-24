import { describe, it, expect } from "vitest";
// ADR-365 / P-17: static, relative import — a missing or renamed JSON fails typecheck and vitest.
// Path: __tests__ → frontend → repo root.
import contract from "../../backend/src/test/resources/wire/take-on-contract.json";
import type {
  TakeOnAttestationRequest,
  TakeOnAttestationStatement,
  TakeOnBatch,
  TakeOnBatchSummary,
  TakeOnCommitRequest,
  TakeOnCounts,
  TakeOnDatasetCatalogue,
  TakeOnField,
  TakeOnGoLiveAcknowledgeRequest,
  TakeOnGoLiveItem,
  TakeOnMapping,
  TakeOnMappingRequest,
  TakeOnParseOptions,
  TakeOnPortalContactsResult,
  TakeOnPosition,
  TakeOnPositionRequest,
  TakeOnPreview,
  TakeOnPreviewRow,
  TakeOnReport,
  TakeOnReportAccount,
  TakeOnReportCreditorLine,
  TakeOnReportCreditorList,
  TakeOnReportFirm,
  TakeOnReportInvestment,
  TakeOnReportMatter,
  TakeOnReportOutstanding,
  TakeOnReportPosition,
  TakeOnReportSignOff,
  TakeOnReportSnapshot,
  TakeOnReportSource,
  TakeOnReportSummary,
  TakeOnReportUnidentified,
  TakeOnRow,
  TakeOnRowMessage,
  TakeOnSectionCatalogue,
  TakeOnSheetName,
  TakeOnSheetRequest,
  TakeOnSignOff,
  TakeOnSignOffDeclaration,
  TakeOnSignOffQueueItem,
  TakeOnSignOffRequest,
  TakeOnSignOffReview,
  TakeOnSource,
  TakeOnTrustDeficit,
  TakeOnTrustEq1,
  TakeOnTrustEq2,
  TakeOnTrustEquation,
  TakeOnTrustInvestmentsTie,
  TakeOnTrustItemsTie,
  TakeOnTrustMessage,
  TakeOnTrustSummary,
  TakeOnUploadResult,
} from "@/lib/types/take-on";
import type {
  ClientFicaAttestation,
  ClientListRow,
  FicaStatus,
  UpdateComplianceSettingsRequest,
} from "@/lib/types";

const componentNames = (components: { name: string }[]) => components.map((c) => c.name);

const COUNTS: Required<TakeOnCounts> = {
  total: 0,
  valid: 0,
  error: 0,
  duplicate: 0,
  blocks: 0,
  created: 0,
  skipped: 0,
  failed: 0,
};
const PARSE_OPTIONS: Required<TakeOnParseOptions> = {
  dateFormat: "",
  decimalSeparator: "POINT",
  delimiter: null,
  encoding: "",
};
const BATCH: Required<TakeOnBatch> = {
  id: "",
  dataset: "CLIENTS",
  status: "UPLOADED",
  name: null,
  cutoverDate: null,
  trustAccountId: null,
  sources: [],
  counts: COUNTS,
  validationHash: null,
  validatedAt: null,
  allowedActions: [],
  createdAt: "",
  cancelledAt: null,
  commitStartedAt: null,
  completedAt: null,
  lastError: null,
  signOffs: [],
  attestationStatement: null,
  summary: null,
  midMonthAcknowledged: false,
  position: null,
  trustAccountName: null,
  reportId: null,
};

// Each literal is typed Required<T>: a missing or extra key fails `pnpm typecheck`; the runtime
// assertion fails when the Java record changes and the literal was not updated. Literal key ORDER
// must equal the Java record's declaration order (toEqual on arrays is order-sensitive).
describe("take-on wire shapes match the backend contract (ADR-365, P-17)", () => {
  it("pins exactly the 56 records of 653A, 655A, 655B, 656A, 657B and 657C", () => {
    expect(Object.keys(contract).sort()).toEqual([
      "AccountHeader",
      "ClientFicaAttestationResponse",
      "ClientListRow",
      "CreditorLineEntry",
      "CreditorListSection",
      "FicaStatusResponse",
      "FirmHeader",
      "InvestmentLine",
      "MatterEntry",
      "OutstandingLine",
      "ReconciledPosition",
      "SignOffSection",
      "SourceFile",
      "TakeOnAttestationRequest",
      "TakeOnAttestationStatementResponse",
      "TakeOnBatchResponse",
      "TakeOnBatchSummaryResponse",
      "TakeOnCommitRequest",
      "TakeOnCountsResponse",
      "TakeOnDatasetResponse",
      "TakeOnFieldResponse",
      "TakeOnGoLiveAcknowledgeRequest",
      "TakeOnGoLiveItemResponse",
      "TakeOnMappingRequest",
      "TakeOnMappingResponse",
      "TakeOnParseOptionsResponse",
      "TakeOnPortalContactsResponse",
      "TakeOnPositionRequest",
      "TakeOnPositionResponse",
      "TakeOnPreviewResponse",
      "TakeOnPreviewRow",
      "TakeOnReportResponse",
      "TakeOnReportSnapshot",
      "TakeOnReportSummaryResponse",
      "TakeOnRowMessage",
      "TakeOnRowResponse",
      "TakeOnSectionResponse",
      "TakeOnSheetNameResponse",
      "TakeOnSheetRequest",
      "TakeOnSignOffDeclarationResponse",
      "TakeOnSignOffQueueItem",
      "TakeOnSignOffRequest",
      "TakeOnSignOffResponse",
      "TakeOnSignOffReviewResponse",
      "TakeOnSourceResponse",
      "TakeOnUploadResponse",
      "TrustDeficit",
      "TrustEq1",
      "TrustEq2",
      "TrustEquationSummary",
      "TrustInvestmentsTie",
      "TrustItemsTie",
      "TrustPositionMessage",
      "TrustPositionSummary",
      "UnidentifiedLine",
      "UpdateComplianceSettingsRequest",
    ]);
  });

  it("TakeOnField ↔ TakeOnFieldResponse", () => {
    const literal: Required<TakeOnField> = {
      key: "",
      label: "",
      type: "TEXT",
      required: false,
      maxLength: null,
      allowedValues: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnFieldResponse));
  });

  it("TakeOnSectionCatalogue ↔ TakeOnSectionResponse", () => {
    const literal: Required<TakeOnSectionCatalogue> = { section: "MAIN", label: "", fields: [] };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnSectionResponse));
  });

  it("TakeOnDatasetCatalogue ↔ TakeOnDatasetResponse", () => {
    const literal: Required<TakeOnDatasetCatalogue> = {
      dataset: "CLIENTS",
      label: "",
      requiresCutoverDate: false,
      requiresTrustAccount: false,
      customFieldEntityType: null,
      sections: [],
      completedBatches: 0,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnDatasetResponse));
  });

  it("TakeOnParseOptions ↔ TakeOnParseOptionsResponse", () => {
    expect(Object.keys(PARSE_OPTIONS)).toEqual(componentNames(contract.TakeOnParseOptionsResponse));
  });

  it("TakeOnMapping ↔ TakeOnMappingResponse", () => {
    const literal: Required<TakeOnMapping> = {
      targets: {},
      customFields: {},
      parked: [],
      defaults: {},
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnMappingResponse));
  });

  it("TakeOnSheetName ↔ TakeOnSheetNameResponse", () => {
    const literal: Required<TakeOnSheetName> = { name: "", hidden: false };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnSheetNameResponse));
  });

  it("TakeOnSource ↔ TakeOnSourceResponse", () => {
    const literal: Required<TakeOnSource> = {
      id: "",
      section: "MAIN",
      fileName: "",
      fileFormat: "CSV",
      fileSize: 0,
      sheetNames: [],
      sheetName: null,
      headerRow: 1,
      parseOptions: PARSE_OPTIONS,
      mapping: null,
      mapped: false,
      rowCount: 0,
      malformedCount: 0,
      blankSkipped: 0,
      purged: false,
      uploadedAt: "",
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnSourceResponse));
  });

  it("TakeOnCounts ↔ TakeOnCountsResponse", () => {
    expect(Object.keys(COUNTS)).toEqual(componentNames(contract.TakeOnCountsResponse));
  });

  it("TakeOnBatch ↔ TakeOnBatchResponse", () => {
    expect(Object.keys(BATCH)).toEqual(componentNames(contract.TakeOnBatchResponse));
  });

  it("TakeOnBatchSummary ↔ TakeOnBatchSummaryResponse", () => {
    const literal: Required<TakeOnBatchSummary> = {
      id: "",
      dataset: "CLIENTS",
      status: "UPLOADED",
      name: null,
      cutoverDate: null,
      totalRows: 0,
      validRows: 0,
      errorRows: 0,
      createdByName: null,
      createdAt: "",
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnBatchSummaryResponse));
  });

  it("TakeOnUploadResult ↔ TakeOnUploadResponse", () => {
    const literal: Required<TakeOnUploadResult> = {
      batch: BATCH,
      sourceId: "",
      headers: [],
      warnings: [],
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnUploadResponse));
  });

  it("TakeOnPreviewRow ↔ TakeOnPreviewRow", () => {
    const literal: Required<TakeOnPreviewRow> = { rowNumber: 0, cells: [] };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnPreviewRow));
  });

  it("TakeOnPreview ↔ TakeOnPreviewResponse", () => {
    const literal: Required<TakeOnPreview> = {
      sourceId: "",
      headers: [],
      rows: [],
      detectedOptions: PARSE_OPTIONS,
      sheets: [],
      sheetName: null,
      headerRow: 1,
      suggestedTargets: {},
      warnings: [],
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnPreviewResponse));
  });

  it("TakeOnRowMessage ↔ TakeOnRowMessage", () => {
    const literal: Required<TakeOnRowMessage> = { code: "", field: null, message: "" };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnRowMessage));
  });

  it("TakeOnRow ↔ TakeOnRowResponse", () => {
    const literal: Required<TakeOnRow> = {
      id: "",
      section: "MAIN",
      rowNumber: 0,
      sourceRef: null,
      status: "PENDING",
      block: false,
      values: null,
      errors: [],
      warnings: [],
      duplicateOfId: null,
      failureReason: null,
      resultEntityId: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnRowResponse));
  });

  it("TakeOnMappingRequest ↔ TakeOnMappingRequest", () => {
    const literal: Required<TakeOnMappingRequest> = {
      targets: {},
      customFields: {},
      parked: [],
      defaults: {},
      parseOptions: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnMappingRequest));
  });

  it("TakeOnSheetRequest ↔ TakeOnSheetRequest", () => {
    const literal: Required<TakeOnSheetRequest> = { sheetName: null, headerRow: 1 };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnSheetRequest));
  });

  it("TakeOnPositionRequest ↔ TakeOnPositionRequest", () => {
    const literal: Required<TakeOnPositionRequest> = {
      cutoverDate: "",
      bankBalance: null,
      outstandingDepositsTotal: null,
      outstandingPaymentsTotal: null,
      cashbookBalance: null,
      fundLedgerBalance: null,
      investedTotal: null,
      midMonthAcknowledged: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnPositionRequest));
  });

  // 657B: the trust position (P-11) and the trust batch's `summary` records (A.4). The summary is
  // a Map on the wire, so these pins are what keeps the frontend mirror of it honest.
  it("TakeOnPosition ↔ TakeOnPositionResponse", () => {
    const literal: Required<TakeOnPosition> = {
      bankBalance: null,
      outstandingDepositsTotal: null,
      outstandingPaymentsTotal: null,
      cashbookBalance: null,
      fundLedgerBalance: null,
      investedTotal: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnPositionResponse));
  });

  it("TakeOnTrustMessage ↔ TrustPositionMessage", () => {
    const literal: Required<TakeOnTrustMessage> = { code: "", message: "" };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TrustPositionMessage));
  });

  it("TakeOnTrustEq1 ↔ TrustEq1", () => {
    const literal: Required<TakeOnTrustEq1> = {
      bankBalance: "",
      plusOutstandingDeposits: "",
      lessOutstandingPayments: "",
      adjustedBank: "",
      cashbook: "",
      difference: "",
      holds: true,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TrustEq1));
  });

  it("TakeOnTrustItemsTie ↔ TrustItemsTie", () => {
    const literal: Required<TakeOnTrustItemsTie> = {
      listedDeposits: "",
      listedPayments: "",
      outstandingDeposits: "",
      outstandingPayments: "",
      difference: "",
      holds: true,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TrustItemsTie));
  });

  it("TakeOnTrustEq2 ↔ TrustEq2", () => {
    const literal: Required<TakeOnTrustEq2> = {
      clientLedgers: "",
      fundLedger: "",
      unidentified: "",
      placedInSection86_3: "",
      creditors: "",
      cashbook: "",
      difference: "",
      holds: true,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TrustEq2));
  });

  it("TakeOnTrustInvestmentsTie ↔ TrustInvestmentsTie", () => {
    const literal: Required<TakeOnTrustInvestmentsTie> = {
      perConfirmations: "",
      listed: "",
      difference: "",
      holds: true,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TrustInvestmentsTie));
  });

  it("TakeOnTrustDeficit ↔ TrustDeficit", () => {
    const literal: Required<TakeOnTrustDeficit> = {
      trustCreditors: "",
      moneyHeld: "",
      difference: "",
      inDeficit: false,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TrustDeficit));
  });

  it("TakeOnTrustEquation ↔ TrustEquationSummary", () => {
    const literal: Required<TakeOnTrustEquation> = {
      eq1: {} as TakeOnTrustEq1,
      itemsTie: {} as TakeOnTrustItemsTie,
      eq2: {} as TakeOnTrustEq2,
      investments: {} as TakeOnTrustInvestmentsTie,
      deficit: {} as TakeOnTrustDeficit,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TrustEquationSummary));
  });

  it("TakeOnTrustSummary ↔ TrustPositionSummary", () => {
    const literal: Required<TakeOnTrustSummary> = {
      accountErrors: [],
      blocks: [],
      warnings: [],
      flags: [],
      equation: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TrustPositionSummary));
  });

  it("TakeOnSignOff ↔ TakeOnSignOffResponse", () => {
    const literal: Required<TakeOnSignOff> = {
      id: "",
      kind: "FICA_ATTESTATION",
      memberName: "",
      roleName: "",
      statementVersion: "",
      signedAt: "",
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnSignOffResponse));
  });

  it("TakeOnAttestationStatement ↔ TakeOnAttestationStatementResponse", () => {
    const literal: Required<TakeOnAttestationStatement> = { version: "", text: "" };
    expect(Object.keys(literal)).toEqual(
      componentNames(contract.TakeOnAttestationStatementResponse)
    );
  });

  it("TakeOnAttestationRequest ↔ TakeOnAttestationRequest", () => {
    const literal: Required<TakeOnAttestationRequest> = {
      validationHash: "",
      statementVersion: "",
      defaults: {},
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnAttestationRequest));
  });

  it("TakeOnCommitRequest ↔ TakeOnCommitRequest", () => {
    const literal: Required<TakeOnCommitRequest> = { validationHash: "" };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnCommitRequest));
  });

  it("TakeOnPortalContactsResult ↔ TakeOnPortalContactsResponse", () => {
    const literal: Required<TakeOnPortalContactsResult> = {
      batch: BATCH,
      createdCount: 0,
      skippedCount: 0,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnPortalContactsResponse));
  });

  // 655B (C-10, D-144): the FICA wire records the review queue reads.
  it("FicaStatus ↔ FicaStatusResponse", () => {
    const literal: Required<FicaStatus> = {
      clientId: "",
      status: "ATTESTED",
      lastVerifiedAt: null,
      requestId: null,
      checklistInstanceId: null,
      reviewReasons: [],
      attestationId: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.FicaStatusResponse));
  });

  it("ClientFicaAttestation ↔ ClientFicaAttestationResponse", () => {
    const literal: Required<ClientFicaAttestation> = {
      id: "",
      clientId: "",
      status: "ACTIVE",
      takeOnBatchId: "",
      cddCompletedDate: "",
      lastReviewDate: null,
      riskRating: "UNRATED",
      ficaClientType: "NATURAL_PERSON",
      beneficialOwnershipStatus: "DONE",
      screeningStatus: "NOT_SCREENED",
      screeningDate: null,
      sourceOfFundsStatus: "NOT_REQUIRED",
      cddRecordsLocation: "",
      sourceSystemRef: null,
      nextReviewDate: null,
      attestedBy: "",
      attestedByName: "",
      attestedByRole: "",
      statementVersion: "",
      attestedAt: "",
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.ClientFicaAttestationResponse));
  });

  it("TakeOnGoLiveItem ↔ TakeOnGoLiveItemResponse", () => {
    const literal: Required<TakeOnGoLiveItem> = {
      itemKey: "TRUST_RECORDS_RETAINED",
      text: "",
      acknowledged: false,
      acknowledgedBy: null,
      acknowledgedByName: null,
      acknowledgedAt: null,
      note: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnGoLiveItemResponse));
  });

  it("TakeOnGoLiveAcknowledgeRequest ↔ TakeOnGoLiveAcknowledgeRequest", () => {
    const literal: Required<TakeOnGoLiveAcknowledgeRequest> = { note: null };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnGoLiveAcknowledgeRequest));
  });

  it("ClientListRow ↔ ClientListRow", () => {
    const literal: Required<ClientListRow> = {
      id: "",
      name: "",
      email: "",
      phone: null,
      clientType: null,
      lifecycleStatus: null,
      status: "ACTIVE",
      createdAt: "",
      lifecycleStatusChangedAt: null,
      ficaStatus: "NOT_STARTED",
      ficaLastVerifiedAt: null,
      ficaReviewReasons: [],
      ficaRiskRating: null,
      ficaNextReviewDate: null,
      ficaRecordsLocation: null,
      outstandingFeeNoteTotal: null,
      outstandingFeeNoteCount: 0,
      currency: "",
      trustBalance: null,
      openMatterCount: 0,
      completenessPercent: null,
      tags: [],
      customFields: {},
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.ClientListRow));
  });

  it("UpdateComplianceSettingsRequest ↔ UpdateComplianceSettingsRequest", () => {
    const literal: Required<UpdateComplianceSettingsRequest> = {
      dormancyThresholdDays: 0,
      dataRequestDeadlineDays: 0,
      ficaReviewMonthsLow: 0,
      ficaReviewMonthsMedium: 0,
      ficaReviewMonthsHigh: 0,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.UpdateComplianceSettingsRequest));
  });

  // 657C (R-7): the trust sign-off, the take-on report and its snapshot records.
  it("TakeOnSignOffQueueItem ↔ TakeOnSignOffQueueItem", () => {
    const literal: Required<TakeOnSignOffQueueItem> = {
      batchId: "",
      trustAccountId: "",
      trustAccountName: null,
      cutoverDate: "",
      preparedByName: null,
      validatedAt: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnSignOffQueueItem));
  });

  it("TakeOnSignOffDeclaration ↔ TakeOnSignOffDeclarationResponse", () => {
    const literal: Required<TakeOnSignOffDeclaration> = { version: "", text: "" };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnSignOffDeclarationResponse));
  });

  it("TakeOnSignOffRequest ↔ TakeOnSignOffRequest", () => {
    const literal: Required<TakeOnSignOffRequest> = {
      validationHash: "",
      statementVersion: "",
      declarationAccepted: true,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnSignOffRequest));
  });

  it("TakeOnSignOffReview ↔ TakeOnSignOffReviewResponse", () => {
    const literal: Required<TakeOnSignOffReview> = {
      batchId: "",
      status: "VALIDATED",
      trustAccountId: null,
      trustAccountName: null,
      cutoverDate: null,
      midMonthAcknowledged: false,
      position: null,
      counts: COUNTS,
      summary: null,
      validationHash: null,
      validatedAt: null,
      preparedByName: null,
      declaration: null,
      liveSignOff: null,
      signedByCaller: false,
      allowedActions: [],
      rows: { content: [], page: { totalElements: 0, totalPages: 0, size: 0, number: 0 } },
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnSignOffReviewResponse));
  });

  it("TakeOnReportSummary ↔ TakeOnReportSummaryResponse", () => {
    const literal: Required<TakeOnReportSummary> = {
      id: "",
      batchId: "",
      trustAccountId: "",
      cutoverDate: "",
      generatedAt: "",
      generatedByName: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnReportSummaryResponse));
  });

  const REPORT_POSITION: Required<TakeOnReportPosition> = {
    bankBalance: null,
    outstandingDeposits: null,
    outstandingPayments: null,
    adjustedBank: null,
    cashbook: null,
    fundLedger: null,
    investedTotal: null,
  };
  const CREDITOR_LIST: Required<TakeOnReportCreditorList> = {
    trustAccountId: "",
    accountName: null,
    asAt: "",
    cashbook: null,
    creditorTotal: null,
    lines: [],
  };

  it("TakeOnReport ↔ TakeOnReportResponse", () => {
    const literal: Required<TakeOnReport> = {
      id: "",
      batchId: "",
      trustAccountId: "",
      cutoverDate: "",
      snapshotSha256: "",
      generatedAt: "",
      snapshot: {} as TakeOnReportSnapshot,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnReportResponse));
  });

  it("TakeOnReportSnapshot ↔ TakeOnReportSnapshot", () => {
    const literal: Required<TakeOnReportSnapshot> = {
      firm: { name: null },
      account: {} as TakeOnReportAccount,
      cutoverDate: "",
      midMonth: false,
      previousSystem: REPORT_POSITION,
      kazi: REPORT_POSITION,
      differences: REPORT_POSITION,
      creditorList: CREDITOR_LIST,
      investmentAccounts: [],
      investments: [],
      unidentified: [],
      outstandingItems: [],
      totals: null,
      flags: [],
      signOff: null,
      sources: [],
      batchId: "",
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnReportSnapshot));
  });

  it("TakeOnReportFirm ↔ FirmHeader", () => {
    const literal: Required<TakeOnReportFirm> = { name: null };
    expect(Object.keys(literal)).toEqual(componentNames(contract.FirmHeader));
  });

  it("TakeOnReportAccount ↔ AccountHeader", () => {
    const literal: Required<TakeOnReportAccount> = {
      id: "",
      name: null,
      accountNumber: null,
      bankName: null,
      accountType: null,
      requireDualApproval: false,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.AccountHeader));
  });

  it("TakeOnReportPosition ↔ ReconciledPosition", () => {
    expect(Object.keys(REPORT_POSITION)).toEqual(componentNames(contract.ReconciledPosition));
  });

  it("TakeOnReportCreditorList ↔ CreditorListSection", () => {
    expect(Object.keys(CREDITOR_LIST)).toEqual(componentNames(contract.CreditorListSection));
  });

  it("TakeOnReportCreditorLine ↔ CreditorLineEntry", () => {
    const literal: Required<TakeOnReportCreditorLine> = {
      kind: "CLIENT",
      clientId: null,
      name: null,
      matters: [],
      balance: null,
      unidentifiedDepositId: null,
      receivedDate: null,
      bankDescription: null,
      totalDeposits: null,
      totalPayments: null,
      totalFeeTransfers: null,
      lastTransactionDate: null,
      broughtForward: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.CreditorLineEntry));
  });

  it("TakeOnReportMatter ↔ MatterEntry", () => {
    const literal: Required<TakeOnReportMatter> = { matterId: null, name: null, balance: null };
    expect(Object.keys(literal)).toEqual(componentNames(contract.MatterEntry));
  });

  it("TakeOnReportInvestment ↔ InvestmentLine", () => {
    const literal: Required<TakeOnReportInvestment> = {
      basis: "",
      clientId: null,
      clientName: null,
      matterId: null,
      matterName: null,
      investmentAccountId: null,
      investmentAccountName: null,
      institution: null,
      accountNumber: null,
      principal: null,
      interestRatePercent: null,
      depositDate: null,
      accruedInterest: null,
      fundAmount: null,
      balancePerConfirmation: null,
      instructionReference: null,
      instructionDate: null,
      endorsementConfirmed: false,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.InvestmentLine));
  });

  it("TakeOnReportUnidentified ↔ UnidentifiedLine", () => {
    const literal: Required<TakeOnReportUnidentified> = {
      receivedDate: null,
      ageInDaysAtCutover: 0,
      amount: null,
      reference: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.UnidentifiedLine));
  });

  it("TakeOnReportOutstanding ↔ OutstandingLine", () => {
    const literal: Required<TakeOnReportOutstanding> = {
      kind: "",
      originalDate: null,
      reference: null,
      clientId: null,
      clientName: null,
      matterId: null,
      matterName: null,
      amount: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.OutstandingLine));
  });

  it("TakeOnReportSignOff ↔ SignOffSection", () => {
    const literal: Required<TakeOnReportSignOff> = {
      signedBy: null,
      role: null,
      statementVersion: null,
      statementText: null,
      signedAt: null,
      preparedBy: null,
      samePerson: false,
      notes: [],
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.SignOffSection));
  });

  it("TakeOnReportSource ↔ SourceFile", () => {
    const literal: Required<TakeOnReportSource> = {
      fileName: null,
      section: null,
      sheetName: null,
      sha256: null,
      rowCount: 0,
      uploadedBy: null,
      uploadedAt: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.SourceFile));
  });
});
