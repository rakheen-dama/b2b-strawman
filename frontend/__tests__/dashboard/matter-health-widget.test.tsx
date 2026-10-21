import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent } from "@testing-library/react";
import { MatterHealthWidget } from "@/components/dashboard/matter-health-widget";
import type { MatterHealth } from "@/lib/dashboard-types";

// Mock next/navigation
const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => "/org/acme/dashboard",
}));

const mockProjects: MatterHealth[] = [
  {
    matterId: "proj-1",
    matterName: "Website Redesign",
    clientName: "Acme Corp",
    healthStatus: "AT_RISK",
    healthReasons: ["Budget 85% consumed", "6 overdue tasks"],
    tasksDone: 20,
    tasksTotal: 40,
    completionPercent: 50.0,
    budgetConsumedPercent: 85.0,
    hoursLogged: 170.0,
  },
  {
    matterId: "proj-2",
    matterName: "Mobile App",
    clientName: null,
    healthStatus: "HEALTHY",
    healthReasons: [],
    tasksDone: 15,
    tasksTotal: 20,
    completionPercent: 75.0,
    budgetConsumedPercent: null,
    hoursLogged: 95.5,
  },
  {
    matterId: "proj-3",
    matterName: "API Migration",
    clientName: "Beta Inc",
    healthStatus: "CRITICAL",
    healthReasons: ["3 weeks overdue"],
    tasksDone: 5,
    tasksTotal: 30,
    completionPercent: 16.7,
    budgetConsumedPercent: 120.0,
    hoursLogged: 200.0,
  },
];

describe("MatterHealthWidget", () => {
  afterEach(() => {
    cleanup();
    mockPush.mockClear();
  });

  it("renders empty state when projects is null", () => {
    render(<MatterHealthWidget matters={null} orgSlug="acme" />);
    expect(screen.getByText("No matters yet")).toBeInTheDocument();
  });

  it("renders empty state when projects is empty", () => {
    render(<MatterHealthWidget matters={[]} orgSlug="acme" />);
    expect(screen.getByText("No matters yet")).toBeInTheDocument();
  });

  it("renders all projects by default", () => {
    render(<MatterHealthWidget matters={mockProjects} orgSlug="acme" />);

    expect(screen.getByText("Website Redesign")).toBeInTheDocument();
    expect(screen.getByText("Mobile App")).toBeInTheDocument();
    expect(screen.getByText("API Migration")).toBeInTheDocument();
  });

  it("shows customer names when available", () => {
    render(<MatterHealthWidget matters={mockProjects} orgSlug="acme" />);

    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("Beta Inc")).toBeInTheDocument();
  });

  it("shows task ratio and hours in dense row", () => {
    render(<MatterHealthWidget matters={mockProjects} orgSlug="acme" />);

    // Task ratios shown as X/Y
    expect(screen.getByText("20/40")).toBeInTheDocument();
    expect(screen.getByText("15/20")).toBeInTheDocument();
    expect(screen.getByText("5/30")).toBeInTheDocument();
  });

  it("filters to at-risk and critical projects when At Risk tab clicked", () => {
    render(<MatterHealthWidget matters={mockProjects} orgSlug="acme" />);

    fireEvent.click(screen.getByRole("button", { name: "At Risk" }));

    // At Risk filter includes both AT_RISK and CRITICAL
    expect(screen.getByText("Website Redesign")).toBeInTheDocument();
    expect(screen.getByText("API Migration")).toBeInTheDocument();
    expect(screen.queryByText("Mobile App")).not.toBeInTheDocument();
  });

  it("filters to over-budget projects when Over Budget tab clicked", () => {
    render(<MatterHealthWidget matters={mockProjects} orgSlug="acme" />);

    fireEvent.click(screen.getByRole("button", { name: "Over Budget" }));

    // Only proj-3 has budgetConsumedPercent > 100
    expect(screen.getByText("API Migration")).toBeInTheDocument();
    expect(screen.queryByText("Website Redesign")).not.toBeInTheDocument();
    expect(screen.queryByText("Mobile App")).not.toBeInTheDocument();
  });

  it("navigates to project detail on row click", () => {
    render(<MatterHealthWidget matters={mockProjects} orgSlug="acme" />);

    fireEvent.click(screen.getByText("Website Redesign"));

    expect(mockPush).toHaveBeenCalledWith("/org/acme/matters/proj-1");
  });

  it("navigates to projects page when 'View all matters' clicked", () => {
    render(<MatterHealthWidget matters={mockProjects} orgSlug="acme" />);

    fireEvent.click(screen.getByText(/View all matters/));

    expect(mockPush).toHaveBeenCalledWith("/org/acme/matters");
  });

  it("renders filter tabs", () => {
    render(<MatterHealthWidget matters={mockProjects} orgSlug="acme" />);

    expect(screen.getByRole("button", { name: "All" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "At Risk" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Over Budget" })).toBeInTheDocument();
  });

  it("shows empty filter message when no projects match filter", () => {
    const healthyOnly: MatterHealth[] = [
      {
        matterId: "proj-1",
        matterName: "Good Project",
        clientName: null,
        healthStatus: "HEALTHY",
        healthReasons: [],
        tasksDone: 10,
        tasksTotal: 10,
        completionPercent: 100,
        budgetConsumedPercent: null,
        hoursLogged: 50,
      },
    ];

    render(<MatterHealthWidget matters={healthyOnly} orgSlug="acme" />);

    fireEvent.click(screen.getByRole("button", { name: "Over Budget" }));

    expect(screen.getByText("No matching matters")).toBeInTheDocument();
  });

  it("renders matter-health-panel data-testid", () => {
    render(<MatterHealthWidget matters={mockProjects} orgSlug="acme" />);

    expect(screen.getByTestId("matter-health-panel")).toBeInTheDocument();
  });
});
