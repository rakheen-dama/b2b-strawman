import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRConfig } from "swr";
import type { ReactNode } from "react";

vi.mock("server-only", () => ({}));

const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, refresh: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/settings/matters",
  useSearchParams: () => new URLSearchParams(),
}));

const actions = vi.hoisted(() => ({
  uploadTakeOnFile: vi.fn(),
  addTakeOnSource: vi.fn(),
  removeTakeOnSource: vi.fn(),
  setSheet: vi.fn(),
  saveMapping: vi.fn(),
  savePosition: vi.fn(),
  validateBatch: vi.fn(),
  cancelBatch: vi.fn(),
  fetchTakeOnBatch: vi.fn(),
  fetchTakeOnPreview: vi.fn(),
  fetchTakeOnRows: vi.fn(),
  fetchTakeOnCustomFields: vi.fn(),
  fetchTakeOnBankingAccounts: vi.fn(),
  downloadTakeOnTemplate: vi.fn(),
  attest: vi.fn(),
  revokeSignOff: vi.fn(),
  commitBatch: vi.fn(),
  createPortalContacts: vi.fn(),
  downloadTakeOnResult: vi.fn(),
  getSignOffReview: vi.fn(),
  signOff: vi.fn(),
  withdrawMySignOff: vi.fn(),
  getTakeOnReport: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/take-on-actions", () => actions);

import { TakeOnWizard } from "@/components/take-on/take-on-wizard";
import { UploadStep } from "@/components/take-on/upload-step";
import type { TakeOnBatch, TakeOnDatasetCatalogue, TakeOnPreview, TakeOnSource } from "@/lib/types";
import {
  TRUST_CATALOGUE,
  TRUST_SIGN_OFF,
  signOffReview,
  signedReview,
  trustBatch,
  trustSource,
  trustSummaryWith,
} from "./fixtures";

const CLIENTS: TakeOnDatasetCatalogue = {
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
          maxLength: 200,
          allowedValues: null,
        },
        {
          key: "email",
          label: "Email",
          type: "EMAIL",
          required: false,
          maxLength: 254,
          allowedValues: null,
        },
      ],
    },
  ],
  completedBatches: 0,
};

const TRUST: TakeOnDatasetCatalogue = {
  dataset: "TRUST_POSITION",
  label: "Trust opening position",
  requiresCutoverDate: true,
  requiresTrustAccount: true,
  customFieldEntityType: null,
  sections: (
    ["CLIENT_LEDGER", "UNIDENTIFIED_DEPOSITS", "INVESTMENTS", "OUTSTANDING_ITEMS"] as const
  ).map((section) => ({ section, label: section, fields: [] })),
  completedBatches: 0,
};

const PARSE = {
  dateFormat: "dd/MM/yyyy",
  decimalSeparator: "POINT" as const,
  delimiter: ",",
  encoding: "UTF-8",
};

function source(overrides: Partial<TakeOnSource> = {}): TakeOnSource {
  return {
    id: "src-1",
    section: "MAIN",
    fileName: "clients.csv",
    fileFormat: "CSV",
    fileSize: 120,
    sheetNames: [{ name: "CSV", hidden: false }],
    sheetName: "CSV",
    headerRow: 1,
    parseOptions: PARSE,
    mapping: null,
    mapped: false,
    rowCount: 3,
    malformedCount: 0,
    blankSkipped: 0,
    purged: false,
    uploadedAt: "2026-11-23T08:00:00Z",
    ...overrides,
  };
}

function batch(overrides: Partial<TakeOnBatch> = {}): TakeOnBatch {
  return {
    id: "batch-1",
    dataset: "CLIENTS",
    status: "UPLOADED",
    name: null,
    cutoverDate: null,
    trustAccountId: null,
    sources: [source()],
    counts: {
      total: 0,
      valid: 0,
      error: 0,
      duplicate: 0,
      blocks: 0,
      created: 0,
      skipped: 0,
      failed: 0,
    },
    validationHash: null,
    validatedAt: null,
    allowedActions: ["REMOVE_SOURCE", "MAP", "CANCEL"],
    createdAt: "2026-11-23T08:00:00Z",
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
    ...overrides,
  };
}

const PREVIEW: TakeOnPreview = {
  sourceId: "src-1",
  headers: ["Name", "Email", "Notes"],
  rows: [{ rowNumber: 2, cells: ["Acme Holdings", "info@acme.example", "VIP"] }],
  detectedOptions: PARSE,
  sheets: [{ name: "CSV", hidden: false }],
  sheetName: "CSV",
  headerRow: 1,
  suggestedTargets: {},
  warnings: [],
};

const VALIDATED = batch({
  status: "VALIDATED",
  sources: [
    source({
      mapped: true,
      mapping: { targets: { name: "Name" }, customFields: {}, parked: [], defaults: {} },
    }),
  ],
  counts: {
    total: 10,
    valid: 7,
    error: 3,
    duplicate: 0,
    blocks: 0,
    created: 0,
    skipped: 0,
    failed: 0,
  },
  validationHash: "abc",
  validatedAt: "2026-11-23T09:00:00Z",
  allowedActions: ["REMOVE_SOURCE", "MAP", "REVALIDATE", "CANCEL"],
});

function withSWR(ui: ReactNode) {
  return <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>{ui}</SWRConfig>;
}

function renderWizard(b: TakeOnBatch, dataset = CLIENTS) {
  return render(withSWR(<TakeOnWizard batch={b} dataset={dataset} />));
}

function fileOf(name: string, size: number, type: string) {
  const file = new File(["x"], name, { type });
  Object.defineProperty(file, "size", { value: size });
  return file;
}

