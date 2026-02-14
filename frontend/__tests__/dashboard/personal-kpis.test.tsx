import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { PersonalKpis } from "@/components/my-work/personal-kpis";
import type { PersonalDashboardResponse } from "@/lib/dashboard-types";

vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: {
    href: string;
    children: React.ReactNode;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

const mockData: PersonalDashboardResponse = {
  utilization: {
    totalHours: 38.5,
    billableHours: 30.0,
    billablePercent: 77.9,
  },
  projectBreakdown: [
    {
      projectId: "p1",
      projectName: "Website Redesign",
      hours: 20.0,
      percent: 51.9,
    },
  ],
  overdueTaskCount: 2,
  upcomingDeadlines: [],
  trend: [
    { period: "2025-W01", value: 36.0 },
    { period: "2025-W02", value: 40.0 },
  ],
};

describe("PersonalKpis", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders hours and overdue count from data", () => {
    render(<PersonalKpis data={mockData} periodLabel="This Week" />);

    expect(screen.getByText("Hours This Week")).toBeInTheDocument();
    expect(screen.getByText("38.5h")).toBeInTheDocument();
    expect(screen.getByText("Billable %")).toBeInTheDocument();
    expect(screen.getByText("78%")).toBeInTheDocument();
    expect(screen.getByText("Overdue Tasks")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
  });

  it("shows empty state for billable when billablePercent is null", () => {
    const dataWithNullBillable: PersonalDashboardResponse = {
      ...mockData,
      utilization: {
        totalHours: 10.0,
        billableHours: 0,
        billablePercent: null as unknown as number,
      },
    };

    render(<PersonalKpis data={dataWithNullBillable} />);

    expect(screen.getByText("Billable %")).toBeInTheDocument();
    expect(screen.getByText("No billable data")).toBeInTheDocument();
  });
});
