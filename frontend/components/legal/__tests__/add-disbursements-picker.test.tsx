import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock server actions BEFORE importing the component under test.
const mockFetchUnbilled = vi.fn();
const mockAddDisbursementLines = vi.fn();

vi.mock("@/app/(app)/org/[slug]/legal/disbursements/actions", () => ({
  fetchUnbilledDisbursementsAction: (...args: unknown[]) =>
    mockFetchUnbilled(...args),
}));

vi.mock("@/app/(app)/org/[slug]/invoices/invoice-crud-actions", () => ({
  addDisbursementLines: (...args: unknown[]) => mockAddDisbursementLines(...args),
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

import { AddDisbursementsPicker } from "@/app/(app)/org/[slug]/invoices/[id]/edit/(components)/add-disbursements-picker";
import { OrgProfileProvider } from "@/lib/org-profile";

function makeUnbilledItem(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: "d1",
    incurredDate: "2026-02-12",
    category: "SHERIFF_FEES",
    description: "Sheriff service",
    amount: 500,
    vatTreatment: "ZERO_RATED_PASS_THROUGH" as const,
    vatAmount: 0,
    supplierName: "Sheriff Sandton",
    ...overrides,
  };
}

function renderPicker(options: {
  moduleEnabled?: boolean;
  open?: boolean;
  projectId?: string | null;
  onOpenChange?: (open: boolean) => void;
  onSuccess?: () => void;
}) {
  const modules = options.moduleEnabled === false ? [] : ["disbursements"];
  return render(
    <OrgProfileProvider
      verticalProfile="legal"
      enabledModules={modules}
      terminologyNamespace={null}
    >
      <AddDisbursementsPicker
        open={options.open ?? true}
        onOpenChange={options.onOpenChange ?? (() => {})}
        invoiceId="inv-1"
        slug="test-org"
        customerId="c1"
        projectId={options.projectId ?? "p1"}
        onSuccess={options.onSuccess ?? (() => {})}
      />
    </OrgProfileProvider>
  );
}

describe("AddDisbursementsPicker", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchUnbilled.mockResolvedValue({
      projectId: "p1",
      currency: "ZAR",
      items: [
        makeUnbilledItem({ id: "d1", description: "Sheriff service", amount: 500 }),
        makeUnbilledItem({
          id: "d2",
          description: "Advocate fee",
          category: "ADVOCATE_FEES",
          amount: 2000,
          vatTreatment: "STANDARD_15",
          vatAmount: 300,
        }),
      ],
      totalAmount: 2500,
      totalVat: 300,
    });
    mockAddDisbursementLines.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders nothing when disbursements module is disabled", () => {
    renderPicker({ moduleEnabled: false });
    expect(screen.queryByTestId("add-disbursements-picker")).not.toBeInTheDocument();
  });

  it("submits selected disbursement ids via addDisbursementLines", async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();
    renderPicker({ onSuccess });

    await waitFor(() => {
      expect(screen.getByTestId("picker-checkbox-d1")).toBeInTheDocument();
      expect(screen.getByTestId("picker-checkbox-d2")).toBeInTheDocument();
    });

    await user.click(screen.getByTestId("picker-checkbox-d1"));
    await user.click(screen.getByTestId("picker-checkbox-d2"));

    await waitFor(() =>
      expect(screen.getByTestId("picker-selected-count")).toHaveTextContent("2")
    );

    await user.click(screen.getByTestId("picker-submit"));

    await waitFor(() => {
      expect(mockAddDisbursementLines).toHaveBeenCalledWith(
        "test-org",
        "inv-1",
        "c1",
        expect.arrayContaining(["d1", "d2"])
      );
    });
    expect(onSuccess).toHaveBeenCalled();
  });

  it("persists checkbox selection across close+reopen within the same session", async () => {
    const user = userEvent.setup();
    // We render with `open={true}` first, make a selection, then re-render with
    // `open={false}` and back to `open={true}` and verify the selection persists.
    const { rerender } = render(
      <OrgProfileProvider
        verticalProfile="legal"
        enabledModules={["disbursements"]}
        terminologyNamespace={null}
      >
        <AddDisbursementsPicker
          open={true}
          onOpenChange={() => {}}
          invoiceId="inv-1"
          slug="test-org"
          customerId="c1"
          projectId="p1"
          onSuccess={() => {}}
        />
      </OrgProfileProvider>
    );

    await waitFor(() =>
      expect(screen.getByTestId("picker-checkbox-d1")).toBeInTheDocument()
    );
    await user.click(screen.getByTestId("picker-checkbox-d1"));
    await waitFor(() =>
      expect(screen.getByTestId("picker-selected-count")).toHaveTextContent("1")
    );

    // Close the dialog.
    rerender(
      <OrgProfileProvider
        verticalProfile="legal"
        enabledModules={["disbursements"]}
        terminologyNamespace={null}
      >
        <AddDisbursementsPicker
          open={false}
          onOpenChange={() => {}}
          invoiceId="inv-1"
          slug="test-org"
          customerId="c1"
          projectId="p1"
          onSuccess={() => {}}
        />
      </OrgProfileProvider>
    );

    // Reopen.
    rerender(
      <OrgProfileProvider
        verticalProfile="legal"
        enabledModules={["disbursements"]}
        terminologyNamespace={null}
      >
        <AddDisbursementsPicker
          open={true}
          onOpenChange={() => {}}
          invoiceId="inv-1"
          slug="test-org"
          customerId="c1"
          projectId="p1"
          onSuccess={() => {}}
        />
      </OrgProfileProvider>
    );

    await waitFor(() =>
      expect(screen.getByTestId("picker-selected-count")).toHaveTextContent("1")
    );
  });
});
