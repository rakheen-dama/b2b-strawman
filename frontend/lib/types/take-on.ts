// ---- Client and trust take-on (Phase 93 653A, architecture §17.4) ----
// Mirrors the wire records nested in backend takeon/TakeOnBatchService.java, keys IN DECLARATION
// ORDER — pinned by __tests__/take-on-wire-shapes.test.ts against
// backend/src/test/resources/wire/take-on-contract.json (ADR-365, P-17).

export type TakeOnDataset = "CLIENTS" | "MATTERS" | "TRUST_POSITION" | "DEBTOR_BALANCES";

export type TakeOnBatchStatus =
  | "UPLOADED"
  | "MAPPED"
  | "VALIDATED"
  | "COMMITTING"
  | "COMPLETED"
  | "CANCELLED";

export type TakeOnSection =
  | "MAIN"
  | "CLIENT_LEDGER"
  | "UNIDENTIFIED_DEPOSITS"
  | "INVESTMENTS"
  | "OUTSTANDING_ITEMS";

export type TakeOnRowStatus =
  | "PENDING"
  | "VALIDATED"
  | "ERROR"
  | "CREATED"
  | "SKIPPED_DUPLICATE"
  | "SKIPPED_ZERO"
  | "FAILED";

/** What the wizard may offer next (TakeOnBatchViews.Action) — drives every control. */
export type TakeOnAction =
  | "ADD_SOURCE"
  | "REMOVE_SOURCE"
  | "MAP"
  | "VALIDATE"
  | "REVALIDATE"
  | "CANCEL"
  | "ATTEST"
  | "REVOKE_ATTESTATION"
  | "COMMIT"
  | "CREATE_PORTAL_ACCESS"
  | "DOWNLOAD_RESULT"
  /** The caller may sign off this trust take-on (657C: SIGN_OFF_TRUST_TAKE_ON + VIEW_TRUST). */
  | "SIGN_OFF"
  /** The caller is the live trust signer and may withdraw their own sign-off (657C). */
  | "WITHDRAW_SIGN_OFF"
  /** A live trust sign-off may be revoked by the preparer or an admin (D-113, 657C). */
  | "REVOKE_SIGN_OFF";

/** TakeOnSignOffKind — the FICA attestation of a clients batch (655A), the trust sign-off (657C). */
export type TakeOnSignOffKind = "FICA_ATTESTATION" | "TRUST_SIGN_OFF";

export type TakeOnFieldType = "TEXT" | "EMAIL" | "DATE" | "AMOUNT" | "DECIMAL" | "ENUM" | "BOOLEAN";

export type TakeOnFileFormat = "CSV" | "XLSX";

export type TakeOnDecimalSeparator = "POINT" | "COMMA";

export type TakeOnCustomFieldEntityType = "CUSTOMER" | "PROJECT" | "INVOICE";

/** TakeOnFieldResponse */
export interface TakeOnField {
  key: string;
  label: string;
  type: TakeOnFieldType;
  required: boolean;
  maxLength: number | null;
  allowedValues: string[] | null;
}

/** TakeOnSectionResponse */
export interface TakeOnSectionCatalogue {
  section: TakeOnSection;
  label: string;
  fields: TakeOnField[];
}

/** TakeOnDatasetResponse */
export interface TakeOnDatasetCatalogue {
  dataset: TakeOnDataset;
  label: string;
  requiresCutoverDate: boolean;
  requiresTrustAccount: boolean;
  customFieldEntityType: TakeOnCustomFieldEntityType | null;
  sections: TakeOnSectionCatalogue[];
  completedBatches: number;
}

/** TakeOnParseOptionsResponse */
export interface TakeOnParseOptions {
  dateFormat: string;
  decimalSeparator: TakeOnDecimalSeparator;
  /** CSV only; null means "detected" / not applicable. */
  delimiter: string | null;
  encoding: string;
}

/** TakeOnMappingResponse — targets: field key → header; customFields: slug → header. */
export interface TakeOnMapping {
  targets: Record<string, string>;
  customFields: Record<string, string>;
  parked: string[];
  defaults: Record<string, string>;
}

