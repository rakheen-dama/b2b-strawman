import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { InvoiceStatusBadge } from "@/components/invoice-status-badge";

describe("InvoiceStatusBadge", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders SENT status with blue color classes", () => {
    const { container } = render(<InvoiceStatusBadge status="SENT" />);
    expect(screen.getByText("Sent")).toBeInTheDocument();
    const badge = container.querySelector("[data-slot='badge']");
    expect(badge?.className).toContain("bg-blue-100");
  });

  it("renders PAID status with green color classes", () => {
    const { container } = render(<InvoiceStatusBadge status="PAID" />);
    expect(screen.getByText("Paid")).toBeInTheDocument();
    const badge = container.querySelector("[data-slot='badge']");
    expect(badge?.className).toContain("bg-green-100");
  });

  it("renders VOID status with slate/gray color classes", () => {
    const { container } = render(<InvoiceStatusBadge status="VOID" />);
    expect(screen.getByText("Void")).toBeInTheDocument();
    const badge = container.querySelector("[data-slot='badge']");
    expect(badge?.className).toContain("bg-slate-100");
  });

  // Phase 88 622B (ADR-347): a part-paid fee note must not read as VOID (the slate fallback).
  it("renders PARTIALLY_PAID with amber color classes, not the VOID fallback", () => {
    const { container } = render(
      <InvoiceStatusBadge status="PARTIALLY_PAID" />,
    );
    expect(screen.getByText("Partially paid")).toBeInTheDocument();
    const badge = container.querySelector("[data-slot='badge']");
    expect(badge?.className).toContain("bg-amber-100");
    expect(badge?.className).not.toContain("bg-slate-100");
  });

  it("falls back gracefully for unknown statuses", () => {
    const { container } = render(
      <InvoiceStatusBadge status="UNKNOWN_STATUS" />,
    );
    expect(screen.getByText("Unknown status")).toBeInTheDocument();
    const badge = container.querySelector("[data-slot='badge']");
    // Falls back to VOID colors
    expect(badge?.className).toContain("bg-slate-100");
  });

  it("renders DRAFT and APPROVED in sentence case", () => {
    render(<InvoiceStatusBadge status="DRAFT" />);
    expect(screen.getByText("Draft")).toBeInTheDocument();
    cleanup();
    render(<InvoiceStatusBadge status="APPROVED" />);
    expect(screen.getByText("Approved")).toBeInTheDocument();
  });
});
