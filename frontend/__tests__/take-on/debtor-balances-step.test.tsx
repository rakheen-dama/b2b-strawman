import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRConfig } from "swr";
import type { ReactNode } from "react";

vi.mock("server-only", () => ({}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
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
  attest: vi.fn(),
  revokeSignOff: vi.fn(),
  commitBatch: vi.fn(),
  createPortalContacts: vi.fn(),
  downloadTakeOnResult: vi.fn(),
  fetchTakeOnBatch: vi.fn(),
  fetchTakeOnPreview: vi.fn(),
  fetchTakeOnRows: vi.fn(),
  fetchTakeOnCustomFields: vi.fn(),
  fetchTakeOnBankingAccounts: vi.fn(),
  downloadTakeOnTemplate: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/take-on-actions", () => actions);

import { TakeOnWizard } from "@/components/take-on/take-on-wizard";
import { formatCurrency, formatDate } from "@/lib/format";
import type { TakeOnBatch, TakeOnRow } from "@/lib/types";
import { DEBTORS_CATALOGUE, debtorsBatch } from "./fixtures";

const withSWR = (ui: ReactNode) => (
  <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>{ui}</SWRConfig>
);

const renderWizard = (batch: TakeOnBatch) =>
  render(withSWR(<TakeOnWizard batch={batch} dataset={DEBTORS_CATALOGUE} />));

function row(rowNumber: number, overrides: Partial<TakeOnRow>): TakeOnRow {
  return {
    id: `row-${rowNumber}`,
    section: "MAIN",
    rowNumber,
    sourceRef: `FN-${rowNumber}`,
    status: "VALIDATED",
    block: false,
    values: null,
    errors: [],
    warnings: [],
    duplicateOfId: null,
    failureReason: null,
    resultEntityId: null,
    ...overrides,
  };
}

const DUE_DEFAULTED =
  "No due date was given. It is set to 15 October 2026, the issue date plus the firm's payment terms of 30 days.";
const DETAILS_MISSING =
  "This client has no billing address or email in Kazi. The brought-forward fee note is taken on as it is; add them before generating a new fee note for this client.";
const NUMBER_EXISTS = "Fee-note number INV-2291 already exists in Kazi";

const ROWS: TakeOnRow[] = [
  row(2, {
    values: {
      clientRef: "C-001",
      clientId: "6f1c9a2e-0000-4000-8000-000000000001",
      clientResolvedBy: "previousSystemId",
      feeNoteNumber: "INV-1001",
      issueDate: "2026-09-15",
      dueDate: "2026-10-15",
      outstandingAmount: "12345.67",
      currency: "ZAR",
      description: "Fees for the estate",
    },
    warnings: [
      { code: "DUE_DATE_DEFAULTED", field: "dueDate", message: DUE_DEFAULTED },
      { code: "CLIENT_DETAILS_MISSING", field: "clientRef", message: DETAILS_MISSING },
    ],
  }),
  row(3, {
    values: {
      clientRef: "mokoena@example.co.za",
      clientId: "6f1c9a2e-0000-4000-8000-000000000002",
      clientResolvedBy: "email",
      feeNoteNumber: "INV-1002",
      issueDate: "2026-08-01",
      dueDate: "2026-08-31",
      outstandingAmount: "800",
      currency: "ZAR",
      matterId: "a1b2c3d4-0000-4000-8000-000000000009",
      matterRef: "LIT-7",
    },
  }),
  row(4, {
    status: "ERROR",
    values: { clientRef: "C-002", feeNoteNumber: "INV-2291", issueDate: "2026-07-01" },
    errors: [{ code: "FEE_NOTE_NUMBER_EXISTS", field: "feeNoteNumber", message: NUMBER_EXISTS }],
  }),
];

const pageOf = (content: TakeOnRow[]) => ({
  success: true,
  data: { content, page: { totalElements: content.length, totalPages: 1, size: 50, number: 0 } },
});

beforeEach(() => {
  actions.fetchTakeOnRows.mockResolvedValue(pageOf(ROWS));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("Debtor opening balances take-on rows (658A)", () => {
  it("shows the fee note, its client, dates and outstanding amount", async () => {
    renderWizard(debtorsBatch());
    const table = await screen.findByTestId("take-on-rows");
    expect(
      within(table)
        .getAllByRole("columnheader")
        .map((h) => h.textContent)
    ).toEqual([
      "Row",
      "Previous system ID",
      "Fee note number",
      "Client",
      "Issue date",
      "Due date",
      "Outstanding",
      "Client details",
      "Status",
      "Other values",
      "Messages",
    ]);
    expect(screen.getByTestId("take-on-row-2-feeNoteNumber")).toHaveTextContent("INV-1001");
    expect(screen.getByTestId("take-on-row-2-client")).toHaveTextContent(
      "Client previous-system ID: C-001"
    );
    expect(screen.getByTestId("take-on-row-3-client")).toHaveTextContent(
      "Client email: mokoena@example.co.za"
    );
    expect(screen.getByTestId("take-on-row-2-issueDate")).toHaveTextContent(
      formatDate("2026-09-15")
    );
    // (textContent, not toHaveTextContent: the currency text holds non-breaking spaces)
    expect(screen.getByTestId("take-on-row-2-outstandingAmount").textContent).toBe(
      formatCurrency(12345.67, "ZAR")
    );
    // Ids and codes the dry run adds never show; the rest stay in "Other values".
    const other = within(screen.getByTestId("take-on-row-3")).getAllByRole("cell")[9];
    expect(other).toHaveTextContent("Matter reference: LIT-7");
    expect(other.textContent).not.toMatch(/clientId|clientResolvedBy|matterId|6f1c9a2e|a1b2c3d4/);
    // A number already in Kazi: the backend's message, verbatim.
    expect(screen.getByTestId("take-on-row-4")).toHaveTextContent(NUMBER_EXISTS);
    expect(screen.getByTestId("take-on-row-4-client")).toHaveTextContent("C-002");
  });

  it("flags a defaulted due date and a client without billing details, warnings only", async () => {
    renderWizard(debtorsBatch());
    expect(await screen.findByTestId("take-on-row-2-due-date-default")).toHaveTextContent(
      "Default"
    );
    expect(screen.getByTestId("take-on-row-2-details-warning")).toHaveTextContent(
      "Address or email missing"
    );
    expect(screen.getByTestId("take-on-row-2")).toHaveTextContent(DUE_DEFAULTED);
    expect(screen.getByTestId("take-on-row-2")).toHaveTextContent(DETAILS_MISSING);
    expect(screen.getByTestId("take-on-row-status-2")).toHaveTextContent("Valid");
    expect(screen.queryByTestId("take-on-row-3-due-date-default")).not.toBeInTheDocument();
    expect(screen.queryByTestId("take-on-row-3-details-warning")).not.toBeInTheDocument();
    expect(screen.getByTestId("take-on-row-3-clientDetails")).toHaveTextContent("—");
  });

  it("shows no values of a row redacted when its client was anonymised", async () => {
    actions.fetchTakeOnRows.mockResolvedValue(
      pageOf([row(2, { values: { redacted: true }, warnings: ROWS[0].warnings })])
    );
    renderWizard(debtorsBatch({ status: "COMPLETED", allowedActions: ["DOWNLOAD_RESULT"] }));
    await userEvent.setup().click(screen.getByTestId("take-on-step-validation"));
    for (const key of [
      "feeNoteNumber",
      "client",
      "issueDate",
      "dueDate",
      "outstandingAmount",
      "clientDetails",
    ]) {
      expect(await screen.findByTestId(`take-on-row-2-${key}`)).toHaveTextContent("—");
    }
    expect(screen.queryByTestId("take-on-row-2-details-warning")).not.toBeInTheDocument();
  });
});

describe("Debtor opening balances commit (658A)", () => {
  it("has no sign-off step and commits from under Validation", async () => {
    const user = userEvent.setup();
    const committing = debtorsBatch({
      status: "COMMITTING",
      allowedActions: [],
      commitStartedAt: "2026-11-24T08:00:00Z",
    });
    actions.commitBatch.mockResolvedValue({ success: true, data: committing });
    actions.fetchTakeOnBatch.mockResolvedValue({ success: true, data: committing });
    renderWizard(debtorsBatch());

    const nav = screen.getByRole("navigation", { name: "Take-on steps" });
    expect(
      within(nav)
        .getAllByRole("button")
        .map((b) => b.textContent)
    ).toEqual(["1Upload", "2Sheet", "3Mapping", "4Validation", "5Results"]);
    expect(screen.queryByTestId("take-on-to-attestation")).not.toBeInTheDocument();

    await user.click(screen.getByTestId("take-on-commit"));
    const dialog = await screen.findByTestId("take-on-commit-dialog");
    expect(within(dialog).getByText("Commit debtor opening balances take-on?")).toBeInTheDocument();
    await user.click(screen.getByTestId("take-on-commit-confirm"));
    expect(actions.commitBatch).toHaveBeenCalledWith("batch-658", "hash-v1");
  });

  it("labels the skipped duplicates and offers no nil-balance filter once committed", async () => {
    const user = userEvent.setup();
    renderWizard(
      debtorsBatch({
        status: "COMPLETED",
        allowedActions: ["DOWNLOAD_RESULT"],
        completedAt: "2026-11-24T08:02:00Z",
        counts: { ...debtorsBatch().counts, created: 2, skipped: 1, failed: 0 },
      })
    );
    expect(screen.getByTestId("take-on-result-skipped")).toHaveTextContent("Duplicate — skipped");

    await user.click(screen.getByTestId("take-on-step-validation"));
    await user.click(await screen.findByTestId("take-on-filter-status"));
    const options = (await screen.findAllByRole("option")).map((o) => o.textContent);
    expect(options).not.toContain("Nil balance — skipped");
    expect(options).toContain("Duplicate — skipped");
  });
});
