import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { act, cleanup, render, renderHook, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AddCommentForm } from "@/components/comments/add-comment-form";
import { CommentItem } from "@/components/comments/comment-item";
import { useCommentAttachments } from "@/components/comments/use-comment-attachments";
import type { Comment } from "@/lib/types";

const mockCreateComment = vi.fn();
const mockUpdateComment = vi.fn();
const mockDeleteComment = vi.fn();

vi.mock("@/lib/actions/comments", () => ({
  createComment: (...args: unknown[]) => mockCreateComment(...args),
  updateComment: (...args: unknown[]) => mockUpdateComment(...args),
  deleteComment: (...args: unknown[]) => mockDeleteComment(...args),
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
  static instances: FakeXhr[] = [];
  upload: {
    onprogress: ((e: { lengthComputable: boolean; loaded: number; total: number }) => void) | null;
  } = { onprogress: null };
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  onabort: (() => void) | null = null;
  status = 0;
  method = "";
  url = "";
  headers: Record<string, string> = {};
  constructor() {
    FakeXhr.instances.push(this);
  }
  open(method: string, url: string) {
    this.method = method;
    this.url = url;
  }
  setRequestHeader(name: string, value: string) {
    this.headers[name] = value;
  }
  send() {
    this.upload.onprogress?.({ lengthComputable: true, loaded: 1, total: 1 });
    this.status = 200;
    this.onload?.();
  }
  abort() {
    this.onabort?.();
  }
}

function makeComment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: "c1",
    entityType: "TASK",
    entityId: "t1",
    matterId: "p1",
    authorMemberId: "m1",
    authorName: "Alice Johnson",
    authorAvatarUrl: null,
    body: "This is a test comment.",
    visibility: "INTERNAL",
    source: "INTERNAL",
    parentId: null,
    createdAt: "2026-02-10T10:00:00Z",
    updatedAt: "2026-02-10T10:00:00Z",
    attachments: [],
    ...overrides,
  };
}

function pdf(name: string): File {
  return new File(["%PDF-1.4 test"], name, { type: "application/pdf" });
}

let docCounter = 0;

function renderForm(canManageVisibility = false) {
  const utils = render(
    <AddCommentForm
      projectId="p1"
      entityType="TASK"
      entityId="t1"
      orgSlug="acme"
      canManageVisibility={canManageVisibility}
    />
  );
  const input = utils.container.querySelector('input[type="file"]') as HTMLInputElement | null;
  return { ...utils, input };
}

describe("CommentItem", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
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

    expect(screen.getByRole("button", { name: /edit comment by me/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /delete comment by me/i })).toBeInTheDocument();
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

    expect(screen.queryByRole("button", { name: /edit comment/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /delete comment/i })).not.toBeInTheDocument();
  });

  it("renders attachment chips that download through getDownloadUrl", async () => {
    mockGetDownloadUrl.mockResolvedValue({
      success: true,
      presignedUrl: "https://s3.example.com/letter.pdf",
    });
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    const user = userEvent.setup();

    render(
      <CommentItem
        comment={makeComment({
          attachments: [
            {
              documentId: "doc-1",
              fileName: "letter.pdf",
              contentType: "application/pdf",
              size: 2048,
            },
          ],
        })}
        currentMemberId="other-member"
        canManageVisibility={false}
        orgSlug="acme"
        projectId="p1"
      />
    );

    expect(screen.getByRole("list", { name: "Attachments" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Download letter.pdf" }));

    await waitFor(() => {
      expect(mockGetDownloadUrl).toHaveBeenCalledWith("doc-1");
    });
    expect(clickSpy).toHaveBeenCalled();
  });

  it("shows an inline error when the attachment download fails", async () => {
    mockGetDownloadUrl.mockResolvedValue({ success: false, error: "Download failed." });
    const user = userEvent.setup();

    render(
      <CommentItem
        comment={makeComment({
          attachments: [
            {
              documentId: "doc-1",
              fileName: "letter.pdf",
              contentType: "application/pdf",
              size: 2048,
            },
          ],
        })}
        currentMemberId="other-member"
        canManageVisibility={false}
        orgSlug="acme"
        projectId="p1"
      />
    );

    await user.click(screen.getByRole("button", { name: "Download letter.pdf" }));
    expect(await screen.findByText("Download failed.")).toBeInTheDocument();
  });

  it("recovers when the download action rejects: shows the error and re-enables the chip", async () => {
    mockGetDownloadUrl.mockRejectedValue(new Error("network down"));
    const user = userEvent.setup();

    render(
      <CommentItem
        comment={makeComment({
          attachments: [
            {
              documentId: "doc-1",
              fileName: "letter.pdf",
              contentType: "application/pdf",
              size: 2048,
            },
          ],
        })}
        currentMemberId="other-member"
        canManageVisibility={false}
        orgSlug="acme"
        projectId="p1"
      />
    );

    await user.click(screen.getByRole("button", { name: "Download letter.pdf" }));
    expect(await screen.findByText("Download failed.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Download letter.pdf" })).not.toBeDisabled();
  });

  it("renders no attachment list when the comment has no attachments", () => {
    render(
      <CommentItem
        comment={makeComment()}
        currentMemberId="other-member"
        canManageVisibility={false}
        orgSlug="acme"
        projectId="p1"
      />
    );

    expect(screen.queryByRole("list", { name: "Attachments" })).not.toBeInTheDocument();
  });
});

