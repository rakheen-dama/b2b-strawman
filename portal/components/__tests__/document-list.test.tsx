import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock api-client
const mockPortalGet = vi.fn();
vi.mock("@/lib/api-client", () => ({
  portalGet: (...args: unknown[]) => mockPortalGet(...args),
}));

// Mock window.open
const mockWindowOpen = vi.fn();
Object.defineProperty(window, "open", {
  value: mockWindowOpen,
  writable: true,
});

import { DocumentList } from "@/components/document-list";

describe("DocumentList", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders documents with file name, size, and date", () => {
    render(
      <DocumentList
        documents={[
          {
            id: "doc-1",
            fileName: "report.pdf",
            contentType: "application/pdf",
            size: 1048576,
            scope: "PROJECT",
            status: "ACTIVE",
            createdAt: "2026-02-01T10:00:00Z",
          },
          {
            id: "doc-2",
            fileName: "photo.jpg",
            contentType: "image/jpeg",
            size: 524288,
            scope: "PROJECT",
            status: "ACTIVE",
            createdAt: "2026-01-15T08:00:00Z",
          },
        ]}
      />,
    );

    // Both mobile and desktop layouts render; use getAllByText
    expect(screen.getAllByText("report.pdf").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("1.0 MB").length).toBeGreaterThanOrEqual(1);

    expect(screen.getAllByText("photo.jpg").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("512.0 KB").length).toBeGreaterThanOrEqual(1);
  });

  it("triggers download via presigned URL", async () => {
    mockPortalGet.mockResolvedValue({
      presignedUrl: "https://s3.example.com/file?signed=abc",
      expiresInSeconds: 3600,
    });

    render(
      <DocumentList
        documents={[
          {
            id: "doc-1",
            fileName: "report.pdf",
            contentType: "application/pdf",
            size: 1048576,
            scope: "PROJECT",
            status: "ACTIVE",
            createdAt: "2026-02-01T10:00:00Z",
          },
        ]}
      />,
    );

    const user = userEvent.setup();
    // Both mobile and desktop layouts render; use first match
    const downloadBtn = screen.getAllByLabelText("Download report.pdf")[0];
    await user.click(downloadBtn);

    expect(mockPortalGet).toHaveBeenCalledWith(
      "/portal/documents/doc-1/presign-download",
    );
    expect(mockWindowOpen).toHaveBeenCalledWith(
      "https://s3.example.com/file?signed=abc",
      "_blank",
    );
  });

  it("shows empty state when no documents", () => {
    render(<DocumentList documents={[]} />);

    expect(
      screen.getByText("No documents shared yet."),
    ).toBeInTheDocument();
  });
});
