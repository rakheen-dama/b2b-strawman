import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";

vi.mock("server-only", () => ({}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/settings/matters",
  useSearchParams: () => new URLSearchParams(),
}));

const { mockGet, MockApiError } = vi.hoisted(() => {
  class MockApiError extends Error {
    constructor(
      public status: number,
      message: string
    ) {
      super(message);
      this.name = "ApiError";
    }
  }
  return { mockGet: vi.fn(), MockApiError };
});
vi.mock("@/lib/api", () => ({ api: { get: mockGet }, ApiError: MockApiError }));

vi.mock("@/app/(app)/org/[slug]/settings/matters/take-on-actions", () => ({
  uploadTakeOnFile: vi.fn(),
  addTakeOnSource: vi.fn(),
  removeTakeOnSource: vi.fn(),
  setSheet: vi.fn(),
  saveMapping: vi.fn(),
  savePosition: vi.fn(),
  validateBatch: vi.fn(),
  cancelBatch: vi.fn(),
  fetchTakeOnBatch: vi.fn(),
  fetchTakeOnPreview: vi.fn().mockResolvedValue({ success: false, error: "not used" }),
  fetchTakeOnRows: vi.fn().mockResolvedValue({
    success: true,
    data: { content: [], page: { totalElements: 0, totalPages: 0, size: 50, number: 0 } },
  }),
  fetchTakeOnCustomFields: vi.fn().mockResolvedValue({ success: true, data: [] }),
  fetchTakeOnBankingAccounts: vi.fn().mockResolvedValue({ success: true, data: [] }),
  downloadTakeOnTemplate: vi.fn(),
  attest: vi.fn(),
  revokeSignOff: vi.fn(),
  commitBatch: vi.fn(),
  createPortalContacts: vi.fn(),
  downloadTakeOnResult: vi.fn(),
}));

import { renderTakeOnTab } from "@/app/(app)/org/[slug]/settings/matters/_tabs/take-on-tab";
import type { TakeOnBatch, TakeOnBatchSummary, TakeOnDatasetCatalogue } from "@/lib/types";

const BATCH_ID = "0b6a2f4e-1c3d-4e5f-8a9b-0c1d2e3f4a5b";

function catalogue(
  dataset: TakeOnDatasetCatalogue["dataset"],
  completedBatches: number,
  sections: TakeOnDatasetCatalogue["sections"] = [{ section: "MAIN", label: "File", fields: [] }]
): TakeOnDatasetCatalogue {
  return {
    dataset,
    label: dataset,
    requiresCutoverDate: dataset === "TRUST_POSITION" || dataset === "DEBTOR_BALANCES",
    requiresTrustAccount: dataset === "TRUST_POSITION",
    customFieldEntityType: null,
    sections,
    completedBatches,
  };
}

// Deliberately out of order: the picker orders clients → matters → trust → debtors itself.
const DATASETS: TakeOnDatasetCatalogue[] = [
  catalogue("DEBTOR_BALANCES", 0),
  catalogue("TRUST_POSITION", 0, [
    { section: "CLIENT_LEDGER", label: "", fields: [] },
    { section: "UNIDENTIFIED_DEPOSITS", label: "", fields: [] },
    { section: "INVESTMENTS", label: "", fields: [] },
    { section: "OUTSTANDING_ITEMS", label: "", fields: [] },
  ]),
  catalogue("CLIENTS", 2),
  catalogue("MATTERS", 1),
];

const SUMMARY: TakeOnBatchSummary = {
  id: BATCH_ID,
  dataset: "CLIENTS",
  status: "VALIDATED",
  name: "Clients from the previous system",
  cutoverDate: null,
  totalRows: 12,
  validRows: 10,
  errorRows: 2,
  createdByName: "Alice Owner",
  createdAt: "2026-11-23T08:00:00Z",
};

const BATCH: TakeOnBatch = {
  id: BATCH_ID,
  dataset: "CLIENTS",
  status: "VALIDATED",
  name: "Clients from the previous system",
  cutoverDate: null,
  trustAccountId: null,
  sources: [],
  counts: {
    total: 12,
    valid: 10,
    error: 2,
    duplicate: 0,
    blocks: 0,
    created: 0,
    skipped: 0,
    failed: 0,
  },
  validationHash: "h",
  validatedAt: "2026-11-23T09:00:00Z",
  allowedActions: ["ADD_SOURCE", "CANCEL"],
  createdAt: "2026-11-23T08:00:00Z",
  cancelledAt: null,
  commitStartedAt: null,
  completedAt: null,
  lastError: null,
  signOffs: [],
  attestationStatement: null,
  summary: null,
};

