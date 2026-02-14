import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { UtilizationTable } from "@/components/profitability/utilization-table";
import { ProjectProfitabilityTable } from "@/components/profitability/project-profitability-table";
import type {
  UtilizationResponse,
  OrgProfitabilityResponse,
} from "@/lib/types";

// Mock server actions
const mockGetUtilization = vi.fn();
const mockGetOrgProfitability = vi.fn();

vi.mock("@/app/(app)/org/[slug]/profitability/actions", () => ({
  getUtilization: (...args: unknown[]) => mockGetUtilization(...args),
  getOrgProfitability: (...args: unknown[]) => mockGetOrgProfitability(...args),
}));

function makeUtilization(
  overrides: Partial<UtilizationResponse> = {},
): UtilizationResponse {
  return {
    from: "2026-02-01",
    to: "2026-02-14",
    members: [
      {
        memberId: "m1",
        memberName: "Alice Smith",
        totalHours: 40,
        billableHours: 32,
        nonBillableHours: 8,
        utilizationPercent: 80,
        currencies: [
          { currency: "USD", billableValue: 4800, costValue: 3200 },
        ],
      },
      {
        memberId: "m2",
        memberName: "Bob Jones",
        totalHours: 35,
        billableHours: 14,
        nonBillableHours: 21,
        utilizationPercent: 40,
        currencies: [],
      },
    ],
    ...overrides,
  };
}

function makeProfitability(
  overrides: Partial<OrgProfitabilityResponse> = {},
): OrgProfitabilityResponse {
  return {
    projects: [
      {
        projectId: "p1",
        projectName: "Alpha Project",
        customerName: "Acme Corp",
        currency: "USD",
        billableHours: 100,
        billableValue: 15000,
        costValue: 9000,
        margin: 6000,
        marginPercent: 40,
      },
      {
        projectId: "p2",
        projectName: "Beta Project",
        customerName: null,
        currency: "EUR",
        billableHours: 50,
        billableValue: 7500,
        costValue: null,
        margin: null,
        marginPercent: null,
      },
    ],
    ...overrides,
  };
}

describe("Profitability Page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  describe("UtilizationTable", () => {
    it("renders utilization table with member data", () => {
      const data = makeUtilization();
      render(
        <UtilizationTable
          initialData={data}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
        />,
      );

      expect(screen.getByText("Team Utilization")).toBeInTheDocument();
      expect(screen.getByText("Alice Smith")).toBeInTheDocument();
      expect(screen.getByText("Bob Jones")).toBeInTheDocument();
      expect(screen.getByText("32.0h")).toBeInTheDocument();
      expect(screen.getByText("80.0%")).toBeInTheDocument();
      expect(screen.getByText("40.0%")).toBeInTheDocument();
    });

    it("renders empty state when no utilization data", () => {
      const data = makeUtilization({ members: [] });
      render(
        <UtilizationTable
          initialData={data}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
        />,
      );

      expect(
        screen.getByText("No utilization data for this period"),
      ).toBeInTheDocument();
    });

    it("sorts by utilization when clicking column header", async () => {
      const user = userEvent.setup();
      const data = makeUtilization();
      render(
        <UtilizationTable
          initialData={data}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
        />,
      );

      // Default sort is utilization desc, so Alice (80%) comes first
      const rows = screen.getAllByRole("row");
      // Row 0 is header, row 1 is first data row
      expect(within(rows[1]).getByText("Alice Smith")).toBeInTheDocument();

      // Click utilization header to toggle to asc
      const utilizationHeader = screen.getByRole("button", {
        name: /Utilization %/i,
      });
      await user.click(utilizationHeader);

      // Now Bob (40%) should come first
      const updatedRows = screen.getAllByRole("row");
      expect(within(updatedRows[1]).getByText("Bob Jones")).toBeInTheDocument();
    });

    it("refetches data when date range changes", async () => {
      const user = userEvent.setup();
      const data = makeUtilization();
      const newData = makeUtilization({
        members: [
          {
            memberId: "m3",
            memberName: "Carol White",
            totalHours: 20,
            billableHours: 15,
            nonBillableHours: 5,
            utilizationPercent: 75,
            currencies: [],
          },
        ],
      });

      mockGetUtilization.mockResolvedValue({ data: newData });

      render(
        <UtilizationTable
          initialData={data}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
        />,
      );

      // Change the "from" date
      const fromInput = screen.getByLabelText("From");
      await user.clear(fromInput);
      await user.type(fromInput, "2026-01-01");

      expect(mockGetUtilization).toHaveBeenCalledWith("2026-01-01", "2026-02-14");
    });
  });

  describe("ProjectProfitabilityTable", () => {
    it("renders project profitability table with project data", () => {
      const data = makeProfitability();
      render(
        <ProjectProfitabilityTable
          initialData={data}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
        />,
      );

      expect(screen.getByText("Project Profitability")).toBeInTheDocument();
      expect(screen.getByText("Alpha Project")).toBeInTheDocument();
      expect(screen.getByText("Acme Corp")).toBeInTheDocument();
      expect(screen.getByText("$15,000.00")).toBeInTheDocument();
      expect(screen.getByText("$6,000.00")).toBeInTheDocument();
    });

    it("shows N/A badge when margin data is missing", () => {
      const data = makeProfitability();
      render(
        <ProjectProfitabilityTable
          initialData={data}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
        />,
      );

      // Beta Project has null margin/marginPercent
      const badges = screen.getAllByText("N/A");
      expect(badges.length).toBeGreaterThanOrEqual(2); // margin + margin %
    });

    it("renders empty state when no project data", () => {
      const data = makeProfitability({ projects: [] });
      render(
        <ProjectProfitabilityTable
          initialData={data}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
        />,
      );

      expect(
        screen.getByText("No project profitability data for this period"),
      ).toBeInTheDocument();
    });

    it("shows dash for projects without a customer", () => {
      const data = makeProfitability();
      render(
        <ProjectProfitabilityTable
          initialData={data}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
        />,
      );

      // Beta Project has no customer
      expect(screen.getByText("\u2014")).toBeInTheDocument();
    });
  });
});
