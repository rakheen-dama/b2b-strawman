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
  | "CANCEL";

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

/** TakeOnCountsResponse (created/skipped/failed arrive with the commit, 655A). */
export interface TakeOnCounts {
  total: number;
  valid: number;
  error: number;
  duplicate: number;
  blocks: number;
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

/** TakeOnPositionRequest — the cutover date only in 653A (P-11). */
export interface TakeOnPositionRequest {
  cutoverDate: string;
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

/** The date formats the dry run accepts (TakeOnValueParser; ISO yyyy-MM-dd is always accepted). */
export const TAKE_ON_DATE_FORMATS: readonly { value: string; label: string }[] = [
  { value: "dd/MM/yyyy", label: "31/12/2025 (dd/mm/yyyy)" },
  { value: "yyyy-MM-dd", label: "2025-12-31 (yyyy-mm-dd)" },
  { value: "dd-MM-yyyy", label: "31-12-2025 (dd-mm-yyyy)" },
  { value: "dd.MM.yyyy", label: "31.12.2025 (dd.mm.yyyy)" },
  { value: "MM/dd/yyyy", label: "12/31/2025 (mm/dd/yyyy)" },
  { value: "d MMM yyyy", label: "31 Dec 2025 (d mmm yyyy)" },
];

/** Kebab-case of a dataset or section for testids ("TRUST_POSITION" → "trust-position"). */
export function takeOnKebab(value: TakeOnDataset | TakeOnSection): string {
  return value.toLowerCase().replace(/_/g, "-");
}
