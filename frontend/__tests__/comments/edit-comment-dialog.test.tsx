import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EditCommentDialog } from "@/components/comments/edit-comment-dialog";
import type { Comment, CommentAttachment } from "@/lib/types";

const mockUpdateComment = vi.fn();

vi.mock("@/lib/actions/comments", () => ({
  updateComment: (...args: unknown[]) => mockUpdateComment(...args),
}));

const mockInitiateUpload = vi.fn();
const mockConfirmUpload = vi.fn();
const mockCancelUpload = vi.fn();
const mockGetDownloadUrl = vi.fn();

vi.mock("@/app/(app)/org/[slug]/matters/[id]/actions", () => ({
  initiateUpload: (...args: unknown[]) => mockInitiateUpload(...args),
  confirmUpload: (...args: unknown[]) => mockConfirmUpload(...args),
  cancelUpload: (...args: unknown[]) => mockCancelUpload(...args),
  getDownloadUrl: (...args: unknown[]) => mockGetDownloadUrl(...args),
}));

/** Minimal XMLHttpRequest stand-in: every PUT succeeds synchronously. */
class FakeXhr {
  upload: {
    onprogress: ((e: { lengthComputable: boolean; loaded: number; total: number }) => void) | null;
  } = { onprogress: null };
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  onabort: (() => void) | null = null;
  status = 0;
  open() {}
  setRequestHeader() {}
  send() {
    this.upload.onprogress?.({ lengthComputable: true, loaded: 1, total: 1 });
    this.status = 200;
    this.onload?.();
  }
  abort() {
    this.onabort?.();
  }
}

const LETTER: CommentAttachment = {
  documentId: "doc-a",
  fileName: "letter.pdf",
  contentType: "application/pdf",
  size: 2048,
};
const RECEIPT: CommentAttachment = {
  documentId: "doc-b",
  fileName: "receipt.pdf",
  contentType: "application/pdf",
  size: 1024,
};

function makeComment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: "c1",
    entityType: "TASK",
    entityId: "t1",
    matterId: "p1",
    authorMemberId: "m1",
    authorName: "Alice Johnson",
    authorAvatarUrl: null,
    body: "Original comment body",
    visibility: "INTERNAL",
    source: "INTERNAL",
    parentId: null,
    createdAt: "2026-02-10T10:00:00Z",
    updatedAt: "2026-02-10T10:00:00Z",
    attachments: [],
    ...overrides,
  };
}

function renderDialog(
  triggerLabel: string,
  comment: Comment = makeComment(),
  canManageVisibility = false
) {
  return render(
    <EditCommentDialog
      comment={comment}
      orgSlug="acme"
      projectId="p1"
      canManageVisibility={canManageVisibility}
      triggerLabel={triggerLabel}
    />
  );
}

function fileInput(): HTMLInputElement {
  return document.querySelector('[role="dialog"] input[type="file"]') as HTMLInputElement;
}

