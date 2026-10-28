import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, cleanup, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ProjectCommentsSection } from "@/components/projects/project-comments-section";
import type { Comment } from "@/lib/types";

const mockFetchProjectComments = vi.fn();
const mockCreateProjectComment = vi.fn();

vi.mock("@/lib/actions/comments", () => ({
  fetchProjectComments: (...args: unknown[]) => mockFetchProjectComments(...args),
  createProjectComment: (...args: unknown[]) => mockCreateProjectComment(...args),
}));

vi.mock("@/components/ui/relative-date", () => ({
  RelativeDate: ({ iso }: { iso: string }) => <span>{iso}</span>,
}));

function makeComment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: "c1",
    entityType: "PROJECT",
    entityId: "p1",
    matterId: "p1",
    authorMemberId: "m1",
    authorName: "Jane Customer",
    authorAvatarUrl: null,
    body: "Hello from the portal",
    visibility: "SHARED",
    source: "PORTAL",
    parentId: null,
    createdAt: "2026-02-10T10:00:00Z",
    updatedAt: "2026-02-10T10:00:00Z",
    attachments: [],
    ...overrides,
  };
}

describe("ProjectCommentsSection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders empty state when no comments", async () => {
    mockFetchProjectComments.mockResolvedValue([]);
    render(<ProjectCommentsSection projectId="p1" orgSlug="test-org" />);
    expect(await screen.findByText(/no client comments yet/i)).toBeInTheDocument();
  });

  it("renders portal comments with Client badge", async () => {
    mockFetchProjectComments.mockResolvedValue([makeComment()]);
    render(<ProjectCommentsSection projectId="p1" orgSlug="test-org" />);
    expect(await screen.findByText("Jane Customer")).toBeInTheDocument();
    // Phase 88 628A (ADR-357): portal-comment badge reads "Client"
    expect(screen.getByText("Client")).toBeInTheDocument();
    expect(screen.queryByText("Customer")).not.toBeInTheDocument();
    expect(screen.getByText("Hello from the portal")).toBeInTheDocument();
  });

  it("renders internal comments without Client badge", async () => {
    mockFetchProjectComments.mockResolvedValue([
      makeComment({ source: "INTERNAL", authorName: "Alice Staff" }),
    ]);
    render(<ProjectCommentsSection projectId="p1" orgSlug="test-org" />);
    expect(await screen.findByText("Alice Staff")).toBeInTheDocument();
    expect(screen.queryByText("Client")).not.toBeInTheDocument();
  });

  it("renders reply textarea and Post Reply button", async () => {
    mockFetchProjectComments.mockResolvedValue([]);
    render(<ProjectCommentsSection projectId="p1" orgSlug="test-org" />);
    await screen.findByText(/no client comments yet/i);
    expect(screen.getByPlaceholderText(/reply to the client thread/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /post reply/i })).toBeInTheDocument();
  });

  it("disables Post Reply button when textarea is empty", async () => {
    mockFetchProjectComments.mockResolvedValue([]);
    render(<ProjectCommentsSection projectId="p1" orgSlug="test-org" />);
    await screen.findByText(/no client comments yet/i);
    expect(screen.getByRole("button", { name: /post reply/i })).toBeDisabled();
  });

  it("enables Post Reply button when textarea has content", async () => {
    mockFetchProjectComments.mockResolvedValue([]);
    const user = userEvent.setup();
    render(<ProjectCommentsSection projectId="p1" orgSlug="test-org" />);
    await screen.findByText(/no client comments yet/i);

    await user.type(screen.getByPlaceholderText(/reply to the client thread/i), "A reply");
    expect(screen.getByRole("button", { name: /post reply/i })).not.toBeDisabled();
  });

  it("calls createProjectComment on submit and refreshes comments", async () => {
    mockFetchProjectComments
      .mockResolvedValueOnce([]) // initial load
      .mockResolvedValueOnce([makeComment({ body: "New reply", source: "INTERNAL" })]); // after submit
    mockCreateProjectComment.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(<ProjectCommentsSection projectId="p1" orgSlug="test-org" />);
    await screen.findByText(/no client comments yet/i);

    await user.type(screen.getByPlaceholderText(/reply to the client thread/i), "New reply");
    await user.click(screen.getByRole("button", { name: /post reply/i }));

    await waitFor(() => {
      expect(mockCreateProjectComment).toHaveBeenCalledWith("test-org", "p1", "New reply");
    });
  });

  it("shows loading state initially", () => {
    mockFetchProjectComments.mockReturnValue(new Promise(() => {})); // never resolves
    render(<ProjectCommentsSection projectId="p1" orgSlug="test-org" />);
    expect(screen.getByText("Loading comments...")).toBeInTheDocument();
  });

  it("renders avatar initials for comment author", async () => {
    mockFetchProjectComments.mockResolvedValue([makeComment()]);
    render(<ProjectCommentsSection projectId="p1" orgSlug="test-org" />);
    // AvatarCircle generates initials: "Jane Customer" -> "JC"
    expect(await screen.findByText("JC")).toBeInTheDocument();
  });
});
