import type {
  TakeOnBatch,
  TakeOnReport,
  TakeOnRow,
  TakeOnSignOffQueueItem,
  TakeOnSignOffReview,
  TakeOnDatasetCatalogue,
  TakeOnField,
  TakeOnGoLiveItem,
  TakeOnGoLiveItemKey,
  TakeOnSignOff,
  TakeOnSource,
  TakeOnTrustEquation,
  TakeOnTrustSummary,
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
    midMonthAcknowledged: false,
    position: null,
    trustAccountName: null,
    reportId: null,
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
    midMonthAcknowledged: false,
    position: null,
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

// ---- 657B: the trust opening position ----

const field = (
  key: string,
  label: string,
  type: TakeOnField["type"],
  required: boolean,
  allowedValues: string[] | null = null
): TakeOnField => ({ key, label, type, required, maxLength: null, allowedValues });

/** The trust catalogue (backend TakeOnFieldCatalogue.TRUST_SECTIONS, X3 section labels). */
export const TRUST_CATALOGUE: TakeOnDatasetCatalogue = {
  dataset: "TRUST_POSITION",
  label: "Trust opening position",
  requiresCutoverDate: true,
  requiresTrustAccount: true,
  customFieldEntityType: null,
  sections: [
    {
      section: "CLIENT_LEDGER",
      label: "Client and matter balances",
      fields: [
        field("clientRef", "Client reference", "TEXT", true),
        field("matterRef", "Matter reference", "TEXT", true),
        field("balance", "Balance at cutover", "AMOUNT", true),
        field("sourceRef", "Previous system ID", "TEXT", false),
      ],
    },
    {
      section: "UNIDENTIFIED_DEPOSITS",
      label: "Unidentified deposits",
      fields: [
        field("receivedDate", "Date received", "DATE", true),
        field("amount", "Amount", "AMOUNT", true),
        field("bankReference", "Bank reference", "TEXT", false),
        field("description", "Description", "TEXT", false),
      ],
    },
    {
      section: "INVESTMENTS",
      label: "Investments",
      fields: [
        field("investmentAccount", "Investment account (number or name)", "TEXT", true),
        field("basis", "Basis", "ENUM", true, ["CLIENT_INSTRUCTION", "FIRM_DISCRETION"]),
        field("principal", "Principal at cutover", "AMOUNT", true),
        field("endorsementConfirmed", "Endorsed under section 86(4)", "BOOLEAN", false),
        field("balancePerConfirmation", "Balance per confirmation", "AMOUNT", true),
      ],
    },
    {
      section: "OUTSTANDING_ITEMS",
      label: "Items outstanding at cutover",
      fields: [
        field("kind", "Item type", "ENUM", true, [
          "DEPOSIT_IN_TRANSIT",
          "UNPRESENTED_PAYMENT",
          "INVESTMENT_IN_TRANSIT",
        ]),
        field("originalDate", "Original date", "DATE", true),
        field("amount", "Amount", "AMOUNT", true),
        field("reference", "Reference", "TEXT", true),
        field("clientRef", "Client reference", "TEXT", true),
        field("matterRef", "Matter reference", "TEXT", true),
      ],
    },
  ],
  completedBatches: 0,
};

/** §17.3.13's expected `summary.equation` (A.4): every difference "0.00". */
export const TRUST_EQUATION_17313: TakeOnTrustEquation = {
  eq1: {
    bankBalance: "418500.00",
    plusOutstandingDeposits: "5000.00",
    lessOutstandingPayments: "20000.00",
    adjustedBank: "403500.00",
    cashbook: "403500.00",
    difference: "0.00",
    holds: true,
  },
  itemsTie: {
    listedDeposits: "5000.00",
    listedPayments: "20000.00",
    outstandingDeposits: "5000.00",
    outstandingPayments: "20000.00",
    difference: "0.00",
    holds: true,
  },
  eq2: {
    clientLedgers: "499500.00",
    fundLedger: "1250.00",
    unidentified: "2750.00",
    placedInSection86_3: "-100000.00",
    creditors: "403500.00",
    cashbook: "403500.00",
    difference: "0.00",
    holds: true,
  },
  investments: {
    perConfirmations: "300000.00",
    listed: "300000.00",
    difference: "0.00",
    holds: true,
  },
  deficit: {
    trustCreditors: "703500.00",
    moneyHeld: "703500.00",
    difference: "0.00",
    inDeficit: false,
  },
};

/** §17.3.13's expected trust summary (A.4). */
export const TRUST_SUMMARY_17313: TakeOnTrustSummary = {
  accountErrors: [],
  blocks: [],
  warnings: [],
  flags: ["UNIDENTIFIED_OVER_ONE_YEAR"],
  equation: TRUST_EQUATION_17313,
};

/** The §17.3.13 summary with parts replaced (an equation row, blocks, account errors…). */
export function trustSummaryWith(
  overrides: Partial<Omit<TakeOnTrustSummary, "equation">> & {
    equation?: Partial<TakeOnTrustEquation> | null;
  } = {}
): TakeOnTrustSummary {
  const { equation, ...rest } = overrides;
  return {
    ...TRUST_SUMMARY_17313,
    ...rest,
    equation: equation === null ? null : { ...TRUST_EQUATION_17313, ...equation },
  };
}

/** A trust source for one section. */
export function trustSource(
  section: TakeOnSource["section"],
  overrides: Partial<TakeOnSource> = {}
): TakeOnSource {
  return clientsSource({
    id: `src-${section.toLowerCase()}`,
    section,
    fileName: `${section.toLowerCase()}.csv`,
    rowCount: 3,
    mapping: { targets: {}, customFields: {}, parked: [], defaults: {} },
    ...overrides,
  });
}

/** A validated §17.3.13 trust batch at 31 October 2026, every difference R0,00, no block. */
export function trustBatch(overrides: Partial<TakeOnBatch> = {}): TakeOnBatch {
  return {
    ...clientsBatch(),
    id: "batch-657b",
    dataset: "TRUST_POSITION",
    cutoverDate: "2026-10-31",
    trustAccountId: "acc-g",
    sources: [
      trustSource("CLIENT_LEDGER"),
      trustSource("UNIDENTIFIED_DEPOSITS", { rowCount: 2 }),
      trustSource("INVESTMENTS", { rowCount: 2 }),
      trustSource("OUTSTANDING_ITEMS", { rowCount: 2 }),
    ],
    counts: {
      total: 9,
      valid: 9,
      error: 0,
      duplicate: 0,
      blocks: 0,
      created: 0,
      skipped: 0,
      failed: 0,
    },
    trustAccountName: "Trust Account G",
    // As the preparer (an admin) sees it before the sign-off (657C, R-5): no SIGN_OFF (owner-only
    // SIGN_OFF_TRUST_TAKE_ON), no COMMIT (a trust commit needs a live TRUST_SIGN_OFF).
    allowedActions: ["REMOVE_SOURCE", "MAP", "REVALIDATE", "CANCEL"],
    attestationStatement: null,
    summary: TRUST_SUMMARY_17313,
    midMonthAcknowledged: false,
    position: {
      bankBalance: 418500,
      outstandingDepositsTotal: 5000,
      outstandingPaymentsTotal: 20000,
      cashbookBalance: 403500,
      fundLedgerBalance: 1250,
      investedTotal: 300000,
    },
    ...overrides,
  };
}

// ---- 657C: the trust sign-off and the take-on report ----

/** TAKEON-SIGNOFF-v1 as the backend fills it for the §17.3.13 batch (§17.10 X3, verbatim). */
export const SIGN_OFF_TEXT =
  "I am a partner or director of Mathebula & Partners responsible for its trust accounting records (Rule 54.19). I have reviewed this take-on of Trust Account G at 31 October 2026. The trust creditors listed agree, to the cent, with the reconciled trust position in the previous system at that date, and no trust creditor is in debit.";

/** A live trust sign-off by the owner. */
export const TRUST_SIGN_OFF: TakeOnSignOff = {
  id: "sign-off-trust-1",
  kind: "TRUST_SIGN_OFF",
  memberName: "Alice Owner",
  roleName: "Owner",
  statementVersion: "TAKEON-SIGNOFF-v1",
  signedAt: "2026-11-24T09:30:00Z",
};

/** One client-ledger row of the §17.3.13 set (client A's matter A1, R300 000,00). */
export const TRUST_ROW_A1: TakeOnRow = {
  id: "row-a1",
  section: "CLIENT_LEDGER",
  rowNumber: 2,
  sourceRef: "AJS-C-0001",
  status: "VALIDATED",
  block: false,
  values: {
    clientRef: "client.a@example.com",
    matterRef: "M-A1",
    balance: "300000.00",
    clientId: "4b1d7c3e-0000-0000-0000-00000000000a",
  },
  errors: [],
  warnings: [],
  duplicateOfId: null,
  failureReason: null,
  resultEntityId: null,
};

/** The queue item of the §17.3.13 batch, as the Home widget lists it. */
export const QUEUE_ITEM: TakeOnSignOffQueueItem = {
  batchId: "batch-657b",
  trustAccountId: "acc-g",
  trustAccountName: "Trust Account G",
  cutoverDate: "2026-10-31",
  preparedByName: "Bob Admin",
  validatedAt: "2026-11-24T08:00:00Z",
};

/** The sign-off review of the §17.3.13 batch, as the owner sees it before signing (SIGN_OFF). */
export function signOffReview(overrides: Partial<TakeOnSignOffReview> = {}): TakeOnSignOffReview {
  const batch = trustBatch();
  return {
    batchId: batch.id,
    status: "VALIDATED",
    trustAccountId: "acc-g",
    trustAccountName: "Trust Account G",
    cutoverDate: "2026-10-31",
    midMonthAcknowledged: false,
    position: batch.position,
    counts: batch.counts,
    summary: TRUST_SUMMARY_17313,
    validationHash: "hash-trust-v1",
    validatedAt: "2026-11-24T08:00:00Z",
    preparedByName: "Bob Admin",
    declaration: { version: "TAKEON-SIGNOFF-v1", text: SIGN_OFF_TEXT },
    liveSignOff: null,
    signedByCaller: false,
    allowedActions: ["SIGN_OFF"],
    rows: {
      content: [TRUST_ROW_A1],
      page: { totalElements: 1, totalPages: 1, size: 50, number: 0 },
    },
    ...overrides,
  };
}

/** The same review once the caller signed it (WITHDRAW_SIGN_OFF). */
export function signedReview(overrides: Partial<TakeOnSignOffReview> = {}): TakeOnSignOffReview {
  return signOffReview({
    liveSignOff: TRUST_SIGN_OFF,
    signedByCaller: true,
    allowedActions: ["WITHDRAW_SIGN_OFF"],
    ...overrides,
  });
}

const POSITION_17313 = {
  bankBalance: "418500.00",
  outstandingDeposits: "5000.00",
  outstandingPayments: "20000.00",
  adjustedBank: "403500.00",
  cashbook: "403500.00",
  fundLedger: "1250.00",
  investedTotal: "300000.00",
};

const ZERO_POSITION = Object.fromEntries(
  Object.keys(POSITION_17313).map((k) => [k, "0.00"])
) as typeof POSITION_17313;

/** The take-on report of the committed §17.3.13 batch (the parts the results summary reads). */
export const TAKE_ON_REPORT_17313: TakeOnReport = {
  id: "report-1",
  batchId: "batch-657b",
  trustAccountId: "acc-g",
  cutoverDate: "2026-10-31",
  snapshotSha256: "a".repeat(64),
  generatedAt: "2026-11-24T10:00:00Z",
  snapshot: {
    firm: { name: "Mathebula & Partners" },
    account: {
      id: "acc-g",
      name: "Trust Account G",
      accountNumber: "1234567890",
      bankName: "Standard Bank",
      accountType: "GENERAL",
      requireDualApproval: true,
    },
    cutoverDate: "2026-10-31",
    midMonth: false,
    previousSystem: POSITION_17313,
    kazi: POSITION_17313,
    differences: ZERO_POSITION,
    creditorList: {
      trustAccountId: "acc-g",
      accountName: "Trust Account G",
      asAt: "2026-10-31",
      cashbook: "403500.00",
      creditorTotal: "403500.00",
      lines: [
        creditorLine("CLIENT", "Client A", "300000.00"),
        creditorLine("CLIENT", "Client B", "199500.00"),
        creditorLine("FUND", "Legal Practitioners' Fidelity Fund", "1250.00"),
        creditorLine("UNIDENTIFIED", null, "2000.00"),
        creditorLine("UNIDENTIFIED", null, "750.00"),
      ],
    },
    investmentAccounts: [],
    investments: [],
    unidentified: [],
    outstandingItems: [],
    totals: TRUST_EQUATION_17313,
    flags: ["UNIDENTIFIED_OVER_ONE_YEAR"],
    signOff: {
      signedBy: "Alice Owner",
      role: "Owner",
      statementVersion: "TAKEON-SIGNOFF-v1",
      statementText: SIGN_OFF_TEXT,
      signedAt: "2026-11-24T09:30:00Z",
      preparedBy: "Bob Admin",
      samePerson: false,
      notes: ["Authorised by the take-on sign-off"],
    },
    sources: [],
    batchId: "batch-657b",
  },
};

function creditorLine(
  kind: TakeOnReport["snapshot"]["creditorList"]["lines"][number]["kind"],
  name: string | null,
  balance: string
): TakeOnReport["snapshot"]["creditorList"]["lines"][number] {
  return {
    kind,
    clientId: null,
    name,
    matters: [],
    balance,
    unidentifiedDepositId: null,
    receivedDate: null,
    bankDescription: null,
    totalDeposits: null,
    totalPayments: null,
    totalFeeTransfers: null,
    lastTransactionDate: null,
    broughtForward: balance,
  };
}
