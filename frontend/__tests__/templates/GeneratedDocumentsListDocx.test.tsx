import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { GeneratedDocumentsList } from "@/components/templates/GeneratedDocumentsList";

const mockFetchGeneratedDocuments = vi.fn();
const mockDeleteGeneratedDocument = vi.fn();
const mockDownloadGeneratedDocument = vi.fn();
const mockDownloadDocxGeneratedDocument = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/templates/template-generation-actions", () => ({
  fetchGeneratedDocumentsAction: (...args: unknown[]) => mockFetchGeneratedDocuments(...args),
  deleteGeneratedDocumentAction: (...args: unknown[]) => mockDeleteGeneratedDocument(...args),
  downloadGeneratedDocumentAction: (...args: unknown[]) => mockDownloadGeneratedDocument(...args),
  downloadDocxGeneratedDocumentAction: (...args: unknown[]) =>
    mockDownloadDocxGeneratedDocument(...args),
}));

vi.mock("@/lib/format", () => ({
  formatDate: (d: string) => new Date(d).toLocaleDateString("en-US"),
}));

vi.mock("server-only", () => ({}));

const mockGetAcceptanceRequests = vi.fn();

vi.mock("@/lib/actions/acceptance-actions", () => ({
  getAcceptanceRequests: (...args: unknown[]) => mockGetAcceptanceRequests(...args),
}));

vi.mock("@/components/acceptance/AcceptanceStatusBadge", () => ({
  AcceptanceStatusBadge: ({ status }: { status: string }) => (
    <span data-testid="acceptance-badge">{status}</span>
  ),
}));

vi.mock("@/components/acceptance/SendForAcceptanceDialog", () => ({
  SendForAcceptanceDialog: () => <div data-testid="send-acceptance-dialog" />,
}));

vi.mock("@/components/acceptance/AcceptanceDetailPanel", () => ({
  AcceptanceDetailPanel: () => <div data-testid="acceptance-detail-panel" />,
}));

describe("GeneratedDocumentsListDocx", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetAcceptanceRequests.mockResolvedValue([]);
  });

  afterEach(() => {
    cleanup();
  });

  it("shows format badge for DOCX document", async () => {
    mockFetchGeneratedDocuments.mockResolvedValue({
      success: true,
      data: [
        {
          id: "gen-docx-1",
          templateName: "DOCX Template",
          fileName: "proposal.docx",
          fileSize: 4096,
          generatedByName: "Alice",
          generatedAt: "2026-03-01T10:00:00Z",
          outputFormat: "DOCX",
          hasDocxDownload: true,
        },
      ],
    });

    render(
      <GeneratedDocumentsList entityType="PROJECT" entityId="proj-1" slug="acme" isAdmin={true} />
    );

    await waitFor(() => {
      expect(screen.getByText("DOCX Template")).toBeInTheDocument();
    });

    expect(screen.getByText("DOCX")).toBeInTheDocument();
    // Should not show PDF badge
    expect(screen.queryByText("PDF")).not.toBeInTheDocument();
  });

  it("shows format badge for PDF document", async () => {
    mockFetchGeneratedDocuments.mockResolvedValue({
      success: true,
      data: [
        {
          id: "gen-pdf-1",
          templateName: "PDF Template",
          fileName: "report.pdf",
          fileSize: 2048,
          generatedByName: "Bob",
          generatedAt: "2026-03-01T11:00:00Z",
          outputFormat: "PDF",
          hasDocxDownload: false,
        },
      ],
    });

    render(
      <GeneratedDocumentsList entityType="PROJECT" entityId="proj-2" slug="acme" isAdmin={true} />
    );

    await waitFor(() => {
      expect(screen.getByText("PDF Template")).toBeInTheDocument();
    });

    expect(screen.getByText("PDF")).toBeInTheDocument();
    expect(screen.queryByText("DOCX")).not.toBeInTheDocument();
  });

  it("shows dual downloads when BOTH format available", async () => {
    mockFetchGeneratedDocuments.mockResolvedValue({
      success: true,
      data: [
        {
          id: "gen-both-1",
          templateName: "Dual Format Template",
          fileName: "contract.pdf",
          fileSize: 8192,
          generatedByName: "Carol",
          generatedAt: "2026-03-01T12:00:00Z",
          outputFormat: "BOTH",
          hasDocxDownload: true,
        },
      ],
    });

    render(
      <GeneratedDocumentsList entityType="CUSTOMER" entityId="cust-1" slug="acme" isAdmin={true} />
    );

    await waitFor(() => {
      expect(screen.getByText("Dual Format Template")).toBeInTheDocument();
    });

    // Should show both PDF and DOCX badges
    expect(screen.getByText("PDF")).toBeInTheDocument();
    expect(screen.getByText("DOCX")).toBeInTheDocument();

    // Should have both download buttons
    const pdfButton = screen.getByTitle("Download PDF");
    const docxButton = screen.getByTitle("Download DOCX");
    expect(pdfButton).toBeInTheDocument();
    expect(docxButton).toBeInTheDocument();
  });

  it("shows single DOCX download when DOCX only", async () => {
    mockFetchGeneratedDocuments.mockResolvedValue({
      success: true,
      data: [
        {
          id: "gen-docx-only",
          templateName: "DOCX Only Template",
          fileName: "letter.docx",
          fileSize: 3072,
          generatedByName: "Alice",
          generatedAt: "2026-03-01T13:00:00Z",
          outputFormat: "DOCX",
          hasDocxDownload: true,
        },
      ],
    });

    render(
      <GeneratedDocumentsList entityType="INVOICE" entityId="inv-1" slug="acme" isAdmin={true} />
    );

    await waitFor(() => {
      expect(screen.getByText("DOCX Only Template")).toBeInTheDocument();
    });

    // Should have DOCX download but NOT PDF download
    const docxButton = screen.getByTitle("Download DOCX");
    expect(docxButton).toBeInTheDocument();
    expect(screen.queryByTitle("Download PDF")).not.toBeInTheDocument();
  });

  it("defaults to PDF format when outputFormat is missing", async () => {
    mockFetchGeneratedDocuments.mockResolvedValue({
      success: true,
      data: [
        {
          id: "gen-legacy-1",
          templateName: "Legacy Template",
          fileName: "old-doc.pdf",
          fileSize: 1024,
          generatedByName: "Bob",
          generatedAt: "2026-03-01T14:00:00Z",
        },
      ],
    });

    render(
      <GeneratedDocumentsList entityType="PROJECT" entityId="proj-3" slug="acme" isAdmin={true} />
    );

    await waitFor(() => {
      expect(screen.getByText("Legacy Template")).toBeInTheDocument();
    });

    // Should default to PDF
    expect(screen.getByText("PDF")).toBeInTheDocument();
    expect(screen.getByTitle("Download PDF")).toBeInTheDocument();
    expect(screen.queryByTitle("Download DOCX")).not.toBeInTheDocument();
  });
});
