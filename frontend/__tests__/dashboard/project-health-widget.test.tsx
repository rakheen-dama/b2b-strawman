import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent } from "@testing-library/react";
import { ProjectHealthWidget } from "@/components/dashboard/project-health-widget";
import type { ProjectHealth } from "@/lib/dashboard-types";

// Mock next/navigation
const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => "/org/acme/dashboard",
}));

const mockProjects: ProjectHealth[] = [
  {
    projectId: "proj-1",
    projectName: "Website Redesign",
    customerName: "Acme Corp",
    healthStatus: "AT_RISK",
    healthReasons: ["Budget 85% consumed", "6 overdue tasks"],
    tasksDone: 20,
    tasksTotal: 40,
    completionPercent: 50.0,
    budgetConsumedPercent: 85.0,
    hoursLogged: 170.0,
  },
  {
    projectId: "proj-2",
    projectName: "Mobile App",
    customerName: null,
    healthStatus: "HEALTHY",
    healthReasons: [],
    tasksDone: 15,
    tasksTotal: 20,
    completionPercent: 75.0,
    budgetConsumedPercent: null,
    hoursLogged: 95.5,
  },
  {
    projectId: "proj-3",
    projectName: "API Migration",
    customerName: "Beta Inc",
    healthStatus: "CRITICAL",
    healthReasons: ["3 weeks overdue"],
    tasksDone: 5,
    tasksTotal: 30,
    completionPercent: 16.7,
    budgetConsumedPercent: 95.0,
    hoursLogged: 200.0,
  },
];

describe("ProjectHealthWidget", () => {
  afterEach(() => {
    cleanup();
    mockPush.mockClear();
  });

  it("renders empty state when projects is null", () => {
    render(<ProjectHealthWidget projects={null} orgSlug="acme" />);
    expect(screen.getByText("No projects yet...")).toBeInTheDocument();
  });

  it("renders empty state when projects is empty", () => {
    render(<ProjectHealthWidget projects={[]} orgSlug="acme" />);
    expect(screen.getByText("No projects yet...")).toBeInTheDocument();
  });

  it("renders all projects by default", () => {
    render(
      <ProjectHealthWidget projects={mockProjects} orgSlug="acme" />
    );

    expect(screen.getByText("Website Redesign")).toBeInTheDocument();
    expect(screen.getByText("Mobile App")).toBeInTheDocument();
    expect(screen.getByText("API Migration")).toBeInTheDocument();
  });

  it("shows customer names when available", () => {
    render(
      <ProjectHealthWidget projects={mockProjects} orgSlug="acme" />
    );

    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("Beta Inc")).toBeInTheDocument();
  });

  it("shows health reasons", () => {
    render(
      <ProjectHealthWidget projects={mockProjects} orgSlug="acme" />
    );

    expect(
      screen.getByText(/Budget 85% consumed/)
    ).toBeInTheDocument();
  });

  it("filters to AT_RISK projects when At Risk tab clicked", () => {
    render(
      <ProjectHealthWidget projects={mockProjects} orgSlug="acme" />
    );

    fireEvent.click(screen.getByRole("button", { name: "At Risk" }));

    expect(screen.getByText("Website Redesign")).toBeInTheDocument();
    expect(screen.queryByText("Mobile App")).not.toBeInTheDocument();
    expect(screen.queryByText("API Migration")).not.toBeInTheDocument();
  });

  it("filters to CRITICAL projects when Critical tab clicked", () => {
    render(
      <ProjectHealthWidget projects={mockProjects} orgSlug="acme" />
    );

    fireEvent.click(screen.getByRole("button", { name: "Critical" }));

    expect(screen.getByText("API Migration")).toBeInTheDocument();
    expect(
      screen.queryByText("Website Redesign")
    ).not.toBeInTheDocument();
    expect(screen.queryByText("Mobile App")).not.toBeInTheDocument();
  });

  it("navigates to project detail on row click", () => {
    render(
      <ProjectHealthWidget projects={mockProjects} orgSlug="acme" />
    );

    fireEvent.click(screen.getByText("Website Redesign"));

    expect(mockPush).toHaveBeenCalledWith(
      "/org/acme/projects/proj-1"
    );
  });

  it("navigates to projects page when 'View all projects' clicked", () => {
    render(
      <ProjectHealthWidget projects={mockProjects} orgSlug="acme" />
    );

    fireEvent.click(screen.getByText(/View all projects/));

    expect(mockPush).toHaveBeenCalledWith("/org/acme/projects");
  });

  it("renders filter tabs", () => {
    render(
      <ProjectHealthWidget projects={mockProjects} orgSlug="acme" />
    );

    expect(screen.getByRole("button", { name: "All" })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "At Risk" })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Critical" })
    ).toBeInTheDocument();
  });

  it("shows empty filter message when no projects match filter", () => {
    const healthyOnly: ProjectHealth[] = [
      {
        projectId: "proj-1",
        projectName: "Good Project",
        customerName: null,
        healthStatus: "HEALTHY",
        healthReasons: [],
        tasksDone: 10,
        tasksTotal: 10,
        completionPercent: 100,
        budgetConsumedPercent: null,
        hoursLogged: 50,
      },
    ];

    render(
      <ProjectHealthWidget projects={healthyOnly} orgSlug="acme" />
    );

    fireEvent.click(screen.getByRole("button", { name: "Critical" }));

    expect(screen.getByText("No critical projects")).toBeInTheDocument();
  });
});
