import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AddMemberDialog } from "./add-member-dialog";
import type { OrgMember, ProjectMember } from "@/lib/types";

const mockFetchOrgMembers = vi.fn();
const mockAddProjectMember = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/member-actions", () => ({
  fetchOrgMembers: (...args: unknown[]) => mockFetchOrgMembers(...args),
  addProjectMember: (...args: unknown[]) => mockAddProjectMember(...args),
}));

const mockOrgMembers: OrgMember[] = [
  { id: "m1", name: "Alice", email: "alice@example.com", avatarUrl: null, orgRole: "org:member" },
  { id: "m2", name: "Bob", email: "bob@example.com", avatarUrl: null, orgRole: "org:admin" },
  {
    id: "m3",
    name: "Charlie",
    email: "charlie@example.com",
    avatarUrl: null,
    orgRole: "org:member",
  },
];

const existingMembers: ProjectMember[] = [
  {
    id: "pm1",
    memberId: "m1",
    name: "Alice",
    email: "alice@example.com",
    avatarUrl: null,
    projectRole: "lead",
    createdAt: "2024-01-01T00:00:00Z",
  },
];

describe("AddMemberDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("fetches org members when dialog opens", async () => {
    mockFetchOrgMembers.mockResolvedValue(mockOrgMembers);
    const user = userEvent.setup();

    render(
      <AddMemberDialog slug="acme" projectId="proj1" existingMembers={[]}>
        <button>Open Dialog</button>
      </AddMemberDialog>,
    );

    await user.click(screen.getByText("Open Dialog"));

    await waitFor(() => {
      expect(mockFetchOrgMembers).toHaveBeenCalledOnce();
    });
  });

  it("filters out existing project members", async () => {
    mockFetchOrgMembers.mockResolvedValue(mockOrgMembers);
    const user = userEvent.setup();

    render(
      <AddMemberDialog slug="acme" projectId="proj1" existingMembers={existingMembers}>
        <button>Open Dialog</button>
      </AddMemberDialog>,
    );

    await user.click(screen.getByText("Open Dialog"));

    await waitFor(() => {
      expect(screen.getByText("Bob")).toBeInTheDocument();
      expect(screen.getByText("Charlie")).toBeInTheDocument();
    });

    // Alice is already a project member — should be filtered out
    expect(screen.queryByText("alice@example.com")).not.toBeInTheDocument();
  });

  it("calls addProjectMember when member is selected", async () => {
    mockFetchOrgMembers.mockResolvedValue(mockOrgMembers);
    mockAddProjectMember.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(
      <AddMemberDialog slug="acme" projectId="proj1" existingMembers={existingMembers}>
        <button>Open Dialog</button>
      </AddMemberDialog>,
    );

    await user.click(screen.getByText("Open Dialog"));

    await waitFor(() => {
      expect(screen.getByText("Bob")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Bob"));

    expect(mockAddProjectMember).toHaveBeenCalledWith("acme", "proj1", "m2");
  });

  it("displays error when addProjectMember fails", async () => {
    mockFetchOrgMembers.mockResolvedValue(mockOrgMembers);
    mockAddProjectMember.mockResolvedValue({ success: false, error: "Permission denied" });
    const user = userEvent.setup();

    render(
      <AddMemberDialog slug="acme" projectId="proj1" existingMembers={existingMembers}>
        <button>Open Dialog</button>
      </AddMemberDialog>,
    );

    await user.click(screen.getByText("Open Dialog"));
    await waitFor(() => screen.getByText("Bob"));
    await user.click(screen.getByText("Bob"));

    await waitFor(() => {
      expect(screen.getByText("Permission denied")).toBeInTheDocument();
    });
  });

  it("shows empty state when all members are on project", async () => {
    mockFetchOrgMembers.mockResolvedValue([mockOrgMembers[0]]); // Only Alice, who is already a member
    const user = userEvent.setup();

    render(
      <AddMemberDialog slug="acme" projectId="proj1" existingMembers={existingMembers}>
        <button>Open Dialog</button>
      </AddMemberDialog>,
    );

    await user.click(screen.getByText("Open Dialog"));

    await waitFor(() => {
      expect(
        screen.getByText("All organization members are already on this project."),
      ).toBeInTheDocument();
    });
  });

  it("shows loading state while fetching members", async () => {
    mockFetchOrgMembers.mockImplementation(
      () => new Promise((resolve) => setTimeout(() => resolve(mockOrgMembers), 500)),
    );
    const user = userEvent.setup();

    render(
      <AddMemberDialog slug="acme" projectId="proj1" existingMembers={[]}>
        <button>Open Dialog</button>
      </AddMemberDialog>,
    );

    await user.click(screen.getByText("Open Dialog"));

    expect(screen.getByText("Loading members...")).toBeInTheDocument();
  });

  it("shows error when fetch fails", async () => {
    mockFetchOrgMembers.mockRejectedValue(new Error("Network error"));
    const user = userEvent.setup();

    render(
      <AddMemberDialog slug="acme" projectId="proj1" existingMembers={[]}>
        <button>Open Dialog</button>
      </AddMemberDialog>,
    );

    await user.click(screen.getByText("Open Dialog"));

    await waitFor(() => {
      expect(screen.getByText("Failed to load organization members.")).toBeInTheDocument();
    });
  });
});