/** TakeOnSheetNameResponse */
export interface TakeOnSheetName {
  name: string;
  hidden: boolean;
}

/** TakeOnSourceResponse */
export interface TakeOnSource {
  id: string;
  section: TakeOnSection;
  fileName: string;
  fileFormat: TakeOnFileFormat;
  fileSize: number;
  sheetNames: TakeOnSheetName[];
  sheetName: string | null;
  headerRow: number;
  parseOptions: TakeOnParseOptions;
  mapping: TakeOnMapping | null;
  mapped: boolean;
  rowCount: number;
  malformedCount: number;
  blankSkipped: number;
  purged: boolean;
  uploadedAt: string;
}

/**
 * TakeOnCountsResponse. `created`/`skipped`/`failed` are the commit outcome (655A): read live
 * from the rows while COMMITTING, written once when the batch completes.
 */
export interface TakeOnCounts {
  total: number;
  valid: number;
  error: number;
  duplicate: number;
  blocks: number;
  created: number;
  skipped: number;
  failed: number;
}

/** TakeOnSignOffResponse — a live (not withdrawn) sign-off; names are snapshots. */
export interface TakeOnSignOff {
  id: string;
  kind: TakeOnSignOffKind;
  memberName: string;
  roleName: string;
  statementVersion: string;
  signedAt: string;
}

/** TakeOnAttestationStatementResponse — the exact text to attest, with the firm name filled in. */
export interface TakeOnAttestationStatement {
  version: string;
  text: string;
}

// ---- The trust position's dry-run summary (657B, §17.4.4) — records nested in backend
// takeon/dataset/TrustPositionEquation.java, keys in declaration order (pinned like the records
// above). Amounts are two-decimal strings ("418500.00", "-100000.00"): `Number()` them only to
// display them. ----

/** TrustPositionMessage — `message` is the backend's text, fully formatted: render it verbatim. */
export interface TakeOnTrustMessage {
  code: string;
  message: string;
}

/** TrustEq1 — EQ-1(C): bank + outstanding deposits − outstanding payments = cashbook. */
export interface TakeOnTrustEq1 {
  bankBalance: string;
  plusOutstandingDeposits: string;
  lessOutstandingPayments: string;
  adjustedBank: string;
  cashbook: string;
  /** adjustedBank − cashbook */
  difference: string;
  holds: boolean;
}

/** TrustItemsTie — the listed outstanding items against the position's totals. */
export interface TakeOnTrustItemsTie {
  listedDeposits: string;
  listedPayments: string;
  outstandingDeposits: string;
  outstandingPayments: string;
  /** |listed deposits − OD| + |listed payments − OP| */
  difference: string;
  holds: boolean;
}

/** TrustEq2 — EQ-2(C): cashbook = trust creditors of the banking account. */
export interface TakeOnTrustEq2 {
  clientLedgers: string;
  fundLedger: string;
  unidentified: string;
  /** Negative: − Σ section 86(3) principal. */
  placedInSection86_3: string;
  creditors: string;
  cashbook: string;
  /** creditors − cashbook */
  difference: string;
  holds: boolean;
}

/** TrustInvestmentsTie — Σ balance per confirmation against the total invested. */
export interface TakeOnTrustInvestmentsTie {
  perConfirmations: string;
  listed: string;
  /** listed − perConfirmations */
  difference: string;
  holds: boolean;
}

/** TrustDeficit — the deficit test: trust creditors of the firm against the trust money held. */
export interface TakeOnTrustDeficit {
  trustCreditors: string;
  moneyHeld: string;
  /** trustCreditors − moneyHeld */
  difference: string;
  inDeficit: boolean;
}

/** TrustEquationSummary */
export interface TakeOnTrustEquation {
  eq1: TakeOnTrustEq1;
  itemsTie: TakeOnTrustItemsTie;
  eq2: TakeOnTrustEq2;
  investments: TakeOnTrustInvestmentsTie;
  deficit: TakeOnTrustDeficit;
}

