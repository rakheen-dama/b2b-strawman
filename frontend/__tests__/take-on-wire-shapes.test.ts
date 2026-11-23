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
  TakeOnMapping,
  TakeOnMappingRequest,
  TakeOnParseOptions,
  TakeOnPortalContactsResult,
  TakeOnPositionRequest,
  TakeOnPreview,
  TakeOnPreviewRow,
  TakeOnRow,
  TakeOnRowMessage,
  TakeOnSectionCatalogue,
  TakeOnSheetName,
  TakeOnSheetRequest,
  TakeOnSignOff,
  TakeOnSource,
  TakeOnUploadResult,
} from "@/lib/types/take-on";

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
};

// Each literal is typed Required<T>: a missing or extra key fails `pnpm typecheck`; the runtime
// assertion fails when the Java record changes and the literal was not updated. Literal key ORDER
// must equal the Java record's declaration order (toEqual on arrays is order-sensitive).
describe("take-on wire shapes match the backend contract (ADR-365, P-17)", () => {
  it("pins exactly the 23 take-on records of 653A and 655A", () => {
    expect(Object.keys(contract).sort()).toEqual([
      "TakeOnAttestationRequest",
      "TakeOnAttestationStatementResponse",
      "TakeOnBatchResponse",
      "TakeOnBatchSummaryResponse",
      "TakeOnCommitRequest",
      "TakeOnCountsResponse",
      "TakeOnDatasetResponse",
      "TakeOnFieldResponse",
      "TakeOnMappingRequest",
      "TakeOnMappingResponse",
      "TakeOnParseOptionsResponse",
      "TakeOnPortalContactsResponse",
      "TakeOnPositionRequest",
      "TakeOnPreviewResponse",
      "TakeOnPreviewRow",
      "TakeOnRowMessage",
      "TakeOnRowResponse",
      "TakeOnSectionResponse",
      "TakeOnSheetNameResponse",
      "TakeOnSheetRequest",
      "TakeOnSignOffResponse",
      "TakeOnSourceResponse",
      "TakeOnUploadResponse",
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
    const literal: Required<TakeOnPositionRequest> = { cutoverDate: "" };
    expect(Object.keys(literal)).toEqual(componentNames(contract.TakeOnPositionRequest));
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
});
