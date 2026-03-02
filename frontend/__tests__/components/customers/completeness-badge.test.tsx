import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { CompletenessBadge } from "@/components/customers/completeness-badge";

describe("CompletenessBadge", () => {
  afterEach(() => cleanup());

  it("renders 100% with success variant (green)", () => {
    render(<CompletenessBadge score={{ totalRequired: 5, filled: 5, percentage: 100 }} />);
    const badge = screen.getByText("100%");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("success");
  });

  it("renders 75% with warning variant (amber)", () => {
    render(<CompletenessBadge score={{ totalRequired: 4, filled: 3, percentage: 75 }} />);
    const badge = screen.getByText("75%");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("warning");
  });

  it("renders 50% with warning variant (amber)", () => {
    render(<CompletenessBadge score={{ totalRequired: 4, filled: 2, percentage: 50 }} />);
    const badge = screen.getByText("50%");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("warning");
  });

  it("renders 25% with destructive variant (red)", () => {
    render(<CompletenessBadge score={{ totalRequired: 4, filled: 1, percentage: 25 }} />);
    const badge = screen.getByText("25%");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("destructive");
  });

  it("renders 0% with destructive variant (red)", () => {
    render(<CompletenessBadge score={{ totalRequired: 3, filled: 0, percentage: 0 }} />);
    const badge = screen.getByText("0%");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("destructive");
  });

  it("renders N/A with neutral variant when totalRequired is 0", () => {
    render(<CompletenessBadge score={{ totalRequired: 0, filled: 0, percentage: 0 }} />);
    const badge = screen.getByText("N/A");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("neutral");
  });
});
