import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { act, cleanup, render, screen } from "@testing-library/react";
import { CommentItem } from "@/components/comments/comment-item";
import { CommentSectionClient } from "@/components/comments/comment-section-client";
import type { Comment } from "@/lib/types";

// G88-M6 (Phase 89 634C): a comment notification deep link (`?commentId=<id>`) lands on the
// comment itself — an `id="comment-<id>"` anchor, scrolled into view, ring-highlighted for ~2 s.

const mockFetchComments = vi.fn();

vi.mock("@/lib/actions/comments", () => ({
  fetchComments: (...args: unknown[]) => mockFetchComments(...args),
  createComment: vi.fn(),
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
    entityType: "TASK",
    entityId: "t1",
    matterId: "p1",
    authorMemberId: "m1",
    authorName: "Thandi Mokoena",
    authorAvatarUrl: null,
    body: "Deep-linked comment body.",
    visibility: "INTERNAL",
    source: "INTERNAL",
    parentId: null,
    createdAt: "2026-02-10T10:00:00Z",
    updatedAt: "2026-02-10T10:00:00Z",
    attachments: [],
    ...overrides,
  };
}

function renderItem(highlighted?: boolean) {
  return render(
    <CommentItem
      comment={makeComment()}
      currentMemberId="other-member"
      canManageVisibility={false}
      orgSlug="acme"
      projectId="p1"
      highlighted={highlighted}
    />
  );
}

describe("CommentItem deep-link anchor + highlight (G88-M6)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearchParams = new URLSearchParams();
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("renders an id anchor for every comment", () => {
    const { container } = renderItem();

    expect(container.querySelector("#comment-c1")).not.toBeNull();
  });

  it("highlighted: scrolls the comment into view and rings it, then removes the ring after ~2 s", () => {
    vi.useFakeTimers();
    const scrollSpy = vi.spyOn(Element.prototype, "scrollIntoView");

    const { container } = renderItem(true);
    const anchor = container.querySelector("#comment-c1");

    expect(anchor).not.toBeNull();
    expect(scrollSpy).toHaveBeenCalledTimes(1);
    expect(scrollSpy).toHaveBeenCalledWith({ block: "center" });
    expect(anchor).toHaveClass("ring-2");

    act(() => {
      vi.advanceTimersByTime(2000);
    });

    expect(anchor).not.toHaveClass("ring-2");
  });

  it("not highlighted: no scroll and no ring", () => {
    const scrollSpy = vi.spyOn(Element.prototype, "scrollIntoView");

    const { container } = renderItem(false);

    expect(scrollSpy).not.toHaveBeenCalled();
    expect(container.querySelector("#comment-c1")).not.toHaveClass("ring-2");
  });
});

describe("CommentSectionClient ?commentId= targeting (G88-M6)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("highlights only the comment named by ?commentId=", async () => {
    mockSearchParams = new URLSearchParams("tab=tasks&taskId=t-target&commentId=c-target");
    mockFetchComments.mockResolvedValue([
      makeComment({ id: "c-other", entityId: "t-target", body: "Earlier remark" }),
      makeComment({ id: "c-target", entityId: "t-target", body: "Please review clause 4" }),
    ]);

    const { container } = render(
      <CommentSectionClient
        projectId="p1"
        entityType="TASK"
        entityId="t-target"
        orgSlug="acme"
        currentMemberId="other-member"
        canManageVisibility={false}
      />
    );

    expect(await screen.findByText("Please review clause 4")).toBeInTheDocument();
    expect(container.querySelector("#comment-c-target")).toHaveClass("ring-2");
    expect(container.querySelector("#comment-c-other")).not.toBeNull();
    expect(container.querySelector("#comment-c-other")).not.toHaveClass("ring-2");
  });
});
