import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { DocumentsPanel } from "@/components/documents/documents-panel";
import type { Document } from "@/lib/types";

vi.mock("server-only", () => ({}));

vi.mock("@/app/(app)/org/[slug]/matters/[id]/actions", () => ({
  initiateUpload: vi.fn().mockResolvedValue({ success: true }),
  confirmUpload: vi.fn().mockResolvedValue({ success: true }),
  cancelUpload: vi.fn().mockResolvedValue({ success: true }),
  getDownloadUrl: vi.fn().mockResolvedValue({ success: true, presignedUrl: "https://example.com" }),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

function makeDocument(overrides: Partial<Document> = {}): Document {
  return {
    id: "d1",
    matterId: "p1",
    fileName: "masters-appointment.pdf",
    contentType: "application/pdf",
    size: 2048,
    status: "UPLOADED",
    scope: "PROJECT",
    clientId: null,
    visibility: "INTERNAL",
    source: "MANUAL",
    uploadedBy: "m1",
    uploadedByName: "Alice Owner",
    uploadedAt: "2026-10-28T10:00:00Z",
    createdAt: "2026-10-28T10:00:00Z",
    ...overrides,
  };
}

describe("DocumentsPanel", () => {
  afterEach(() => {
    cleanup();
  });

  it("shows empty state with correct description when no documents", () => {
    render(<DocumentsPanel documents={[]} projectId="p1" slug="acme" />);

    expect(screen.getByText("No documents yet")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Documents live inside matters. Create engagement letters, contracts, reports, or any other document your team needs."
      )
    ).toBeInTheDocument();
  });

  it("labels documents that came from a comment with a 'Comment' badge", () => {
    render(
      <DocumentsPanel
        documents={[
          makeDocument({ id: "d1", fileName: "masters-appointment.pdf", source: "COMMENT" }),
          makeDocument({ id: "d2", fileName: "engagement-letter.pdf", source: "MANUAL" }),
        ]}
        projectId="p1"
        slug="acme"
      />
    );

    const rows = screen.getAllByRole("row");
    const commentRow = rows.find((r) => r.textContent?.includes("masters-appointment.pdf"));
    const manualRow = rows.find((r) => r.textContent?.includes("engagement-letter.pdf"));
    expect(commentRow).toBeDefined();
    expect(manualRow).toBeDefined();
    expect(within(commentRow!).getByText("Comment")).toBeInTheDocument();
    expect(within(manualRow!).queryByText("Comment")).not.toBeInTheDocument();
  });
});