describe("EditCommentDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal("XMLHttpRequest", FakeXhr);
    mockInitiateUpload.mockResolvedValue({
      success: true,
      documentId: "doc-new",
      presignedUrl: "https://s3.example.com/put-new",
    });
    mockConfirmUpload.mockResolvedValue({ success: true });
    mockCancelUpload.mockResolvedValue({ success: true });
    mockUpdateComment.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("opens dialog with pre-filled comment body", async () => {
    const user = userEvent.setup();

    renderDialog("Edit comment open");

    await user.click(screen.getByRole("button", { name: "Edit comment open" }));

    expect(screen.getByLabelText("Comment")).toHaveValue("Original comment body");
  });

  it("saves updated body via updateComment", async () => {
    const user = userEvent.setup();

    renderDialog("Edit comment save");

    await user.click(screen.getByRole("button", { name: "Edit comment save" }));

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
        undefined,
        undefined
      );
    });
  });

  it("shows visibility toggle only when canManageVisibility is true", async () => {
    const user = userEvent.setup();

    // Without visibility management
    const { unmount } = renderDialog("Edit no visibility");

    await user.click(screen.getByRole("button", { name: "Edit no visibility" }));
    expect(screen.queryByLabelText("Visibility")).not.toBeInTheDocument();

    unmount();
    cleanup();

    // With visibility management
    renderDialog("Edit with visibility", makeComment(), true);

    await user.click(screen.getByRole("button", { name: "Edit with visibility" }));
    expect(screen.getByLabelText("Visibility")).toBeInTheDocument();
  });

  it("shows the comment's existing attachments as removable chips", async () => {
    const user = userEvent.setup();

    renderDialog("Edit chips shown", makeComment({ attachments: [LETTER, RECEIPT] }));
    await user.click(screen.getByRole("button", { name: "Edit chips shown" }));

    expect(screen.getByText("letter.pdf")).toBeInTheDocument();
    expect(screen.getByText("receipt.pdf")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove letter.pdf" })).toHaveAttribute(
      "type",
      "button"
    );
  });

  it("visibility change keeps chips and does not re-send the attachment ids", async () => {
    const user = userEvent.setup();

    renderDialog("Edit visibility chips", makeComment({ attachments: [LETTER] }), true);
    await user.click(screen.getByRole("button", { name: "Edit visibility chips" }));

    await user.selectOptions(screen.getByLabelText("Visibility"), "SHARED");

    expect(screen.getByText("letter.pdf")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove letter.pdf" })).toBeInTheDocument();
    expect(
      screen.getByText("Attachments on a client-visible comment are shared with the client.")
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(mockUpdateComment).toHaveBeenCalledWith(
        "acme",
        "p1",
        "c1",
        "Original comment body",
        "SHARED",
        undefined
      );
    });
  });

  it("removing one chip sends the remaining ids in their original order", async () => {
    const user = userEvent.setup();

    renderDialog("Edit remove one", makeComment({ attachments: [LETTER, RECEIPT] }));
    await user.click(screen.getByRole("button", { name: "Edit remove one" }));

    await user.click(screen.getByRole("button", { name: "Remove letter.pdf" }));
    expect(screen.queryByText("letter.pdf")).not.toBeInTheDocument();
    // An attached document is not a pending upload: removal never cancels it.
    expect(mockCancelUpload).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(mockUpdateComment).toHaveBeenCalledWith(
        "acme",
        "p1",
        "c1",
        "Original comment body",
        undefined,
        ["doc-b"]
      );
    });
  });

  it("removing every chip sends an empty list", async () => {
    const user = userEvent.setup();

    renderDialog("Edit remove all", makeComment({ attachments: [LETTER] }));
    await user.click(screen.getByRole("button", { name: "Edit remove all" }));

    await user.click(screen.getByRole("button", { name: "Remove letter.pdf" }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(mockUpdateComment).toHaveBeenCalledWith(
        "acme",
        "p1",
        "c1",
        "Original comment body",
        undefined,
        []
      );
    });
  });

  it("adding a file confirms it and appends it after the kept ids", async () => {
    const user = userEvent.setup();

    renderDialog("Edit add file", makeComment({ attachments: [LETTER] }));
    await user.click(screen.getByRole("button", { name: "Edit add file" }));

    await user.upload(
      fileInput(),
      new File(["%PDF-1.4 new"], "new.pdf", { type: "application/pdf" })
    );
    await screen.findByText("new.pdf");
    expect(mockConfirmUpload).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(mockUpdateComment).toHaveBeenCalledWith(
        "acme",
        "p1",
        "c1",
        "Original comment body",
        undefined,
        ["doc-a", "doc-new"]
      );
    });
    expect(mockConfirmUpload).toHaveBeenCalledWith("acme", "p1", "doc-new");
    expect(mockCancelUpload).not.toHaveBeenCalled();
  });

  it("cancel releases staged uploads and re-opening resets the chips", async () => {
    const user = userEvent.setup();

    renderDialog("Edit cancel staged", makeComment({ attachments: [LETTER] }));
    await user.click(screen.getByRole("button", { name: "Edit cancel staged" }));

    await user.upload(
      fileInput(),
      new File(["%PDF-1.4 new"], "new.pdf", { type: "application/pdf" })
    );
    await screen.findByText("new.pdf");
    await user.click(screen.getByRole("button", { name: "Remove letter.pdf" }));

    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(mockCancelUpload).toHaveBeenCalledWith("doc-new");
    expect(mockCancelUpload).toHaveBeenCalledTimes(1);
    expect(mockUpdateComment).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Edit cancel staged" }));
    expect(await screen.findByText("letter.pdf")).toBeInTheDocument();
    expect(screen.queryByText("new.pdf")).not.toBeInTheDocument();
  });
});
