import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import type { Comment } from "@/lib/actions/comments";

// Mock dialog components to avoid Radix DOM leaks
vi.mock("@/components/comments/edit-comment-dialog", () => ({
  EditCommentDialog: () => null,
}));
vi.mock("@/components/comments/delete-comment-dialog", () => ({
  DeleteCommentDialog: () => null,
}));

import { CommentItem } from "@/components/comments/comment-item";

afterEach(() => {
  cleanup();
});

function makeComment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: "c-1",
    entityType: "PROJECT",
    entityId: "p-1",
    projectId: "p-1",
    authorMemberId: "m-1",
    authorName: "AI System",
    authorAvatarUrl: null,
    body: "Activity summary for the last 7 days.",
    visibility: "INTERNAL",
    source: "INTERNAL",
    parentId: null,
    createdAt: "2026-05-06T10:00:00Z",
    updatedAt: "2026-05-06T10:00:00Z",
    ...overrides,
  };
}

describe("Inbox Assistant attribution tag", () => {
  const baseProps = {
    currentMemberId: "m-2",
    canManageVisibility: false,
    orgSlug: "test-org",
    projectId: "p-1",
  };

  it("renders sparkle pill when attribution is 'Inbox Assistant'", () => {
    render(
      <CommentItem
        comment={makeComment({ attribution: "Inbox Assistant" })}
        {...baseProps}
      />
    );

    const tag = screen.getByTestId("inbox-assistant-tag");
    expect(tag).toBeDefined();
    expect(tag.textContent).toContain("Posted by Inbox Assistant");
  });

  it("does not render sparkle pill when attribution is null", () => {
    render(
      <CommentItem
        comment={makeComment({ attribution: null })}
        {...baseProps}
      />
    );

    expect(screen.queryByTestId("inbox-assistant-tag")).toBeNull();
  });

  it("does not render sparkle pill when attribution is absent", () => {
    render(
      <CommentItem comment={makeComment()} {...baseProps} />
    );

    expect(screen.queryByTestId("inbox-assistant-tag")).toBeNull();
  });
});
