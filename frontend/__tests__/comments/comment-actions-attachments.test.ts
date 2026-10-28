import { describe, it, expect, vi, beforeEach } from "vitest";

// 627I: the comment server actions forward attachmentDocumentIds and map the backend's
// 422 "Attachment not allowed" ProblemDetail to { error, attachmentDocumentId }.

vi.mock("server-only", () => ({}));

const { mockPost, mockPut, apiMock, MockApiError } = vi.hoisted(() => {
  const mockPost = vi.fn();
  const mockPut = vi.fn();
  class MockApiError extends Error {
    constructor(
      public status: number,
      message: string,
      public detail?: Record<string, unknown>
    ) {
      super(message);
      this.name = "ApiError";
    }
  }
  const apiMock = {
    get: vi.fn(),
    post: (...args: unknown[]) => mockPost(...args),
    put: (...args: unknown[]) => mockPut(...args),
    patch: vi.fn(),
    delete: vi.fn(),
  };
  return { mockPost, mockPut, apiMock, MockApiError };
});

vi.mock("@/lib/api", () => ({ api: apiMock, ApiError: MockApiError }));
vi.mock("@/lib/api/client", () => ({ api: apiMock, ApiError: MockApiError }));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

import { createComment, updateComment } from "@/lib/actions/comments";

function attachmentError(detail: string, documentId?: string) {
  return new MockApiError(422, detail, {
    title: "Attachment not allowed",
    status: 422,
    detail,
    ...(documentId ? { documentId } : {}),
  });
}

describe("comment actions: attachments", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPost.mockResolvedValue({});
    mockPut.mockResolvedValue({});
  });

  it("createComment posts attachmentDocumentIds when non-empty and omits an empty list", async () => {
    await createComment("acme", "m1", "DOCUMENT", "d1", "hi", "INTERNAL", ["doc-1", "doc-2"]);
    expect(mockPost).toHaveBeenLastCalledWith("/api/matters/m1/comments", {
      entityType: "DOCUMENT",
      entityId: "d1",
      body: "hi",
      visibility: "INTERNAL",
      attachmentDocumentIds: ["doc-1", "doc-2"],
    });

    await createComment("acme", "m1", "TASK", "t1", "x", undefined, []);
    expect(mockPost).toHaveBeenLastCalledWith("/api/matters/m1/comments", {
      entityType: "TASK",
      entityId: "t1",
      body: "x",
    });
  });

  it("updateComment sends a list (including []) and omits the key when undefined", async () => {
    await updateComment("acme", "m1", "c1", "y", undefined, []);
    expect(mockPut).toHaveBeenLastCalledWith("/api/matters/m1/comments/c1", {
      body: "y",
      attachmentDocumentIds: [],
    });

    await updateComment("acme", "m1", "c1", "y", "SHARED", ["doc-1"]);
    expect(mockPut).toHaveBeenLastCalledWith("/api/matters/m1/comments/c1", {
      body: "y",
      visibility: "SHARED",
      attachmentDocumentIds: ["doc-1"],
    });

    await updateComment("acme", "m1", "c1", "y", "SHARED", undefined);
    expect(mockPut).toHaveBeenLastCalledWith("/api/matters/m1/comments/c1", {
      body: "y",
      visibility: "SHARED",
    });
  });

  it("maps a 422 Attachment not allowed to the detail plus the offending document id", async () => {
    mockPost.mockRejectedValueOnce(
      attachmentError("Document doc-1 is already attached to another comment", "doc-1")
    );
    await expect(
      createComment("acme", "m1", "TASK", "t1", "hi", undefined, ["doc-1"])
    ).resolves.toEqual({
      success: false,
      error: "Document doc-1 is already attached to another comment",
      attachmentDocumentId: "doc-1",
    });

    mockPut.mockRejectedValueOnce(
      attachmentError("A comment can have at most 5 attachments (got 6)")
    );
    await expect(
      updateComment("acme", "m1", "c1", "hi", undefined, ["a", "b", "c", "d", "e", "f"])
    ).resolves.toEqual({
      success: false,
      error: "A comment can have at most 5 attachments (got 6)",
    });
  });

  it("keeps the existing 403 mapping on update", async () => {
    mockPut.mockRejectedValueOnce(new MockApiError(403, "Cannot update comment"));
    await expect(updateComment("acme", "m1", "c1", "hi")).resolves.toEqual({
      success: false,
      error: "You do not have permission to edit this comment.",
    });
  });
});
