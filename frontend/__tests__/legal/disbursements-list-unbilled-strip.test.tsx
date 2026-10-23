import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRConfig, type Cache } from "swr";

// Mock server actions BEFORE importing the component under test.
const mockFetchDisbursements = vi.fn();
const mockFetchUnbilled = vi.fn();
const mockRestore = vi.fn();

vi.mock("@/app/(app)/org/[slug]/legal/disbursements/actions", () => ({
  fetchDisbursements: (...args: unknown[]) => mockFetchDisbursements(...args),
  fetchUnbilledDisbursementsAction: (...args: unknown[]) => mockFetchUnbilled(...args),
  restoreDisbursementAction: (...args: unknown[]) => mockRestore(...args),
  writeOffDisbursementAction: vi.fn(),
  createDisbursementAction: vi.fn(),
  fetchProjects: vi.fn().mockResolvedValue([]),
  fetchCustomers: vi.fn().mockResolvedValue([]),
  fetchApprovedTrustDisbursementPayments: vi.fn().mockResolvedValue([]),
}));
vi.mock("@/app/(app)/org/[slug]/matters/[id]/actions", () => ({
  initiateUpload: vi.fn(),
  confirmUpload: vi.fn(),
  cancelUpload: vi.fn(),
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock("@/lib/org-profile", () => ({
  useOrgProfile: () => ({
    verticalProfile: "legal-za",
    enabledModules: ["disbursements"],
    terminologyNamespace: null,
    isModuleEnabled: () => true,
  }),
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import { CapabilityProvider } from "@/lib/capabilities";
import { DisbursementsListClient } from "@/app/(app)/org/[slug]/legal/disbursements/disbursements-list-client";
import { unbilledDisbursementsSummaryKey } from "@/components/legal/unbilled-disbursements-summary";
import { formatCurrency } from "@/lib/format";
import type {
  DisbursementResponse,
  UnbilledDisbursementsResponse,
} from "@/lib/api/legal-disbursements";

function makeDisbursement(overrides: Partial<DisbursementResponse> = {}): DisbursementResponse {
  return {
    id: "d1",
    matterId: "p1",
    clientId: "c1",
    category: "SHERIFF_FEES",
    description: "Sheriff fee — service of process",
    amount: 750,
    vatTreatment: "ZERO_RATED_PASS_THROUGH",
    vatAmount: 0,
    paymentSource: "OFFICE_ACCOUNT",
    trustTransactionId: null,
    incurredDate: "2026-10-01",
    supplierName: "Sheriff Johannesburg North",
    supplierReference: null,
    receiptDocumentId: null,
    approvalStatus: "APPROVED",
    approvedBy: "u1",
    approvedAt: "2026-10-02T10:00:00Z",
    approvalNotes: null,
    billingStatus: "WRITTEN_OFF",
    invoiceLineId: null,
    writeOffReason: "Client dispute",
    createdBy: "u1",
    createdAt: "2026-10-01T10:00:00Z",
    updatedAt: "2026-10-01T10:00:00Z",
    ...overrides,
  };
}

function page(rows: DisbursementResponse[]) {
  return {
    content: rows,
    page: { totalElements: rows.length, totalPages: 1, size: 100, number: 0 },
  };
}

function summary(totalAmount: number): UnbilledDisbursementsResponse {
  return {
    matterId: "p1",
    currency: "ZAR",
    items: [
      {
        id: "d0",
        incurredDate: "2026-10-01",
        category: "SHERIFF_FEES",
        description: "Sheriff fee",
        amount: totalAmount,
        vatTreatment: "ZERO_RATED_PASS_THROUGH",
        vatAmount: 0,
        supplierName: "Sheriff",
      },
    ],
    totalAmount,
    totalVat: 0,
  };
}
const STALE = summary(150);
const FRESH = summary(900);

const key = unbilledDisbursementsSummaryKey("p1");
const cacheData = (cache: Map<string, unknown>) =>
  (cache.get(key) as { data?: unknown } | undefined)?.data;
const totalText = () => screen.queryByTestId("unbilled-disbursements-total")?.textContent;

function renderList(cache: Map<string, unknown>) {
  const row = makeDisbursement();
  render(
    <SWRConfig value={{ provider: () => cache as Cache, dedupingInterval: 0 }}>
      <CapabilityProvider
        capabilities={["WRITE_OFF_DISBURSEMENTS"]}
        role="admin"
        isAdmin={true}
        isOwner={false}
      >
        <DisbursementsListClient
          slug="test-org"
          initialDisbursements={[row]}
          initialTotal={1}
          initialProjectNames={{ p1: "Matter One", p2: "Matter Two" }}
        />
      </CapabilityProvider>
    </SWRConfig>
  );
}

async function undoWriteOff(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByTestId("disbursement-actions-d1"));
  await user.click(await screen.findByRole("menuitem", { name: "Undo write-off" }));
  await waitFor(() => expect(mockRestore).toHaveBeenCalledWith("test-org", "d1"));
}

describe("DisbursementsListClient unbilled strip after write-off / undo (Phase 88 621C)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchDisbursements.mockReset();
    mockFetchUnbilled.mockReset();
    mockFetchDisbursements.mockResolvedValue(page([makeDisbursement()]));
    mockRestore.mockResolvedValue({
      success: true,
      data: makeDisbursement({ billingStatus: "UNBILLED", writeOffReason: null }),
    });
  });

  afterEach(() => {
    cleanup();
  });

  it("All Matters: evicts the changed matter's cached total so re-selecting it never paints stale", async () => {
    const user = userEvent.setup();
    const cache = new Map<string, unknown>();
    mockFetchUnbilled.mockResolvedValueOnce(STALE).mockResolvedValue(FRESH);
    renderList(cache);
    const matterFilter = screen.getByLabelText("Matter filter");

    // Filter to Matter One: its strip caches the pre-undo total.
    await user.selectOptions(matterFilter, "p1");
    await waitFor(() => expect(totalText()).toBe(formatCurrency(STALE.totalAmount, "ZAR")));
    // Back to All Matters: no strip mounted, the total stays in the shared cache.
    await user.selectOptions(matterFilter, "");
    expect(screen.queryByTestId("unbilled-disbursements-summary")).not.toBeInTheDocument();
    mockFetchUnbilled.mockClear();

    await undoWriteOff(user);

    await waitFor(() => expect(cacheData(cache)).toBeUndefined());
    expect(mockFetchUnbilled).not.toHaveBeenCalled();

    // Hold the refetch so the first paint after re-selecting is observable.
    let resolveFresh: (value: UnbilledDisbursementsResponse) => void = () => {};
    mockFetchUnbilled.mockReturnValueOnce(
      new Promise<UnbilledDisbursementsResponse>((resolve) => {
        resolveFresh = resolve;
      })
    );
    await user.selectOptions(matterFilter, "p1");
    // First paint after re-selecting: no stale total (the evicted entry has no data).
    expect(totalText()).toBeUndefined();
    resolveFresh(FRESH);
    await waitFor(() => expect(totalText()).toBe(formatCurrency(FRESH.totalAmount, "ZAR")));
    expect(mockFetchUnbilled).toHaveBeenCalledTimes(1);
    expect(mockFetchUnbilled).toHaveBeenCalledWith("p1");
  });

  it("filtered to the changed matter: revalidates the mounted strip in place", async () => {
    const user = userEvent.setup();
    const cache = new Map<string, unknown>();
    mockFetchUnbilled.mockResolvedValueOnce(STALE).mockResolvedValue(FRESH);
    renderList(cache);

    await user.selectOptions(screen.getByLabelText("Matter filter"), "p1");
    await waitFor(() => expect(totalText()).toBe(formatCurrency(STALE.totalAmount, "ZAR")));

    await undoWriteOff(user);

    await waitFor(() => expect(totalText()).toBe(formatCurrency(FRESH.totalAmount, "ZAR")));
    expect(mockFetchUnbilled).toHaveBeenCalledTimes(2);
    expect(cacheData(cache)).toEqual(FRESH);
  });
});
