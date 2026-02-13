import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemberRatesTable } from "@/components/rates/member-rates-table";
import type { OrgMember, BillingRate, CostRate } from "@/lib/types";

const mockUpdateDefaultCurrency = vi.fn();
const mockCreateBillingRate = vi.fn();
const mockCreateCostRate = vi.fn();
const mockUpdateBillingRate = vi.fn();
const mockUpdateCostRate = vi.fn();
const mockDeleteBillingRate = vi.fn();
const mockDeleteCostRate = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/rates/actions", () => ({
  updateDefaultCurrency: (...args: unknown[]) =>
    mockUpdateDefaultCurrency(...args),
  createBillingRate: (...args: unknown[]) => mockCreateBillingRate(...args),
  updateBillingRate: (...args: unknown[]) => mockUpdateBillingRate(...args),
  deleteBillingRate: (...args: unknown[]) => mockDeleteBillingRate(...args),
  createCostRate: (...args: unknown[]) => mockCreateCostRate(...args),
  updateCostRate: (...args: unknown[]) => mockUpdateCostRate(...args),
  deleteCostRate: (...args: unknown[]) => mockDeleteCostRate(...args),
}));

function makeMembers(): OrgMember[] {
  return [
    {
      id: "m1",
      name: "Alice Johnson",
      email: "alice@example.com",
      avatarUrl: null,
      orgRole: "org:admin",
    },
    {
      id: "m2",
      name: "Bob Smith",
      email: "bob@example.com",
      avatarUrl: null,
      orgRole: "org:member",
    },
  ];
}

const today = new Date().toLocaleDateString("en-CA");

function makeBillingRates(): BillingRate[] {
  return [
    {
      id: "br1",
      memberId: "m1",
      memberName: "Alice Johnson",
      projectId: null,
      projectName: null,
      customerId: null,
      customerName: null,
      scope: "MEMBER_DEFAULT",
      currency: "USD",
      hourlyRate: 150,
      effectiveFrom: "2025-01-01",
      effectiveTo: null,
      createdAt: "2025-01-01T00:00:00Z",
      updatedAt: "2025-01-01T00:00:00Z",
    },
  ];
}

function makeCostRates(): CostRate[] {
  return [
    {
      id: "cr1",
      memberId: "m1",
      memberName: "Alice Johnson",
      currency: "USD",
      hourlyCost: 80,
      effectiveFrom: "2025-01-01",
      effectiveTo: null,
      createdAt: "2025-01-01T00:00:00Z",
      updatedAt: "2025-01-01T00:00:00Z",
    },
  ];
}

describe("RatesSettings", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders currency selector with current currency", () => {
    render(
      <MemberRatesTable
        slug="acme"
        members={makeMembers()}
        billingRates={makeBillingRates()}
        costRates={makeCostRates()}
        defaultCurrency="USD"
      />,
    );

    expect(screen.getByText("Default Currency")).toBeInTheDocument();
    expect(screen.getByRole("combobox")).toHaveTextContent("USD");
  });

  it("renders member rates table with member names", () => {
    render(
      <MemberRatesTable
        slug="acme"
        members={makeMembers()}
        billingRates={makeBillingRates()}
        costRates={makeCostRates()}
        defaultCurrency="USD"
      />,
    );

    expect(screen.getByText("Alice Johnson")).toBeInTheDocument();
    expect(screen.getByText("Bob Smith")).toBeInTheDocument();
    expect(screen.getByText("$150.00")).toBeInTheDocument();
  });

  it("currency change triggers server action", async () => {
    mockUpdateDefaultCurrency.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(
      <MemberRatesTable
        slug="acme"
        members={makeMembers()}
        billingRates={makeBillingRates()}
        costRates={makeCostRates()}
        defaultCurrency="USD"
      />,
    );

    const combobox = screen.getByRole("combobox");
    await user.click(combobox);

    // Select EUR from the dropdown
    const eurOption = await screen.findByText("EUR — Euro");
    await user.click(eurOption);

    await waitFor(() => {
      expect(mockUpdateDefaultCurrency).toHaveBeenCalledWith("acme", "EUR");
    });
  });

  it("add rate dialog opens with form fields", async () => {
    const user = userEvent.setup();

    render(
      <MemberRatesTable
        slug="acme"
        members={makeMembers()}
        billingRates={[]}
        costRates={[]}
        defaultCurrency="USD"
      />,
    );

    // Find the first "Add Rate" button (for Alice)
    const addButton = screen.getByLabelText("Add rate for Alice Johnson");
    await user.click(addButton);

    // Dialog should open with form fields
    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Add Rate" }),
      ).toBeInTheDocument();
    });

    expect(screen.getByText("Rate Type")).toBeInTheDocument();
    expect(screen.getByLabelText("Hourly Rate")).toBeInTheDocument();
    expect(screen.getByLabelText("Effective From")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Create Rate" }),
    ).toBeInTheDocument();
  });

  it("edit rate dialog opens with pre-filled values", async () => {
    const user = userEvent.setup();

    render(
      <MemberRatesTable
        slug="acme"
        members={makeMembers()}
        billingRates={makeBillingRates()}
        costRates={makeCostRates()}
        defaultCurrency="USD"
      />,
    );

    // Click the edit button for Alice's billing rate
    const editButton = screen.getByLabelText("Edit billing rate for Alice Johnson");
    await user.click(editButton);

    // Dialog should open with pre-filled values
    await waitFor(() => {
      expect(
        screen.getByText("Edit Billing Rate", { selector: "h2" }),
      ).toBeInTheDocument();
    });

    const rateInput = screen.getByLabelText("Hourly Rate");
    expect(rateInput).toHaveValue(150);
  });

  it("delete rate shows confirmation and calls server action", async () => {
    mockDeleteBillingRate.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(
      <MemberRatesTable
        slug="acme"
        members={makeMembers()}
        billingRates={makeBillingRates()}
        costRates={makeCostRates()}
        defaultCurrency="USD"
      />,
    );

    // Click the delete button for Alice's billing rate
    const deleteButton = screen.getByLabelText(
      "Delete billing rate for Alice Johnson",
    );
    await user.click(deleteButton);

    // Confirmation dialog should appear
    await waitFor(() => {
      expect(screen.getByText("Delete Billing Rate")).toBeInTheDocument();
    });

    // Confirm deletion
    await user.click(screen.getByRole("button", { name: "Delete" }));

    await waitFor(() => {
      expect(mockDeleteBillingRate).toHaveBeenCalledWith("acme", "br1");
    });
  });
});
