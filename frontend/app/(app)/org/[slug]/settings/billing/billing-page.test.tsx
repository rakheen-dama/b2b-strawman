import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import BillingPage from "./page";

vi.mock("@clerk/nextjs", () => ({
  PricingTable: (props: Record<string, unknown>) => (
    <div data-testid="pricing-table" data-for={props.for} />
  ),
}));

afterEach(() => cleanup());

describe("BillingPage", () => {
  it("renders heading and description", () => {
    render(<BillingPage />);
    expect(screen.getByText("Billing")).toBeInTheDocument();
    expect(
      screen.getByText(/Manage your organization/)
    ).toBeInTheDocument();
  });

  it("mounts PricingTable with organization scope", () => {
    render(<BillingPage />);
    const table = screen.getByTestId("pricing-table");
    expect(table).toHaveAttribute("data-for", "organization");
  });
});
