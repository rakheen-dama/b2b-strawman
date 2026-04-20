import { describe, it, expect, afterEach, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [k: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

import { HourBankCard } from "@/components/retainer/hour-bank-card";
import type { PortalRetainerSummary } from "@/lib/api/retainer";

function buildSummary(
  overrides: Partial<PortalRetainerSummary> = {},
): PortalRetainerSummary {
  return {
    id: "ret-1",
    name: "Monthly Retainer",
    periodType: "MONTHLY",
    hoursAllotted: 20,
    hoursConsumed: 5,
    hoursRemaining: 15,
    periodStart: "2026-04-01",
    periodEnd: "2026-04-30",
    rolloverHours: 0,
    nextRenewalDate: "2026-05-01",
    status: "ACTIVE",
    ...overrides,
  };
}

describe("HourBankCard", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders remaining hours, progress bar width, and renewal date", () => {
    render(<HourBankCard summary={buildSummary()} />);

    // Retainer name from the header
    expect(screen.getByText("Monthly Retainer")).toBeInTheDocument();

    // Remaining hours — labelled for screen readers
    const remaining = screen.getByLabelText("Hours remaining");
    expect(remaining.textContent).toBe("15.00h");

    // Allotted figure appears in sub-label
    expect(screen.getByText(/of 20\.00h allotted/)).toBeInTheDocument();

    // Progress bar width: 5 / 20 = 25% consumed
    const fill = screen.getByTestId("hour-bank-progress-fill");
    expect(fill.style.width).toBe("25%");

    // Progress bar aria state
    const bar = screen.getByRole("progressbar");
    expect(bar.getAttribute("aria-valuenow")).toBe("25");

    // Renewal date formatted as "1 May 2026"
    expect(screen.getByText(/Renews 1 May 2026/)).toBeInTheDocument();

    // No urgency banner when remaining >= 20%
    expect(screen.queryByTestId("hour-bank-urgency-banner")).toBeNull();
  });

  it("applies urgency tint when less than 20% of hours remain", () => {
    render(
      <HourBankCard
        summary={buildSummary({
          hoursConsumed: 17,
          hoursRemaining: 3,
        })}
      />,
    );

    // Remaining figure turns red
    const remaining = screen.getByLabelText("Hours remaining");
    expect(remaining.className).toContain("text-red-600");

    // Urgency banner rendered
    const banner = screen.getByTestId("hour-bank-urgency-banner");
    expect(banner).toBeInTheDocument();
    expect(banner.textContent).toMatch(/Less than 20%/);

    // Progress fill uses the red colour at 85% consumed
    const fill = screen.getByTestId("hour-bank-progress-fill");
    expect(fill.className).toContain("bg-red-500");
    expect(fill.style.width).toBe("85%");
  });
});
