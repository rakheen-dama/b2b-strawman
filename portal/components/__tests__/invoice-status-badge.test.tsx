import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { InvoiceStatusBadge } from "@/components/invoice-status-badge";

describe("InvoiceStatusBadge", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders SENT status with blue color classes", () => {
    const { container } = render(<InvoiceStatusBadge status="SENT" />);
    expect(screen.getByText("SENT")).toBeInTheDocument();
    const badge = container.querySelector("[data-slot='badge']");
    expect(badge?.className).toContain("bg-blue-100");
  });

  it("renders PAID status with green color classes", () => {
    const { container } = render(<InvoiceStatusBadge status="PAID" />);
    expect(screen.getByText("PAID")).toBeInTheDocument();
    const badge = container.querySelector("[data-slot='badge']");
    expect(badge?.className).toContain("bg-green-100");
  });

  it("renders VOID status with slate/gray color classes", () => {
    const { container } = render(<InvoiceStatusBadge status="VOID" />);
    expect(screen.getByText("VOID")).toBeInTheDocument();
    const badge = container.querySelector("[data-slot='badge']");
    expect(badge?.className).toContain("bg-slate-100");
  });

  it("falls back gracefully for unknown statuses", () => {
    const { container } = render(<InvoiceStatusBadge status="UNKNOWN_STATUS" />);
    expect(screen.getByText("UNKNOWN STATUS")).toBeInTheDocument();
    const badge = container.querySelector("[data-slot='badge']");
    // Falls back to VOID colors
    expect(badge?.className).toContain("bg-slate-100");
  });
});
