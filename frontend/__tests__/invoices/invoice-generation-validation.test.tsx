import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ValidationCheck } from "@/lib/types";

// Mock server actions
const mockValidateInvoiceGeneration = vi.fn();
const mockFetchUnbilledTime = vi.fn();
const mockCreateInvoiceDraft = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/customers/[id]/invoice-actions",
  () => ({
    fetchUnbilledTime: (...args: unknown[]) => mockFetchUnbilledTime(...args),
    createInvoiceDraft: (...args: unknown[]) => mockCreateInvoiceDraft(...args),
    validateInvoiceGeneration: (...args: unknown[]) =>
      mockValidateInvoiceGeneration(...args),
  }),
);

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/org/test-org/customers/123",
}));

// Mock sendInvoice action
const mockSendInvoice = vi.fn();
vi.mock("@/app/(app)/org/[slug]/invoices/actions", () => ({
  sendInvoice: (...args: unknown[]) => mockSendInvoice(...args),
  updateInvoice: vi.fn().mockResolvedValue({ success: true }),
  deleteInvoice: vi.fn().mockResolvedValue({ success: true }),
  approveInvoice: vi.fn().mockResolvedValue({ success: true }),
  recordPayment: vi.fn().mockResolvedValue({ success: true }),
  voidInvoice: vi.fn().mockResolvedValue({ success: true }),
  addLineItem: vi.fn().mockResolvedValue({ success: true }),
  updateLineItem: vi.fn().mockResolvedValue({ success: true }),
  deleteLineItem: vi.fn().mockResolvedValue({ success: true }),
}));

import { InvoiceGenerationDialog } from "@/components/invoices/invoice-generation-dialog";
import { InvoiceDetailClient } from "@/components/invoices/invoice-detail-client";

describe("Invoice Generation Validation", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders_validation_checklist", async () => {
    const user = userEvent.setup();

    const mockChecks: ValidationCheck[] = [
      {
        name: "customer_required_fields",
        severity: "WARNING",
        passed: true,
        message: "All customer required fields are filled",
      },
      {
        name: "org_name",
        severity: "WARNING",
        passed: true,
        message: "Organization name is set",
      },
      {
        name: "time_entry_rates",
        severity: "WARNING",
        passed: false,
        message: "2 time entries without billing rates",
      },
    ];

    mockFetchUnbilledTime.mockResolvedValue({
      success: true,
      data: {
        projects: [
          {
            projectId: "p1",
            projectName: "Test Project",
            entries: [
              {
                id: "e1",
                taskTitle: "Task 1",
                memberName: "Alice",
                date: "2026-02-20",
                durationMinutes: 60,
                billableValue: 1800,
                billingRateCurrency: "ZAR",
                billingRateSnapshot: 1800,
              },
            ],
          },
        ],
        totals: [{ currency: "ZAR", totalMinutes: 60, totalBillable: 1800 }],
      },
    });

    mockValidateInvoiceGeneration.mockResolvedValue({
      success: true,
      checks: mockChecks,
    });

    render(
      <InvoiceGenerationDialog
        customerId="cust-1"
        customerName="Test Customer"
        slug="test-org"
        defaultCurrency="ZAR"
      />,
    );

    // Open dialog
    await user.click(screen.getByText("New Invoice"));

    // Click fetch unbilled time (step 1 -> step 2)
    await user.click(screen.getByText("Fetch Unbilled Time"));

    // Wait for step 2
    await waitFor(() => {
      expect(screen.getByText("Select Time Entries")).toBeInTheDocument();
    });

    // Click validate
    await user.click(screen.getByText("Validate & Create Draft"));

    // Wait for validation checklist to appear
    await waitFor(() => {
      expect(screen.getByTestId("validation-checklist")).toBeInTheDocument();
    });

    // Verify check items rendered
    expect(
      screen.getByText("All customer required fields are filled"),
    ).toBeInTheDocument();
    expect(screen.getByText("Organization name is set")).toBeInTheDocument();
    expect(
      screen.getByText("2 time entries without billing rates"),
    ).toBeInTheDocument();

    // After validation, button should say "Create Draft (1 warnings)"
    expect(screen.getByText("Create Draft (1 warnings)")).toBeInTheDocument();
  });

  it("shows_override_dialog_for_admin", async () => {
    const user = userEvent.setup();

    const mockValidationChecks: ValidationCheck[] = [
      {
        name: "org_name",
        severity: "CRITICAL",
        passed: false,
        message: "Organization name is missing",
      },
    ];

    mockSendInvoice.mockResolvedValue({
      success: false,
      error: "Invoice has validation issues",
      canOverride: true,
      validationChecks: mockValidationChecks,
    });

    const mockInvoice = {
      id: "inv-1",
      customerId: "cust-1",
      invoiceNumber: "INV-001",
      status: "APPROVED" as const,
      currency: "ZAR",
      issueDate: "2026-02-20",
      dueDate: "2026-03-20",
      subtotal: 1800,
      taxAmount: 270,
      total: 2070,
      notes: null,
      paymentTerms: "Net 30",
      paymentReference: null,
      paidAt: null,
      customerName: "Test Corp",
      customerEmail: "test@test.com",
      customerAddress: null,
      orgName: "",
      createdBy: "user-1",
      createdByName: "Alice",
      approvedBy: "user-1",
      approvedByName: "Alice",
      createdAt: "2026-02-20T10:00:00Z",
      updatedAt: "2026-02-20T10:00:00Z",
      lines: [],
    };

    render(
      <InvoiceDetailClient
        invoice={mockInvoice}
        slug="test-org"
        isAdmin={true}
      />,
    );

    // Click Mark as Sent
    await user.click(screen.getByText("Mark as Sent"));

    // Wait for override dialog
    await waitFor(() => {
      expect(screen.getByTestId("send-override-dialog")).toBeInTheDocument();
    });

    // Verify override content
    expect(
      screen.getByText("Validation issues found"),
    ).toBeInTheDocument();
    expect(screen.getByText("Send Anyway")).toBeInTheDocument();

    // Click Send Anyway
    mockSendInvoice.mockResolvedValue({
      success: true,
      invoice: { ...mockInvoice, status: "SENT" },
    });

    await user.click(screen.getByText("Send Anyway"));

    // Verify override call was made with overrideWarnings=true
    await waitFor(() => {
      expect(mockSendInvoice).toHaveBeenCalledWith(
        "test-org",
        "inv-1",
        "cust-1",
        true,
      );
    });
  });
});