/** TrustPositionSummary.flags — flags, never blocks. */
export type TakeOnTrustFlag = "FUND_LEDGER_IN_DEBIT" | "UNIDENTIFIED_OVER_ONE_YEAR";

/** TrustPositionSummary — the whole `summary` of a trust batch. */
export interface TakeOnTrustSummary {
  /** §17.3.9 account checks; [] when they pass. Errors, shown above the table. */
  accountErrors: TakeOnTrustMessage[];
  /** Batch-level statutory blocks, the deficit first. Never overridable. */
  blocks: TakeOnTrustMessage[];
  /** Batch-level flags with their text (the Fund ledger in debit). */
  warnings: TakeOnTrustMessage[];
  flags: TakeOnTrustFlag[];
  /** null when an account check failed. */
  equation: TakeOnTrustEquation | null;
}

/**
 * TakeOnBatchResponse.summary — the dry run's stored summary (take_on_batches.summary), present
 * from validation on (VALIDATED, COMMITTING, COMPLETED), else null. Per dataset; CLIENTS (655A):
 * the FICA columns counted over the rows that will become clients; TRUST_POSITION (657B): the
 * account errors, blocks, flags and equation of `TakeOnTrustSummary`.
 */
export interface TakeOnValidationSummary extends Partial<TakeOnTrustSummary> {
  /** CLIENTS: the valid rows that will become clients (existing-client matches excluded). */
  clients?: number;
  /** CLIENTS: FICA field key → stored value → rows. */
  fica?: Record<string, Record<string, number>>;
}

/**
 * TakeOnPositionResponse — the trust take-on's reconciled position at cutover (657B), JSON
 * numbers; a figure never entered is null (the Fund ledger and total invested store 0, D-183).
 */
export interface TakeOnPosition {
  bankBalance: number | null;
  outstandingDepositsTotal: number | null;
  outstandingPaymentsTotal: number | null;
  cashbookBalance: number | null;
  fundLedgerBalance: number | null;
  investedTotal: number | null;
}

/** TakeOnBatchResponse */
export interface TakeOnBatch {
  id: string;
  dataset: TakeOnDataset;
  status: TakeOnBatchStatus;
  name: string | null;
  cutoverDate: string | null;
  trustAccountId: string | null;
  sources: TakeOnSource[];
  counts: TakeOnCounts;
  validationHash: string | null;
  validatedAt: string | null;
  allowedActions: TakeOnAction[];
  createdAt: string;
  cancelledAt: string | null;
  commitStartedAt: string | null;
  completedAt: string | null;
  /** Why the last commit returned the batch to VALIDATED (plain English), else null. */
  lastError: string | null;
  signOffs: TakeOnSignOff[];
  /** Non-null only for a VALIDATED clients batch. */
  attestationStatement: TakeOnAttestationStatement | null;
  summary: TakeOnValidationSummary | null;
  /** Trust only (657B): "This is a mid-month cutover" was ticked; false on every other dataset. */
  midMonthAcknowledged: boolean;
  /** Trust only (657B): the reconciled position at cutover; null on every other dataset. */
  position: TakeOnPosition | null;
  /** Trust only (657C): the banking account's name; null on every other dataset. */
  trustAccountName: string | null;
  /** Trust only (657C): the take-on report, once the batch completed; else null. */
  reportId: string | null;
}

/** TakeOnBatchSummaryResponse */
export interface TakeOnBatchSummary {
  id: string;
  dataset: TakeOnDataset;
  status: TakeOnBatchStatus;
  name: string | null;
  cutoverDate: string | null;
  totalRows: number;
  validRows: number;
  errorRows: number;
  createdByName: string | null;
  createdAt: string;
}

/** TakeOnUploadResponse */
export interface TakeOnUploadResult {
  batch: TakeOnBatch;
  sourceId: string;
  headers: string[];
  warnings: string[];
}

/** TakeOnPreviewRow */
export interface TakeOnPreviewRow {
  rowNumber: number;
  cells: string[];
}

