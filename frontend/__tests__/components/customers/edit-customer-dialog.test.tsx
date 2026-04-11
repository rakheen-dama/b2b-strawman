import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EditCustomerDialog } from "@/components/customers/edit-customer-dialog";
import type { Customer } from "@/lib/types";

const mockUpdateCustomer = vi.fn();

vi.mock("@/app/(app)/org/[slug]/customers/actions", () => ({
  updateCustomer: (...args: unknown[]) => mockUpdateCustomer(...args),
}));

const mockCustomer: Customer = {
  id: "c1",
  name: "Acme Corp",
  email: "contact@acme.com",
  phone: "+1 555-0100",
  idNumber: "CUS-001",
  status: "ACTIVE",
  notes: "Important customer",
  createdBy: "m1",
  createdAt: "2024-01-15T00:00:00Z",
  updatedAt: "2024-01-15T00:00:00Z",
};

describe("EditCustomerDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("pre-populates form with customer data", async () => {
    const user = userEvent.setup();

    render(
      <EditCustomerDialog customer={mockCustomer} slug="acme">
        <button>Edit Customer</button>
      </EditCustomerDialog>
    );

    await user.click(screen.getByText("Edit Customer"));

    expect(screen.getByLabelText("Name")).toHaveValue("Acme Corp");
    expect(screen.getByLabelText("Email")).toHaveValue("contact@acme.com");
  });

  it("calls updateCustomer on submit", async () => {
    mockUpdateCustomer.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(
      <EditCustomerDialog customer={mockCustomer} slug="acme">
        <button>Edit Customer</button>
      </EditCustomerDialog>
    );

    await user.click(screen.getByText("Edit Customer"));

    const nameInput = screen.getByLabelText("Name");
    await user.clear(nameInput);
    await user.type(nameInput, "Acme Inc");
    await user.click(screen.getByText("Save Changes"));

    await waitFor(() => {
      expect(mockUpdateCustomer).toHaveBeenCalledWith(
        "acme",
        "c1",
        expect.objectContaining({ name: "Acme Inc", email: "contact@acme.com" })
      );
    });
  });

  it("submits with all promoted fields populated", async () => {
    mockUpdateCustomer.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    const customerWithPromoted: Customer = {
      ...mockCustomer,
      addressLine1: "100 Main St",
      city: "Cape Town",
      country: "ZA",
      contactEmail: "jane@acme.com",
      entityType: "PTY_LTD",
    };

    render(
      <EditCustomerDialog customer={customerWithPromoted} slug="acme">
        <button>Edit Customer</button>
      </EditCustomerDialog>
    );

    await user.click(screen.getByText("Edit Customer"));

    // Verify promoted fields are pre-populated
    expect(screen.getByLabelText(/address line 1/i)).toHaveValue("100 Main St");
    expect(screen.getByLabelText(/^city$/i)).toHaveValue("Cape Town");
    expect(screen.getByLabelText(/country/i)).toHaveValue("ZA");

    await user.click(screen.getByText("Save Changes"));

    await waitFor(() => {
      expect(mockUpdateCustomer).toHaveBeenCalledWith(
        "acme",
        "c1",
        expect.objectContaining({
          addressLine1: "100 Main St",
          city: "Cape Town",
          country: "ZA",
          contactEmail: "jane@acme.com",
          entityType: "PTY_LTD",
        })
      );
    });
  });

  it("displays error when update fails", async () => {
    mockUpdateCustomer.mockResolvedValue({ success: false, error: "Email already exists" });
    const user = userEvent.setup();

    render(
      <EditCustomerDialog customer={mockCustomer} slug="acme">
        <button>Edit Customer</button>
      </EditCustomerDialog>
    );

    await user.click(screen.getByText("Edit Customer"));
    await user.click(screen.getByText("Save Changes"));

    await waitFor(() => {
      expect(screen.getByText("Email already exists")).toBeInTheDocument();
    });
  });
});
