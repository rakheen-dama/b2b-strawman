import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { InvoiceGenerationDialog } from "@/components/invoices/invoice-generation-dialog";

const mockFetchUnbilledTime = vi.fn();
const mockCreateInvoiceDraft = vi.fn();
const mockValidateInvoiceGeneration = vi.fn();
const mockCheckPrerequisitesAction = vi.fn();

vi.mock("@/app/(app)/org/[slug]/customers/[id]/invoice-actions", () => ({
  fetchUnbilledTime: (...args: unknown[]) => mockFetchUnbilledTime(...args),
  createInvoiceDraft: (...args: unknown[]) => mockCreateInvoiceDraft(...args),
  validateInvoiceGeneration: (...args: unknown[]) =>
    mockValidateInvoiceGeneration(...args),
}));

vi.mock("@/lib/actions/prerequisite-actions", () => ({
  checkPrerequisitesAction: (...args: unknown[]) =>
    mockCheckPrerequisitesAction(...args),
  updateEntityCustomFieldsAction: vi.fn(),
}));

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  cleanup();
});

describe("InvoiceGenerationDialog — prerequisite gate", () => {
  it("shows PrerequisiteModal when check fails", async () => {
    const user = userEvent.setup();
    mockCheckPrerequisitesAction.mockResolvedValueOnce({
      passed: false,
      context: "INVOICE_GENERATION",
      violations: [
        {
          code: "MISSING_FIELD",
          message: "Billing address is required",
          entityType: "CUSTOMER",
          entityId: "c1",
          fieldSlug: null,
          groupName: null,
          resolution: "Add billing address to customer",
        },
      ],
    });

    render(
      <InvoiceGenerationDialog
        customerId="c1"
        customerName="Acme Corp"
        slug="acme"
        defaultCurrency="USD"
      />,
    );

    await user.click(screen.getByRole("button", { name: /New Invoice/i }));

    await waitFor(() => {
      expect(mockCheckPrerequisitesAction).toHaveBeenCalledWith(
        "INVOICE_GENERATION",
        "CUSTOMER",
        "c1",
      );
    });

    await waitFor(() => {
      expect(
        screen.getByText("Prerequisites: Invoice Generation"),
      ).toBeInTheDocument();
    });
  });

  it("opens dialog when check passes", async () => {
    const user = userEvent.setup();
    mockCheckPrerequisitesAction.mockResolvedValueOnce({
      passed: true,
      context: "INVOICE_GENERATION",
      violations: [],
    });

    render(
      <InvoiceGenerationDialog
        customerId="c1"
        customerName="Acme Corp"
        slug="acme"
        defaultCurrency="USD"
      />,
    );

    await user.click(screen.getByRole("button", { name: /New Invoice/i }));

    await waitFor(() => {
      expect(screen.getByText("Generate Invoice")).toBeInTheDocument();
    });

    // PrerequisiteModal should NOT be visible
    expect(
      screen.queryByText("Prerequisites: Invoice Generation"),
    ).not.toBeInTheDocument();
  });
});