describe("AddCommentForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    docCounter = 0;
    FakeXhr.instances = [];
    vi.stubGlobal("XMLHttpRequest", FakeXhr);
    mockInitiateUpload.mockImplementation(async () => {
      docCounter += 1;
      return {
        success: true,
        documentId: `doc-${docCounter}`,
        presignedUrl: `https://s3.example.com/put-${docCounter}`,
      };
    });
    mockConfirmUpload.mockResolvedValue({ success: true });
    mockCancelUpload.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("shows visibility toggle when canManageVisibility is true", () => {
    renderForm(true);

    expect(screen.getByLabelText("Visibility")).toBeInTheDocument();
    expect(screen.getByText("Internal only")).toBeInTheDocument();
    expect(screen.getByText("Customer visible")).toBeInTheDocument();
  });

  it("hides visibility toggle when canManageVisibility is false", () => {
    renderForm(false);

    expect(screen.queryByLabelText("Visibility")).not.toBeInTheDocument();
  });

  it("calls createComment on successful submit", async () => {
    mockCreateComment.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    renderForm(false);

    await user.type(screen.getByLabelText("Add a comment"), "My new comment");
    await user.click(screen.getByRole("button", { name: "Post Comment" }));

    await waitFor(() => {
      expect(mockCreateComment).toHaveBeenCalledWith(
        "acme",
        "p1",
        "TASK",
        "t1",
        "My new comment",
        undefined,
        []
      );
    });
    expect(mockConfirmUpload).not.toHaveBeenCalled();
  });

  it("displays error when createComment fails", async () => {
    mockCreateComment.mockResolvedValue({
      success: false,
      error: "Server error",
    });
    const user = userEvent.setup();

    renderForm(false);

    await user.type(screen.getByLabelText("Add a comment"), "My comment");
    await user.click(screen.getByRole("button", { name: "Post Comment" }));

    await waitFor(() => {
      expect(screen.getByText("Server error")).toBeInTheDocument();
    });
  });

  it("renders a compact attachment dropzone whose name stays clear of submit words", () => {
    const { input } = renderForm(false);

    const zone = screen.getByRole("button", { name: /attach files/i });
    expect(zone).toBeInTheDocument();
    expect(zone.textContent ?? "").not.toMatch(/send|post|submit|comment/i);
    expect(input).not.toBeNull();
    expect(input).not.toHaveAttribute("name");
  });

  it("stages a chosen file through upload-init + PUT and shows a chip before submit", async () => {
    const user = userEvent.setup();
    const { input } = renderForm(false);
    const file = pdf("letter.pdf");

    await user.upload(input!, file);

    await waitFor(() => {
      expect(mockInitiateUpload).toHaveBeenCalledWith(
        "acme",
        "p1",
        "letter.pdf",
        "application/pdf",
        file.size
      );
    });
    expect(await screen.findByText("letter.pdf")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove letter.pdf" })).toHaveAttribute(
      "type",
      "button"
    );
    expect(FakeXhr.instances).toHaveLength(1);
    expect(FakeXhr.instances[0].method).toBe("PUT");
    expect(FakeXhr.instances[0].url).toBe("https://s3.example.com/put-1");
    expect(FakeXhr.instances[0].headers["Content-Type"]).toBe("application/pdf");
    // Staged only: the document stays PENDING until the comment is posted.
    expect(mockConfirmUpload).not.toHaveBeenCalled();
    expect(mockCreateComment).not.toHaveBeenCalled();
  });

  it("confirms staged uploads on Post and sends attachmentDocumentIds in the body", async () => {
    mockCreateComment.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    const { input } = renderForm(false);

    await user.upload(input!, pdf("masters-appointment.pdf"));
    await screen.findByText("masters-appointment.pdf");

    await user.type(screen.getByLabelText("Add a comment"), "Master's appointment received");
    await user.click(screen.getByRole("button", { name: "Post Comment" }));

    await waitFor(() => {
      expect(mockCreateComment).toHaveBeenCalledWith(
        "acme",
        "p1",
        "TASK",
        "t1",
        "Master's appointment received",
        undefined,
        ["doc-1"]
      );
    });
    expect(mockConfirmUpload).toHaveBeenCalledWith("acme", "p1", "doc-1");
    expect(mockConfirmUpload.mock.invocationCallOrder[0]).toBeLessThan(
      mockCreateComment.mock.invocationCallOrder[0]
    );
    // Success clears the chips; nothing is released (the document is attached).
    await waitFor(() => {
      expect(screen.queryByText("masters-appointment.pdf")).not.toBeInTheDocument();
    });
    expect(mockCancelUpload).not.toHaveBeenCalled();
  });

  it("removing a staged chip cancels the pending upload and drops it from the body", async () => {
    mockCreateComment.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    const { input } = renderForm(false);

    await user.upload(input!, pdf("letter.pdf"));
    await screen.findByText("letter.pdf");

    await user.click(screen.getByRole("button", { name: "Remove letter.pdf" }));

    expect(mockCancelUpload).toHaveBeenCalledWith("doc-1");
    expect(screen.queryByText("letter.pdf")).not.toBeInTheDocument();
    expect(mockCreateComment).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText("Add a comment"), "No files now");
    await user.click(screen.getByRole("button", { name: "Post Comment" }));

    await waitFor(() => {
      expect(mockCreateComment).toHaveBeenCalledWith(
        "acme",
        "p1",
        "TASK",
        "t1",
        "No files now",
        undefined,
        []
      );
    });
    expect(mockConfirmUpload).not.toHaveBeenCalled();
  });

  it("stages at most 5 files and explains the limit", async () => {
    const user = userEvent.setup();
    const { input } = renderForm(false);

    await user.upload(input!, [
      pdf("a.pdf"),
      pdf("b.pdf"),
      pdf("c.pdf"),
      pdf("d.pdf"),
      pdf("e.pdf"),
      pdf("f.pdf"),
    ]);

    expect(
      await screen.findByText("A comment can have at most 5 attachments.")
    ).toBeInTheDocument();
    await waitFor(() => {
      expect(mockInitiateUpload).toHaveBeenCalledTimes(5);
    });
    expect(screen.getByText("e.pdf")).toBeInTheDocument();
    expect(screen.queryByText("f.pdf")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /attach files/i })).toHaveAttribute("tabindex", "-1");
  });

  it("keeps chips on a 422 and names the rejected file instead of its id", async () => {
    mockCreateComment.mockResolvedValue({
      success: false,
      error: "Document doc-1 is already attached to another comment",
      attachmentDocumentId: "doc-1",
    });
    const user = userEvent.setup();
    const { input } = renderForm(false);

    await user.upload(input!, pdf("letter.pdf"));
    await screen.findByText("letter.pdf");
    await user.type(screen.getByLabelText("Add a comment"), "Retry me");
    await user.click(screen.getByRole("button", { name: "Post Comment" }));

    expect(
      await screen.findByText("Document letter.pdf is already attached to another comment")
    ).toBeInTheDocument();
    expect(screen.getByText("letter.pdf")).toBeInTheDocument();
    expect(screen.queryByText(/doc-1/)).not.toBeInTheDocument();
    // The rejected chip is in error, so Post is blocked until it is removed.
    expect(screen.getByText("Remove failed attachments to continue.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Post Comment" })).toBeDisabled();

    // The chip was confirmed by the failed attempt: removing it must not cancel (409).
    await user.click(screen.getByRole("button", { name: "Remove letter.pdf" }));
    expect(mockCancelUpload).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Post Comment" })).not.toBeDisabled();
  });

  it("does not re-confirm an already-confirmed chip on retry", async () => {
    mockCreateComment
      .mockResolvedValueOnce({ success: false, error: "Network hiccup" })
      .mockResolvedValueOnce({ success: true });
    const user = userEvent.setup();
    const { input } = renderForm(false);

    await user.upload(input!, pdf("letter.pdf"));
    await screen.findByText("letter.pdf");

    await user.type(screen.getByLabelText("Add a comment"), "First try");
    await user.click(screen.getByRole("button", { name: "Post Comment" }));
    expect(await screen.findByText("Network hiccup")).toBeInTheDocument();
    expect(screen.getByText("letter.pdf")).toBeInTheDocument();

    await user.clear(screen.getByLabelText("Add a comment"));
    await user.type(screen.getByLabelText("Add a comment"), "Second try");
    await user.click(screen.getByRole("button", { name: "Post Comment" }));

    await waitFor(() => {
      expect(mockCreateComment).toHaveBeenLastCalledWith(
        "acme",
        "p1",
        "TASK",
        "t1",
        "Second try",
        undefined,
        ["doc-1"]
      );
    });
    expect(mockConfirmUpload).toHaveBeenCalledTimes(1);
    expect(mockInitiateUpload).toHaveBeenCalledTimes(1);
  });

  it("shows the client-visible hint only for SHARED with at least one chip", async () => {
    const user = userEvent.setup();
    const { input } = renderForm(true);
    const hint = "Attachments on a client-visible comment are shared with the client.";

    await user.selectOptions(screen.getByLabelText("Visibility"), "SHARED");
    expect(screen.queryByText(hint)).not.toBeInTheDocument();

    await user.upload(input!, pdf("letter.pdf"));
    expect(await screen.findByText(hint)).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Visibility"), "INTERNAL");
    expect(screen.queryByText(hint)).not.toBeInTheDocument();
  });

  it("a rejected confirm leaves the chip in error (not stuck finishing) so Post recovers", async () => {
    mockConfirmUpload.mockRejectedValue(new Error("network down"));
    const user = userEvent.setup();
    const { input } = renderForm(false);

    await user.upload(input!, pdf("letter.pdf"));
    await screen.findByText("letter.pdf");
    await user.type(screen.getByLabelText("Add a comment"), "Confirm will throw");
    await user.click(screen.getByRole("button", { name: "Post Comment" }));

    expect(await screen.findByText("letter.pdf: Failed to finish the upload.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Finishing")).not.toBeInTheDocument();
    expect(screen.getByText("Remove failed attachments to continue.")).toBeInTheDocument();
    expect(mockCreateComment).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Remove letter.pdf" }));
    expect(screen.getByRole("button", { name: "Post Comment" })).not.toBeDisabled();
  });

  it("a rejected upload-init puts the chip in error instead of uploading forever", async () => {
    mockInitiateUpload.mockRejectedValue(new Error("network down"));
    const user = userEvent.setup();
    const { input } = renderForm(false);

    await user.upload(input!, pdf("letter.pdf"));

    expect(await screen.findByText("Failed to start the upload.")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(screen.getByText("Remove failed attachments to continue.")).toBeInTheDocument();
    expect(FakeXhr.instances).toHaveLength(0);
  });

  it("releases staged uploads when the form unmounts", async () => {
    const user = userEvent.setup();
    const { input, unmount } = renderForm(false);

    await user.upload(input!, pdf("letter.pdf"));
    await screen.findByText("letter.pdf");

    unmount();

    expect(mockCancelUpload).toHaveBeenCalledWith("doc-1");
  });
});

