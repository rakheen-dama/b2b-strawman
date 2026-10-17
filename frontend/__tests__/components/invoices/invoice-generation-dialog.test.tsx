import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { InvoiceGenerationDialog } from "@/components/invoices/invoice-generation-dialog";
import type { UnbilledTimeResponse, UnbilledDisbursementEntry } from "@/lib/types";

const mockFetchUnbilledTime = vi.fn();
const mockCreateInvoiceDraft = vi.fn();
const mockValidateInvoiceGeneration = vi.fn();

vi.mock("@/app/(app)/org/[slug]/customers/[id]/invoice-actions", () => ({
  fetchUnbilledTime: (...args: unknown[]) => mockFetchUnbilledTime(...args),
  createInvoiceDraft: (...args: unknown[]) => mockCreateInvoiceDraft(...args),
  validateInvoiceGeneration: (...args: unknown[]) => mockValidateInvoiceGeneration(...args),
}));

// Mock prerequisite actions (needed because InvoiceGenerationDialog now imports them)
vi.mock("@/lib/actions/prerequisite-actions", () => ({
  checkPrerequisitesAction: vi
    .fn()
    .mockResolvedValue({ passed: true, context: "INVOICE_GENERATION", violations: [] }),
  updateEntityCustomFieldsAction: vi.fn(),
}));

const sampleUnbilledData: UnbilledTimeResponse = {
  customerId: "c1",
  customerName: "Acme Corp",
  projects: [
    {
      projectId: "p1",
      projectName: "Project Alpha",
      entries: [
        {
          id: "e1",
          taskTitle: "Implement feature",
          memberName: "Alice",
          date: "2026-01-15",
          durationMinutes: 120,
          billingRateSnapshot: 100,
          billingRateCurrency: "USD",
          billableValue: 200,
          description: null,
        },
        {
          id: "e2",
          taskTitle: "Code review",
          memberName: "Bob",
          date: "2026-01-16",
          durationMinutes: 60,
          billingRateSnapshot: 100,
          billingRateCurrency: "USD",
          billableValue: 100,
          description: null,
        },
        {
          id: "e3",
          taskTitle: "Fix bug",
          memberName: "Charlie",
          date: "2026-01-17",
          durationMinutes: 90,
          billingRateSnapshot: 800,
          billingRateCurrency: "ZAR",
          billableValue: 1200,
          description: null,
        },
      ],
      totals: {
        USD: { hours: 3, amount: 300 },
        ZAR: { hours: 1.5, amount: 1200 },
      },
    },
  ],
  grandTotals: {
    USD: { hours: 3, amount: 300 },
    ZAR: { hours: 1.5, amount: 1200 },
  },
  unbilledExpenses: [],
  unbilledExpenseTotals: {},
};

