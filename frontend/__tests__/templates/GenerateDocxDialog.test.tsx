import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { GenerateDocxDialog } from "@/components/templates/GenerateDocxDialog";

// Mock server-only (imported transitively via api)
vi.mock("server-only", () => ({}));

const mockGenerateDocxAction = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/templates/template-generation-actions", () => ({
  generateDocxAction: (...args: unknown[]) => mockGenerateDocxAction(...args),
}));

describe("GenerateDocxDialog", () => {
  const baseProps = {
    templateId: "tpl-docx-1",
    templateName: "NDA Template",
    entityId: "proj-1",
    open: true,
    onOpenChange: vi.fn(),
    onGenerated: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders output format selector with DOCX as default", () => {
    render(<GenerateDocxDialog {...baseProps} />);

    expect(screen.getByText("Generate: NDA Template")).toBeInTheDocument();
    expect(screen.getByText("Output Format")).toBeInTheDocument();

    const docxRadio = screen.getByDisplayValue("DOCX") as HTMLInputElement;
    const pdfRadio = screen.getByDisplayValue("PDF") as HTMLInputElement;
    const bothRadio = screen.getByDisplayValue("BOTH") as HTMLInputElement;

    expect(docxRadio.checked).toBe(true);
    expect(pdfRadio.checked).toBe(false);
    expect(bothRadio.checked).toBe(false);
  });

  it("submits with DOCX format and calls action", async () => {
    mockGenerateDocxAction.mockResolvedValue({
      success: true,
      data: {
        id: "gen-1",
        templateId: "tpl-docx-1",
        templateName: "NDA Template",
        outputFormat: "DOCX",
        fileName: "nda-template.docx",
        downloadUrl: "https://s3.example.com/nda-template.docx",
        pdfDownloadUrl: null,
        fileSize: 12345,
        generatedAt: "2026-03-09T10:00:00Z",
        warnings: [],
      },
    });

    const user = userEvent.setup();
    render(<GenerateDocxDialog {...baseProps} />);

    await user.click(screen.getByRole("button", { name: /Generate/i }));

    await waitFor(() => {
      expect(mockGenerateDocxAction).toHaveBeenCalledWith(
        "tpl-docx-1",
        "proj-1",
        "DOCX",
      );
    });
  });

  it("shows download links on success", async () => {
    mockGenerateDocxAction.mockResolvedValue({
      success: true,
      data: {
        id: "gen-1",
        templateId: "tpl-docx-1",
        templateName: "NDA Template",
        outputFormat: "DOCX",
        fileName: "nda-template.docx",
        downloadUrl: "https://s3.example.com/nda-template.docx",
        pdfDownloadUrl: null,
        fileSize: 12345,
        generatedAt: "2026-03-09T10:00:00Z",
        warnings: [],
      },
    });

    const user = userEvent.setup();
    render(<GenerateDocxDialog {...baseProps} />);

    await user.click(screen.getByRole("button", { name: /Generate/i }));

    await waitFor(() => {
      expect(
        screen.getByText("Document generated successfully"),
      ).toBeInTheDocument();
    });

    const docxLink = screen.getByRole("link", { name: /Download .docx/i });
    expect(docxLink).toHaveAttribute(
      "href",
      "https://s3.example.com/nda-template.docx",
    );
    expect(docxLink).toHaveAttribute("target", "_blank");
  });

  it("shows warning when PDF is unavailable", async () => {
    mockGenerateDocxAction.mockResolvedValue({
      success: true,
      data: {
        id: "gen-1",
        templateId: "tpl-docx-1",
        templateName: "NDA Template",
        outputFormat: "PDF",
        fileName: "nda-template.docx",
        downloadUrl: "https://s3.example.com/nda-template.docx",
        pdfDownloadUrl: null,
        fileSize: 12345,
        generatedAt: "2026-03-09T10:00:00Z",
        warnings: [],
      },
    });

    const user = userEvent.setup();
    render(<GenerateDocxDialog {...baseProps} />);

    // Select PDF format
    await user.click(screen.getByDisplayValue("PDF"));
    await user.click(screen.getByRole("button", { name: /Generate/i }));

    await waitFor(() => {
      expect(
        screen.getByText(
          "PDF conversion is not available. Your document has been generated as .docx",
        ),
      ).toBeInTheDocument();
    });
  });

  it("shows both download links when Both format selected", async () => {
    mockGenerateDocxAction.mockResolvedValue({
      success: true,
      data: {
        id: "gen-1",
        templateId: "tpl-docx-1",
        templateName: "NDA Template",
        outputFormat: "BOTH",
        fileName: "nda-template.docx",
        downloadUrl: "https://s3.example.com/nda-template.docx",
        pdfDownloadUrl: "https://s3.example.com/nda-template.pdf",
        fileSize: 12345,
        generatedAt: "2026-03-09T10:00:00Z",
        warnings: [],
      },
    });

    const user = userEvent.setup();
    render(<GenerateDocxDialog {...baseProps} />);

    // Select Both format
    await user.click(screen.getByDisplayValue("BOTH"));
    await user.click(screen.getByRole("button", { name: /Generate/i }));

    await waitFor(() => {
      expect(
        screen.getByText("Document generated successfully"),
      ).toBeInTheDocument();
    });

    const docxLink = screen.getByRole("link", { name: /Download .docx/i });
    const pdfLink = screen.getByRole("link", { name: /Download PDF/i });

    expect(docxLink).toHaveAttribute(
      "href",
      "https://s3.example.com/nda-template.docx",
    );
    expect(pdfLink).toHaveAttribute(
      "href",
      "https://s3.example.com/nda-template.pdf",
    );
  });
});