/** TakeOnPreviewResponse */
export interface TakeOnPreview {
  sourceId: string;
  headers: string[];
  rows: TakeOnPreviewRow[];
  detectedOptions: TakeOnParseOptions;
  sheets: TakeOnSheetName[];
  sheetName: string | null;
  headerRow: number;
  /** field key → header */
  suggestedTargets: Record<string, string>;
  warnings: string[];
}

/** TakeOnRowMessage — `field` is the column header for CELL_ERROR, else the field key. */
export interface TakeOnRowMessage {
  code: string;
  field: string | null;
  /** null once the batch is cancelled or purged: the text may quote a cell (X4). */
  message: string | null;
}

/** TakeOnRowResponse — `values`: typed after a dry run, raw before, `{purged: true}` after cancel. */
export interface TakeOnRow {
  id: string;
  section: TakeOnSection;
  rowNumber: number;
  sourceRef: string | null;
  status: TakeOnRowStatus;
  block: boolean;
  values: Record<string, unknown> | null;
  errors: TakeOnRowMessage[];
  warnings: TakeOnRowMessage[];
  duplicateOfId: string | null;
  /** Why a FAILED row was not committed (fixed text, never a cell value). */
  failureReason: string | null;
  /** The record a CREATED or SKIPPED_DUPLICATE row resolved to. */
  resultEntityId: string | null;
}

/** TakeOnMappingRequest — only dateFormat and decimalSeparator of parseOptions are honoured. */
export interface TakeOnMappingRequest {
  targets: Record<string, string>;
  customFields: Record<string, string>;
  parked: string[];
  defaults: Record<string, string>;
  parseOptions: Partial<TakeOnParseOptions> | null;
}

/** TakeOnSheetRequest — headerRow 1–50. */
export interface TakeOnSheetRequest {
  sheetName: string | null;
  headerRow: number;
}

/**
 * TakeOnPositionRequest — the cutover date and (trust, 657B) the reconciled position (P-11). A
 * missing or null figure keeps the stored value (D-183), so the upload step's date-only call stays
 * valid; the trust position form always sends every field.
 */
export interface TakeOnPositionRequest {
  cutoverDate: string;
  bankBalance?: number | null;
  outstandingDepositsTotal?: number | null;
  outstandingPaymentsTotal?: number | null;
  cashbookBalance?: number | null;
  fundLedgerBalance?: number | null;
  investedTotal?: number | null;
  midMonthAcknowledged?: boolean | null;
}

/** TakeOnAttestationRequest — `defaults` must equal the source's mapping defaults (else 400). */
export interface TakeOnAttestationRequest {
  validationHash: string;
  statementVersion: string;
  defaults: Record<string, string>;
}

/** TakeOnCommitRequest */
export interface TakeOnCommitRequest {
  validationHash: string;
}

/** TakeOnPortalContactsResponse — portal contacts created; nothing is emailed. */
export interface TakeOnPortalContactsResult {
  batch: TakeOnBatch;
  createdCount: number;
  skippedCount: number;
}

// ---- Go-live checklist (Phase 93 656A, §17.3.14) — records nested in backend
// takeon/TakeOnGoLiveService.java, keys in declaration order (pinned like the records above) ----

/** GoLiveItem — the four go-live acknowledgements, in X3 order. */
export type TakeOnGoLiveItemKey =
  | "TRUST_RECORDS_RETAINED"
  | "FICA_RECORDS_RETAINED"
  | "ARCHIVE_HOSTING_CONSIDERED"
  | "AUDITOR_INFORMED";

/**
 * TakeOnGoLiveItemResponse — `text` is the X3 statement verbatim (legal copy, the one source is
 * the backend enum); the acknowledgement fields are null when the item is not acknowledged.
 */
export interface TakeOnGoLiveItem {
  itemKey: TakeOnGoLiveItemKey;
  text: string;
  acknowledged: boolean;
  acknowledgedBy: string | null;
  acknowledgedByName: string | null;
  acknowledgedAt: string | null;
  note: string | null;
}

