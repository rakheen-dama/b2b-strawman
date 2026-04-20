import {
  describe,
  it,
  expect,
  vi,
  beforeEach,
  afterEach,
} from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const mockGetConsumption = vi.fn();
vi.mock("@/lib/api/retainer", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api/retainer")>(
    "@/lib/api/retainer",
  );
  return {
    ...actual,
    getConsumption: (...args: unknown[]) => mockGetConsumption(...args),
  };
});

import { ConsumptionList } from "@/components/retainer/consumption-list";
import type { PortalRetainerConsumptionEntry } from "@/lib/api/retainer";

function entry(
  overrides: Partial<PortalRetainerConsumptionEntry> & { id: string },
): PortalRetainerConsumptionEntry {
  return {
    occurredAt: "2026-04-15",
    hours: 1.5,
    description: "Drafted memo",
    memberDisplayName: "A. Lawyer",
    ...overrides,
  } as PortalRetainerConsumptionEntry;
}

describe("ConsumptionList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
    cleanup();
  });

  it("groups consumption rows by date", async () => {
    mockGetConsumption.mockResolvedValueOnce([
      entry({
        id: "e1",
        occurredAt: "2026-04-15",
        description: "Research",
        hours: 2,
      }),
      entry({
        id: "e2",
        occurredAt: "2026-04-15",
        description: "Client call",
        hours: 1,
      }),
      entry({
        id: "e3",
        occurredAt: "2026-04-14",
        description: "Review contract",
        hours: 0.5,
      }),
    ]);

    render(
      <ConsumptionList
        retainerId="ret-1"
        periodStart="2026-04-01"
        periodEnd="2026-04-30"
        periodType="MONTHLY"
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Research")).toBeInTheDocument();
    });

    // One group element per distinct date
    const group15 = screen.getByTestId("consumption-group-2026-04-15");
    const group14 = screen.getByTestId("consumption-group-2026-04-14");
    expect(group15).toBeInTheDocument();
    expect(group14).toBeInTheDocument();

    // Each date header formatted by formatDate (en-GB)
    expect(group15.textContent).toMatch(/15 Apr 2026/);
    expect(group14.textContent).toMatch(/14 Apr 2026/);

    // Rows live under the correct group
    expect(group15.textContent).toContain("Research");
    expect(group15.textContent).toContain("Client call");
    expect(group14.textContent).toContain("Review contract");

    // Hours rendered with .00 suffix and tabular-nums styling
    expect(group15.textContent).toContain("2.00h");
    expect(group15.textContent).toContain("1.00h");
    expect(group14.textContent).toContain("0.50h");
  });

  it("re-fetches with previous-period bounds when the selector changes", async () => {
    mockGetConsumption
      .mockResolvedValueOnce([
        entry({
          id: "e1",
          description: "Current period entry",
          occurredAt: "2026-04-15",
        }),
      ])
      .mockResolvedValueOnce([
        entry({
          id: "e2",
          description: "Previous period entry",
          occurredAt: "2026-03-15",
        }),
      ]);

    render(
      <ConsumptionList
        retainerId="ret-1"
        periodStart="2026-04-01"
        periodEnd="2026-04-30"
        periodType="MONTHLY"
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Current period entry")).toBeInTheDocument();
    });

    // First call uses the current-period bounds.
    expect(mockGetConsumption).toHaveBeenNthCalledWith(1, "ret-1", {
      from: "2026-04-01",
      to: "2026-04-30",
    });

    // Switch to previous period.
    const select = screen.getByLabelText("Period") as HTMLSelectElement;
    await userEvent.selectOptions(select, "previous");

    await waitFor(() => {
      expect(mockGetConsumption).toHaveBeenCalledTimes(2);
    });

    // Second call uses the previous-period bounds: March 2026.
    expect(mockGetConsumption).toHaveBeenNthCalledWith(2, "ret-1", {
      from: "2026-03-01",
      to: "2026-03-31",
    });

    await waitFor(() => {
      expect(screen.getByText("Previous period entry")).toBeInTheDocument();
    });
  });

  it("renders the empty-state placeholder when the period has no entries", async () => {
    mockGetConsumption.mockResolvedValueOnce([]);

    render(
      <ConsumptionList
        retainerId="ret-1"
        periodStart="2026-04-01"
        periodEnd="2026-04-30"
        periodType="MONTHLY"
      />,
    );

    await waitFor(() => {
      expect(screen.getByTestId("consumption-empty")).toBeInTheDocument();
    });

    expect(
      screen.getByText(/No consumption recorded for this period/),
    ).toBeInTheDocument();
  });
});
