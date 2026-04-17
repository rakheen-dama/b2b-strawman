import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Mock swr FIRST (before component import)
vi.mock("swr", () => ({ default: vi.fn() }));

// Mock next/link
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

// Mock the server action module to prevent `server-only` from being imported
// into the test environment. The fetcher itself is never invoked because
// `useSWR` is fully mocked above, but the import chain still runs at module
// load time — so this stub must remain.
vi.mock("@/lib/actions/utilization", () => ({
  fetchTeamUtilizationTrend: vi.fn(),
}));

// Mock the profile hook
vi.mock("@/lib/hooks/useProfile", () => ({
  useProfile: vi.fn(),
}));

import useSWR from "swr";
import { useProfile } from "@/lib/hooks/useProfile";
import { TeamUtilizationWidget } from "@/components/dashboard/team-utilization-widget";
import type { TeamUtilizationResponse } from "@/lib/api/capacity";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

type TrendFixture = Array<Pick<TeamUtilizationResponse, "teamAverages"> & Partial<TeamUtilizationResponse>>;

function mockSWRData(trend: TrendFixture) {
  vi.mocked(useSWR).mockReturnValue({
    data: trend,
    error: undefined,
    isLoading: false,
    mutate: vi.fn(),
  } as unknown as ReturnType<typeof useSWR>);
}

function mockSWRLoading() {
  vi.mocked(useSWR).mockReturnValue({
    data: undefined,
    error: undefined,
    isLoading: true,
    mutate: vi.fn(),
  } as unknown as ReturnType<typeof useSWR>);
}

function mockSWRError() {
  vi.mocked(useSWR).mockReturnValue({
    data: undefined,
    error: new Error("failed"),
    isLoading: false,
    mutate: vi.fn(),
  } as unknown as ReturnType<typeof useSWR>);
}

