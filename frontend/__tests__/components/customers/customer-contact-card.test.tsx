import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { CustomerContactCard } from "@/components/customers/customer-contact-card";
import type { Customer } from "@/lib/types";

const baseCustomer: Customer = {
  id: "c1",
  name: "Acme",
  email: "a@a.com",
  phone: null,
  idNumber: null,
  status: "ACTIVE",
  notes: null,
  createdBy: "m1",
  createdByName: null,
  createdAt: "2024-01-01T00:00:00Z",
  updatedAt: "2024-01-01T00:00:00Z",
};

describe("CustomerContactCard", () => {
  afterEach(() => cleanup());

  it("renders email as mailto link and phone as tel link", () => {
    render(
      <CustomerContactCard
        customer={{
          ...baseCustomer,
          contactName: "Jane Doe",
          contactEmail: "jane@acme.com",
          contactPhone: "+27215550100",
        }}
      />
    );

    const emailLink = screen.getByRole("link", { name: "jane@acme.com" });
    expect(emailLink).toHaveAttribute("href", "mailto:jane@acme.com");

    const phoneLink = screen.getByRole("link", { name: "+27215550100" });
    expect(phoneLink).toHaveAttribute("href", "tel:+27215550100");

    expect(screen.getByText("Jane Doe")).toBeInTheDocument();
  });

  it("renders empty state when all contact fields are null", () => {
    render(<CustomerContactCard customer={baseCustomer} />);
    expect(screen.getByText("No contact on file.")).toBeInTheDocument();
  });
});