/** TakeOnGoLiveAcknowledgeRequest — the optional note (trimmed by the backend, at most 1 000). */
export interface TakeOnGoLiveAcknowledgeRequest {
  note: string | null;
}

// ---- Trust sign-off and the take-on report (Phase 93 657C, §17.4.1/§17.4.2, R-7) — records
// nested in backend takeon/TakeOnSignOffService.java, TakeOnReportService.java and
// TakeOnReportSnapshot.java, keys in declaration order (pinned like the records above). ----

/** TakeOnSignOffQueueItem — a trust take-on awaiting the caller's sign-off (the Home widget). */
export interface TakeOnSignOffQueueItem {
  batchId: string;
  trustAccountId: string;
  trustAccountName: string | null;
  cutoverDate: string;
  preparedByName: string | null;
  validatedAt: string | null;
}

/**
 * TakeOnSignOffDeclarationResponse — the TAKEON-SIGNOFF-v1 declaration (§17.10 X3, legal copy)
 * with the firm, account and cutover date filled in by the backend: render it verbatim.
 */
export interface TakeOnSignOffDeclaration {
  version: string;
  text: string;
}

/** TakeOnSignOffRequest — `statementVersion` is the declaration's own version, sent back. */
export interface TakeOnSignOffRequest {
  validationHash: string;
  statementVersion: string;
  declarationAccepted: boolean;
}

/**
 * TakeOnSignOffReviewResponse — what the signer signs (sign-off-review, and the answer of sign-off
 * and withdraw): the position, the dry-run summary, the declaration and a page of the rows.
 */
export interface TakeOnSignOffReview {
  batchId: string;
  status: TakeOnBatchStatus;
  trustAccountId: string | null;
  trustAccountName: string | null;
  cutoverDate: string | null;
  midMonthAcknowledged: boolean;
  position: TakeOnPosition | null;
  counts: TakeOnCounts;
  summary: TakeOnValidationSummary | null;
  validationHash: string | null;
  validatedAt: string | null;
  preparedByName: string | null;
  /** null without a firm, account or cutover date (nothing to declare). */
  declaration: TakeOnSignOffDeclaration | null;
  liveSignOff: TakeOnSignOff | null;
  signedByCaller: boolean;
  /** SIGN_OFF and/or WITHDRAW_SIGN_OFF only — the signer's own controls. */
  allowedActions: TakeOnAction[];
  rows: TakeOnPage<TakeOnRow>;
}

/** TakeOnReportSummaryResponse — one take-on report in the list. */
export interface TakeOnReportSummary {
  id: string;
  batchId: string;
  trustAccountId: string;
  cutoverDate: string;
  generatedAt: string;
  generatedByName: string | null;
}

/** FirmHeader */
export interface TakeOnReportFirm {
  name: string | null;
}

/** AccountHeader */
export interface TakeOnReportAccount {
  id: string;
  name: string | null;
  accountNumber: string | null;
  bankName: string | null;
  accountType: string | null;
  requireDualApproval: boolean;
}

/** ReconciledPosition — two-decimal amount strings. */
export interface TakeOnReportPosition {
  bankBalance: string | null;
  outstandingDeposits: string | null;
  outstandingPayments: string | null;
  adjustedBank: string | null;
  cashbook: string | null;
  fundLedger: string | null;
  investedTotal: string | null;
}

/** MatterEntry */
export interface TakeOnReportMatter {
  matterId: string | null;
  name: string | null;
  balance: string | null;
}

/** CreditorLine.Kind of a liveList line. */
export type TakeOnReportCreditorKind =
  | "CLIENT"
  | "CLIENT_INVESTMENT"
  | "FUND"
  | "UNIDENTIFIED"
  | "INTER_ACCOUNT";