describe("InvoiceGenerationDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default: validation passes with no warnings
    mockValidateInvoiceGeneration.mockResolvedValue({
      success: true,
      checks: [
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
          passed: true,
          message: "All time entries have billing rates",
        },
      ],
    });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders step 1 with date inputs and currency field", async () => {
    const user = userEvent.setup();

    render(
      <InvoiceGenerationDialog
        customerId="c1"
        customerName="Acme Corp"
        slug="acme"
        defaultCurrency="USD"
      />
    );

    await user.click(screen.getByText("New Invoice"));

    expect(screen.getByText("Generate Invoice")).toBeInTheDocument();
    expect(screen.getByLabelText("From Date")).toBeInTheDocument();
    expect(screen.getByLabelText("To Date")).toBeInTheDocument();
    expect(screen.getByLabelText("Currency")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Fetch Unbilled Time" })).toBeInTheDocument();
  });

  it("fetches unbilled time and advances to step 2", async () => {
    mockFetchUnbilledTime.mockResolvedValue({
      success: true,
      data: sampleUnbilledData,
    });
    const user = userEvent.setup();

    render(
      <InvoiceGenerationDialog
        customerId="c1"
        customerName="Acme Corp"
        slug="acme"
        defaultCurrency="USD"
      />
    );

    await user.click(screen.getByText("New Invoice"));
    await user.click(screen.getByRole("button", { name: "Fetch Unbilled Time" }));

    await waitFor(() => {
      expect(mockFetchUnbilledTime).toHaveBeenCalledWith("c1", undefined, undefined);
    });

    await waitFor(() => {
      expect(screen.getByText("Select Unbilled Items")).toBeInTheDocument();
    });

    expect(screen.getByText("Project Alpha")).toBeInTheDocument();
    expect(screen.getByText("Implement feature")).toBeInTheDocument();
  });

  it("displays grouped entries with currency mismatch disabled", async () => {
    mockFetchUnbilledTime.mockResolvedValue({
      success: true,
      data: sampleUnbilledData,
    });
    const user = userEvent.setup();

    render(
      <InvoiceGenerationDialog
        customerId="c1"
        customerName="Acme Corp"
        slug="acme"
        defaultCurrency="USD"
      />
    );

    await user.click(screen.getByText("New Invoice"));
    await user.click(screen.getByRole("button", { name: "Fetch Unbilled Time" }));

    await waitFor(() => {
      expect(screen.getByText("Select Unbilled Items")).toBeInTheDocument();
    });

    // Check the ZAR entry shows currency mismatch indicator
    expect(screen.getByText("(ZAR)")).toBeInTheDocument();

    // The ZAR entry's checkbox should be disabled
    const checkboxes = screen.getAllByRole("checkbox");
    // Find the checkbox for ZAR entry (the disabled one)
    const disabledCheckboxes = checkboxes.filter((cb) => (cb as HTMLInputElement).disabled);
    expect(disabledCheckboxes.length).toBeGreaterThanOrEqual(1);
  });

  it("auto-selects matching currency entries on fetch", async () => {
    mockFetchUnbilledTime.mockResolvedValue({
      success: true,
      data: sampleUnbilledData,
    });
    const user = userEvent.setup();

    render(
      <InvoiceGenerationDialog
        customerId="c1"
        customerName="Acme Corp"
        slug="acme"
        defaultCurrency="USD"
      />
    );

    await user.click(screen.getByText("New Invoice"));
    await user.click(screen.getByRole("button", { name: "Fetch Unbilled Time" }));

    await waitFor(() => {
      expect(screen.getByText("Select Unbilled Items")).toBeInTheDocument();
    });

    // The running total should show the auto-selected USD entries (200 + 100 = 300)
    expect(screen.getByText("2 items selected for Acme Corp")).toBeInTheDocument();
  });

  it("calls createInvoiceDraft on Create Draft click", async () => {
    mockFetchUnbilledTime.mockResolvedValue({
      success: true,
      data: sampleUnbilledData,
    });
    mockCreateInvoiceDraft.mockResolvedValue({ success: true, invoice: {} });
    const user = userEvent.setup();

    render(
      <InvoiceGenerationDialog
        customerId="c1"
        customerName="Acme Corp"
        slug="acme"
        defaultCurrency="USD"
      />
    );

    await user.click(screen.getByText("New Invoice"));
    await user.click(screen.getByRole("button", { name: "Fetch Unbilled Time" }));

    await waitFor(() => {
      expect(screen.getByText("Select Unbilled Items")).toBeInTheDocument();
    });

    // First click "Validate & Create Draft" to run validation
    await user.click(screen.getByRole("button", { name: "Validate & Create Draft" }));

    // After validation, click "Create Draft" to actually create
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Create Draft" })).toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: "Create Draft" }));

    await waitFor(() => {
      expect(mockCreateInvoiceDraft).toHaveBeenCalledWith("acme", "c1", {
        customerId: "c1",
        currency: "USD",
        timeEntryIds: expect.arrayContaining(["e1", "e2"]),
      });
    });
  });

  describe("disbursements (legal vertical)", () => {
    const sheriffFeesDisbursement: UnbilledDisbursementEntry = {
      id: "d1",
      incurredDate: "2026-04-22",
      category: "SHERIFF_FEES",
      description: "Sheriff Fees",
      amount: 1250,
      vatTreatment: "STANDARD",
      vatAmount: 187.5,
      supplierName: "Sheriff Pretoria",
    };

    const zarUnbilledData: UnbilledTimeResponse = {
      customerId: "c1",
      customerName: "Mathebula Partners",
      projects: [
        {
          projectId: "p1",
          projectName: "Matter Alpha",
          entries: [
            {
              id: "t1",
              taskTitle: "Drafting",
              memberName: "Bob",
              date: "2026-04-15",
              durationMinutes: 60,
              billingRateSnapshot: 2250,
              billingRateCurrency: "ZAR",
              billableValue: 2250,
              description: null,
              rateSource: "SNAPSHOT",
            },
          ],
          totals: { ZAR: { hours: 1, amount: 2250 } },
        },
      ],
      grandTotals: { ZAR: { hours: 1, amount: 2250 } },
      unbilledExpenses: [],
      unbilledExpenseTotals: {},
      disbursements: [sheriffFeesDisbursement],
    };

    it("renders disbursements section when API returns disbursements", async () => {
      mockFetchUnbilledTime.mockResolvedValue({ success: true, data: zarUnbilledData });
      const user = userEvent.setup();

      render(
        <InvoiceGenerationDialog
          customerId="c1"
          customerName="Mathebula Partners"
          slug="mathebula"
          defaultCurrency="ZAR"
        />
      );

      await user.click(screen.getByText("New Invoice"));
      await user.click(screen.getByRole("button", { name: "Fetch Unbilled Time" }));

      await waitFor(() => {
        expect(screen.getByText("Select Unbilled Items")).toBeInTheDocument();
      });

      // Section renders with testid hook used by QA / regression harnesses.
      const section = screen.getByTestId("disbursement-selection-section");
      expect(section).toBeInTheDocument();
      expect(screen.getByText("Disbursements")).toBeInTheDocument();
      // Row metadata: description + humanised category label + supplier line.
      // "Sheriff Fees" appears twice (description + humanised category label
      // of SHERIFF_FEES), so use getAllByText.
      expect(screen.getAllByText("Sheriff Fees").length).toBeGreaterThanOrEqual(2);
      expect(screen.getByText(/Sheriff Pretoria/)).toBeInTheDocument();
      // Row amount shows amount + VAT = R 1 437,50. Currency formatting can
      // vary by locale; assert on the numeric content robustly.
      const amountCell = screen.getByText(/1[\s,.]?437[.,]50/);
      expect(amountCell).toBeInTheDocument();
    });

    it("pre-selects disbursements when currency is ZAR", async () => {
      mockFetchUnbilledTime.mockResolvedValue({ success: true, data: zarUnbilledData });
      const user = userEvent.setup();

      render(
        <InvoiceGenerationDialog
          customerId="c1"
          customerName="Mathebula Partners"
          slug="mathebula"
          defaultCurrency="ZAR"
        />
      );

      await user.click(screen.getByText("New Invoice"));
      await user.click(screen.getByRole("button", { name: "Fetch Unbilled Time" }));

      await waitFor(() => {
        expect(screen.getByText("Select Unbilled Items")).toBeInTheDocument();
      });

      // 1 time entry + 1 disbursement = 2 items total.
      expect(screen.getByText("2 items selected for Mathebula Partners")).toBeInTheDocument();
    });

    it("createInvoiceDraft receives disbursementIds for selected rows", async () => {
      mockFetchUnbilledTime.mockResolvedValue({ success: true, data: zarUnbilledData });
      mockCreateInvoiceDraft.mockResolvedValue({ success: true, invoice: {} });
      const user = userEvent.setup();

      render(
        <InvoiceGenerationDialog
          customerId="c1"
          customerName="Mathebula Partners"
          slug="mathebula"
          defaultCurrency="ZAR"
        />
      );

      await user.click(screen.getByText("New Invoice"));
      await user.click(screen.getByRole("button", { name: "Fetch Unbilled Time" }));

      await waitFor(() => {
        expect(screen.getByText("Select Unbilled Items")).toBeInTheDocument();
      });

      await user.click(screen.getByRole("button", { name: "Validate & Create Draft" }));
      await waitFor(() => {
        expect(screen.getByRole("button", { name: "Create Draft" })).toBeInTheDocument();
      });
      await user.click(screen.getByRole("button", { name: "Create Draft" }));

      await waitFor(() => {
        expect(mockCreateInvoiceDraft).toHaveBeenCalledWith(
          "mathebula",
          "c1",
          expect.objectContaining({
            customerId: "c1",
            currency: "ZAR",
            timeEntryIds: expect.arrayContaining(["t1"]),
            disbursementIds: ["d1"],
          })
        );
      });
    });

    it("does not pre-select disbursements when currency is not ZAR", async () => {
      mockFetchUnbilledTime.mockResolvedValue({ success: true, data: zarUnbilledData });
      const user = userEvent.setup();

      render(
        <InvoiceGenerationDialog
          customerId="c1"
          customerName="Mathebula Partners"
          slug="mathebula"
          defaultCurrency="USD"
        />
      );

      await user.click(screen.getByText("New Invoice"));
      await user.click(screen.getByRole("button", { name: "Fetch Unbilled Time" }));

      await waitFor(() => {
        expect(screen.getByText("Select Unbilled Items")).toBeInTheDocument();
      });

      // Section still renders (data is present), but the row is disabled and
      // nothing should be pre-selected. The ZAR time entry is also mismatched
      // against USD, so `0 items selected` is expected.
      expect(screen.getByTestId("disbursement-selection-section")).toBeInTheDocument();
      expect(screen.getByText("0 items selected for Mathebula Partners")).toBeInTheDocument();

      // The disbursement checkbox should be rendered but disabled.
      const checkboxes = screen
        .getAllByRole("checkbox")
        .filter((cb) => (cb as HTMLInputElement).disabled);
      expect(checkboxes.length).toBeGreaterThanOrEqual(1);
    });
  });
});
