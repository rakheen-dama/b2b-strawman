import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { InvoiceDraftForm } from "@/components/invoices/invoice-draft-form";

function renderForm(overrides: Partial<Parameters<typeof InvoiceDraftForm>[0]> = {}) {
  const props: Parameters<typeof InvoiceDraftForm>[0] = {
    dueDate: "",
    onDueDateChange: vi.fn(),
    notes: "",
    onNotesChange: vi.fn(),
    paymentTerms: "",
    onPaymentTermsChange: vi.fn(),
    taxAmount: "0",
    onTaxAmountChange: vi.fn(),
    poNumber: "",
    onPoNumberChange: vi.fn(),
    taxType: "",
    onTaxTypeChange: vi.fn(),
    billingPeriodStart: "",
    onBillingPeriodStartChange: vi.fn(),
    billingPeriodEnd: "",
    onBillingPeriodEndChange: vi.fn(),
    hasPerLineTax: false,
    isPending: false,
    onSave: vi.fn(),
    ...overrides,
  };
  return { props, ...render(<InvoiceDraftForm {...props} />) };
}

describe("InvoiceDraftForm promoted fields (Epic 464)", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders PO Number input", () => {
    renderForm();
    expect(screen.getByLabelText(/po number/i)).toBeInTheDocument();
  });

  it("renders Tax Type select with VAT, GST, Sales Tax, and None options", () => {
    renderForm();
    const select = screen.getByLabelText(/tax type/i) as HTMLSelectElement;
    expect(select).toBeInTheDocument();
    expect(select.querySelector('option[value="VAT"]')).not.toBeNull();
    expect(select.querySelector('option[value="GST"]')).not.toBeNull();
    expect(select.querySelector('option[value="SALES_TAX"]')).not.toBeNull();
    expect(select.querySelector('option[value="NONE"]')).not.toBeNull();
  });

  it("renders Billing Period Start and End date inputs", () => {
    renderForm();
    const start = screen.getByLabelText(/billing period start/i) as HTMLInputElement;
    const end = screen.getByLabelText(/billing period end/i) as HTMLInputElement;
    expect(start.type).toBe("date");
    expect(end.type).toBe("date");
  });

  it("invokes onPoNumberChange when user types in PO Number", async () => {
    const onPoNumberChange = vi.fn();
    const user = userEvent.setup();
    renderForm({ onPoNumberChange });
    const input = screen.getByLabelText(/po number/i);
    await user.type(input, "P");
    expect(onPoNumberChange).toHaveBeenCalledWith("P");
  });
});