/** CreditorLineEntry — one liveList line. */
export interface TakeOnReportCreditorLine {
  kind: TakeOnReportCreditorKind;
  clientId: string | null;
  name: string | null;
  matters: TakeOnReportMatter[];
  balance: string | null;
  unidentifiedDepositId: string | null;
  receivedDate: string | null;
  bankDescription: string | null;
  totalDeposits: string | null;
  totalPayments: string | null;
  totalFeeTransfers: string | null;
  lastTransactionDate: string | null;
  broughtForward: string | null;
}

/** CreditorListSection — one account's liveList at the cutover date. */
export interface TakeOnReportCreditorList {
  trustAccountId: string;
  accountName: string | null;
  asAt: string;
  cashbook: string | null;
  creditorTotal: string | null;
  lines: TakeOnReportCreditorLine[];
}

/** InvestmentLine */
export interface TakeOnReportInvestment {
  basis: string;
  clientId: string | null;
  clientName: string | null;
  matterId: string | null;
  matterName: string | null;
  investmentAccountId: string | null;
  investmentAccountName: string | null;
  institution: string | null;
  accountNumber: string | null;
  principal: string | null;
  interestRatePercent: string | null;
  depositDate: string | null;
  accruedInterest: string | null;
  fundAmount: string | null;
  balancePerConfirmation: string | null;
  instructionReference: string | null;
  instructionDate: string | null;
  endorsementConfirmed: boolean;
}

/** UnidentifiedLine */
export interface TakeOnReportUnidentified {
  receivedDate: string | null;
  ageInDaysAtCutover: number;
  amount: string | null;
  reference: string | null;
}

/** OutstandingLine */
export interface TakeOnReportOutstanding {
  kind: string;
  originalDate: string | null;
  reference: string | null;
  clientId: string | null;
  clientName: string | null;
  matterId: string | null;
  matterName: string | null;
  amount: string | null;
}

/** SignOffSection — `notes`: "Prepared and signed off by the same person" and/or "Authorised by the take-on sign-off". */
export interface TakeOnReportSignOff {
  signedBy: string | null;
  role: string | null;
  statementVersion: string | null;
  statementText: string | null;
  signedAt: string | null;
  preparedBy: string | null;
  samePerson: boolean;
  notes: string[];
}

/** SourceFile */
export interface TakeOnReportSource {
  fileName: string | null;
  section: string | null;
  sheetName: string | null;
  sha256: string | null;
  rowCount: number;
  uploadedBy: string | null;
  uploadedAt: string | null;
}

/** The report flags: the batch's trust flags plus the mid-month cutover. */
export type TakeOnReportFlag = TakeOnTrustFlag | "MID_MONTH_CUTOVER";

/** TakeOnReportSnapshot — the immutable snapshot (§17.3.12, R-9); `differences` = kazi − previous. */
export interface TakeOnReportSnapshot {
  firm: TakeOnReportFirm;
  account: TakeOnReportAccount;
  cutoverDate: string;
  midMonth: boolean;
  previousSystem: TakeOnReportPosition;
  kazi: TakeOnReportPosition;
  differences: TakeOnReportPosition;
  creditorList: TakeOnReportCreditorList;
  investmentAccounts: TakeOnReportCreditorList[];
  investments: TakeOnReportInvestment[];
  unidentified: TakeOnReportUnidentified[];
  outstandingItems: TakeOnReportOutstanding[];
  totals: TakeOnTrustEquation | null;
  flags: TakeOnReportFlag[];
  signOff: TakeOnReportSignOff | null;
  sources: TakeOnReportSource[];
  batchId: string;
}

/** TakeOnReportResponse — the snapshot as JSON (the results step). */
export interface TakeOnReport {
  id: string;
  batchId: string;
  trustAccountId: string;
  cutoverDate: string;
  snapshotSha256: string;
  generatedAt: string;
  snapshot: TakeOnReportSnapshot;
}

/** Spring Data VIA_DTO page. */
export interface TakeOnPage<T> {
  content: T[];
  page: { totalElements: number; totalPages: number; size: number; number: number };
}

// ---- X3 labels (never show a raw enum) ----

