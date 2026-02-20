import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { RetainerSummaryCards } from "@/components/retainers/retainer-summary-cards";

describe("RetainerSummaryCards", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders zero values correctly", () => {
    render(
      <RetainerSummaryCards
        activeCount={0}
        readyToCloseCount={0}
        totalOverageHours={0}
      />,
    );
    expect(screen.getByText("Active Retainers")).toBeInTheDocument();
    expect(screen.getByText("Periods Ready to Close")).toBeInTheDocument();
    expect(screen.getByText("Total Overage Hours")).toBeInTheDocument();
    expect(screen.getByText("0.0 hrs")).toBeInTheDocument();
    // Both activeCount and readyToCloseCount are 0
    const zeros = screen.getAllByText("0");
    expect(zeros).toHaveLength(2);
  });

  it("highlights 'Periods Ready to Close' card when count > 0", () => {
    const { container } = render(
      <RetainerSummaryCards
        activeCount={3}
        readyToCloseCount={2}
        totalOverageHours={0}
      />,
    );
    // The card with readyToCloseCount > 0 should have amber background
    const amberCards = container.querySelectorAll(".bg-amber-50");
    expect(amberCards.length).toBeGreaterThanOrEqual(1);
  });

  it("highlights overage hours card when total > 0", () => {
    const { container } = render(
      <RetainerSummaryCards
        activeCount={3}
        readyToCloseCount={0}
        totalOverageHours={12.5}
      />,
    );
    expect(screen.getByText("12.5 hrs")).toBeInTheDocument();
    const amberCards = container.querySelectorAll(".bg-amber-50");
    expect(amberCards.length).toBeGreaterThanOrEqual(1);
  });
});
