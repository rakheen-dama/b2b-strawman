import {
  describe,
  it,
  expect,
  vi,
  beforeEach,
  afterEach,
} from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";

const mockGetConsumption = vi.fn();
vi.mock("@/lib/api/retainer", async () => {
  const actual =
    await vi.importActual<typeof import("@/lib/api/retainer")>(
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
  };
}

/**
 * Epic 499B — ConsumptionList already renders as a padded list (not a table),
 * so the mobile/desktop contract is that rows stay full-width and each row is
 * at least 44px tall for touch-friendly tap targets.
 */
describe("ConsumptionList responsive layout", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
    cleanup();
  });

  it("each entry row is full-width and >=44px tall (tap target)", async () => {
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
      expect(screen.getByTestId("consumption-entry-e1")).toBeInTheDocument();
    });

    const row1 = screen.getByTestId("consumption-entry-e1");
    const row2 = screen.getByTestId("consumption-entry-e2");
    for (const row of [row1, row2]) {
      expect(row.className).toMatch(/\bmin-h-11\b/);
      expect(row.className).toMatch(/\bw-full\b/);
    }
  });
});
