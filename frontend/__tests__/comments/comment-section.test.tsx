import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AddCommentForm } from "@/components/comments/add-comment-form";
import { CommentItem } from "@/components/comments/comment-item";
import type { Comment } from "@/lib/actions/comments";

const mockCreateComment = vi.fn();
const mockUpdateComment = vi.fn();
const mockDeleteComment = vi.fn();

vi.mock("@/lib/actions/comments", () => ({
  createComment: (...args: unknown[]) => mockCreateComment(...args),
  updateComment: (...args: unknown[]) => mockUpdateComment(...args),
  deleteComment: (...args: unknown[]) => mockDeleteComment(...args),
}));

function makeComment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: "c1",
    entityType: "TASK",
    entityId: "t1",
    projectId: "p1",
    authorMemberId: "m1",
    authorName: "Alice Johnson",
    authorAvatarUrl: null,
    body: "This is a test comment.",
    visibility: "INTERNAL",
    parentId: null,
    createdAt: "2026-02-10T10:00:00Z",
    updatedAt: "2026-02-10T10:00:00Z",
    ...overrides,
  };
}

describe("CommentItem", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders comment with avatar, name, body, and timestamp", () => {
    render(
      <CommentItem
        comment={makeComment()}
        currentMemberId="other-member"
        canManageVisibility={false}
        orgSlug="acme"
        projectId="p1"
      />
    );

    expect(screen.getByText("AJ")).toBeInTheDocument(); // Avatar initials
    expect(screen.getByText("Alice Johnson")).toBeInTheDocument();
    expect(screen.getByText("This is a test comment.")).toBeInTheDocument();
  });

  it("shows 'Customer visible' badge for SHARED comments", () => {
    render(
      <CommentItem
        comment={makeComment({ visibility: "SHARED" })}
        currentMemberId="other-member"
        canManageVisibility={false}
        orgSlug="acme"
        projectId="p1"
      />
    );

    expect(screen.getByText("Customer visible")).toBeInTheDocument();
  });

  it("shows edit and delete buttons for own comments", () => {
    render(
      <CommentItem
        comment={makeComment({ authorMemberId: "current-member", authorName: "Me" })}
        currentMemberId="current-member"
        canManageVisibility={false}
        orgSlug="acme"
        projectId="p1"
      />
    );

    expect(
      screen.getByRole("button", { name: /edit comment by me/i })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /delete comment by me/i })
    ).toBeInTheDocument();
  });

  it("hides edit and delete buttons for other users' comments", () => {
    render(
      <CommentItem
        comment={makeComment({ authorMemberId: "other-member" })}
        currentMemberId="current-member"
        canManageVisibility={false}
        orgSlug="acme"
        projectId="p1"
      />
    );

    expect(
      screen.queryByRole("button", { name: /edit comment/i })
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /delete comment/i })
    ).not.toBeInTheDocument();
  });
});

describe("AddCommentForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("shows visibility toggle when canManageVisibility is true", () => {
    render(
      <AddCommentForm
        projectId="p1"
        entityType="TASK"
        entityId="t1"
        orgSlug="acme"
        canManageVisibility={true}
      />
    );

    expect(screen.getByLabelText("Visibility")).toBeInTheDocument();
    expect(screen.getByText("Internal only")).toBeInTheDocument();
    expect(screen.getByText("Customer visible")).toBeInTheDocument();
  });

  it("hides visibility toggle when canManageVisibility is false", () => {
    render(
      <AddCommentForm
        projectId="p1"
        entityType="TASK"
        entityId="t1"
        orgSlug="acme"
        canManageVisibility={false}
      />
    );

    expect(screen.queryByLabelText("Visibility")).not.toBeInTheDocument();
  });

  it("calls createComment on successful submit", async () => {
    mockCreateComment.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(
      <AddCommentForm
        projectId="p1"
        entityType="TASK"
        entityId="t1"
        orgSlug="acme"
        canManageVisibility={false}
      />
    );

    await user.type(screen.getByLabelText("Add a comment"), "My new comment");
    await user.click(screen.getByRole("button", { name: "Post Comment" }));

    await waitFor(() => {
      expect(mockCreateComment).toHaveBeenCalledWith(
        "acme",
        "p1",
        "TASK",
        "t1",
        "My new comment",
        undefined
      );
    });
  });

  it("displays error when createComment fails", async () => {
    mockCreateComment.mockResolvedValue({
      success: false,
      error: "Server error",
    });
    const user = userEvent.setup();

    render(
      <AddCommentForm
        projectId="p1"
        entityType="TASK"
        entityId="t1"
        orgSlug="acme"
        canManageVisibility={false}
      />
    );

    await user.type(screen.getByLabelText("Add a comment"), "My comment");
    await user.click(screen.getByRole("button", { name: "Post Comment" }));

    await waitFor(() => {
      expect(screen.getByText("Server error")).toBeInTheDocument();
    });
  });
});
