import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { act, cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { InvoiceDetailClient } from "@/components/invoices/invoice-detail-client";
import {
  addLineItem,
  deleteLineItem,
  fetchInvoice,
  updateInvoice,
} from "@/app/(app)/org/[slug]/invoices/invoice-crud-actions";
import { formatCurrency } from "@/lib/format";
import type { InvoiceResponse, InvoiceLineResponse } from "@/lib/types";

const TODAY = "2026-11-28";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/lib/capabilities", () => ({
  RequiresCapability: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useCapabilities: () => ({
    capabilities: new Set(),
    role: "Admin",
    isAdmin: true,
    isOwner: false,
    isLoading: false,
    hasCapability: () => true,
  }),
}));

vi.mock("@/app/(app)/org/[slug]/invoices/invoice-crud-actions", () => ({
  fetchInvoice: vi.fn(),
  updateInvoice: vi.fn(),
  deleteInvoice: vi.fn(),
  addLineItem: vi.fn(),
  updateLineItem: vi.fn(),
  deleteLineItem: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/invoices/invoice-payment-actions", () => ({
  approveInvoice: vi.fn(),
  sendInvoice: vi.fn(),
  recordPayment: vi.fn(),
  voidInvoice: vi.fn(),
  refreshPaymentLink: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/legal/tariffs/actions", () => ({
  fetchTariffSchedules: vi.fn().mockResolvedValue({ content: [], page: { totalElements: 0 } }),
  fetchActiveSchedule: vi.fn().mockResolvedValue(null),
  fetchTariffItems: vi.fn().mockResolvedValue({ content: [], page: { totalElements: 0 } }),
}));

