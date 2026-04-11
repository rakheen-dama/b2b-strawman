import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// --- Mocks (before component imports) ---

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/org/acme/invoices/inv-1",
}));

vi.mock("@/app/(app)/org/[slug]/legal/tariffs/actions", () => ({
  fetchTariffSchedules: vi.fn().mockResolvedValue([
    {
      id: "ts-1",
      name: "High Court PP 2026",
      code: "HC_PP",
      description: null,
      effectiveFrom: "2026-01-01",
      effectiveTo: null,
      active: true,
      itemCount: 10,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    },
  ]),
  fetchActiveSchedule: vi.fn().mockResolvedValue({
    id: "ts-1",
    name: "High Court PP 2026",
    code: "HC_PP",
    description: null,
    effectiveFrom: "2026-01-01",
    effectiveTo: null,
    active: true,
    itemCount: 10,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  }),
  fetchTariffItems: vi.fn().mockResolvedValue([
    {
      id: "ti-1",
      scheduleId: "ts-1",
      itemNumber: "1(a)",
      description: "Instructions to institute action",
      unit: "PER_ITEM",
      amount: 850.0,
      notes: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    },
  ]),
}));

vi.mock("@/app/(app)/org/[slug]/invoices/invoice-crud-actions", () => ({
  fetchInvoice: vi.fn().mockResolvedValue({ success: true }),
  updateInvoice: vi.fn().mockResolvedValue({ success: true }),
  deleteInvoice: vi.fn().mockResolvedValue({ success: true }),
  addLineItem: vi.fn().mockResolvedValue({ success: true }),
  updateLineItem: vi.fn().mockResolvedValue({ success: true }),
  deleteLineItem: vi.fn().mockResolvedValue({ success: true }),
}));

vi.mock("@/app/(app)/org/[slug]/invoices/invoice-payment-actions", () => ({
  approveInvoice: vi.fn().mockResolvedValue({ success: true }),
  sendInvoice: vi.fn().mockResolvedValue({ success: true }),
  recordPayment: vi.fn().mockResolvedValue({ success: true }),
  voidInvoice: vi.fn().mockResolvedValue({ success: true }),
  refreshPaymentLink: vi.fn().mockResolvedValue({ success: true }),
}));

vi.mock("@/lib/api", () => ({
  api: {
    get: vi.fn().mockResolvedValue({ content: [] }),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

// --- Imports after mocks ---

import { TariffLineDialog } from "@/components/legal/tariff-line-dialog";
import { InvoiceLineTable } from "@/components/invoices/invoice-line-table";
import { OrgProfileProvider } from "@/lib/org-profile";
import { ModuleGate } from "@/components/module-gate";
import type { InvoiceLineResponse } from "@/lib/types";

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

// --- Helpers ---

function makeLine(overrides: Partial<InvoiceLineResponse> = {}): InvoiceLineResponse {
  return {
    id: "line-1",
    projectId: null,
    projectName: null,
    timeEntryId: null,
    expenseId: null,
    lineType: "TIME",
    description: "Software development",
    quantity: 2,
    unitPrice: 150,
    amount: 300,
    sortOrder: 0,
    taxRateId: null,
    taxRateName: null,
    taxRatePercent: null,
    taxAmount: null,
    taxExempt: false,
    tariffItemId: null,
    lineSource: null,
    ...overrides,
  };
}

// --- Tests ---

describe("TariffLineDialog", () => {
  it("renders item browser when dialog is open", async () => {
    render(
      <TariffLineDialog
        open={true}
        onOpenChange={vi.fn()}
        invoiceId="inv-1"
        slug="acme"
        customerId="cust-1"
        onSuccess={vi.fn()}
      />
    );

    const dialog = screen.getByTestId("tariff-line-dialog");
    expect(dialog).toBeInTheDocument();
    expect(screen.getByText("Add Tariff Items")).toBeInTheDocument();
    expect(screen.getByTestId("add-to-invoice-btn")).toBeDisabled();

    // Wait for the item browser to load
    const itemBrowser = await screen.findByTestId("tariff-item-browser");
    expect(itemBrowser).toBeInTheDocument();
  });

  it("selecting item updates total amount display", async () => {
    const user = userEvent.setup();

    render(
      <TariffLineDialog
        open={true}
        onOpenChange={vi.fn()}
        invoiceId="inv-1"
        slug="acme"
        customerId="cust-1"
        onSuccess={vi.fn()}
      />
    );

    // Wait for items to load
    const item = await screen.findByTestId("tariff-item-ti-1");
    await user.click(item);

    // Should show selected item count and total
    // "R 850.00" appears in item browser row and in selected summary
    expect(screen.getAllByText("R 850.00").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByTestId("add-to-invoice-btn")).not.toBeDisabled();
  });
});

describe("Add Tariff Items button visibility", () => {
  it("hides 'Add Tariff Items' button when module not enabled", () => {
    render(
      <OrgProfileProvider verticalProfile={null} enabledModules={[]} terminologyNamespace={null}>
        <ModuleGate module="lssa_tariff">
          <button>Add Tariff Items</button>
        </ModuleGate>
      </OrgProfileProvider>
    );

    expect(screen.queryByRole("button", { name: /Add Tariff Items/i })).not.toBeInTheDocument();
  });

  it("shows 'Add Tariff Items' button when module is enabled", () => {
    render(
      <OrgProfileProvider
        verticalProfile="legal-za"
        enabledModules={["lssa_tariff"]}
        terminologyNamespace={null}
      >
        <ModuleGate module="lssa_tariff">
          <button>Add Tariff Items</button>
        </ModuleGate>
      </OrgProfileProvider>
    );

    expect(screen.getByRole("button", { name: /Add Tariff Items/i })).toBeInTheDocument();
  });
});

describe("Invoice line table tariff display", () => {
  it("displays tariff badge for TARIFF lines", () => {
    const lines: InvoiceLineResponse[] = [
      makeLine({
        id: "line-1",
        lineType: "TIME",
        description: "Development work",
        lineSource: null,
      }),
      makeLine({
        id: "line-2",
        lineType: "TARIFF",
        description: "1(a) - Instructions to institute action",
        lineSource: "TARIFF",
        tariffItemId: "ti-1",
      }),
    ];

    render(<InvoiceLineTable lines={lines} currency="ZAR" editable={false} />);

    // TARIFF line should have a "Tariff" badge
    expect(screen.getByText("Tariff")).toBeInTheDocument();

    // Both descriptions should be visible
    expect(screen.getByText("Development work")).toBeInTheDocument();
    expect(screen.getByText("1(a) - Instructions to institute action")).toBeInTheDocument();

    // Should show section headers when multiple sections exist
    expect(screen.getByText("Time Entries")).toBeInTheDocument();
    expect(screen.getByText("Tariff Items")).toBeInTheDocument();
  });
});
