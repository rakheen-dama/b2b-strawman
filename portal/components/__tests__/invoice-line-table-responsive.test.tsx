import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

import { InvoiceLineTable } from "@/components/invoice-line-table";

const mockLines = [
  {
    id: "line-1",
    description: "Design consultation",
    quantity: 5,
    unitPrice: 200,
    amount: 1000,
    sortOrder: 1,
  },
  {
    id: "line-2",
    description: "Development work",
    quantity: 10,
    unitPrice: 150,
    amount: 1500,
    sortOrder: 2,
  },
];

const baseProps = {
  lines: mockLines,
  currency: "ZAR",
  subtotal: 2500,
  taxAmount: 375,
  total: 2875,
};

describe("InvoiceLineTable responsive layout", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders both mobile card variant and desktop table variant", () => {
    render(<InvoiceLineTable {...baseProps} />);
    expect(screen.getByTestId("invoice-lines-mobile")).toBeInTheDocument();
    expect(screen.getByTestId("invoice-lines-desktop")).toBeInTheDocument();
  });

  it("mobile variant uses md:hidden class so CSS hides it on desktop", () => {
    render(<InvoiceLineTable {...baseProps} />);
    const mobile = screen.getByTestId("invoice-lines-mobile");
    expect(mobile.className).toContain("md:hidden");
  });

  it("desktop variant uses `hidden md:block` so CSS hides it on mobile", () => {
    render(<InvoiceLineTable {...baseProps} />);
    const desktop = screen.getByTestId("invoice-lines-desktop");
    expect(desktop.className).toContain("hidden");
    expect(desktop.className).toContain("md:block");
  });

  it("desktop variant contains a table element", () => {
    render(<InvoiceLineTable {...baseProps} />);
    const desktop = screen.getByTestId("invoice-lines-desktop");
    expect(desktop.querySelector("table")).not.toBeNull();
  });

  it("mobile variant does NOT contain a table element (cards instead)", () => {
    render(<InvoiceLineTable {...baseProps} />);
    const mobile = screen.getByTestId("invoice-lines-mobile");
    expect(mobile.querySelector("table")).toBeNull();
  });

  it("both variants include each line's description", () => {
    render(<InvoiceLineTable {...baseProps} />);
    const mobile = screen.getByTestId("invoice-lines-mobile");
    const desktop = screen.getByTestId("invoice-lines-desktop");
    expect(mobile.textContent).toContain("Design consultation");
    expect(mobile.textContent).toContain("Development work");
    expect(desktop.textContent).toContain("Design consultation");
    expect(desktop.textContent).toContain("Development work");
  });

  it("both variants include totals", () => {
    render(<InvoiceLineTable {...baseProps} />);
    expect(screen.getAllByText("Subtotal").length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText("Total").length).toBeGreaterThanOrEqual(2);
  });
});
