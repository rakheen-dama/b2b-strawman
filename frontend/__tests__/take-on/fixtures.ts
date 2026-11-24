import type {
  TakeOnBatch,
  TakeOnDatasetCatalogue,
  TakeOnField,
  TakeOnGoLiveItem,
  TakeOnGoLiveItemKey,
  TakeOnSignOff,
  TakeOnSource,
  TakeOnValidationSummary,
} from "@/lib/types";

/**
 * Take-on fixtures for the 655A/656A step tests (attestation, commit dialog, results, matters,
 * go-live checklist). Not a test file (no `.test.` suffix).
 */

export const ATTEST_TEXT =
  "I confirm that customer due diligence for the clients in this batch was performed by Mathebula & Partners in its previous system, as recorded in each row, and that the records and the copies of, or references to, the verification documents remain available at the location recorded for each client for at least as long as FICA requires (sections 22 and 23). These clients will be shown as verified in the prior system, not as verified in Kazi.";

export const CLIENTS_CATALOGUE: TakeOnDatasetCatalogue = {
  dataset: "CLIENTS",
  label: "Clients",
  requiresCutoverDate: false,
  requiresTrustAccount: false,
  customFieldEntityType: "CUSTOMER",
  sections: [
    {
      section: "MAIN",
      label: "File",
      fields: [
        {
          key: "name",
          label: "Client name",
          type: "TEXT",
          required: true,
          maxLength: 255,
          allowedValues: null,
        },
        {
          key: "riskRating",
          label: "Risk rating",
          type: "ENUM",
          required: false,
          maxLength: null,
          allowedValues: ["LOW", "MEDIUM", "HIGH", "UNRATED"],
        },
        {
          key: "cddRecordsLocation",
          label: "CDD records location",
          type: "TEXT",
          required: true,
          maxLength: 500,
          allowedValues: null,
        },
      ],
    },
  ],
  completedBatches: 0,
};

export function clientsSource(overrides: Partial<TakeOnSource> = {}): TakeOnSource {
  return {
    id: "src-1",
    section: "MAIN",
    fileName: "clients.csv",
    fileFormat: "CSV",
    fileSize: 1200,
    sheetNames: [{ name: "CSV", hidden: false }],
    sheetName: "CSV",
    headerRow: 1,
    parseOptions: {
      dateFormat: "dd/MM/yyyy",
      decimalSeparator: "POINT",
      delimiter: ",",
      encoding: "UTF-8",
    },
    mapping: {
      targets: { name: "Name" },
      customFields: {},
      parked: [],
      defaults: { cddRecordsLocation: "Archive room, box 12" },
    },
    mapped: true,
    rowCount: 12,
    malformedCount: 0,
    blankSkipped: 0,
    purged: false,
    uploadedAt: "2026-11-23T08:00:00Z",
    ...overrides,
  };
}

export const ATTESTATION: TakeOnSignOff = {
  id: "sign-off-1",
  kind: "FICA_ATTESTATION",
  memberName: "Alice Owner",
  roleName: "Owner",
  statementVersion: "FICA-ATTEST-v1",
  signedAt: "2026-11-23T10:00:00Z",
};

/** A validated clients batch: 10 valid rows (2 duplicates of existing clients), 2 errors. */
/** The stored validation summary of clientsBatch(): 10 valid − 2 duplicates = 8 clients. */
export const CLIENTS_SUMMARY: TakeOnValidationSummary = {
  clients: 8,
  fica: {
    ficaClientType: { NATURAL_PERSON: 5, COMPANY: 3 },
    riskRating: { LOW: 6, UNRATED: 2 },
    beneficialOwnershipStatus: { NOT_APPLICABLE: 5, DONE: 3 },
    screeningStatus: { SCREENED_CLEAR: 7, NOT_SCREENED: 1 },
    sourceOfFundsStatus: { NOT_REQUIRED: 8 },
  },
};

export function clientsBatch(overrides: Partial<TakeOnBatch> = {}): TakeOnBatch {
  return {
    id: "batch-655",
    dataset: "CLIENTS",
    status: "VALIDATED",
    name: null,
    cutoverDate: null,
    trustAccountId: null,
    sources: [clientsSource()],
    counts: {
      total: 12,
      valid: 10,
      error: 2,
      duplicate: 2,
      blocks: 0,
      created: 0,
      skipped: 0,
      failed: 0,
    },
    validationHash: "hash-v1",
    validatedAt: "2026-11-23T09:00:00Z",
    allowedActions: ["REMOVE_SOURCE", "MAP", "REVALIDATE", "ATTEST", "CANCEL"],
    createdAt: "2026-11-23T08:00:00Z",
    cancelledAt: null,
    commitStartedAt: null,
    completedAt: null,
    lastError: null,
    signOffs: [],
    attestationStatement: { version: "FICA-ATTEST-v1", text: ATTEST_TEXT },
    summary: CLIENTS_SUMMARY,
    ...overrides,
  };
}

