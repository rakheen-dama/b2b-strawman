import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { CustomerAddressBlock } from "@/components/customers/customer-address-block";
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

describe("CustomerAddressBlock", () => {
  afterEach(() => cleanup());

  it("renders formatted address when all fields are set", () => {
    render(
      <CustomerAddressBlock
        customer={{
          ...baseCustomer,
          addressLine1: "100 Main St",
          addressLine2: "Suite 5",
          city: "Cape Town",
          stateProvince: "Western Cape",
          postalCode: "8001",
          country: "ZA",
        }}
      />
    );
    expect(screen.getByText("100 Main St")).toBeInTheDocument();
    expect(screen.getByText("Suite 5")).toBeInTheDocument();
    expect(
      screen.getByText(
        (content) =>
          content.includes("Cape Town") &&
          content.includes("Western Cape") &&
          content.includes("8001")
      )
    ).toBeInTheDocument();
    expect(screen.getByText("ZA")).toBeInTheDocument();
  });

  it("renders empty state when no address fields are set", () => {
    render(<CustomerAddressBlock customer={baseCustomer} />);
    expect(screen.getByText("No address on file.")).toBeInTheDocument();
  });

  it("skips empty lines and joins city/state/postal", () => {
    render(
      <CustomerAddressBlock
        customer={{
          ...baseCustomer,
          addressLine1: "10 Oak Rd",
          addressLine2: null,
          city: "Johannesburg",
          stateProvince: null,
          postalCode: "2000",
          country: "ZA",
        }}
      />
    );
    expect(screen.getByText("10 Oak Rd")).toBeInTheDocument();
    expect(screen.getByText("Johannesburg, 2000")).toBeInTheDocument();
    expect(screen.getByText("ZA")).toBeInTheDocument();
  });
});
