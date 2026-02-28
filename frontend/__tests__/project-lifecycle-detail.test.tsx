import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("server-only", () => ({}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    refresh: vi.fn(),
  }),
}));

const mockCompleteProject = vi.fn();
const mockArchiveProject = vi.fn();
const mockReopenProject = vi.fn();
vi.mock("@/app/(app)/org/[slug]/projects/actions", () => ({
  completeProject: (...args: unknown[]) => mockCompleteProject(...args),
  archiveProject: (...args: unknown[]) => mockArchiveProject(...args),
  reopenProject: (...args: unknown[]) => mockReopenProject(...args),
  createProject: vi.fn(),
  updateProject: vi.fn(),
  deleteProject: vi.fn(),
  fetchActiveCustomers: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/customers/[id]/actions", () => ({
  unlinkProject: vi.fn(),
}));

import { CompleteProjectDialog } from "@/components/projects/complete-project-dialog";
import { ProjectLifecycleActions } from "@/components/projects/project-lifecycle-actions";
import { ArchivedProjectBanner } from "@/components/projects/archived-project-banner";
import { CustomerProjectsPanel } from "@/components/customers/customer-projects-panel";
import type { Project } from "@/lib/types";

function makeProject(overrides: Partial<Project> = {}): Project {
  return {
    id: "proj-1",
    name: "Test Project",
    description: "A test project",
    status: "ACTIVE",
    customerId: null,
    dueDate: null,
    createdBy: "user-1",
    createdByName: "Alice",
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
    completedAt: null,
    completedBy: null,
    completedByName: null,
    archivedAt: null,
    projectRole: null,
    ...overrides,
  };
}

describe("CompleteProjectDialog", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders initial confirmation when opened", async () => {
    const user = userEvent.setup();
    render(
      <CompleteProjectDialog slug="test-org" projectId="proj-1" projectName="Test Project">
        <button>Open Complete Dialog</button>
      </CompleteProjectDialog>,
    );

    await user.click(screen.getByText("Open Complete Dialog"));
    expect(screen.getByText("Complete Project")).toBeInTheDocument();
    expect(screen.getByTestId("confirm-complete-btn")).toBeInTheDocument();
    expect(screen.getByText("Cancel")).toBeInTheDocument();
  });

  it("shows unbilled time warning on 409-like error", async () => {
    const user = userEvent.setup();
    mockCompleteProject.mockResolvedValueOnce({
      success: false,
      error: "Project has unbilled time entries.",
    });

    render(
      <CompleteProjectDialog slug="test-org" projectId="proj-1" projectName="Test Project">
        <button>Open Unbilled Dialog</button>
      </CompleteProjectDialog>,
    );

    await user.click(screen.getByText("Open Unbilled Dialog"));
    await user.click(screen.getByTestId("confirm-complete-btn"));

    expect(await screen.findByText("Unbilled Time Warning")).toBeInTheDocument();
    expect(screen.getByTestId("complete-anyway-btn")).toBeInTheDocument();
  });

  it("shows blocking error on 400-like error (open tasks)", async () => {
    const user = userEvent.setup();
    mockCompleteProject.mockResolvedValueOnce({
      success: false,
      error: "Cannot complete: project has 3 open tasks.",
    });

    render(
      <CompleteProjectDialog slug="test-org" projectId="proj-1" projectName="Test Project">
        <button>Open Blocking Dialog</button>
      </CompleteProjectDialog>,
    );

    await user.click(screen.getByText("Open Blocking Dialog"));
    await user.click(screen.getByTestId("confirm-complete-btn"));

    expect(
      await screen.findByText("Cannot complete: project has 3 open tasks."),
    ).toBeInTheDocument();
    // Should NOT show "Complete Anyway" for blocking errors
    expect(screen.queryByTestId("complete-anyway-btn")).not.toBeInTheDocument();
  });
});

describe("ProjectLifecycleActions", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("shows Complete Project button and overflow menu for ACTIVE status", () => {
    render(
      <ProjectLifecycleActions
        slug="test-org"
        projectId="proj-1"
        projectName="Test Project"
        projectStatus="ACTIVE"
      />,
    );

    expect(screen.getByTestId("complete-project-btn")).toBeInTheDocument();
    expect(screen.getByText("Complete Project")).toBeInTheDocument();
    expect(screen.getByTestId("project-overflow-menu")).toBeInTheDocument();
  });

  it("shows Archive button and overflow with Reopen for COMPLETED status", () => {
    render(
      <ProjectLifecycleActions
        slug="test-org"
        projectId="proj-1"
        projectName="Test Project"
        projectStatus="COMPLETED"
      />,
    );

    expect(screen.getByTestId("archive-project-btn")).toBeInTheDocument();
    expect(screen.getByText("Archive")).toBeInTheDocument();
    expect(screen.getByTestId("project-overflow-menu")).toBeInTheDocument();
  });

  it("shows Restore button for ARCHIVED status", () => {
    render(
      <ProjectLifecycleActions
        slug="test-org"
        projectId="proj-1"
        projectName="Test Project"
        projectStatus="ARCHIVED"
      />,
    );

    expect(screen.getByTestId("restore-project-btn")).toBeInTheDocument();
    expect(screen.getByText("Restore")).toBeInTheDocument();
    // No overflow menu for archived
    expect(screen.queryByTestId("project-overflow-menu")).not.toBeInTheDocument();
  });
});

describe("ArchivedProjectBanner", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders archived banner with restore button", () => {
    render(<ArchivedProjectBanner slug="test-org" projectId="proj-1" />);

    expect(screen.getByTestId("archived-project-banner")).toBeInTheDocument();
    expect(
      screen.getByText("This project is archived. It is read-only."),
    ).toBeInTheDocument();
    expect(screen.getByText("Restore")).toBeInTheDocument();
  });
});

describe("CustomerProjectsPanel", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders status badges and due dates for projects", () => {
    const projects: Project[] = [
      makeProject({
        id: "proj-1",
        name: "Active Project",
        status: "ACTIVE",
        dueDate: "2099-12-31",
      }),
      makeProject({
        id: "proj-2",
        name: "Completed Project",
        status: "COMPLETED",
        dueDate: null,
      }),
    ];

    render(
      <CustomerProjectsPanel
        projects={projects}
        slug="test-org"
        customerId="cust-1"
        canManage={false}
      />,
    );

    const statusBadges = screen.getAllByTestId("customer-project-status-badge");
    expect(statusBadges).toHaveLength(2);
    expect(within(statusBadges[0]).getByText("Active")).toBeInTheDocument();
    expect(within(statusBadges[1]).getByText("Completed")).toBeInTheDocument();

    // Due date shown for Active Project
    expect(screen.getByTestId("customer-project-due-date")).toBeInTheDocument();
  });
});
