import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { GenerateDocumentDialog } from "@/components/templates/GenerateDocumentDialog";

const mockPreviewTemplate = vi.fn();
const mockGenerateDocument = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/templates/actions", () => ({
  previewTemplateAction: (...args: unknown[]) => mockPreviewTemplate(...args),
  generateDocumentAction: (...args: unknown[]) => mockGenerateDocument(...args),
}));

describe("GenerateDocumentDialog", () => {
  const baseProps = {
    templateId: "tpl-1",
    templateName: "Engagement Letter",
    entityId: "proj-1",
    entityType: "PROJECT",
    open: true,
    onOpenChange: vi.fn(),
    onSaved: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockPreviewTemplate.mockResolvedValue({
      success: true,
      html: "<h1>Preview Content</h1>",
    });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders dialog with template name in header", async () => {
    render(<GenerateDocumentDialog {...baseProps} />);

    expect(
      screen.getByText("Generate: Engagement Letter"),
    ).toBeInTheDocument();
  });

  it("loads preview on mount and displays iframe", async () => {
    render(<GenerateDocumentDialog {...baseProps} />);

    await waitFor(() => {
      expect(mockPreviewTemplate).toHaveBeenCalledWith("tpl-1", "proj-1");
    });

    await waitFor(() => {
      const iframe = screen.getByTitle("Document Preview");
      expect(iframe).toBeInTheDocument();
      expect(iframe).toHaveAttribute(
        "srcdoc",
        "<h1>Preview Content</h1>",
      );
    });
  });

  it("download button triggers generateDocument with saveToDocuments=false", async () => {
    mockGenerateDocument.mockResolvedValue({
      success: true,
      pdfBase64: btoa("fake-pdf-content"),
    });

    const user = userEvent.setup();
    render(<GenerateDocumentDialog {...baseProps} />);

    // Wait for preview to load
    await waitFor(() => {
      expect(screen.getByTitle("Document Preview")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /Download PDF/i }));

    await waitFor(() => {
      expect(mockGenerateDocument).toHaveBeenCalledWith(
        "tpl-1",
        "proj-1",
        false,
      );
    });
  });

  it("save button triggers generateDocument with saveToDocuments=true and shows success", async () => {
    mockGenerateDocument.mockResolvedValue({
      success: true,
      data: {
        id: "gen-1",
        fileName: "engagement-letter.pdf",
        fileSize: 1024,
        documentId: "doc-1",
        generatedAt: "2026-01-15T00:00:00Z",
      },
    });

    const user = userEvent.setup();
    render(<GenerateDocumentDialog {...baseProps} />);

    // Wait for preview to load
    await waitFor(() => {
      expect(screen.getByTitle("Document Preview")).toBeInTheDocument();
    });

    await user.click(
      screen.getByRole("button", { name: /Save to Documents/i }),
    );

    await waitFor(() => {
      expect(mockGenerateDocument).toHaveBeenCalledWith(
        "tpl-1",
        "proj-1",
        true,
      );
    });

    await waitFor(() => {
      expect(
        screen.getByText("Document saved successfully"),
      ).toBeInTheDocument();
    });

    expect(baseProps.onSaved).toHaveBeenCalled();
  });

  it("displays error message when preview fails", async () => {
    mockPreviewTemplate.mockResolvedValue({
      success: false,
      error: "Template not found",
    });

    render(<GenerateDocumentDialog {...baseProps} />);

    await waitFor(() => {
      expect(screen.getByText("Template not found")).toBeInTheDocument();
    });
  });
});
