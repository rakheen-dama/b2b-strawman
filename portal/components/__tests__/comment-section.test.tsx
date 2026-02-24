import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/projects/proj-1",
}));

// Mock api-client
const mockPortalPost = vi.fn();
vi.mock("@/lib/api-client", () => ({
  portalPost: (...args: unknown[]) => mockPortalPost(...args),
}));

import { CommentSection } from "@/components/comment-section";

describe("CommentSection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders existing comments", () => {
    render(
      <CommentSection
        projectId="proj-1"
        comments={[
          {
            id: "c-1",
            authorName: "Alice",
            content: "Looking great!",
            createdAt: new Date().toISOString(),
          },
          {
            id: "c-2",
            authorName: "Bob",
            content: "I agree.",
            createdAt: new Date().toISOString(),
          },
        ]}
        onCommentPosted={vi.fn()}
      />,
    );

    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("Looking great!")).toBeInTheDocument();
    expect(screen.getByText("Bob")).toBeInTheDocument();
    expect(screen.getByText("I agree.")).toBeInTheDocument();
  });

  it("shows empty state when no comments", () => {
    render(
      <CommentSection
        projectId="proj-1"
        comments={[]}
        onCommentPosted={vi.fn()}
      />,
    );

    expect(screen.getByText("No comments yet.")).toBeInTheDocument();
  });

  it("posts a new comment with optimistic UI", async () => {
    const onCommentPosted = vi.fn();
    mockPortalPost.mockResolvedValue({
      id: "c-new",
      authorName: "You",
      content: "My comment",
      createdAt: new Date().toISOString(),
    });

    render(
      <CommentSection
        projectId="proj-1"
        comments={[]}
        onCommentPosted={onCommentPosted}
      />,
    );

    const user = userEvent.setup();
    const textarea = screen.getByPlaceholderText("Write a comment...");
    await user.type(textarea, "My comment");

    const submitBtn = screen.getByRole("button", { name: "Post Comment" });
    await user.click(submitBtn);

    // Optimistic: comment appears immediately
    await waitFor(() => {
      expect(screen.getByText("My comment")).toBeInTheDocument();
    });

    expect(mockPortalPost).toHaveBeenCalledWith(
      "/portal/projects/proj-1/comments",
      { content: "My comment" },
    );
    expect(onCommentPosted).toHaveBeenCalled();
  });

  it("shows character count when close to limit", async () => {
    render(
      <CommentSection
        projectId="proj-1"
        comments={[]}
        onCommentPosted={vi.fn()}
      />,
    );

    const user = userEvent.setup();
    const textarea = screen.getByPlaceholderText("Write a comment...");

    // Type text that exceeds 1800 chars to trigger counter
    const longText = "a".repeat(1850);
    await user.type(textarea, longText);

    await waitFor(() => {
      expect(screen.getByText("1850/2000")).toBeInTheDocument();
    });
  });
});
