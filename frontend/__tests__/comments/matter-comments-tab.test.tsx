import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRConfig } from "swr";
import { CommentSectionClient } from "@/components/comments/comment-section-client";
import type { Comment } from "@/lib/types";

// Phase 89 634E (§14.12 #6 / B7, rulings §9.5): the matter Comments tab mounts CommentSectionClient
// with a PROJECT target. Matter comments are always SHARED (CommentService rejects INTERNAL for a
// PROJECT target), so the form offers no visibility choice; a ?commentId= deep link highlights the
// comment (634C behaviour).

const mockFetchComments = vi.fn();
const mockCreateComment = vi.fn();

vi.mock("@/lib/actions/comments", () => ({
  fetchComments: (...args: unknown[]) => mockFetchComments(...args),
  createComment: (...args: unknown[]) => mockCreateComment(...args),
  updateComment: vi.fn(),
  deleteComment: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/matters/[id]/actions", () => ({
  initiateUpload: vi.fn(),
  confirmUpload: vi.fn(),
  cancelUpload: vi.fn(),
  getDownloadUrl: vi.fn(),
}));

let mockSearchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useSearchParams: () => mockSearchParams,
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
}));

function makeComment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: "c1",
    entityType: "PROJECT",
    entityId: "m1",
    matterId: "m1",
    authorMemberId: "m-alice",
    authorName: "Alice Owner",
    authorAvatarUrl: null,
    body: "Matter thread comment.",
    visibility: "SHARED",
    source: "INTERNAL",
    parentId: null,
    createdAt: "2026-11-05T10:00:00Z",
    updatedAt: "2026-11-05T10:00:00Z",
    attachments: [],
    ...overrides,
  };
}

function renderPanel(canManageVisibility = true) {
  // Fresh SWR cache per render: every test uses the same key (comments-PROJECT-m1).
  return render(
    <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>
      <CommentSectionClient
        projectId="m1"
        entityType="PROJECT"
        entityId="m1"
        orgSlug="acme"
        currentMemberId="m-other"
        canManageVisibility={canManageVisibility}
      />
    </SWRConfig>
  );
}

describe("Matter Comments tab (CommentSectionClient, PROJECT target)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearchParams = new URLSearchParams("tab=comments");
    mockFetchComments.mockResolvedValue([]);
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("fetches the matter's PROJECT comments", async () => {
    renderPanel();
    await screen.findByLabelText("Add a comment");
    expect(mockFetchComments).toHaveBeenCalledWith("m1", "PROJECT", "m1");
  });

  it("lists the matter's existing comments", async () => {
    mockFetchComments.mockResolvedValue([
      makeComment({ id: "c-a", body: "First matter remark" }),
      makeComment({ id: "c-b", body: "Second matter remark" }),
    ]);
    renderPanel();
    expect(await screen.findByText("First matter remark")).toBeInTheDocument();
    expect(screen.getByText("Second matter remark")).toBeInTheDocument();
  });

  it.each([true, false])(
    "offers no visibility choice and shows the Shared-with-client badge (canManageVisibility=%s)",
    async (canManage) => {
      renderPanel(canManage);
      await screen.findByLabelText("Add a comment");
      expect(screen.queryByLabelText("Visibility")).not.toBeInTheDocument();
      expect(screen.getByText("Shared with client")).toBeInTheDocument();
    }
  );

  it.each([true, false])(
    "posts with SHARED visibility regardless of canManageVisibility (%s)",
    async (canManage) => {
      mockCreateComment.mockResolvedValue({ success: true });
      const user = userEvent.setup();
      renderPanel(canManage);

      await user.type(await screen.findByLabelText("Add a comment"), "Hello client @Bob");
      await user.click(screen.getByRole("button", { name: "Post Comment" }));

      await waitFor(() => {
        expect(mockCreateComment).toHaveBeenCalledWith(
          "acme",
          "m1",
          "PROJECT",
          "m1",
          "Hello client @Bob",
          "SHARED",
          []
        );
      });
    }
  );

  it("highlights the comment named by ?commentId=, then clears the ring after ~2 s", async () => {
    const scrollSpy = vi.spyOn(Element.prototype, "scrollIntoView");
    mockSearchParams = new URLSearchParams("tab=comments&commentId=c-target");
    mockFetchComments.mockResolvedValue([
      makeComment({ id: "c-other", body: "Earlier remark" }),
      makeComment({ id: "c-target", body: "Please sign the offer" }),
    ]);

    const { container } = renderPanel();

    expect(await screen.findByText("Please sign the offer")).toBeInTheDocument();
    const target = container.querySelector("#comment-c-target");
    expect(target).toHaveClass("ring-2");
    expect(target).toHaveAttribute("data-highlighted", "true");
    expect(container.querySelector("#comment-c-other")).not.toHaveClass("ring-2");
    expect(scrollSpy).toHaveBeenCalledWith({ block: "center" });

    // The ring is a 2 s timer started on mount (real timers: SWR resolves the list first).
    await waitFor(() => expect(target).not.toHaveClass("ring-2"), { timeout: 3000 });
    expect(target).not.toHaveAttribute("data-highlighted");
  });

  // D3: parity with the retired Client comments section (628A / ADR-357) — a portal-authored
  // comment is marked as the client's.
  it("marks portal-authored comments with a Client badge", async () => {
    mockFetchComments.mockResolvedValue([
      makeComment({
        id: "c-portal",
        source: "PORTAL",
        authorName: null,
        authorMemberId: "contact-1",
        body: "From the client",
      }),
      makeComment({ id: "c-firm", source: "INTERNAL", body: "From the firm" }),
    ]);
    renderPanel();
    expect(await screen.findByText("From the client")).toBeInTheDocument();
    expect(screen.getAllByText("Client")).toHaveLength(1);
    expect(
      screen.getByText("From the client").closest('[id="comment-c-portal"]')
    ).toHaveTextContent("Client");
  });
});