export const TAKE_ON_DATASET_ORDER: readonly TakeOnDataset[] = [
  "CLIENTS",
  "MATTERS",
  "TRUST_POSITION",
  "DEBTOR_BALANCES",
];

/** The take-on wizard's steps (653A/655A/656A). */
export type TakeOnStep =
  | "upload"
  | "sheet"
  | "mapping"
  | "validation"
  | "attestation"
  | "signOff"
  | "results";

/** How a dataset reaches its commit in the wizard, and what its commit can skip. */
export interface TakeOnCommitTraits {
  /** The sign-off step between the dry run and the commit (clients: the FICA attestation). */
  signOffStep: TakeOnStep | null;
  /** The commit sits under the Validation step: no sign-off (matters, 656A D-169). */
  commitsFromValidation: boolean;
  /** The commit skips nil-balance rows (`SKIPPED_ZERO`): the balance datasets only. */
  skipsNilBalance: boolean;
}

/**
 * The one place the wizard reads each dataset's commit path from (656A, D-177). The trust position
 * is signed off by a partner or director, then committed (657C); debtors set theirs in 658A.
 */
export const TAKE_ON_COMMIT_TRAITS: Record<TakeOnDataset, TakeOnCommitTraits> = {
  CLIENTS: { signOffStep: "attestation", commitsFromValidation: false, skipsNilBalance: false },
  MATTERS: { signOffStep: null, commitsFromValidation: true, skipsNilBalance: false },
  TRUST_POSITION: { signOffStep: "signOff", commitsFromValidation: false, skipsNilBalance: true },
  DEBTOR_BALANCES: { signOffStep: null, commitsFromValidation: false, skipsNilBalance: true },
};

export const TAKE_ON_DATASET_LABEL: Record<TakeOnDataset, string> = {
  CLIENTS: "Clients",
  MATTERS: "Matters",
  TRUST_POSITION: "Trust opening position",
  DEBTOR_BALANCES: "Debtor opening balances",
};

export const TAKE_ON_SECTION_LABEL: Record<TakeOnSection, string> = {
  MAIN: "File",
  CLIENT_LEDGER: "Client and matter balances",
  UNIDENTIFIED_DEPOSITS: "Unidentified deposits",
  INVESTMENTS: "Investments",
  OUTSTANDING_ITEMS: "Items outstanding at cutover",
};

export const TAKE_ON_BATCH_STATUS_LABEL: Record<TakeOnBatchStatus, string> = {
  UPLOADED: "Uploaded",
  MAPPED: "Mapped",
  VALIDATED: "Validated",
  COMMITTING: "Committing",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

export const TAKE_ON_ROW_STATUS_LABEL: Record<TakeOnRowStatus, string> = {
  PENDING: "Pending",
  VALIDATED: "Valid",
  ERROR: "Error",
  CREATED: "Created",
  SKIPPED_DUPLICATE: "Duplicate — skipped",
  SKIPPED_ZERO: "Nil balance — skipped",
  FAILED: "Failed",
};

/**
 * A VALIDATED row that matches an existing record (`duplicateOfId`): it will commit as
 * "Duplicate — skipped" (OBS-655A-1). Not a row status of its own — the row is valid.
 */
export const TAKE_ON_DUPLICATE_PENDING_LABEL = "Duplicate — will be skipped";

/** The row statuses only a commit produces (655A). */
export const TAKE_ON_COMMIT_ROW_STATUSES: readonly TakeOnRowStatus[] = [
  "CREATED",
  "SKIPPED_DUPLICATE",
  "SKIPPED_ZERO",
  "FAILED",
];

/** Labels of the clients dataset's coded FICA values (catalogue `allowedValues`). */
export const TAKE_ON_CLIENT_VALUE_LABEL: Record<string, Record<string, string>> = {
  ficaClientType: {
    NATURAL_PERSON: "Natural person",
    COMPANY: "Company",
    CLOSE_CORPORATION: "Close corporation",
    TRUST: "Trust",
    PARTNERSHIP: "Partnership",
    OTHER: "Other",
  },
  riskRating: { LOW: "Low", MEDIUM: "Medium", HIGH: "High", UNRATED: "Not risk-rated" },
  beneficialOwnershipStatus: {
    DONE: "Done",
    NOT_APPLICABLE: "Not applicable",
    OUTSTANDING: "Outstanding",
  },
  screeningStatus: {
    SCREENED_CLEAR: "Screened — clear",
    SCREENED_MATCH: "Screened — match",
    NOT_SCREENED: "Not screened",
  },
  sourceOfFundsStatus: { DONE: "Done", NOT_REQUIRED: "Not required", OUTSTANDING: "Outstanding" },
};

/** The trust flags (never a raw enum on screen; product copy, D-190). */
export const TAKE_ON_TRUST_FLAG_LABEL: Record<TakeOnTrustFlag, string> = {
  FUND_LEDGER_IN_DEBIT: "Legal Practitioners' Fidelity Fund ledger in debit",
  UNIDENTIFIED_OVER_ONE_YEAR: "Unidentified deposit older than one year",
};

/** The take-on report's flags (product copy, 657C): the trust flags plus a mid-month cutover. */
export const TAKE_ON_REPORT_FLAG_LABEL: Record<TakeOnReportFlag, string> = {
  ...TAKE_ON_TRUST_FLAG_LABEL,
  MID_MONTH_CUTOVER: "Mid-month cutover",
};

const MONTHS = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
] as const;