// ---- 656A: matters and the go-live checklist ----

const text = (key: string, label: string, required = false): TakeOnField => ({
  key,
  label,
  type: "TEXT",
  required,
  maxLength: 255,
  allowedValues: null,
});

/** The matters catalogue fields (backend TakeOnFieldCatalogue.MATTERS + the previous-system ID). */
export const MATTERS_CATALOGUE: TakeOnDatasetCatalogue = {
  dataset: "MATTERS",
  label: "Matters",
  requiresCutoverDate: false,
  requiresTrustAccount: false,
  customFieldEntityType: "PROJECT",
  sections: [
    {
      section: "MAIN",
      label: "File",
      fields: [
        text("name", "Matter name", true),
        text("clientEmail", "Client email"),
        text("clientRef", "Client previous-system ID"),
        text("clientIdNumber", "Client ID number"),
        text("referenceNumber", "Matter reference"),
        text("description", "Description"),
        text("dueDate", "Due date"),
        text("workType", "Work type"),
        {
          key: "priority",
          label: "Priority",
          type: "ENUM",
          required: false,
          maxLength: null,
          allowedValues: ["LOW", "MEDIUM", "HIGH"],
        },
        text("leadMemberEmail", "Responsible attorney email"),
        text("sourceRef", "Previous system ID"),
      ],
    },
  ],
  completedBatches: 0,
};

/** A validated matters batch: 15 rows, 14 valid (1 a duplicate of an existing matter), 1 error. */
export function mattersBatch(overrides: Partial<TakeOnBatch> = {}): TakeOnBatch {
  return {
    ...clientsBatch(),
    id: "batch-656",
    dataset: "MATTERS",
    sources: [
      clientsSource({
        fileName: "matters.csv",
        rowCount: 15,
        mapping: { targets: { name: "Matter" }, customFields: {}, parked: [], defaults: {} },
      }),
    ],
    counts: {
      total: 15,
      valid: 14,
      error: 1,
      duplicate: 1,
      blocks: 0,
      created: 0,
      skipped: 0,
      failed: 0,
    },
    // Matters need no attestation: COMMIT straight after a clean dry run (D-155).
    allowedActions: ["REMOVE_SOURCE", "MAP", "REVALIDATE", "COMMIT", "CANCEL"],
    attestationStatement: null,
    summary: null,
    ...overrides,
  };
}

/** The four go-live statements, §17.10 X3 verbatim, in X3 order (pinned again here, D-167). */
export const GO_LIVE_TEXT: Readonly<Record<TakeOnGoLiveItemKey, string>> = {
  TRUST_RECORDS_RETAINED:
    "Trust records from the previous system will be kept for at least seven years from the last entry, secured and immediately accessible to the firm and the Council (Rules 54.9.1 and 54.9.2).",
  FICA_RECORDS_RETAINED:
    "FICA records from the previous system will be kept for at least five years after each business relationship ends (section 23).",
  ARCHIVE_HOSTING_CONSIDERED:
    "If the previous vendor keeps hosting the archive, or its location changes, we have considered whether Council consent (Rule 54.9.2) or a notification to the FIC (section 24(3)) is needed. Kazi does not file either.",
  AUDITOR_INFORMED:
    "Our trust auditor has been told the cutover date and will receive the take-on report.",
};

/** The checklist as the backend lists it; `acknowledged` items carry who, when and a note. */
export function goLiveItems(
  acknowledged: Partial<
    Record<TakeOnGoLiveItemKey, { name: string; at: string; note?: string }>
  > = {}
): TakeOnGoLiveItem[] {
  return (Object.keys(GO_LIVE_TEXT) as TakeOnGoLiveItemKey[]).map((itemKey) => {
    const ack = acknowledged[itemKey];
    return {
      itemKey,
      text: GO_LIVE_TEXT[itemKey],
      acknowledged: ack !== undefined,
      acknowledgedBy: ack ? "member-1" : null,
      acknowledgedByName: ack?.name ?? null,
      acknowledgedAt: ack?.at ?? null,
      note: ack?.note ?? null,
    };
  });
}
