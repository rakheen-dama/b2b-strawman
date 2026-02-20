import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { RetainerIndicator } from "@/components/time-entries/retainer-indicator";
import type { RetainerSummaryResponse } from "@/lib/api/retainers";

describe("RetainerIndicator", () => {
  afterEach(() => {
    cleanup();
  });

  it("shows hours remaining for HOUR_BANK retainer", () => {
    const summary: RetainerSummaryResponse = {
      hasActiveRetainer: true,
      agreementId: "uuid-1",
      agreementName: "Monthly Retainer",
      type: "HOUR_BANK",
      allocatedHours: 40,
      consumedHours: 24.5,
      remainingHours: 15.5,
      percentConsumed: 61,
      isOverage: false,
    };

    render(<RetainerIndicator summary={summary} />);
    expect(screen.getByText(/15\.5 hrs remaining/)).toBeInTheDocument();
  });

  it("renders nothing when no retainer", () => {
    const summary: RetainerSummaryResponse = {
      hasActiveRetainer: false,
      agreementId: null,
      agreementName: null,
      type: null,
      allocatedHours: null,
      consumedHours: null,
      remainingHours: null,
      percentConsumed: null,
      isOverage: false,
    };

    const { container } = render(<RetainerIndicator summary={summary} />);
    expect(container.innerHTML).toBe("");
  });

  it("shows overage warning when fully consumed", () => {
    const summary: RetainerSummaryResponse = {
      hasActiveRetainer: true,
      agreementId: "uuid-2",
      agreementName: "Overused Retainer",
      type: "HOUR_BANK",
      allocatedHours: 40,
      consumedHours: 42,
      remainingHours: 0,
      percentConsumed: 105,
      isOverage: true,
    };

    render(<RetainerIndicator summary={summary} />);
    expect(
      screen.getByText(/Retainer fully consumed/),
    ).toBeInTheDocument();
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("shows fixed fee label without hours", () => {
    const summary: RetainerSummaryResponse = {
      hasActiveRetainer: true,
      agreementId: "uuid-3",
      agreementName: "Monthly Fixed Fee",
      type: "FIXED_FEE",
      allocatedHours: null,
      consumedHours: 28,
      remainingHours: null,
      percentConsumed: null,
      isOverage: false,
    };

    render(<RetainerIndicator summary={summary} />);
    expect(screen.getByText(/Fixed Fee Retainer/)).toBeInTheDocument();
    expect(screen.queryByText(/hrs remaining/)).not.toBeInTheDocument();
  });
});
