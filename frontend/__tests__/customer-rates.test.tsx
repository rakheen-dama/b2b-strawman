import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CustomerRatesTab } from "@/components/rates/customer-rates-tab";
import type { BillingRate, OrgMember } from "@/lib/types";

const mockCreateCustomerBillingRate = vi.fn();
const mockUpdateCustomerBillingRate = vi.fn();
const mockDeleteCustomerBillingRate = vi.fn();

vi.mock("@/app/(app)/org/[slug]/customers/[id]/rate-actions", () => ({
  createCustomerBillingRate: (...args: unknown[]) =>
    mockCreateCustomerBillingRate(...args),
  updateCustomerBillingRate: (...args: unknown[]) =>
    mockUpdateCustomerBillingRate(...args),
  deleteCustomerBillingRate: (...args: unknown[]) =>
    mockDeleteCustomerBillingRate(...args),
}));

function makeOrgMembers(): OrgMember[] {
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

function makeCustomerBillingRates(): BillingRate[] {
  return [
    {
      id: "cbr1",
      memberId: "m1",
      memberName: "Alice Johnson",
      projectId: null,
      projectName: null,
      customerId: "c1",
      customerName: "Acme Corp",
      scope: "CUSTOMER_OVERRIDE",
      currency: "EUR",
      hourlyRate: 175,
      effectiveFrom: "2025-03-01",
      effectiveTo: "2025-12-31",
      createdAt: "2025-03-01T00:00:00Z",
      updatedAt: "2025-03-01T00:00:00Z",
    },
  ];
}

describe("CustomerRatesTab", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders empty state when no customer rate overrides exist", () => {
    render(
      <CustomerRatesTab
        billingRates={[]}
        members={makeOrgMembers()}
        customerId="c1"
        slug="acme"
        defaultCurrency="USD"
      />,
    );

    expect(
      screen.getByText("No customer rate overrides"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "Add billing rate overrides for team members when working for this customer.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Add Override/ }),
    ).toBeInTheDocument();
  });

  it("renders customer rate overrides table with rate data", () => {
    render(
      <CustomerRatesTab
        billingRates={makeCustomerBillingRates()}
        members={makeOrgMembers()}
        customerId="c1"
        slug="acme"
        defaultCurrency="USD"
      />,
    );

    expect(
      screen.getByText("Customer Rate Overrides"),
    ).toBeInTheDocument();
    expect(screen.getByText("Alice Johnson")).toBeInTheDocument();
    expect(screen.getByText("EUR")).toBeInTheDocument();
  });

  it("opens add customer rate override dialog", async () => {
    const user = userEvent.setup();

    render(
      <CustomerRatesTab
        billingRates={[]}
        members={makeOrgMembers()}
        customerId="c1"
        slug="acme"
        defaultCurrency="USD"
      />,
    );

    await user.click(screen.getByRole("button", { name: /Add Override/ }));

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Add Customer Rate Override" }),
      ).toBeInTheDocument();
    });

    expect(screen.getByLabelText("Member")).toBeInTheDocument();
    expect(screen.getByLabelText("Hourly Rate")).toBeInTheDocument();
    expect(screen.getByLabelText("Effective From")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Create Override" }),
    ).toBeInTheDocument();
  });

  it("delete customer rate shows confirmation and calls server action", async () => {
    mockDeleteCustomerBillingRate.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(
      <CustomerRatesTab
        billingRates={makeCustomerBillingRates()}
        members={makeOrgMembers()}
        customerId="c1"
        slug="acme"
        defaultCurrency="USD"
      />,
    );

    const deleteButton = screen.getByLabelText(
      "Delete customer rate for Alice Johnson",
    );
    await user.click(deleteButton);

    await waitFor(() => {
      expect(
        screen.getByText("Delete Customer Rate Override"),
      ).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Delete" }));

    await waitFor(() => {
      expect(mockDeleteCustomerBillingRate).toHaveBeenCalledWith(
        "acme",
        "c1",
        "cbr1",
      );
    });
  });
});
