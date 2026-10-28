import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/matters/proj-1",
}));

// Mock api-client
const mockPortalPost = vi.fn();
const mockPortalGet = vi.fn();
vi.mock("@/lib/api-client", () => ({
  portalPost: (...args: unknown[]) => mockPortalPost(...args),
  portalGet: (...args: unknown[]) => mockPortalGet(...args),
}));

// Mock window.open
const mockWindowOpen = vi.fn();
Object.defineProperty(window, "open", {
  value: mockWindowOpen,
  writable: true,
});

import { CommentSection } from "@/components/comment-section";
import type { PortalComment } from "@/lib/types";

const withChips: PortalComment = {
  id: "c-att",
  authorName: "Thandi Mathebula",
  content: "Master's appointment received",
  createdAt: new Date().toISOString(),
  attachments: [
    {
      documentId: "doc-1",
      fileName: "masters-letter.pdf",
      contentType: "application/pdf",
      size: 1048576,
    },
    {
      documentId: "doc-2",
      fileName: "id-copy.png",
      contentType: "image/png",
      size: 83,
    },
  ],
};

const noChips: PortalComment = {
  id: "c-plain",
  authorName: "Thandi Mathebula",
  content: "No files on this one",
  createdAt: new Date().toISOString(),
  attachments: [],
};

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
      "/portal/matters/proj-1/comments",
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

describe("CommentSection attachment chips", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders one chip per attachment with file name and size", () => {
    render(
      <CommentSection
        projectId="proj-1"
        comments={[withChips, noChips]}
        onCommentPosted={vi.fn()}
      />,
    );

    const lists = screen.getAllByRole("list", { name: "Attachments" });
    // Only the comment that has attachments gets a list
    expect(lists).toHaveLength(1);
    const items = within(lists[0]).getAllByRole("listitem");
    expect(items).toHaveLength(2);
    // API order is preserved
    expect(items[0]).toHaveTextContent("masters-letter.pdf");
    expect(items[1]).toHaveTextContent("id-copy.png");

    const pdf = within(lists[0]).getByRole("button", {
      name: "Download masters-letter.pdf",
    });
    expect(pdf).toHaveTextContent("masters-letter.pdf");
    expect(pdf).toHaveTextContent("1.0 MB");
    expect(
      within(lists[0]).getByRole("button", { name: "Download id-copy.png" }),
    ).toHaveTextContent("83 B");
  });

  it("renders no attachment list when attachments is empty or absent", () => {
    const legacy: PortalComment = {
      id: "c-old",
      authorName: "Older backend",
      content: "pre-627H comment",
      createdAt: new Date().toISOString(),
    };

    render(
      <CommentSection
        projectId="proj-1"
        comments={[noChips, legacy]}
        onCommentPosted={vi.fn()}
      />,
    );

    expect(screen.getByText("No files on this one")).toBeInTheDocument();
    expect(screen.getByText("pre-627H comment")).toBeInTheDocument();
    expect(
      screen.queryByRole("list", { name: "Attachments" }),
    ).not.toBeInTheDocument();
  });

  it("downloads through the portal presign endpoint", async () => {
    mockPortalGet.mockResolvedValue({
      presignedUrl: "https://s3.example.com/masters?sig=1",
      expiresInSeconds: 3600,
    });

    render(
      <CommentSection
        projectId="proj-1"
        comments={[withChips]}
        onCommentPosted={vi.fn()}
      />,
    );

    const list = screen.getByRole("list", { name: "Attachments" });
    await userEvent.setup().click(
      within(list).getByRole("button", {
        name: "Download masters-letter.pdf",
      }),
    );

    expect(mockPortalGet).toHaveBeenCalledWith(
      "/portal/documents/doc-1/presign-download",
    );
    await waitFor(() => {
      expect(mockWindowOpen).toHaveBeenCalledWith(
        "https://s3.example.com/masters?sig=1",
        "_blank",
        "noopener,noreferrer",
      );
    });
  });

  it("disables the chip while the presign request is in flight", async () => {
    let resolvePresign: (value: unknown) => void = () => {};
    mockPortalGet.mockReturnValue(
      new Promise((resolve) => {
        resolvePresign = resolve;
      }),
    );

    render(
      <CommentSection
        projectId="proj-1"
        comments={[withChips]}
        onCommentPosted={vi.fn()}
      />,
    );

    const list = screen.getByRole("list", { name: "Attachments" });
    const chip = within(list).getByRole("button", {
      name: "Download masters-letter.pdf",
    });
    const user = userEvent.setup();
    await user.click(chip);

    await waitFor(() => {
      expect(chip).toBeDisabled();
    });
    expect(chip).toHaveAttribute("aria-busy", "true");

    // A second click while in flight must not start another presign request.
    await user.click(chip);
    expect(mockPortalGet).toHaveBeenCalledTimes(1);

    resolvePresign({
      presignedUrl: "https://s3.example.com/masters?sig=2",
      expiresInSeconds: 3600,
    });

    await waitFor(() => {
      expect(chip).toBeEnabled();
    });
    expect(mockWindowOpen).toHaveBeenCalledWith(
      "https://s3.example.com/masters?sig=2",
      "_blank",
      "noopener,noreferrer",
    );
  });

  it("shows an inline error, opens nothing, and clears the error on the next click", async () => {
    mockPortalGet.mockRejectedValueOnce(
      new Error("The requested resource was not found."),
    );

    render(
      <CommentSection
        projectId="proj-1"
        comments={[withChips]}
        onCommentPosted={vi.fn()}
      />,
    );

    const list = screen.getByRole("list", { name: "Attachments" });
    const chip = within(list).getByRole("button", {
      name: "Download masters-letter.pdf",
    });
    const user = userEvent.setup();
    await user.click(chip);

    const alert = await within(list).findByRole("alert");
    expect(alert).toHaveTextContent("Download failed. Please try again.");
    expect(alert).not.toHaveTextContent(
      "The requested resource was not found.",
    );
    expect(mockWindowOpen).not.toHaveBeenCalled();
    expect(chip).toBeEnabled();

    // The error clears when the user next clicks that chip
    mockPortalGet.mockResolvedValueOnce({
      presignedUrl: "https://s3.example.com/masters?sig=3",
      expiresInSeconds: 3600,
    });
    await user.click(chip);

    await waitFor(() => {
      expect(mockWindowOpen).toHaveBeenCalledWith(
        "https://s3.example.com/masters?sig=3",
        "_blank",
        "noopener,noreferrer",
      );
    });
    expect(within(list).queryByRole("alert")).not.toBeInTheDocument();
  });
});