function routeGets(batch: () => Promise<unknown>) {
  mockGet.mockImplementation(async (endpoint: string) => {
    if (endpoint === "/api/take-on/datasets") return DATASETS;
    if (endpoint.startsWith("/api/take-on/batches?")) {
      return {
        content: [SUMMARY],
        page: { totalElements: 1, totalPages: 1, size: 20, number: 0 },
      };
    }
    if (endpoint.startsWith("/api/take-on/batches/")) return batch();
    throw new Error(`unexpected GET ${endpoint}`);
  });
}

async function renderTab(searchParams: Record<string, string> = { tab: "take-on" }) {
  render(await renderTakeOnTab({ slug: "acme", searchParams }));
}

describe("Matters › Take-on tab", () => {
  beforeEach(() => routeGets(async () => BATCH));

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("shows the four dataset cards in take-on order with the guidance line and completed counts", async () => {
    await renderTab();
    expect(screen.getByTestId("take-on-header")).toHaveTextContent("Take-on");
    expect(screen.getByTestId("take-on-guidance")).toHaveTextContent(
      "Take on clients first, then matters, then the trust position, then debtors."
    );
    const cards = screen
      .getAllByTestId(/^take-on-dataset-(?!picker)/)
      .map((card) => card.getAttribute("data-testid"));
    expect(cards).toEqual([
      "take-on-dataset-clients",
      "take-on-dataset-matters",
      "take-on-dataset-trust-position",
      "take-on-dataset-debtor-balances",
    ]);
    expect(
      within(screen.getByTestId("take-on-dataset-trust-position")).getByText(
        "Trust opening position"
      )
    ).toBeInTheDocument();
    expect(screen.getByTestId("take-on-completed-clients")).toHaveTextContent("2 completed");
    expect(screen.getByTestId("take-on-completed-matters")).toHaveTextContent("1 completed");
    expect(screen.getByTestId("take-on-start-debtor-balances")).toBeInTheDocument();
    // The trust position has one template per section.
    expect(
      screen.getByTestId("take-on-template-trust-position-unidentified-deposits")
    ).toHaveTextContent("Template: Unidentified deposits");
  });

  it("lists the history with a link to each batch's wizard", async () => {
    await renderTab();
    const link = screen.getByTestId(`take-on-history-link-${BATCH_ID}`);
    expect(link).toHaveTextContent("Clients from the previous system");
    expect(link).toHaveAttribute(
      "href",
      `/org/acme/settings/matters?tab=take-on&batch=${BATCH_ID}`
    );
    expect(screen.getByTestId(`take-on-history-status-${BATCH_ID}`)).toHaveTextContent("Validated");
  });

  it("opens the wizard for ?batch= with a way back", async () => {
    await renderTab({ tab: "take-on", batch: BATCH_ID });
    expect(screen.getByTestId("take-on-wizard")).toBeInTheDocument();
    expect(screen.getByTestId("take-on-back")).toHaveAttribute(
      "href",
      "/org/acme/settings/matters?tab=take-on"
    );
    expect(screen.queryByTestId("take-on-dataset-picker")).not.toBeInTheDocument();
  });

  it("falls back to the picker with a notice when the batch is not available", async () => {
    routeGets(async () => {
      throw new MockApiError(404, "Take-on batch not found");
    });
    await renderTab({ tab: "take-on", batch: BATCH_ID });
    expect(screen.getByTestId("take-on-batch-notice")).toHaveTextContent(
      "That take-on batch is not available."
    );
    expect(screen.getByTestId("take-on-dataset-picker")).toBeInTheDocument();
    expect(screen.queryByTestId("take-on-wizard")).not.toBeInTheDocument();
  });

  it("treats a malformed ?batch= as not available without calling the backend", async () => {
    await renderTab({ tab: "take-on", batch: "not-a-uuid" });
    expect(screen.getByTestId("take-on-batch-notice")).toBeInTheDocument();
    expect(mockGet).not.toHaveBeenCalledWith(expect.stringMatching(/batches\/not-a-uuid/));
  });

  it("renders a one-line notice instead of throwing on a 403", async () => {
    mockGet.mockRejectedValue(new MockApiError(403, "Access denied"));
    await renderTab();
    expect(screen.getByTestId("take-on-load-notice")).toHaveTextContent(
      "You don't have access to take-on."
    );
    expect(screen.queryByTestId("take-on-dataset-picker")).not.toBeInTheDocument();
  });
});
