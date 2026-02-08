import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ProjectMembersPanel } from "./project-members-panel";
import type { ProjectMember } from "@/lib/types";

// Mock server actions
const mockRemoveProjectMember = vi.fn();
vi.mock("@/app/(app)/org/[slug]/projects/[id]/member-actions", () => ({
  removeProjectMember: (...args: unknown[]) => mockRemoveProjectMember(...args),
  fetchOrgMembers: vi.fn().mockResolvedValue([]),
  addProjectMember: vi.fn().mockResolvedValue({ success: true }),
}));

// Mock child dialog components for isolation
vi.mock("@/components/projects/add-member-dialog", () => ({
  AddMemberDialog: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="add-member-dialog">{children}</div>
  ),
}));

vi.mock("@/components/projects/transfer-lead-dialog", () => ({
  TransferLeadDialog: ({
    children,
    targetMemberName,
  }: {
    children: React.ReactNode;
    targetMemberName: string;
  }) => (
    <div data-testid="transfer-lead-dialog" data-target={targetMemberName}>
      {children}
    </div>
  ),
}));

const mockMembers: ProjectMember[] = [
  {
    id: "pm1",
    memberId: "user1",
    name: "Alice Lead",
    email: "alice@example.com",
    avatarUrl: null,
    projectRole: "lead",
    createdAt: "2024-01-15T10:00:00Z",
  },
  {
    id: "pm2",
    memberId: "user2",
    name: "Bob Member",
    email: "bob@example.com",
    avatarUrl: null,
    projectRole: "member",
    createdAt: "2024-01-20T14:30:00Z",
  },
];

describe("ProjectMembersPanel", () => {
  const defaultProps = {
    slug: "acme",
    projectId: "proj1",
    canManage: false,
    isCurrentLead: false,
    currentUserId: "user3",
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  describe("empty state", () => {
    it("renders empty state when no members", () => {
      render(<ProjectMembersPanel {...defaultProps} members={[]} />);

      expect(screen.getByText("No members yet")).toBeInTheDocument();
      expect(
        screen.getByText("Add team members to collaborate on this project"),
      ).toBeInTheDocument();
    });

    it("shows Add Member button in empty state when canManage", () => {
      render(<ProjectMembersPanel {...defaultProps} members={[]} canManage={true} />);

      expect(screen.queryByTestId("add-member-dialog")).toBeInTheDocument();
      expect(screen.getByText("Add Member")).toBeInTheDocument();
    });

    it("hides Add Member button in empty state when cannot manage", () => {
      render(<ProjectMembersPanel {...defaultProps} members={[]} canManage={false} />);

      expect(screen.queryByTestId("add-member-dialog")).not.toBeInTheDocument();
    });
  });

  describe("member list", () => {
    it("renders all members with correct roles", () => {
      render(<ProjectMembersPanel {...defaultProps} members={mockMembers} />);

      expect(screen.getByText("Alice Lead")).toBeInTheDocument();
      expect(screen.getByText("Bob Member")).toBeInTheDocument();
      expect(screen.getByText("Lead")).toBeInTheDocument();
      expect(screen.getByText("Member")).toBeInTheDocument();
    });

    it("displays member count in header", () => {
      const { container } = render(
        <ProjectMembersPanel {...defaultProps} members={mockMembers} />,
      );

      const heading = container.querySelector("h2");
      expect(heading).toHaveTextContent("Members (2)");
    });

    it("renders member emails", () => {
      render(<ProjectMembersPanel {...defaultProps} members={mockMembers} />);

      expect(screen.getByText("alice@example.com")).toBeInTheDocument();
      expect(screen.getByText("bob@example.com")).toBeInTheDocument();
    });
  });

  describe("action button visibility", () => {
    it("shows Add Member button when canManage", () => {
      render(<ProjectMembersPanel {...defaultProps} members={mockMembers} canManage={true} />);

      expect(screen.queryByTestId("add-member-dialog")).toBeInTheDocument();
      expect(screen.getByText("Add Member")).toBeInTheDocument();
    });

    it("hides Add Member button when cannot manage", () => {
      render(<ProjectMembersPanel {...defaultProps} members={mockMembers} canManage={false} />);

      expect(screen.queryByTestId("add-member-dialog")).not.toBeInTheDocument();
    });

    it("shows action menu only for non-lead members when canManage", () => {
      render(<ProjectMembersPanel {...defaultProps} members={mockMembers} canManage={true} />);

      // Only Bob (member) should have action menu, not Alice (lead)
      const actionButtons = screen.getAllByRole("button", { name: /open menu/i });
      expect(actionButtons).toHaveLength(1);
    });

    it("hides all action menus when cannot manage", () => {
      render(<ProjectMembersPanel {...defaultProps} members={mockMembers} canManage={false} />);

      expect(screen.queryByRole("button", { name: /open menu/i })).not.toBeInTheDocument();
    });
  });

  describe("remove member", () => {
    it("calls removeProjectMember when remove is clicked", async () => {
      mockRemoveProjectMember.mockResolvedValue({ success: true });
      const user = userEvent.setup();

      render(<ProjectMembersPanel {...defaultProps} members={mockMembers} canManage={true} />);

      // Open dropdown for Bob (the non-lead member)
      const actionButton = screen.getByRole("button", { name: /open menu/i });
      await user.click(actionButton);

      const removeButton = screen.getByRole("menuitem", { name: /remove/i });
      await user.click(removeButton);

      expect(mockRemoveProjectMember).toHaveBeenCalledWith("acme", "proj1", "user2");
    });
  });

  describe("transfer lead", () => {
    it("shows Transfer Lead option when isCurrentLead and member is not self", async () => {
      const user = userEvent.setup();

      render(
        <ProjectMembersPanel
          {...defaultProps}
          members={mockMembers}
          canManage={true}
          isCurrentLead={true}
          currentUserId="user1" // Alice is current user (lead)
        />,
      );

      // Open dropdown for Bob
      const actionButton = screen.getByRole("button", { name: /open menu/i });
      await user.click(actionButton);

      expect(screen.getByTestId("transfer-lead-dialog")).toBeInTheDocument();
      expect(screen.getByText("Transfer Lead")).toBeInTheDocument();
    });

    it("hides Transfer Lead option when not current lead", async () => {
      const user = userEvent.setup();

      render(
        <ProjectMembersPanel
          {...defaultProps}
          members={mockMembers}
          canManage={true}
          isCurrentLead={false}
        />,
      );

      const actionButton = screen.getByRole("button", { name: /open menu/i });
      await user.click(actionButton);

      expect(screen.queryByTestId("transfer-lead-dialog")).not.toBeInTheDocument();
    });
  });
});
