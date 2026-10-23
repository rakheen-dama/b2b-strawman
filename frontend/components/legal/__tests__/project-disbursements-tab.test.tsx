import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRTestProvider } from "@/lib/swr/test-utils";

// Mock server actions BEFORE importing the component under test.
const mockFetchDisbursements = vi.fn();
const mockFetchUnbilled = vi.fn();
const mockRestore = vi.fn();

vi.mock("@/app/(app)/org/[slug]/legal/disbursements/actions", () => ({
  fetchDisbursements: (...args: unknown[]) => mockFetchDisbursements(...args),
  fetchUnbilledDisbursementsAction: (...args: unknown[]) => mockFetchUnbilled(...args),
  restoreDisbursementAction: (...args: unknown[]) => mockRestore(...args),
  writeOffDisbursementAction: vi.fn(),
  updateDisbursementAction: vi.fn(),
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
import { ProjectDisbursementsTab } from "@/components/legal/project-disbursements-tab";
import type { DisbursementResponse } from "@/lib/api/legal-disbursements";

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
    billingStatus: "UNBILLED",
    invoiceLineId: null,
    writeOffReason: null,
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

function unbilled(totalAmount: number) {
  return {
    matterId: "p1",
    currency: "ZAR" as const,
    items:
      totalAmount > 0
        ? [
            {
              id: "d1",
              incurredDate: "2026-10-01",
              category: "SHERIFF_FEES" as const,
              description: "Sheriff fee",
              amount: totalAmount,
              vatTreatment: "ZERO_RATED_PASS_THROUGH" as const,
              vatAmount: 0,
              supplierName: "Sheriff",
            },
          ]
        : [],
    totalAmount,
    totalVat: 0,
  };
}

function renderTab() {
  render(
    <SWRTestProvider>
      <CapabilityProvider
        capabilities={["WRITE_OFF_DISBURSEMENTS"]}
        role="admin"
        isAdmin={true}
        isOwner={false}
      >
        <ProjectDisbursementsTab projectId="p1" slug="test-org" />
      </CapabilityProvider>
    </SWRTestProvider>
  );
}

describe("ProjectDisbursementsTab unbilled strip (Phase 88 621C)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchDisbursements.mockReset();
    mockFetchUnbilled.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it("mounts the strip inside the tab without adding a second Disbursements heading", async () => {
    mockFetchDisbursements.mockResolvedValue(page([makeDisbursement()]));
    mockFetchUnbilled.mockResolvedValue(unbilled(750));

    renderTab();

    const tab = screen.getByTestId("project-disbursements-tab");
    const strip = await within(tab).findByTestId("unbilled-disbursements-summary");
    expect(mockFetchUnbilled).toHaveBeenCalledWith("p1");
    expect(strip.textContent).toMatch(/Unbilled\s+Disbursements:?\s*R\s?750[.,]00/);
    // Playwright's getByRole("heading", { name: "Disbursements" }) is a substring match.
    expect(within(tab).getAllByRole("heading", { name: /Disbursements/i })).toHaveLength(1);
  });

  it("refreshes the strip together with the list after Undo write-off", async () => {
    const user = userEvent.setup();
    mockFetchDisbursements
      .mockResolvedValueOnce(
        page([makeDisbursement({ billingStatus: "WRITTEN_OFF", writeOffReason: "Dispute" })])
      )
      .mockResolvedValue(page([makeDisbursement()]));
    mockFetchUnbilled.mockResolvedValueOnce(unbilled(0)).mockResolvedValue(unbilled(750));
    mockRestore.mockResolvedValue({ success: true, data: makeDisbursement() });

    renderTab();

    const strip = await screen.findByTestId("unbilled-disbursements-summary");
    expect(strip).toHaveTextContent("No unbilled disbursements");

    await user.click(await screen.findByTestId("disbursement-actions-d1"));
    await user.click(await screen.findByRole("menuitem", { name: "Undo write-off" }));

    await waitFor(() => {
      expect(screen.getByTestId("unbilled-disbursements-total").textContent).toBe("R 750,00");
    });
    expect(mockRestore).toHaveBeenCalledWith("test-org", "d1");
    expect(mockFetchDisbursements).toHaveBeenCalledTimes(2);
    expect(mockFetchUnbilled).toHaveBeenCalledTimes(2);
  });
});
