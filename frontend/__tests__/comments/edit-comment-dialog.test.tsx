import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EditCommentDialog } from "@/components/comments/edit-comment-dialog";
import type { Comment } from "@/lib/actions/comments";

const mockUpdateComment = vi.fn();

vi.mock("@/lib/actions/comments", () => ({
  updateComment: (...args: unknown[]) => mockUpdateComment(...args),
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
    body: "Original comment body",
    visibility: "INTERNAL",
    parentId: null,
    createdAt: "2026-02-10T10:00:00Z",
    updatedAt: "2026-02-10T10:00:00Z",
    ...overrides,
  };
}

describe("EditCommentDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("opens dialog with pre-filled comment body", async () => {
    const user = userEvent.setup();

    render(
      <EditCommentDialog
        comment={makeComment()}
        orgSlug="acme"
        projectId="p1"
        canManageVisibility={false}
      >
        <button>Edit comment</button>
      </EditCommentDialog>
    );

    await user.click(screen.getByRole("button", { name: "Edit comment" }));

    expect(screen.getByLabelText("Comment")).toHaveValue(
      "Original comment body"
    );
  });

  it("saves updated body via updateComment", async () => {
    mockUpdateComment.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(
      <EditCommentDialog
        comment={makeComment()}
        orgSlug="acme"
        projectId="p1"
        canManageVisibility={false}
      >
        <button>Edit comment</button>
      </EditCommentDialog>
    );

    await user.click(screen.getByRole("button", { name: "Edit comment" }));

    const textarea = screen.getByLabelText("Comment");
    await user.clear(textarea);
    await user.type(textarea, "Updated comment body");
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(mockUpdateComment).toHaveBeenCalledWith(
        "acme",
        "p1",
        "c1",
        "Updated comment body",
        undefined
      );
    });
  });

  it("shows visibility toggle only when canManageVisibility is true", async () => {
    const user = userEvent.setup();

    // Without visibility management
    const { unmount } = render(
      <EditCommentDialog
        comment={makeComment()}
        orgSlug="acme"
        projectId="p1"
        canManageVisibility={false}
      >
        <button>Edit no visibility</button>
      </EditCommentDialog>
    );

    await user.click(
      screen.getByRole("button", { name: "Edit no visibility" })
    );
    expect(screen.queryByLabelText("Visibility")).not.toBeInTheDocument();

    unmount();
    cleanup();

    // With visibility management
    render(
      <EditCommentDialog
        comment={makeComment()}
        orgSlug="acme"
        projectId="p1"
        canManageVisibility={true}
      >
        <button>Edit with visibility</button>
      </EditCommentDialog>
    );

    await user.click(
      screen.getByRole("button", { name: "Edit with visibility" })
    );
    expect(screen.getByLabelText("Visibility")).toBeInTheDocument();
  });
});
