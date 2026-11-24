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
import type { TakeOnBatch, TakeOnRow } from "@/lib/types";
import { MATTERS_CATALOGUE, mattersBatch } from "./fixtures";

const withSWR = (ui: ReactNode) => (
  <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>{ui}</SWRConfig>
);

const renderWizard = (batch: TakeOnBatch) =>
  render(withSWR(<TakeOnWizard batch={batch} dataset={MATTERS_CATALOGUE} />));

function row(rowNumber: number, overrides: Partial<TakeOnRow>): TakeOnRow {
  return {
    id: `row-${rowNumber}`,
    section: "MAIN",
    rowNumber,
    sourceRef: `M-${rowNumber}`,
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

const UNKNOWN_LEAD =
  "No member of the firm has this email address. The member who commits the take-on will be the matter's lead.";
const ORPHAN = "No client matches C-999. Take the client on first, or correct the reference.";

const ROWS: TakeOnRow[] = [
  row(2, {
    values: {
      name: "Smith v Jones",
      clientRef: "C-001",
      clientEmail: "smith@example.co.za",
      clientId: "6f1c9a2e-0000-4000-8000-000000000001",
      clientResolvedBy: "clientRef",
      referenceNumber: "LIT-2026-001",
      leadMemberEmail: "thandi@firm.example",
      priority: "HIGH",
      workType: "Litigation",
    },
    warnings: [{ code: "LEAD_MEMBER_UNKNOWN", field: "leadMemberEmail", message: UNKNOWN_LEAD }],
  }),
  row(3, {
    values: {
      name: "Estate late Mokoena",
      clientEmail: "mokoena@example.co.za",
      clientId: "6f1c9a2e-0000-4000-8000-000000000002",
      clientResolvedBy: "clientEmail",
      leadMemberEmail: "bob@firm.example",
      leadMemberId: "a1b2c3d4-0000-4000-8000-000000000009",
    },
  }),
  row(4, {
    status: "ERROR",
    values: { name: "Orphan matter", clientRef: "C-999" },
    errors: [{ code: "CLIENT_UNRESOLVED", field: "clientRef", message: ORPHAN }],
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

describe("Matters take-on rows (656A)", () => {
  it("shows the matter, the client the dry run matched and by which reference", async () => {
    renderWizard(mattersBatch());
    const table = await screen.findByTestId("take-on-rows");
    expect(
      within(table)
        .getAllByRole("columnheader")
        .map((h) => h.textContent)
    ).toEqual([
      "Row",
      "Previous system ID",
      "Matter name",
      "Matter reference",
      "Client",
      "Responsible attorney",
      "Status",
      "Other values",
      "Messages",
    ]);
    expect(screen.getByTestId("take-on-row-2-name")).toHaveTextContent("Smith v Jones");
    expect(screen.getByTestId("take-on-row-2-referenceNumber")).toHaveTextContent("LIT-2026-001");
    expect(screen.getByTestId("take-on-row-2-client")).toHaveTextContent(
      "Client previous-system ID: C-001"
    );
    expect(screen.getByTestId("take-on-row-3-client")).toHaveTextContent(
      "Client email: mokoena@example.co.za"
    );
    // An orphan matched no client; the backend's message says why, verbatim.
    expect(screen.getByTestId("take-on-row-4-client")).toHaveTextContent("—");
    expect(screen.getByTestId("take-on-row-4")).toHaveTextContent(ORPHAN);
    // Ids and codes the dry run adds never show; coded values show their label.
    const other = within(screen.getByTestId("take-on-row-2")).getAllByRole("cell")[7];
    expect(other).toHaveTextContent("Priority: High");
    expect(other).toHaveTextContent("Work type: Litigation");
    expect(other.textContent).not.toMatch(/clientId|clientResolvedBy|6f1c9a2e|HIGH|C-001/);
  });

  it("flags a responsible attorney who is not a member of the firm", async () => {
    renderWizard(mattersBatch());
    expect(await screen.findByTestId("take-on-row-2-leadMemberEmail")).toHaveTextContent(
      "thandi@firm.example"
    );
    expect(screen.getByTestId("take-on-row-2-lead-warning")).toHaveTextContent("Not a member");
    expect(screen.getByTestId("take-on-row-2")).toHaveTextContent(UNKNOWN_LEAD);
    expect(screen.getByTestId("take-on-row-3-leadMemberEmail")).toHaveTextContent(
      "bob@firm.example"
    );
    expect(screen.queryByTestId("take-on-row-3-lead-warning")).not.toBeInTheDocument();
  });

  it("shows no values of a row redacted when its client was anonymised", async () => {
    actions.fetchTakeOnRows.mockResolvedValue(
      pageOf([row(2, { values: { redacted: true }, warnings: ROWS[0].warnings })])
    );
    renderWizard(mattersBatch({ status: "COMPLETED", allowedActions: ["DOWNLOAD_RESULT"] }));
    await userEvent.setup().click(screen.getByTestId("take-on-step-validation"));
    for (const key of ["name", "referenceNumber", "client", "leadMemberEmail"]) {
      expect(await screen.findByTestId(`take-on-row-2-${key}`)).toHaveTextContent("—");
    }
    expect(screen.queryByTestId("take-on-row-2-lead-warning")).not.toBeInTheDocument();
  });
});

describe("Matters take-on commit (656A, D-169)", () => {
  it("has no attestation step and commits from under Validation", async () => {
    const user = userEvent.setup();
    const committing = mattersBatch({
      status: "COMMITTING",
      allowedActions: [],
      commitStartedAt: "2026-11-24T08:00:00Z",
    });
    actions.commitBatch.mockResolvedValue({ success: true, data: committing });
    actions.fetchTakeOnBatch.mockResolvedValue({ success: true, data: committing });
    renderWizard(mattersBatch());

    const nav = screen.getByRole("navigation", { name: "Take-on steps" });
    expect(
      within(nav)
        .getAllByRole("button")
        .map((b) => b.textContent)
    ).toEqual(["1Upload", "2Sheet", "3Mapping", "4Validation", "5Results"]);
    expect(screen.getByTestId("take-on-step-validation")).toHaveAttribute("aria-current", "step");
    expect(screen.queryByTestId("take-on-to-attestation")).not.toBeInTheDocument();

    await user.click(screen.getByTestId("take-on-commit"));
    const dialog = await screen.findByTestId("take-on-commit-dialog");
    expect(within(dialog).getByText("Commit matters take-on?")).toBeInTheDocument();
    expect(screen.getByTestId("take-on-commit-body")).toHaveTextContent(
      "13 records will be created, and 1 duplicate skipped. This cannot be undone."
    );
    await user.click(screen.getByTestId("take-on-commit-confirm"));
    expect(actions.commitBatch).toHaveBeenCalledWith("batch-656", "hash-v1");
    expect(await screen.findByTestId("take-on-commit-progress")).toHaveTextContent(
      "Committing… 0 of 14"
    );
    expect(screen.getByTestId("take-on-step-results")).toHaveAttribute("aria-current", "step");
  });

  it("offers no commit until the dry run allows it", () => {
    renderWizard(mattersBatch({ allowedActions: ["REMOVE_SOURCE", "MAP", "REVALIDATE"] }));
    expect(screen.queryByTestId("take-on-commit")).not.toBeInTheDocument();
  });

  it("labels the skipped duplicates and offers no nil-balance filter once committed", async () => {
    const user = userEvent.setup();
    renderWizard(
      mattersBatch({
        status: "COMPLETED",
        allowedActions: ["DOWNLOAD_RESULT"],
        completedAt: "2026-11-24T08:02:00Z",
        counts: { ...mattersBatch().counts, created: 13, skipped: 1, failed: 0 },
      })
    );
    expect(screen.getByTestId("take-on-result-created")).toHaveTextContent("13");
    expect(screen.getByTestId("take-on-result-skipped")).toHaveTextContent("Duplicate — skipped");
    expect(screen.getByTestId("take-on-result-download")).toBeInTheDocument();
    expect(screen.queryByTestId("take-on-portal")).not.toBeInTheDocument();

    await user.click(screen.getByTestId("take-on-step-validation"));
    await user.click(await screen.findByTestId("take-on-filter-status"));
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
