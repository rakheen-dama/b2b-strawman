import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { RetainerIndicator } from "@/components/time-entries/retainer-indicator";
import type { RetainerSummaryResponse } from "@/lib/types";

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
      periodStart: "2026-03-01",
      periodEnd: "2026-04-01",
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
      periodStart: null,
      periodEnd: null,
    };

    const { container } = render(<RetainerIndicator summary={summary} />);
    expect(container.innerHTML).toBe("");
  });

  it("shows overage warning when fully consumed and date within period", () => {
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
      periodStart: "2026-03-01",
      periodEnd: "2026-04-01",
    };

    render(
      <RetainerIndicator summary={summary} selectedDate="2026-03-15" />,
    );
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
      periodStart: "2026-03-01",
      periodEnd: "2026-04-01",
    };

    render(<RetainerIndicator summary={summary} />);
    expect(screen.getByText(/Fixed Fee Retainer/)).toBeInTheDocument();
    expect(screen.queryByText(/hrs remaining/)).not.toBeInTheDocument();
  });

  it("shows period date range when period dates are available", () => {
    const summary: RetainerSummaryResponse = {
      hasActiveRetainer: true,
      agreementId: "uuid-4",
      agreementName: "Date Range Retainer",
      type: "HOUR_BANK",
      allocatedHours: 40,
      consumedHours: 10,
      remainingHours: 30,
      percentConsumed: 25,
      isOverage: false,
      periodStart: "2026-03-01",
      periodEnd: "2026-04-01",
    };

    render(<RetainerIndicator summary={summary} />);
    expect(screen.getByTestId("retainer-period-range")).toBeInTheDocument();
    const rangeEl = screen.getByTestId("retainer-period-range");
    expect(rangeEl.textContent).toContain("2026");
  });

  it("shows warning when selected date is outside retainer period", () => {
    const summary: RetainerSummaryResponse = {
      hasActiveRetainer: true,
      agreementId: "uuid-5",
      agreementName: "Period Warning Test",
      type: "HOUR_BANK",
      allocatedHours: 40,
      consumedHours: 10,
      remainingHours: 30,
      percentConsumed: 25,
      isOverage: false,
      periodStart: "2026-03-01",
      periodEnd: "2026-04-01",
    };

    render(
      <RetainerIndicator summary={summary} selectedDate="2026-05-15" />,
    );
    expect(screen.getByTestId("retainer-period-warning")).toBeInTheDocument();
    expect(
      screen.getByText(/outside the current retainer period/),
    ).toBeInTheDocument();
  });

  it("does not show warning when selected date is within retainer period", () => {
    const summary: RetainerSummaryResponse = {
      hasActiveRetainer: true,
      agreementId: "uuid-6",
      agreementName: "In Period Test",
      type: "HOUR_BANK",
      allocatedHours: 40,
      consumedHours: 10,
      remainingHours: 30,
      percentConsumed: 25,
      isOverage: false,
      periodStart: "2026-03-01",
      periodEnd: "2026-04-01",
    };

    render(
      <RetainerIndicator summary={summary} selectedDate="2026-03-15" />,
    );
    expect(
      screen.queryByTestId("retainer-period-warning"),
    ).not.toBeInTheDocument();
  });

  it("shows period warning instead of overage warning when date is outside period", () => {
    const summary: RetainerSummaryResponse = {
      hasActiveRetainer: true,
      agreementId: "uuid-7",
      agreementName: "Priority Test",
      type: "HOUR_BANK",
      allocatedHours: 40,
      consumedHours: 42,
      remainingHours: 0,
      percentConsumed: 105,
      isOverage: true,
      periodStart: "2026-03-01",
      periodEnd: "2026-04-01",
    };

    render(
      <RetainerIndicator summary={summary} selectedDate="2026-05-15" />,
    );
    // Should show the period warning, not the overage warning
    expect(screen.getByTestId("retainer-period-warning")).toBeInTheDocument();
    expect(
      screen.queryByTestId("retainer-overage-warning"),
    ).not.toBeInTheDocument();
  });

  it("shows period range for FIXED_FEE retainer with warning when date outside", () => {
    const summary: RetainerSummaryResponse = {
      hasActiveRetainer: true,
      agreementId: "uuid-8",
      agreementName: "Fixed Fee Period",
      type: "FIXED_FEE",
      allocatedHours: null,
      consumedHours: 28,
      remainingHours: null,
      percentConsumed: null,
      isOverage: false,
      periodStart: "2026-03-01",
      periodEnd: "2026-04-01",
    };

    render(
      <RetainerIndicator summary={summary} selectedDate="2026-05-15" />,
    );
    expect(screen.getByTestId("retainer-period-range")).toBeInTheDocument();
    expect(screen.getByTestId("retainer-period-warning")).toBeInTheDocument();
  });
});