describe("useCommentAttachments.describeRejection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    FakeXhr.instances = [];
    vi.stubGlobal("XMLHttpRequest", FakeXhr);
    mockInitiateUpload.mockResolvedValue({
      success: true,
      documentId: "doc-staged",
      presignedUrl: "https://s3.example.com/put-staged",
    });
    mockCancelUpload.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  async function stagedHook() {
    const hook = renderHook(() => useCommentAttachments({ slug: "acme", projectId: "p1" }));
    act(() => hook.result.current.addFiles([pdf("staged.pdf")]));
    await waitFor(() => expect(hook.result.current.items[0]?.status).toBe("ready"));
    return hook;
  }

  it("names a staged chip and marks it in error", async () => {
    const { result } = await stagedHook();

    let message = "";
    act(() => {
      message = result.current.describeRejection(
        "Document doc-staged is not allowed",
        "doc-staged"
      );
    });

    expect(message).toBe("Document staged.pdf is not allowed");
    expect(result.current.items[0].status).toBe("error");
    expect(result.current.items[0].error).toBe("Not attached");
  });

  it("names an attachment from the extra list without touching the staged chips", async () => {
    const { result } = await stagedHook();

    let message = "";
    act(() => {
      message = result.current.describeRejection("Document doc-kept is not allowed", "doc-kept", [
        { documentId: "doc-kept", fileName: "kept.pdf" },
      ]);
    });

    expect(message).toBe("Document kept.pdf is not allowed");
    expect(result.current.items[0].status).toBe("ready");
  });

  it("returns the message unchanged when the id is absent or unknown", async () => {
    const { result } = await stagedHook();

    expect(result.current.describeRejection("Server error")).toBe("Server error");
    expect(result.current.describeRejection("Document doc-x is gone", "doc-x")).toBe(
      "Document doc-x is gone"
    );
    expect(result.current.items[0].status).toBe("ready");
  });
});