beforeEach(() => {
  actions.fetchTakeOnPreview.mockResolvedValue({ success: true, data: PREVIEW });
  actions.fetchTakeOnCustomFields.mockResolvedValue({ success: true, data: [] });
  actions.fetchTakeOnBankingAccounts.mockResolvedValue({
    success: true,
    data: [{ id: "acc-1", accountName: "Trust Account" }],
  });
  actions.fetchTakeOnRows.mockResolvedValue({
    success: true,
    data: { content: [], page: { totalElements: 0, totalPages: 0, size: 50, number: 0 } },
  });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("TakeOnWizard — stepper", () => {
  it("labels the six clients steps and resumes an unmapped CSV at Mapping", async () => {
    renderWizard(batch());
    const nav = screen.getByRole("navigation", { name: "Take-on steps" });
    expect(
      within(nav)
        .getAllByRole("button")
        .map((b) => b.textContent)
    ).toEqual(["1Upload", "2Sheet", "3Mapping", "4Validation", "5Attestation", "6Results"]);
    expect(screen.getByTestId("take-on-step-attestation")).toBeDisabled();
    expect(screen.getByTestId("take-on-step-results")).toBeDisabled();
    expect(screen.getByTestId("take-on-step-mapping")).toHaveAttribute("aria-current", "step");
    expect(await screen.findByTestId("take-on-mapping")).toBeInTheDocument();
  });

  it("resumes a validated batch at Validation and a multi-sheet XLSX at Sheet", () => {
    renderWizard(VALIDATED);
    expect(screen.getByTestId("take-on-step-validation")).toHaveAttribute("aria-current", "step");
    cleanup();
    renderWizard(
      batch({
        sources: [
          source({
            fileName: "book.xlsx",
            fileFormat: "XLSX",
            sheetNames: [
              { name: "Clients", hidden: false },
              { name: "Old", hidden: true },
            ],
            sheetName: "Clients",
          }),
        ],
      })
    );
    expect(screen.getByTestId("take-on-step-sheet")).toHaveAttribute("aria-current", "step");
  });

  it("offers no commit control on Validation — the commit follows the attestation", () => {
    renderWizard(VALIDATED);
    expect(screen.getByTestId("take-on-to-attestation")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /commit/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/commit/i)).not.toBeInTheDocument();
  });
});

describe("UploadStep — client-side refusals", () => {
  it("refuses an .xls file before calling the action", async () => {
    const user = userEvent.setup({ applyAccept: false });
    render(withSWR(<UploadStep dataset={CLIENTS} onUploaded={vi.fn()} onClose={vi.fn()} />));
    await user.upload(
      screen.getByTestId("take-on-file-input"),
      fileOf("clients.xls", 2000, "application/vnd.ms-excel")
    );
    expect(screen.getByTestId("take-on-upload-error")).toHaveTextContent(
      "Save the file as .xlsx or .csv and upload it again."
    );
    expect(screen.getByTestId("take-on-upload-submit")).toBeDisabled();
    expect(actions.uploadTakeOnFile).not.toHaveBeenCalled();
  });

  // FR-3: the server-action body limit is 3 MB, so the take-on file limit is 3 MB (3 000 000 bytes)
  it("refuses a file over 3 MB before calling the action", async () => {
    const user = userEvent.setup({ applyAccept: false });
    render(withSWR(<UploadStep dataset={CLIENTS} onUploaded={vi.fn()} onClose={vi.fn()} />));
    await user.upload(
      screen.getByTestId("take-on-file-input"),
      fileOf("clients.csv", 3_000_001, "text/csv")
    );
    expect(screen.getByTestId("take-on-upload-error")).toHaveTextContent(
      "File exceeds the 3 MB limit (3.1 MB)"
    );
    expect(screen.getByTestId("take-on-upload-submit")).toBeDisabled();
    expect(actions.uploadTakeOnFile).not.toHaveBeenCalled();
  });

  it("accepts a file of exactly 3 MB and states the 3 MB limit", async () => {
    const user = userEvent.setup({ applyAccept: false });
    render(withSWR(<UploadStep dataset={CLIENTS} onUploaded={vi.fn()} onClose={vi.fn()} />));
    expect(screen.getByText(/A \.csv or \.xlsx file of up to 3 MB/)).toBeInTheDocument();
    await user.upload(
      screen.getByTestId("take-on-file-input"),
      fileOf("clients.csv", 3_000_000, "text/csv")
    );
    expect(screen.queryByTestId("take-on-upload-error")).not.toBeInTheDocument();
  });

  it("requires the account, the section and the cutover date for a trust upload", async () => {
    const user = userEvent.setup();
    render(withSWR(<UploadStep dataset={TRUST} onUploaded={vi.fn()} onClose={vi.fn()} />));
    await user.upload(
      screen.getByTestId("take-on-file-input"),
      fileOf("ledger.csv", 500, "text/csv")
    );
    await user.click(screen.getByTestId("take-on-upload-submit"));
    expect(
      await screen.findByText("Choose the trust banking account to take on.")
    ).toBeInTheDocument();
    expect(
      screen.getByText("Choose which part of the trust position this file holds.")
    ).toBeInTheDocument();
    expect(screen.getByText("Enter the cutover date.")).toBeInTheDocument();
    expect(actions.uploadTakeOnFile).not.toHaveBeenCalled();
  });
});

describe("UploadStep — cutover date not saved", () => {
  it("carries a failed savePosition into the upload warnings instead of dropping it", async () => {
    const DEBTORS: TakeOnDatasetCatalogue = {
      ...CLIENTS,
      dataset: "DEBTOR_BALANCES",
      label: "Debtor opening balances",
      requiresCutoverDate: true,
      customFieldEntityType: "INVOICE",
    };
    const uploaded = {
      batch: batch({ dataset: "DEBTOR_BALANCES" }),
      sourceId: "src-1",
      headers: ["Fee note number"],
      warnings: ["Row 9 was blank and was skipped."],
    };
    actions.uploadTakeOnFile.mockResolvedValue({ success: true, data: uploaded });
    actions.savePosition.mockResolvedValue({ success: false, error: "Access denied" });
    const onUploaded = vi.fn();
    const user = userEvent.setup();
    render(withSWR(<UploadStep dataset={DEBTORS} onUploaded={onUploaded} onClose={vi.fn()} />));
    await user.upload(
      screen.getByTestId("take-on-file-input"),
      fileOf("debtors.csv", 500, "text/csv")
    );
    await user.type(screen.getByTestId("take-on-cutover-date"), "2026-10-31");
    await user.click(screen.getByTestId("take-on-upload-submit"));

    await waitFor(() => expect(onUploaded).toHaveBeenCalledTimes(1));
    expect(actions.savePosition).toHaveBeenCalledWith("batch-1", { cutoverDate: "2026-10-31" });
    expect(onUploaded.mock.calls[0][0].warnings).toEqual([
      "Row 9 was blank and was skipped.",
      "The cutover date was not saved: Access denied. Enter it again before the dry run.",
    ]);
  });
});

describe("TakeOnWizard — sheet picker", () => {
  it("marks a hidden sheet and applies the chosen sheet with setSheet", async () => {
    const user = userEvent.setup();
    const xlsx = source({
      fileName: "book.xlsx",
      fileFormat: "XLSX",
      sheetNames: [
        { name: "Clients", hidden: false },
        { name: "Archive", hidden: true },
      ],
      sheetName: "Clients",
    });
    actions.setSheet.mockResolvedValue({
      success: true,
      data: {
        batch: batch({ sources: [{ ...xlsx, sheetName: "Archive" }] }),
        sourceId: "src-1",
        headers: ["Name"],
        warnings: [],
      },
    });
    renderWizard(batch({ sources: [xlsx] }));
    expect(screen.getByTestId("take-on-sheet-hidden-1")).toHaveTextContent("Hidden sheet");
    expect(screen.queryByTestId("take-on-sheet-hidden-0")).not.toBeInTheDocument();
    await user.click(screen.getByTestId("take-on-sheet-1"));
    await user.click(screen.getByTestId("take-on-apply-sheet"));
    expect(actions.setSheet).toHaveBeenCalledWith("batch-1", "src-1", {
      sheetName: "Archive",
      headerRow: 1,
    });
    await waitFor(() =>
      expect(screen.getByTestId("take-on-step-mapping")).toHaveAttribute("aria-current", "step")
    );
  });

  it("stays on Sheet when the chosen header row has no rows below it (a header-only file)", async () => {
    const user = userEvent.setup();
    const empty = source({ fileName: "header-only.csv", rowCount: 0 });
    actions.fetchTakeOnPreview.mockResolvedValue({
      success: true,
      data: { ...PREVIEW, rows: [], warnings: [] },
    });
    actions.setSheet.mockResolvedValue({
      success: true,
      data: {
        batch: batch({ sources: [{ ...empty, headerRow: 2, rowCount: 0 }] }),
        sourceId: "src-1",
        headers: ["Client code"],
        warnings: [],
      },
    });
    renderWizard(batch({ sources: [empty] }));

    await user.clear(screen.getByTestId("take-on-header-row"));
    await user.type(screen.getByTestId("take-on-header-row"), "2");
    await user.click(screen.getByTestId("take-on-apply-sheet"));
    expect(await screen.findByTestId("take-on-sheet-error")).toHaveTextContent(
      "There are no rows below this header row."
    );
    expect(screen.getByTestId("take-on-step-sheet")).toHaveAttribute("aria-current", "step");
    expect(screen.queryByTestId("take-on-change-sheet")).not.toBeInTheDocument();
  });
});

describe("TakeOnWizard — header not on row 1 (OBS F-1)", () => {
  const WARNING =
    "No row could be read with row 1 as the header row. Choose the sheet and the row that holds the column names.";

  it("resumes a file kept with no rows at Sheet, shows the warning and re-reads at the chosen header row", async () => {
    const user = userEvent.setup();
    const empty = source({ fileName: "debtors.csv", rowCount: 0 });
    actions.fetchTakeOnPreview.mockResolvedValue({
      success: true,
      data: { ...PREVIEW, headers: [], rows: [], warnings: [WARNING] },
    });
    actions.setSheet.mockResolvedValue({
      success: true,
      data: {
        batch: batch({ sources: [{ ...empty, headerRow: 3, rowCount: 4 }] }),
        sourceId: "src-1",
        headers: ["Client code"],
        warnings: [],
      },
    });
    actions.setSheet.mockResolvedValueOnce({
      success: false,
      error: "No row of this file could be read.",
    });
    renderWizard(batch({ sources: [empty] }));
    expect(screen.getByTestId("take-on-step-sheet")).toHaveAttribute("aria-current", "step");
    expect(await screen.findByText(WARNING)).toBeInTheDocument();

    // Row 1 unchanged is still sent: the backend explains why it cannot be read.
    await user.click(screen.getByTestId("take-on-apply-sheet"));
    expect(actions.setSheet).toHaveBeenLastCalledWith("batch-1", "src-1", {
      sheetName: null,
      headerRow: 1,
    });
    expect(await screen.findByTestId("take-on-sheet-error")).toHaveTextContent(
      "No row of this file could be read."
    );

    await user.clear(screen.getByTestId("take-on-header-row"));
    await user.type(screen.getByTestId("take-on-header-row"), "3");
    await user.click(screen.getByTestId("take-on-apply-sheet"));
    expect(actions.setSheet).toHaveBeenLastCalledWith("batch-1", "src-1", {
      sheetName: null,
      headerRow: 3,
    });
    await waitFor(() =>
      expect(screen.getByTestId("take-on-step-mapping")).toHaveAttribute("aria-current", "step")
    );
  });

  it("stays on Sheet when the chosen header row has no rows below it (a header-only file)", async () => {
    const user = userEvent.setup();
    const empty = source({ fileName: "header-only.csv", rowCount: 0 });
    actions.fetchTakeOnPreview.mockResolvedValue({
      success: true,
      data: { ...PREVIEW, rows: [], warnings: [] },
    });
    actions.setSheet.mockResolvedValue({
      success: true,
      data: {
        batch: batch({ sources: [{ ...empty, headerRow: 2, rowCount: 0 }] }),
        sourceId: "src-1",
        headers: ["Client code"],
        warnings: [],
      },
    });
    renderWizard(batch({ sources: [empty] }));

    await user.clear(screen.getByTestId("take-on-header-row"));
    await user.type(screen.getByTestId("take-on-header-row"), "2");
    await user.click(screen.getByTestId("take-on-apply-sheet"));
    expect(await screen.findByTestId("take-on-sheet-error")).toHaveTextContent(
      "There are no rows below this header row."
    );
    expect(screen.getByTestId("take-on-step-sheet")).toHaveAttribute("aria-current", "step");
    expect(screen.queryByTestId("take-on-change-sheet")).not.toBeInTheDocument();
  });
});

describe("TakeOnWizard — mapping", () => {
  it("keeps Save disabled until every required field is mapped or defaulted", async () => {
    const user = userEvent.setup();
    renderWizard(batch());
    const save = await screen.findByTestId("take-on-save-mapping");
    expect(save).toBeDisabled();
    expect(screen.getByTestId("take-on-mapping-issues")).toHaveTextContent(
      "Map Client name to a column, or give it a default value."
    );
    await user.type(screen.getByTestId("take-on-default-name"), "Unnamed client");
    expect(screen.getByTestId("take-on-save-mapping")).toBeEnabled();
  });

  it("saves targets, parked columns and parse options in the request shape", async () => {
    const user = userEvent.setup();
    actions.saveMapping.mockResolvedValue({
      success: true,
      data: batch({ status: "MAPPED", sources: [source({ mapped: true })] }),
    });
    renderWizard(batch());
    await user.click(await screen.findByTestId("take-on-map-name"));
    await user.click(await screen.findByRole("option", { name: "Name" }));
    // Leftover columns now: Email (index 1) and Notes (index 2).
    await user.click(screen.getByTestId("take-on-leftover-2"));
    await user.click(await screen.findByRole("option", { name: "Keep as a note on the record" }));
    await user.click(screen.getByTestId("take-on-save-mapping"));
    expect(actions.saveMapping).toHaveBeenCalledWith("batch-1", "src-1", {
      targets: { name: "Name" },
      customFields: {},
      parked: ["Notes"],
      defaults: {},
      parseOptions: { dateFormat: "dd/MM/yyyy", decimalSeparator: "POINT" },
    });
    await waitFor(() =>
      expect(screen.getByTestId("take-on-step-validation")).toHaveAttribute("aria-current", "step")
    );
  });
});

describe("TakeOnWizard — validation", () => {
  it("shows the summary cards from the batch counts, without a block banner", async () => {
    renderWizard(VALIDATED);
    expect(screen.getByTestId("take-on-card-total")).toHaveTextContent("10");
    expect(screen.getByTestId("take-on-card-valid")).toHaveTextContent("7");
    expect(screen.getByTestId("take-on-card-error")).toHaveTextContent("3");
    expect(screen.getByTestId("take-on-card-duplicate")).toHaveTextContent("0");
    expect(screen.getByTestId("take-on-card-blocks")).toHaveTextContent("0");
    expect(screen.queryByTestId("take-on-block-banner")).not.toBeInTheDocument();
    expect(screen.getByTestId("take-on-validate")).toHaveTextContent("Run the dry run again");
    await waitFor(() => expect(actions.fetchTakeOnRows).toHaveBeenCalled());
  });

  it("shows the block banner when rows hold a statutory block", () => {
    renderWizard({ ...VALIDATED, counts: { ...VALIDATED.counts, blocks: 2 } });
    expect(screen.getByTestId("take-on-block-banner")).toHaveTextContent(
      "2 rows hold a statutory block. They cannot be overridden"
    );
  });

  it("re-fetches the rows with the status filter", async () => {
    const user = userEvent.setup();
    actions.fetchTakeOnRows.mockResolvedValue({
      success: true,
      data: {
        content: [
          {
            id: "row-1",
            section: "MAIN",
            rowNumber: 4,
            sourceRef: "C-004",
            status: "ERROR",
            block: false,
            values: { name: "" },
            errors: [{ code: "REQUIRED", field: "name", message: "Client name is required." }],
            warnings: [],
            duplicateOfId: null,
            failureReason: null,
            resultEntityId: null,
          },
        ],
        page: { totalElements: 1, totalPages: 1, size: 50, number: 0 },
      },
    });
    renderWizard(VALIDATED);
    expect(await screen.findByTestId("take-on-row-4")).toHaveTextContent(
      "Client name is required."
    );
    expect(screen.getByTestId("take-on-row-status-4")).toHaveTextContent("Error");
    await user.click(screen.getByTestId("take-on-filter-status"));
    await user.click(await screen.findByRole("option", { name: "Error" }));
    await waitFor(() =>
      expect(actions.fetchTakeOnRows).toHaveBeenLastCalledWith(
        "batch-1",
        expect.objectContaining({ status: "ERROR", page: 0, size: 50 })
      )
    );
  });

  it("marks a valid row that matches an existing client and filters to those rows (OBS-655A-1)", async () => {
    const user = userEvent.setup();
    const row = (
      rowNumber: number,
      status: "VALIDATED" | "ERROR",
      duplicateOfId: string | null
    ) => ({
      id: `row-${rowNumber}`,
      section: "MAIN" as const,
      rowNumber,
      sourceRef: `C-0${rowNumber}`,
      status,
      block: false,
      values: { name: `Client ${rowNumber}` },
      errors:
        status === "ERROR"
          ? [
              {
                code: "DUPLICATE_IN_FILE",
                field: "email",
                message: "Duplicate of row 18 in this file",
              },
            ]
          : [],
      warnings: [],
      duplicateOfId,
      failureReason: null,
      resultEntityId: null,
    });
    actions.fetchTakeOnRows.mockResolvedValue({
      success: true,
      data: {
        content: [
          row(2, "VALIDATED", null),
          row(18, "VALIDATED", "cust-acme"),
          row(19, "ERROR", "cust-acme"),
        ],
        page: { totalElements: 3, totalPages: 1, size: 50, number: 0 },
      },
    });
    renderWizard({ ...VALIDATED, counts: { ...VALIDATED.counts, duplicate: 1 } });
    expect(await screen.findByTestId("take-on-row-status-18")).toHaveTextContent(
      "Duplicate — will be skipped"
    );
    expect(screen.getByTestId("take-on-row-duplicate-18")).toHaveTextContent(
      "Matches a client already in Kazi"
    );
    expect(screen.getByTestId("take-on-row-status-2")).toHaveTextContent("Valid");
    expect(screen.queryByTestId("take-on-row-duplicate-2")).not.toBeInTheDocument();
    // an error row keeps its link to the client but is not a skipped duplicate (D-120)
    expect(screen.getByTestId("take-on-row-status-19")).toHaveTextContent("Error");
    expect(screen.queryByTestId("take-on-row-duplicate-19")).not.toBeInTheDocument();

    await user.click(screen.getByTestId("take-on-filter-status"));
    const options = (await screen.findAllByRole("option")).map((o) => o.textContent);
    expect(options).toEqual([
      "All statuses",
      "Pending",
      "Valid",
      "Duplicate — will be skipped",
      "Error",
    ]);
    await user.click(screen.getByRole("option", { name: "Duplicate — will be skipped" }));
    await waitFor(() =>
      expect(actions.fetchTakeOnRows).toHaveBeenLastCalledWith(
        "batch-1",
        expect.objectContaining({ status: "VALIDATED", duplicatesOnly: true, page: 0 })
      )
    );
  });

  it("runs the dry run and shows the new counts", async () => {
    const user = userEvent.setup();
    const mapped = batch({
      status: "MAPPED",
      sources: [source({ mapped: true })],
      allowedActions: ["REMOVE_SOURCE", "MAP", "VALIDATE", "CANCEL"],
    });
    actions.validateBatch.mockResolvedValue({ success: true, data: VALIDATED });
    renderWizard(mapped);
    expect(screen.queryByTestId("take-on-cards")).not.toBeInTheDocument();
    await user.click(screen.getByTestId("take-on-validate"));
    expect(actions.validateBatch).toHaveBeenCalledWith("batch-1");
    expect(await screen.findByTestId("take-on-card-error")).toHaveTextContent("3");
  });

  it("cancels the take-on after confirmation", async () => {
    const user = userEvent.setup();
    actions.cancelBatch.mockResolvedValue({
      success: true,
      data: { ...VALIDATED, status: "CANCELLED", allowedActions: [] },
    });
    renderWizard(VALIDATED);
    await user.click(screen.getByTestId("take-on-cancel"));
    expect(
      await screen.findByText("The uploaded file and its rows are deleted. This cannot be undone.")
    ).toBeInTheDocument();
    await user.click(screen.getByTestId("take-on-cancel-confirm"));
    expect(actions.cancelBatch).toHaveBeenCalledWith("batch-1");
    expect(await screen.findByTestId("take-on-cancelled")).toBeInTheDocument();
    expect(screen.getByTestId("take-on-batch-status")).toHaveTextContent("Cancelled");
  });
});

describe("TakeOnWizard — clients commit steps (655A)", () => {
  const SIGN_OFF = {
    id: "so-1",
    kind: "FICA_ATTESTATION" as const,
    memberName: "Alice Owner",
    roleName: "Owner",
    statementVersion: "FICA-ATTEST-v1",
    signedAt: "2026-11-23T10:00:00Z",
  };
  const COMPLETED = batch({
    ...VALIDATED,
    status: "COMPLETED",
    signOffs: [SIGN_OFF],
    allowedActions: ["DOWNLOAD_RESULT", "CREATE_PORTAL_ACCESS"],
    completedAt: "2026-11-23T10:06:00Z",
    counts: { ...VALIDATED.counts, created: 6, skipped: 0, failed: 1 },
  });

  it("resumes a completed batch at Results and an attested one at Attestation", () => {
    renderWizard(COMPLETED);
    expect(screen.getByTestId("take-on-step-results")).toHaveAttribute("aria-current", "step");
    expect(screen.getByTestId("take-on-result-created")).toHaveTextContent("6");
    cleanup();
    renderWizard({
      ...VALIDATED,
      signOffs: [SIGN_OFF],
      allowedActions: ["REVALIDATE", "COMMIT", "REVOKE_ATTESTATION", "CANCEL"],
    });
    expect(screen.getByTestId("take-on-step-attestation")).toHaveAttribute("aria-current", "step");
    expect(screen.getByTestId("take-on-commit")).toBeInTheDocument();
  });

  it("moves from Validation to Attestation", async () => {
    const user = userEvent.setup();
    renderWizard(VALIDATED);
    await user.click(screen.getByTestId("take-on-to-attestation"));
    expect(screen.getByTestId("take-on-attestation")).toBeInTheDocument();
  });

  it("shows the clients columns, the failure reason and the commit statuses after a commit", async () => {
    const user = userEvent.setup();
    actions.fetchTakeOnRows.mockResolvedValue({
      success: true,
      data: {
        content: [
          {
            id: "row-9",
            section: "MAIN",
            rowNumber: 9,
            sourceRef: "C-009",
            status: "FAILED",
            block: false,
            values: {
              name: "Dlamini Family Trust",
              email: "trust@dlamini.example",
              ficaClientType: "TRUST",
              riskRating: "UNRATED",
              cddRecordsLocation: "Box 4",
            },
            errors: [],
            warnings: [],
            duplicateOfId: null,
            failureReason: "A client with this email address already exists",
            resultEntityId: null,
          },
        ],
        page: { totalElements: 1, totalPages: 1, size: 50, number: 0 },
      },
    });
    renderWizard(COMPLETED);
    await user.click(screen.getByTestId("take-on-step-validation"));
    expect(await screen.findByTestId("take-on-row-9-ficaClientType")).toHaveTextContent("Trust");
    expect(screen.getByTestId("take-on-row-9-riskRating")).toHaveTextContent("Not risk-rated");
    expect(screen.getByTestId("take-on-row-9-email")).toHaveTextContent("trust@dlamini.example");
    expect(screen.getByTestId("take-on-row-status-9")).toHaveTextContent("Failed");
    expect(screen.getByTestId("take-on-row-failure-9")).toHaveTextContent(
      "A client with this email address already exists"
    );
    await user.click(screen.getByTestId("take-on-filter-status"));
    const options = (await screen.findAllByRole("option")).map((o) => o.textContent);
    expect(options).toEqual([
      "All statuses",
      "Pending",
      "Valid",
      "Error",
      "Created",
      "Duplicate — skipped",
      "Failed",
    ]);
  });
});

describe("TakeOnWizard — trust position (657B)", () => {
  const DEFICIT =
    "The trust creditors (R713\u00a0500,00) exceed the trust money held (R703\u00a0500,00) by R10\u00a0000,00 at the cutover date. This is a trust deficit (Rules 54.14.8 and 54.14.10). Take-on cannot continue until it is resolved in the previous system.";
  const EQ2 =
    "The trust creditors (R413\u00a0500,00) do not equal the cashbook balance at cutover (R403\u00a0500,00): difference R10\u00a0000,00. Every creditor group must be included and must agree to the cent.";

  it("lists the four trust sections on the upload step, one source each, without a cutover-date row", async () => {
    const user = userEvent.setup();
    renderWizard(
      trustBatch({ sources: [trustSource("CLIENT_LEDGER", { fileName: "ledgers.csv" })] }),
      TRUST_CATALOGUE
    );
    await user.click(screen.getByTestId("take-on-step-upload"));
    const ledger = screen.getByTestId("take-on-trust-section-client-ledger");
    expect(ledger).toHaveTextContent("Client and matter balances");
    expect(ledger).toHaveTextContent("ledgers.csv");
    expect(ledger).toHaveAttribute("data-uploaded", "true");
    for (const [kebab, label] of [
      ["unidentified-deposits", "Unidentified deposits"],
      ["investments", "Investments"],
      ["outstanding-items", "Items outstanding at cutover"],
    ]) {
      const row = screen.getByTestId(`take-on-trust-section-${kebab}`);
      expect(row).toHaveTextContent(label);
      expect(row).toHaveTextContent("Not uploaded");
    }
    // The position form on Validation owns the trust cutover date (OP-5).
    expect(screen.queryByTestId("take-on-cutover-date")).not.toBeInTheDocument();
  });

  it("puts the position form and the indicator on Validation, without the generic block banner", () => {
    renderWizard(trustBatch(), TRUST_CATALOGUE);
    expect(screen.getByTestId("take-on-position-form")).toBeInTheDocument();
    expect(screen.getByTestId("take-on-equation")).toBeInTheDocument();
    expect(screen.queryByTestId("take-on-trust-block-banner")).not.toBeInTheDocument();
    expect(screen.queryByTestId("take-on-block-banner")).not.toBeInTheDocument();
    // The over-one-year flag by its label, never the raw code.
    expect(screen.getByTestId("take-on-trust-flags")).toHaveTextContent(
      "Unidentified deposit older than one year"
    );
    expect(screen.queryByText("UNIDENTIFIED_OVER_ONE_YEAR")).not.toBeInTheDocument();
  });

  it("shows the statutory blocks verbatim, the deficit first, with no control to override them", () => {
    renderWizard(
      trustBatch({
        counts: { ...trustBatch().counts, valid: 8, blocks: 3 },
        summary: trustSummaryWith({
          blocks: [
            { code: "TRUST_DEFICIT", message: DEFICIT },
            { code: "CONTROL_TOTAL_EQ2", message: EQ2 },
          ],
        }),
      }),
      TRUST_CATALOGUE
    );
    const banner = screen.getByTestId("take-on-trust-block-banner");
    expect(banner).toHaveAttribute("role", "alert");
    expect(banner).toHaveTextContent("Statutory block — cannot be overridden");
    expect(screen.getByTestId("take-on-trust-block-0").textContent).toBe(DEFICIT);
    expect(screen.getByTestId("take-on-trust-block-1").textContent).toBe(EQ2);
    // 3 blocks = 2 batch-level + 1 row (D-184).
    expect(screen.getByTestId("take-on-trust-row-blocks")).toHaveTextContent(
      "1 row holds a statutory block"
    );
    expect(within(banner).queryByRole("button")).not.toBeInTheDocument();
    expect(within(banner).queryByRole("checkbox")).not.toBeInTheDocument();
    expect(screen.queryByTestId("take-on-block-banner")).not.toBeInTheDocument();
  });

  it("shows the account errors above the table and no indicator when an account check failed", async () => {
    const closed = "Trust Account G is closed. Take on only into open trust accounts.";
    renderWizard(
      trustBatch({
        // D-191: the failed account check is counted as an error of the batch
        counts: { ...trustBatch().counts, error: 1 },
        summary: trustSummaryWith({
          accountErrors: [{ code: "ACCOUNT_CLOSED", message: closed }],
          flags: [],
          equation: null,
        }),
      }),
      TRUST_CATALOGUE
    );
    expect(screen.getByTestId("take-on-account-errors")).toHaveTextContent(closed);
    expect(screen.getByTestId("take-on-card-error")).toHaveTextContent("1");
    expect(screen.queryByTestId("take-on-equation")).not.toBeInTheDocument();
    const errors = screen.getByTestId("take-on-account-errors");
    const filters = screen.getByTestId("take-on-row-filters");
    expect(errors.compareDocumentPosition(filters) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    await waitFor(() => expect(actions.fetchTakeOnRows).toHaveBeenCalled());
  });

  it("shows trust row values by their labels and hides the resolved ids", async () => {
    actions.fetchTakeOnRows.mockResolvedValue({
      success: true,
      data: {
        content: [
          {
            id: "row-t1",
            section: "OUTSTANDING_ITEMS",
            rowNumber: 2,
            sourceRef: null,
            status: "VALIDATED",
            block: false,
            values: {
              kind: "DEPOSIT_IN_TRANSIT",
              reference: "EFT B-0001",
              clientRef: "client.b@example.com",
              clientId: "4b1d7c3e-0000-0000-0000-000000000001",
              clientResolvedBy: "email",
              matterId: "4b1d7c3e-0000-0000-0000-000000000002",
            },
            errors: [],
            warnings: [],
            duplicateOfId: null,
            failureReason: null,
            resultEntityId: null,
          },
        ],
        page: { totalElements: 1, totalPages: 1, size: 50, number: 0 },
      },
    });
    renderWizard(trustBatch(), TRUST_CATALOGUE);
    const row = await screen.findByTestId("take-on-row-2");
    expect(row).toHaveTextContent("Item type: Deposit in transit");
    expect(row).toHaveTextContent("Client matched by: Client email");
    expect(row).not.toHaveTextContent("DEPOSIT_IN_TRANSIT");
    expect(row).not.toHaveTextContent("4b1d7c3e");
  });

  it("shares one in-flight state: a position save disables the dry run, and a dry run the save", async () => {
    const user = userEvent.setup();
    let finishSave: (v: unknown) => void = () => {};
    actions.savePosition.mockReturnValue(new Promise((resolve) => (finishSave = resolve)));
    renderWizard(trustBatch(), TRUST_CATALOGUE);

    await user.click(screen.getByTestId("take-on-position-save"));
    await waitFor(() => expect(screen.getByTestId("take-on-validate")).toBeDisabled());
    finishSave({ success: true, data: trustBatch() }); // unchanged: stays Validated, no dry run
    await waitFor(() => expect(screen.getByTestId("take-on-validate")).toBeEnabled());

    let finishDryRun: (v: unknown) => void = () => {};
    actions.validateBatch.mockReturnValue(new Promise((resolve) => (finishDryRun = resolve)));
    await user.click(screen.getByTestId("take-on-validate"));
    await waitFor(() => expect(screen.getByTestId("take-on-position-save")).toBeDisabled());
    expect(screen.getByTestId("take-on-position-cutover-date")).toBeDisabled();
    expect(screen.getByTestId("take-on-validate")).toHaveTextContent("Running the dry run…");
    finishDryRun({ success: true, data: trustBatch() });
    await waitFor(() => expect(screen.getByTestId("take-on-position-save")).toBeEnabled());
    expect(actions.savePosition).toHaveBeenCalledTimes(1);
    expect(actions.validateBatch).toHaveBeenCalledTimes(1);
  });

  it("returns the row table to the first page when a position save publishes a new batch", async () => {
    const user = userEvent.setup();
    actions.fetchTakeOnRows.mockResolvedValue({
      success: true,
      data: { content: [], page: { totalElements: 120, totalPages: 3, size: 50, number: 0 } },
    });
    const mapped = trustBatch({ status: "MAPPED", summary: null, validatedAt: null });
    const revalidated = trustBatch({ validatedAt: "2026-11-24T10:00:00Z" });
    actions.savePosition.mockResolvedValue({ success: true, data: mapped });
    actions.validateBatch.mockResolvedValue({ success: true, data: revalidated });
    renderWizard(trustBatch(), TRUST_CATALOGUE);

    await user.click(await screen.findByTestId("take-on-rows-next"));
    await waitFor(() =>
      expect(actions.fetchTakeOnRows).toHaveBeenLastCalledWith(
        "batch-657b",
        expect.objectContaining({ page: 1 })
      )
    );

    await user.click(screen.getByTestId("take-on-position-save"));
    await waitFor(() => expect(actions.validateBatch).toHaveBeenCalledWith("batch-657b"));
    await waitFor(() =>
      expect(actions.fetchTakeOnRows).toHaveBeenLastCalledWith(
        "batch-657b",
        expect.objectContaining({ page: 0 })
      )
    );
    expect(screen.getByText(/Page/)).toHaveTextContent("Page 1 of 3");
  });
});

describe("TakeOnWizard — trust sign-off (657C)", () => {
  /** As the owner sees it: SIGN_OFF (owner-only capability + VIEW_TRUST, R-5). */
  const OWNER_VIEW = trustBatch({
    allowedActions: ["REMOVE_SOURCE", "MAP", "REVALIDATE", "CANCEL", "SIGN_OFF"],
  });
  /** As the preparer (admin) sees it once the owner signed: COMMIT and REVOKE_SIGN_OFF. */
  const SIGNED_ADMIN_VIEW = trustBatch({
    signOffs: [TRUST_SIGN_OFF],
    allowedActions: ["REVALIDATE", "CANCEL", "COMMIT", "REVOKE_SIGN_OFF"],
  });

  it("adds a Sign-off step and a Continue to sign-off button on a validated trust batch", async () => {
    const user = userEvent.setup();
    renderWizard(trustBatch(), TRUST_CATALOGUE);
    expect(screen.getByTestId("take-on-step-signOff")).toHaveTextContent("Sign-off");
    expect(screen.getByTestId("take-on-step-signOff")).toBeEnabled();
    expect(screen.queryByTestId("take-on-step-attestation")).not.toBeInTheDocument();
    await user.click(screen.getByTestId("take-on-to-sign-off"));
    expect(screen.getByTestId("take-on-step-signOff")).toHaveAttribute("aria-current", "step");
  });

  it("admin without SIGN_OFF: the status text, no panel and no read of the signer's review", async () => {
    const user = userEvent.setup();
    renderWizard(trustBatch(), TRUST_CATALOGUE);
    await user.click(screen.getByTestId("take-on-to-sign-off"));
    expect(screen.getByTestId("take-on-signoff-status")).toHaveTextContent(
      "A partner or director signs off this take-on from their Home page."
    );
    expect(screen.queryByTestId("takeon-signoff-panel")).not.toBeInTheDocument();
    expect(screen.queryByTestId("take-on-commit")).not.toBeInTheDocument();
    expect(actions.getSignOffReview).not.toHaveBeenCalled();
  });

  it("resumes a signed batch on Sign-off: the admin sees who signed, may withdraw it and commit", async () => {
    const user = userEvent.setup();
    const unsigned = trustBatch();
    actions.revokeSignOff.mockResolvedValue({ success: true, data: unsigned });
    renderWizard(SIGNED_ADMIN_VIEW, TRUST_CATALOGUE);

    expect(screen.getByTestId("take-on-step-signOff")).toHaveAttribute("aria-current", "step");
    expect(screen.getByTestId("take-on-signoff-status")).toHaveTextContent(
      "Signed off by Alice Owner (Owner) on"
    );
    expect(screen.queryByTestId("takeon-signoff-panel")).not.toBeInTheDocument();
    expect(screen.getByTestId("take-on-commit")).toBeInTheDocument();

    await user.click(screen.getByTestId("take-on-signoff-revoke"));
    expect(actions.revokeSignOff).toHaveBeenCalledWith("batch-657b", "sign-off-trust-1");
    await waitFor(() =>
      expect(screen.getByTestId("take-on-signoff-status")).toHaveTextContent(
        "A partner or director signs off"
      )
    );
    expect(screen.queryByTestId("take-on-commit")).not.toBeInTheDocument();
  });

  it("owner with SIGN_OFF: the same panel as the Home sheet; signing re-reads the batch and offers the commit", async () => {
    const user = userEvent.setup();
    actions.getSignOffReview.mockResolvedValue({ success: true, data: signOffReview() });
    actions.signOff.mockResolvedValue({ success: true, data: signedReview() });
    actions.fetchTakeOnBatch.mockResolvedValue({
      success: true,
      data: trustBatch({
        signOffs: [TRUST_SIGN_OFF],
        allowedActions: ["REVALIDATE", "CANCEL", "COMMIT", "WITHDRAW_SIGN_OFF", "REVOKE_SIGN_OFF"],
      }),
    });
    renderWizard(OWNER_VIEW, TRUST_CATALOGUE);

    await user.click(screen.getByTestId("take-on-to-sign-off"));
    await user.click(await screen.findByTestId("takeon-signoff-accept"));
    await user.click(screen.getByTestId("takeon-signoff-submit"));
    await waitFor(() => expect(actions.fetchTakeOnBatch).toHaveBeenCalledWith("batch-657b"));
    expect(await screen.findByTestId("take-on-commit")).toBeInTheDocument();
    // The signer keeps the panel (WITHDRAW_SIGN_OFF), not the preparer's status block.
    expect(screen.getByTestId("takeon-signoff-withdraw")).toBeInTheDocument();
    expect(screen.queryByTestId("take-on-signoff-status")).not.toBeInTheDocument();
  });

  it("disables the step buttons while a position save is in flight (F-657B-6)", async () => {
    const user = userEvent.setup();
    let finishSave: (v: unknown) => void = () => {};
    actions.savePosition.mockReturnValue(new Promise((resolve) => (finishSave = resolve)));
    renderWizard(trustBatch(), TRUST_CATALOGUE);

    await user.click(screen.getByTestId("take-on-position-save"));
    await waitFor(() => expect(screen.getByTestId("take-on-step-upload")).toBeDisabled());
    expect(screen.getByTestId("take-on-step-signOff")).toBeDisabled();
    expect(screen.getByTestId("take-on-to-sign-off")).toBeDisabled();
    finishSave({ success: true, data: trustBatch() });
    await waitFor(() => expect(screen.getByTestId("take-on-step-upload")).toBeEnabled());
    expect(screen.getByTestId("take-on-step-signOff")).toBeEnabled();
  });
});
