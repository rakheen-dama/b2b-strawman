import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { BudgetStatusDot } from "@/components/projects/budget-status-dot";

describe("BudgetStatusDot", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders correct indicator for each budget status", () => {
    const { unmount: u1 } = render(<BudgetStatusDot status="ON_TRACK" />);
    expect(screen.getByLabelText("Budget: On track")).toBeInTheDocument();
    u1();

    const { unmount: u2 } = render(<BudgetStatusDot status="AT_RISK" />);
    expect(screen.getByLabelText("Budget: At risk")).toBeInTheDocument();
    u2();

    render(<BudgetStatusDot status="OVER_BUDGET" />);
    expect(screen.getByLabelText("Budget: Over budget")).toBeInTheDocument();
  });

  it("renders as a small dot with the correct color class", () => {
    render(<BudgetStatusDot status="AT_RISK" />);
    const dot = screen.getByLabelText("Budget: At risk");
    expect(dot.className).toContain("bg-amber-500");
    expect(dot.className).toContain("size-2");
    expect(dot.className).toContain("rounded-full");
  });
});
