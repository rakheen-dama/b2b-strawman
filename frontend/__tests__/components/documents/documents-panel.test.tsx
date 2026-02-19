import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { DocumentsPanel } from "@/components/documents/documents-panel";

vi.mock("server-only", () => ({}));

vi.mock("@/app/(app)/org/[slug]/projects/[id]/actions", () => ({
  initiateUpload: vi.fn().mockResolvedValue({ success: true }),
  confirmUpload: vi.fn().mockResolvedValue({ success: true }),
  cancelUpload: vi.fn().mockResolvedValue({ success: true }),
  getDownloadUrl: vi
    .fn()
    .mockResolvedValue({ success: true, presignedUrl: "https://example.com" }),
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

describe("DocumentsPanel", () => {
  afterEach(() => {
    cleanup();
  });

  it("shows empty state with correct description when no documents", () => {
    render(
      <DocumentsPanel documents={[]} projectId="p1" slug="acme" />,
    );

    expect(screen.getByText("No documents yet")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Upload proposals, contracts, and deliverables for this project.",
      ),
    ).toBeInTheDocument();
  });
});
