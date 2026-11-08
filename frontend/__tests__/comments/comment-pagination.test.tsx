import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { SWRConfig } from "swr";
import { CommentSectionClient } from "@/components/comments/comment-section-client";
import type { Comment } from "@/lib/types";

// 634E(1): the comment list endpoint pages (default 50, oldest first). Loading one unpaged call
// dropped every comment past the first page — the newest ones, including one just posted — and a
// notification deep link to such a comment highlighted nothing. The real fetchComments action runs
// here against a mocked API layer that serves 150 comments in pages of 100.

vi.mock("server-only", () => ({}));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

const { mockListMatterComments } = vi.hoisted(() => ({ mockListMatterComments: vi.fn() }));

vi.mock("@/lib/api", () => ({
  api: { get: vi.fn().mockResolvedValue([]) },
  ApiError: class ApiError extends Error {},
}));

vi.mock("@/lib/api/matters", () => ({
  listMatterComments: (...args: unknown[]) => mockListMatterComments(...args),
  listMatterMembers: vi.fn().mockResolvedValue([]),
  createMatterComment: vi.fn(),
  updateMatterComment: vi.fn(),
  deleteMatterComment: vi.fn(),
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

const TOTAL = 150;

function makeComment(n: number): Comment {
  return {
    id: `c-${n}`,
    entityType: "PROJECT",
    entityId: "m1",
    matterId: "m1",
    authorMemberId: "m-alice",
    authorName: "Alice Owner",
    authorAvatarUrl: null,
    body: `Paged comment number ${n}`,
    visibility: "SHARED",
    source: "INTERNAL",
    parentId: null,
    createdAt: "2026-11-05T10:00:00Z",
    updatedAt: "2026-11-05T10:00:00Z",
    attachments: [],
  };
}

const ALL = Array.from({ length: TOTAL }, (_, i) => makeComment(i + 1));

/** Serves the comments like the backend: `page`/`size` slices, default size 50. */
function servePages(_matterId: string, params: { page?: number; size?: number }) {
  const size = params.size ?? 50;
  const page = params.page ?? 0;
  return Promise.resolve(ALL.slice(page * size, page * size + size));
}

function renderSection() {
  return render(
    <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>
      <CommentSectionClient
        projectId="m1"
        entityType="PROJECT"
        entityId="m1"
        orgSlug="acme"
        currentMemberId="m-other"
        canManageVisibility
      />
    </SWRConfig>
  );
}

describe("Comment thread pagination (634E(1))", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearchParams = new URLSearchParams("tab=comments");
    mockListMatterComments.mockImplementation(servePages);
  });

  afterEach(() => {
    cleanup();
  });

  it("renders all 150 comments across two pages", async () => {
    renderSection();

    expect(await screen.findByText("Paged comment number 150")).toBeInTheDocument();
    expect(screen.getByText("Paged comment number 1")).toBeInTheDocument();
    expect(screen.getAllByText(/^Paged comment number \d+$/)).toHaveLength(TOTAL);
    expect(mockListMatterComments).toHaveBeenCalledTimes(2);
  });

  it("highlights a deep-linked comment beyond the first page (#120)", async () => {
    mockSearchParams = new URLSearchParams("tab=comments&commentId=c-120");
    const { container } = renderSection();

    await screen.findByText("Paged comment number 120");
    expect(container.querySelector("#comment-c-120")).toHaveAttribute("data-highlighted", "true");
  });

  it("stops after a short page and de-duplicates a comment repeated across pages", async () => {
    mockListMatterComments.mockImplementation(
      (_matterId: string, params: { page?: number; size?: number }) => {
        const size = params.size ?? 50;
        // Page 1 starts one early, as if a comment was posted between the two reads.
        const start = params.page === 1 ? size - 1 : 0;
        return Promise.resolve(ALL.slice(start, start + size));
      }
    );
    renderSection();

    expect(await screen.findByText("Paged comment number 150")).toBeInTheDocument();
    expect(screen.getAllByText("Paged comment number 100")).toHaveLength(1);
    expect(screen.getAllByText(/^Paged comment number \d+$/)).toHaveLength(TOTAL);
  });
});