// Only the disbursements module is on, so the page shows "Add Disbursements" and its picker.
vi.mock("@/lib/org-profile", () => ({
  useOrgProfile: () => ({
    verticalProfile: null,
    enabledModules: ["disbursements"],
    terminologyNamespace: null,
    isModuleEnabled: (m: string) => m === "disbursements",
  }),
  OrgProfileProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// The real picker imports a "use server" module; this stand-in reports a successful add the way
// the real one does: by calling onSuccess, which makes the page re-read the fee note.
vi.mock("@/app/(app)/org/[slug]/invoices/[id]/edit/(components)/add-disbursements-picker", () => ({
  AddDisbursementsPicker: ({ open, onSuccess }: { open: boolean; onSuccess: () => void }) =>
    open ? (
      <button type="button" onClick={() => onSuccess()}>
        Confirm disbursements
      </button>
    ) : null,
}));

const NO_TAX: Partial<InvoiceLineResponse> = {
  taxRateId: null,
  taxRateName: null,
  taxRatePercent: null,
  taxAmount: null,
};

function makeLine(overrides?: Partial<InvoiceLineResponse>): InvoiceLineResponse {
  return {
    id: "line-vat",
    matterId: "p1",
    matterName: "Matter",
    timeEntryId: null,
    expenseId: null,
    lineType: "MANUAL",
    description: "Consultation",
    quantity: 1,
    unitPrice: 100,
    amount: 100,
    sortOrder: 0,
    taxRateId: "tr-vat",
    taxRateName: "VAT",
    taxRatePercent: 15,
    taxAmount: 15,
    taxExempt: false,
    tariffItemId: null,
    lineSource: null,
    ...overrides,
  };
}

const VAT_LINE = makeLine();
const NONE_LINE = makeLine({
  id: "line-none",
  description: "Drafting",
  unitPrice: 50,
  amount: 50,
  ...NO_TAX,
});
const DISBURSEMENT_LINE = makeLine({
  id: "line-disb",
  description: "Sheriff fee",
  unitPrice: 200,
  amount: 200,
  lineType: "DISBURSEMENT",
  ...NO_TAX,
});

function makeInvoice(overrides?: Partial<InvoiceResponse>): InvoiceResponse {
  return {
    id: "inv-1",
    clientId: "c1",
    invoiceNumber: null,
    status: "DRAFT",
    currency: "ZAR",
    issueDate: null,
    dueDate: null,
    subtotal: 150,
    taxAmount: 15,
    total: 165,
    notes: null,
    paymentTerms: null,
    poNumber: null,
    taxType: null,
    billingPeriodStart: null,
    billingPeriodEnd: null,
    amountPaid: 0,
    balanceDue: 165,
    payments: [],
    paymentReference: null,
    paidAt: null,
    clientName: "Client",
    clientEmail: "client@example.com",
    clientAddress: null,
    orgName: "Firm",
    createdBy: "user-1",
    createdByName: null,
    approvedBy: null,
    approvedByName: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    lines: [VAT_LINE, NONE_LINE],
    paymentSessionId: null,
    paymentUrl: null,
    paymentDestination: null,
    taxBreakdown: [{ rateName: "VAT", ratePercent: 15, taxableAmount: 100, taxAmount: 15 }],
    taxInclusive: false,
    taxRegistrationNumber: null,
    taxRegistrationLabel: null,
    taxLabel: null,
    hasPerLineTax: true,
    ...overrides,
  };
}

const MIXED = makeInvoice();
const UNTAXED = {
  subtotal: 250,
  taxAmount: 0,
  total: 250,
  balanceDue: 250,
  taxBreakdown: [],
  hasPerLineTax: false,
} satisfies Partial<InvoiceResponse>;

let server: InvoiceResponse;

function renderPage(initial: InvoiceResponse) {
  server = initial;
  return render(
    <InvoiceDetailClient todayIso={TODAY} invoice={initial} slug="acme" isAdmin taxRates={[]} />
  );
}

function totalRow() {
  // The totals block sits below the header's stat card, which also has a "Total" label.
  return screen.getAllByText("Total").at(-1)!.parentElement!.textContent;
}

function headerTaxInput() {
  return screen.getByLabelText("Tax Amount") as HTMLInputElement;
}

describe("InvoiceDetailClient — the header Tax Amount stays on the server's VAT", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchInvoice).mockImplementation(async () => ({ success: true, invoice: server }));
    vi.mocked(updateInvoice).mockImplementation(async () => ({ success: true, invoice: server }));
  });

  afterEach(() => {
    cleanup();
  });

  it("delete the taxed line, add a disbursement: the field shows 0 and Save Changes stores no VAT", async () => {
    const user = userEvent.setup();
    vi.mocked(deleteLineItem).mockImplementation(async () => {
      server = makeInvoice({ ...UNTAXED, lines: [NONE_LINE], subtotal: 50, total: 50 });
      return { success: true };
    });
    renderPage(MIXED);
    expect(screen.queryByLabelText("Tax Amount")).not.toBeInTheDocument();

    expect(totalRow()).toBe(`Total${formatCurrency(165, "ZAR")}`);
    await user.click(screen.getByRole("button", { name: "Delete Consultation" }));

    // The deleted line's R15 VAT leaves the total straight away (not R65 until a reload).
    expect(screen.queryByRole("button", { name: "Delete Consultation" })).not.toBeInTheDocument();
    expect(totalRow()).toBe(`Total${formatCurrency(50, "ZAR")}`);

    server = makeInvoice({ ...UNTAXED, lines: [NONE_LINE, DISBURSEMENT_LINE] });
    await user.click(screen.getByTestId("add-disbursements-button"));
    await user.click(screen.getByRole("button", { name: "Confirm disbursements" }));

    expect(await screen.findByLabelText("Tax Amount")).toHaveValue(0);
    await user.click(screen.getByRole("button", { name: "Save Changes" }));
    expect(vi.mocked(updateInvoice).mock.calls[0][3].taxAmount).toBe(0);
  });

  it("locks the header Tax Amount while a line save is pending, so the save cannot overwrite a typed value", async () => {
    const user = userEvent.setup();
    let landAddLine!: () => void;
    vi.mocked(addLineItem).mockImplementation(
      () =>
        new Promise((resolve) => {
          landAddLine = () =>
            resolve({
              success: true,
              invoice: makeInvoice({ ...UNTAXED, lines: [NONE_LINE, DISBURSEMENT_LINE] }),
            });
        })
    );
    renderPage(makeInvoice({ lines: [VAT_LINE], subtotal: 100, total: 115, balanceDue: 115 }));

    // Start a line save and leave it in flight.
    await user.click(screen.getByRole("button", { name: /add line/i }));
    await user.type(screen.getByPlaceholderText("Line item description"), "Drafting");
    await user.click(screen.getByRole("button", { name: "Add" }));

    // Meanwhile a disbursement add refreshes the note into header-tax mode, revealing the field.
    server = makeInvoice({ ...UNTAXED, lines: [VAT_LINE, DISBURSEMENT_LINE] });
    await user.click(screen.getByTestId("add-disbursements-button"));
    await user.click(screen.getByRole("button", { name: "Confirm disbursements" }));
    const field = await screen.findByLabelText("Tax Amount");

    await user.type(field, "12");
    const seenByUser = headerTaxInput().value;
    const fieldLocked = headerTaxInput().disabled;
    const saveLocked = screen
      .getByRole("button", { name: "Save Changes" })
      .hasAttribute("disabled");

    await act(async () => landAddLine());

    // Whatever the field showed while the save was in flight is what it shows after it lands.
    expect(headerTaxInput().value).toBe(seenByUser);
    expect(fieldLocked).toBe(true);
    expect(saveLocked).toBe(true);
    expect(headerTaxInput()).toBeEnabled();
    expect(screen.getByRole("button", { name: "Save Changes" })).toBeEnabled();
  });
});
