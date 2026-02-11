import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LinkProjectDialog } from "@/components/customers/link-project-dialog";
import type { Project } from "@/lib/types";

const mockFetchProjects = vi.fn();
const mockLinkProject = vi.fn();

vi.mock("@/app/(app)/org/[slug]/customers/[id]/actions", () => ({
  fetchProjects: (...args: unknown[]) => mockFetchProjects(...args),
  linkProject: (...args: unknown[]) => mockLinkProject(...args),
}));

const mockAllProjects: Project[] = [
  {
    id: "p1",
    name: "Project Alpha",
    description: "First project",
    createdBy: "m1",
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
    projectRole: null,
  },
  {
    id: "p2",
    name: "Project Beta",
    description: "Second project",
    createdBy: "m1",
    createdAt: "2024-02-01T00:00:00Z",
    updatedAt: "2024-02-01T00:00:00Z",
    projectRole: null,
  },
  {
    id: "p3",
    name: "Project Gamma",
    description: null,
    createdBy: "m1",
    createdAt: "2024-03-01T00:00:00Z",
    updatedAt: "2024-03-01T00:00:00Z",
    projectRole: null,
  },
];

const existingProjects: Project[] = [mockAllProjects[0]]; // Project Alpha is already linked

describe("LinkProjectDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("fetches projects when dialog opens", async () => {
    mockFetchProjects.mockResolvedValue(mockAllProjects);
    const user = userEvent.setup();

    render(
      <LinkProjectDialog slug="acme" customerId="c1" existingProjects={[]}>
        <button>Link Project Dialog Trigger</button>
      </LinkProjectDialog>,
    );

    await user.click(screen.getByText("Link Project Dialog Trigger"));

    await waitFor(() => {
      expect(mockFetchProjects).toHaveBeenCalledOnce();
    });
  });

  it("filters out already-linked projects", async () => {
    mockFetchProjects.mockResolvedValue(mockAllProjects);
    const user = userEvent.setup();

    render(
      <LinkProjectDialog slug="acme" customerId="c1" existingProjects={existingProjects}>
        <button>Link Project Dialog Trigger</button>
      </LinkProjectDialog>,
    );

    await user.click(screen.getByText("Link Project Dialog Trigger"));

    await waitFor(() => {
      expect(screen.getByText("Project Beta")).toBeInTheDocument();
      expect(screen.getByText("Project Gamma")).toBeInTheDocument();
    });

    // Project Alpha is already linked — should be filtered out
    expect(screen.queryByText("First project")).not.toBeInTheDocument();
  });

  it("calls linkProject when a project is selected", async () => {
    mockFetchProjects.mockResolvedValue(mockAllProjects);
    mockLinkProject.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(
      <LinkProjectDialog slug="acme" customerId="c1" existingProjects={existingProjects}>
        <button>Link Project Dialog Trigger</button>
      </LinkProjectDialog>,
    );

    await user.click(screen.getByText("Link Project Dialog Trigger"));

    await waitFor(() => {
      expect(screen.getByText("Project Beta")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Project Beta"));

    expect(mockLinkProject).toHaveBeenCalledWith("acme", "c1", "p2");
  });

  it("shows empty state when all projects are linked", async () => {
    mockFetchProjects.mockResolvedValue([mockAllProjects[0]]); // Only Alpha, which is already linked
    const user = userEvent.setup();

    render(
      <LinkProjectDialog slug="acme" customerId="c1" existingProjects={existingProjects}>
        <button>Link Project Dialog Trigger</button>
      </LinkProjectDialog>,
    );

    await user.click(screen.getByText("Link Project Dialog Trigger"));

    await waitFor(() => {
      expect(
        screen.getByText("All projects are already linked to this customer."),
      ).toBeInTheDocument();
    });
  });

  it("shows loading state while fetching projects", async () => {
    mockFetchProjects.mockImplementation(
      () => new Promise((resolve) => setTimeout(() => resolve(mockAllProjects), 500)),
    );
    const user = userEvent.setup();

    render(
      <LinkProjectDialog slug="acme" customerId="c1" existingProjects={[]}>
        <button>Link Project Dialog Trigger</button>
      </LinkProjectDialog>,
    );

    await user.click(screen.getByText("Link Project Dialog Trigger"));

    expect(screen.getByText("Loading projects...")).toBeInTheDocument();
  });

  it("displays error when linkProject fails", async () => {
    mockFetchProjects.mockResolvedValue(mockAllProjects);
    mockLinkProject.mockResolvedValue({ success: false, error: "Already linked" });
    const user = userEvent.setup();

    render(
      <LinkProjectDialog slug="acme" customerId="c1" existingProjects={[]}>
        <button>Link Project Dialog Trigger</button>
      </LinkProjectDialog>,
    );

    await user.click(screen.getByText("Link Project Dialog Trigger"));
    await waitFor(() => screen.getByText("Project Alpha"));
    await user.click(screen.getByText("Project Alpha"));

    await waitFor(() => {
      expect(screen.getByText("Already linked")).toBeInTheDocument();
    });
  });
});