/**
 * A cutover date as X3 writes it, `d MMMM yyyy` ("2026-10-31" → "31 October 2026") — the same
 * form as the backend's declaration. Plain arithmetic on the ISO date: no zone, no locale.
 */
export function takeOnLongDate(isoDate: string | null | undefined): string {
  if (!isoDate) return "";
  const [y, m, d] = isoDate.split("-").map(Number);
  if (!y || !m || !d || m < 1 || m > 12) return isoDate;
  return `${d} ${MONTHS[m - 1]} ${y}`;
}

/**
 * Labels of the trust position's coded values (catalogue `allowedValues`) and of the dry run's
 * `clientResolvedBy` (D-189 b) — the row table never shows the raw code (D-190).
 */
export const TAKE_ON_TRUST_VALUE_LABEL: Record<string, Record<string, string>> = {
  basis: {
    CLIENT_INSTRUCTION: "Section 86(4) — on a client's instruction",
    FIRM_DISCRETION: "Section 86(3) — at the firm's discretion",
  },
  kind: {
    DEPOSIT_IN_TRANSIT: "Deposit in transit",
    UNPRESENTED_PAYMENT: "Unpresented payment",
    INVESTMENT_IN_TRANSIT: "Investment movement in transit",
  },
  clientResolvedBy: {
    email: "Client email",
    previousSystemId: "Client previous-system ID",
    idNumber: "Client ID number",
  },
  endorsementConfirmed: { true: "Yes", false: "No" },
};

/** The date formats the dry run accepts (TakeOnValueParser; ISO yyyy-MM-dd is always accepted). */
export const TAKE_ON_DATE_FORMATS: readonly { value: string; label: string }[] = [
  { value: "dd/MM/yyyy", label: "31/12/2025 (dd/mm/yyyy)" },
  { value: "yyyy-MM-dd", label: "2025-12-31 (yyyy-mm-dd)" },
  { value: "dd-MM-yyyy", label: "31-12-2025 (dd-mm-yyyy)" },
  { value: "dd.MM.yyyy", label: "31.12.2025 (dd.mm.yyyy)" },
  { value: "MM/dd/yyyy", label: "12/31/2025 (mm/dd/yyyy)" },
  { value: "d MMM yyyy", label: "31 Dec 2025 (d mmm yyyy)" },
];

/** Kebab-case of a dataset, section or go-live item for testids ("TRUST_POSITION" → "trust-position"). */
export function takeOnKebab(value: TakeOnDataset | TakeOnSection | TakeOnGoLiveItemKey): string {
  return value.toLowerCase().replace(/_/g, "-");
}
