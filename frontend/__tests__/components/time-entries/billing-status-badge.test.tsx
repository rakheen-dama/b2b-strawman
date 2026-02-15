import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { BillingStatusBadge } from "@/components/time-entries/billing-status-badge";

describe("BillingStatusBadge", () => {
  afterEach(() => cleanup());

  it("renders green Billed badge with link when invoiceId is set", () => {
    render(
      <BillingStatusBadge
        billable={true}
        invoiceId="inv-123"
        invoiceNumber="INV-0001"
        slug="acme"
      />,
    );

    const badge = screen.getByText("Billed");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("success");
    // Should be wrapped in a link
    const link = badge.closest("a");
    expect(link).toHaveAttribute("href", "/org/acme/invoices/inv-123");
  });

  it("renders gray Unbilled badge when billable with no invoiceId", () => {
    render(
      <BillingStatusBadge
        billable={true}
        invoiceId={null}
        invoiceNumber={null}
        slug="acme"
      />,
    );

    const badge = screen.getByText("Unbilled");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("neutral");
  });

  it("renders nothing when not billable", () => {
    const { container } = render(
      <BillingStatusBadge
        billable={false}
        invoiceId={null}
        invoiceNumber={null}
        slug="acme"
      />,
    );

    expect(container.firstChild).toBeNull();
  });

  it("renders Billed badge without link when slug is not provided", () => {
    render(
      <BillingStatusBadge
        billable={true}
        invoiceId="inv-789"
        invoiceNumber="INV-0003"
      />,
    );

    const badge = screen.getByText("Billed");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("success");
    // No link without slug
    const link = badge.closest("a");
    expect(link).toBeNull();
  });
});
