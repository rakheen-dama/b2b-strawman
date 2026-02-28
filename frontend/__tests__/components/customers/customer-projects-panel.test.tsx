import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { CustomerProjectsPanel } from "@/components/customers/customer-projects-panel";
import type { Project } from "@/lib/types";

// Mock server actions used by LinkProjectDialog (child component)
vi.mock("@/app/(app)/org/[slug]/customers/[id]/actions", () => ({
  fetchProjects: vi.fn().mockResolvedValue([]),
  linkProject: vi.fn().mockResolvedValue({ success: true }),
  unlinkProject: vi.fn().mockResolvedValue({ success: true }),
}));

const mockProjects: Project[] = [
  {
    id: "p1",
    name: "Project Alpha",
    description: "First project",
    status: "ACTIVE",
    customerId: null,
    dueDate: null,
    createdBy: "m1",
    createdByName: "Member 1",
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
    completedAt: null,
    completedBy: null,
    completedByName: null,
    archivedAt: null,
    projectRole: null,
  },
  {
    id: "p2",
    name: "Project Beta",
    description: "Second project",
    status: "ACTIVE",
    customerId: null,
    dueDate: null,
    createdBy: "m1",
    createdByName: "Member 1",
    createdAt: "2024-02-01T00:00:00Z",
    updatedAt: "2024-02-01T00:00:00Z",
    completedAt: null,
    completedBy: null,
    completedByName: null,
    archivedAt: null,
    projectRole: null,
  },
];

describe("CustomerProjectsPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders projects with correct names", () => {
    render(
      <CustomerProjectsPanel
        projects={mockProjects}
        slug="acme"
        customerId="c1"
        canManage={false}
      />,
    );

    expect(screen.getByText("Project Alpha")).toBeInTheDocument();
    expect(screen.getByText("Project Beta")).toBeInTheDocument();
  });

  it("renders empty state when no projects", () => {
    render(
      <CustomerProjectsPanel
        projects={[]}
        slug="acme"
        customerId="c1"
        canManage={false}
      />,
    );

    expect(screen.getByText("No linked projects")).toBeInTheDocument();
  });

  it("shows Link Project button when canManage is true", () => {
    render(
      <CustomerProjectsPanel
        projects={[]}
        slug="acme"
        customerId="c1"
        canManage={true}
      />,
    );

    expect(screen.getByText("Link Project")).toBeInTheDocument();
  });

  it("hides Link Project button when canManage is false", () => {
    render(
      <CustomerProjectsPanel
        projects={mockProjects}
        slug="acme"
        customerId="c1"
        canManage={false}
      />,
    );

    expect(screen.queryByText("Link Project")).not.toBeInTheDocument();
  });

  it("shows unlink buttons when canManage is true", () => {
    render(
      <CustomerProjectsPanel
        projects={mockProjects}
        slug="acme"
        customerId="c1"
        canManage={true}
      />,
    );

    const unlinkButtons = screen.getAllByTitle("Unlink project");
    expect(unlinkButtons).toHaveLength(2);
  });

  it("shows project count badge", () => {
    render(
      <CustomerProjectsPanel
        projects={mockProjects}
        slug="acme"
        customerId="c1"
        canManage={false}
      />,
    );

    expect(screen.getByText("2")).toBeInTheDocument();
  });
});
