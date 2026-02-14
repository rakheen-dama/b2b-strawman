import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { KpiCardRow } from "@/components/dashboard/kpi-card-row";
import type { KpiResponse } from "@/lib/dashboard-types";

// Mock next/link to render as a plain anchor
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

const mockKpis: KpiResponse = {
  activeProjectCount: 12,
  totalHoursLogged: 847.5,
  billablePercent: 72.3,
  overdueTaskCount: 8,
  averageMarginPercent: 34.2,
  trend: [
    { period: "2025-W01", value: 120.0 },
    { period: "2025-W02", value: 145.5 },
    { period: "2025-W03", value: 138.0 },
  ],
  previousPeriod: {
    activeProjectCount: 11,
    totalHoursLogged: 792.0,
    billablePercent: 68.5,
    overdueTaskCount: 5,
    averageMarginPercent: 31.8,
  },
};

describe("KpiCardRow", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders 5 cards for admin users", () => {
    render(
      <KpiCardRow kpis={mockKpis} isAdmin={true} orgSlug="acme" />
    );

    expect(screen.getByText("Active Projects")).toBeInTheDocument();
    expect(screen.getByText("Hours Logged")).toBeInTheDocument();
    expect(screen.getByText("Billable %")).toBeInTheDocument();
    expect(screen.getByText("Overdue Tasks")).toBeInTheDocument();
    expect(screen.getByText("Avg. Margin")).toBeInTheDocument();
  });

  it("renders 3 cards for non-admin users (hides Billable % and Avg. Margin)", () => {
    render(
      <KpiCardRow kpis={mockKpis} isAdmin={false} orgSlug="acme" />
    );

    expect(screen.getByText("Active Projects")).toBeInTheDocument();
    expect(screen.getByText("Hours Logged")).toBeInTheDocument();
    expect(screen.getByText("Overdue Tasks")).toBeInTheDocument();
    expect(screen.queryByText("Billable %")).not.toBeInTheDocument();
    expect(screen.queryByText("Avg. Margin")).not.toBeInTheDocument();
  });

  it("renders empty state when kpis is null", () => {
    render(
      <KpiCardRow kpis={null} isAdmin={false} orgSlug="acme" />
    );

    expect(screen.getByText("Active Projects")).toBeInTheDocument();
    expect(screen.getAllByText("No data")).toHaveLength(3);
  });

  it("displays active project count value", () => {
    render(
      <KpiCardRow kpis={mockKpis} isAdmin={true} orgSlug="acme" />
    );

    expect(screen.getByText("12")).toBeInTheDocument();
  });

  it("links active projects card to projects page", () => {
    render(
      <KpiCardRow kpis={mockKpis} isAdmin={false} orgSlug="acme" />
    );

    const links = screen.getAllByRole("link");
    const projectsLink = links.find(
      (l) => l.getAttribute("href") === "/org/acme/projects"
    );
    expect(projectsLink).toBeDefined();
  });

  it("displays formatted hours value", () => {
    render(
      <KpiCardRow kpis={mockKpis} isAdmin={false} orgSlug="acme" />
    );

    expect(screen.getByText("847.5h")).toBeInTheDocument();
  });

  it("displays overdue task count", () => {
    render(
      <KpiCardRow kpis={mockKpis} isAdmin={false} orgSlug="acme" />
    );

    expect(screen.getByText("8")).toBeInTheDocument();
  });
});
