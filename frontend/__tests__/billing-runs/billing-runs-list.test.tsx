import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { BillingRunStatusBadge } from "@/components/billing-runs/billing-run-status-badge";
import { BillingRunItemStatusBadge } from "@/components/billing-runs/billing-run-item-status-badge";

afterEach(() => {
  cleanup();
});

describe("BillingRunStatusBadge", () => {
  it("renders PREVIEW with neutral variant", () => {
    render(<BillingRunStatusBadge status="PREVIEW" />);
    const badge = screen.getByText("Preview", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "neutral");
  });

  it("renders IN_PROGRESS with warning variant", () => {
    render(<BillingRunStatusBadge status="IN_PROGRESS" />);
    const badge = screen.getByText("In Progress", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "warning");
  });

  it("renders COMPLETED with success variant", () => {
    render(<BillingRunStatusBadge status="COMPLETED" />);
    const badge = screen.getByText("Completed", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "success");
  });

  it("renders CANCELLED with destructive variant", () => {
    render(<BillingRunStatusBadge status="CANCELLED" />);
    const badge = screen.getByText("Cancelled", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "destructive");
  });
});

describe("BillingRunItemStatusBadge", () => {
  it("renders PENDING with neutral variant", () => {
    render(<BillingRunItemStatusBadge status="PENDING" />);
    const badge = screen.getByText("Pending", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "neutral");
  });

  it("renders GENERATING with warning variant", () => {
    render(<BillingRunItemStatusBadge status="GENERATING" />);
    const badge = screen.getByText("Generating", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "warning");
  });

  it("renders GENERATED with success variant", () => {
    render(<BillingRunItemStatusBadge status="GENERATED" />);
    const badge = screen.getByText("Generated", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "success");
  });

  it("renders FAILED with destructive variant", () => {
    render(<BillingRunItemStatusBadge status="FAILED" />);
    const badge = screen.getByText("Failed", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "destructive");
  });

  it("renders EXCLUDED with secondary variant", () => {
    render(<BillingRunItemStatusBadge status="EXCLUDED" />);
    const badge = screen.getByText("Excluded", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "secondary");
  });

  it("renders CANCELLED with neutral variant", () => {
    render(<BillingRunItemStatusBadge status="CANCELLED" />);
    const badge = screen.getByText("Cancelled", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "neutral");
  });
});