describe("TeamUtilizationWidget", () => {
  it("renders KPI card when profile = consulting-za", () => {
    vi.mocked(useProfile).mockReturnValue("consulting-za");
    mockSWRData([
      { teamAverages: { avgBillableUtilizationPct: 60, avgPlannedUtilizationPct: 0, avgActualUtilizationPct: 0 } },
      { teamAverages: { avgBillableUtilizationPct: 64, avgPlannedUtilizationPct: 0, avgActualUtilizationPct: 0 } },
      { teamAverages: { avgBillableUtilizationPct: 66, avgPlannedUtilizationPct: 0, avgActualUtilizationPct: 0 } },
      { teamAverages: { avgBillableUtilizationPct: 68, avgPlannedUtilizationPct: 0, avgActualUtilizationPct: 0 } },
    ]);

    render(<TeamUtilizationWidget slug="acme" />);

    expect(screen.getByText("Team Billable Utilization")).toBeInTheDocument();
    expect(screen.getByText("68%")).toBeInTheDocument();
  });

  it("returns null when profile = legal-za", () => {
    vi.mocked(useProfile).mockReturnValue("legal-za");
    mockSWRData([]);

    const { container } = render(<TeamUtilizationWidget slug="acme" />);

    expect(container.firstChild).toBeNull();
    expect(screen.queryByText("Team Billable Utilization")).not.toBeInTheDocument();
  });

  it("returns null when profile = accounting-za", () => {
    vi.mocked(useProfile).mockReturnValue("accounting-za");
    mockSWRData([]);

    const { container } = render(<TeamUtilizationWidget slug="acme" />);

    expect(container.firstChild).toBeNull();
    expect(screen.queryByText("Team Billable Utilization")).not.toBeInTheDocument();
  });

  it("returns null when profile = consulting-generic", () => {
    vi.mocked(useProfile).mockReturnValue("consulting-generic");
    mockSWRData([]);

    const { container } = render(<TeamUtilizationWidget slug="acme" />);

    expect(container.firstChild).toBeNull();
    expect(screen.queryByText("Team Billable Utilization")).not.toBeInTheDocument();
  });

  it("returns null when profile = null (no active profile)", () => {
    vi.mocked(useProfile).mockReturnValue(null);
    mockSWRData([]);

    const { container } = render(<TeamUtilizationWidget slug="acme" />);

    expect(container.firstChild).toBeNull();
    expect(screen.queryByText("Team Billable Utilization")).not.toBeInTheDocument();
  });

  it("shows empty state when all 4 weeks report zero utilization", () => {
    vi.mocked(useProfile).mockReturnValue("consulting-za");
    mockSWRData([
      { teamAverages: { avgBillableUtilizationPct: 0, avgPlannedUtilizationPct: 0, avgActualUtilizationPct: 0 } },
      { teamAverages: { avgBillableUtilizationPct: 0, avgPlannedUtilizationPct: 0, avgActualUtilizationPct: 0 } },
      { teamAverages: { avgBillableUtilizationPct: 0, avgPlannedUtilizationPct: 0, avgActualUtilizationPct: 0 } },
      { teamAverages: { avgBillableUtilizationPct: 0, avgPlannedUtilizationPct: 0, avgActualUtilizationPct: 0 } },
    ]);

    render(<TeamUtilizationWidget slug="acme" />);

    // Should render the empty/unable-to-load fallback, not "0%" headline.
    expect(screen.getByText(/Unable to load/i)).toBeInTheDocument();
    expect(screen.queryByText("0%")).not.toBeInTheDocument();
  });

  it("shows loading state when 4 API calls are pending", () => {
    vi.mocked(useProfile).mockReturnValue("consulting-za");
    mockSWRLoading();

    render(<TeamUtilizationWidget slug="acme" />);

    expect(screen.getByText(/Loading/i)).toBeInTheDocument();
  });

  it("shows empty/error state when fetch fails", () => {
    vi.mocked(useProfile).mockReturnValue("consulting-za");
    mockSWRError();

    render(<TeamUtilizationWidget slug="acme" />);

    expect(screen.getByText(/Unable to load/i)).toBeInTheDocument();
  });

  it("renders sparkline with 4-element data when 4 weeks returned (484.10)", () => {
    vi.mocked(useProfile).mockReturnValue("consulting-za");
    mockSWRData([
      { teamAverages: { avgBillableUtilizationPct: 60, avgPlannedUtilizationPct: 0, avgActualUtilizationPct: 0 } },
      { teamAverages: { avgBillableUtilizationPct: 64, avgPlannedUtilizationPct: 0, avgActualUtilizationPct: 0 } },
      { teamAverages: { avgBillableUtilizationPct: 66, avgPlannedUtilizationPct: 0, avgActualUtilizationPct: 0 } },
      { teamAverages: { avgBillableUtilizationPct: 68, avgPlannedUtilizationPct: 0, avgActualUtilizationPct: 0 } },
    ]);

    render(<TeamUtilizationWidget slug="acme" />);

    // Sparkline primitive emits data-testid="sparkline"
    expect(screen.getByTestId("sparkline")).toBeInTheDocument();

    // Prior-week delta: 68 - 66 = +2 pp → rendered as +2% via KpiCard
    expect(screen.getByText(/2%/)).toBeInTheDocument();
  });

  it("renders CTA link to the utilization page", () => {
    vi.mocked(useProfile).mockReturnValue("consulting-za");
    mockSWRData([
      { teamAverages: { avgBillableUtilizationPct: 60, avgPlannedUtilizationPct: 0, avgActualUtilizationPct: 0 } },
      { teamAverages: { avgBillableUtilizationPct: 64, avgPlannedUtilizationPct: 0, avgActualUtilizationPct: 0 } },
      { teamAverages: { avgBillableUtilizationPct: 66, avgPlannedUtilizationPct: 0, avgActualUtilizationPct: 0 } },
      { teamAverages: { avgBillableUtilizationPct: 68, avgPlannedUtilizationPct: 0, avgActualUtilizationPct: 0 } },
    ]);

    render(<TeamUtilizationWidget slug="acme" />);

    const link = screen.getByRole("link");
    expect(link).toHaveAttribute("href", "/org/acme/resources/utilization");
  });
});
