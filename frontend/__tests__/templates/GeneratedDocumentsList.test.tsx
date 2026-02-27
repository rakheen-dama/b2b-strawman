import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { GeneratedDocumentsList } from "@/components/templates/GeneratedDocumentsList";

const mockFetchGeneratedDocuments = vi.fn();
const mockDeleteGeneratedDocument = vi.fn();
const mockDownloadGeneratedDocument = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/templates/actions", () => ({
  fetchGeneratedDocumentsAction: (...args: unknown[]) =>
    mockFetchGeneratedDocuments(...args),
  deleteGeneratedDocumentAction: (...args: unknown[]) =>
    mockDeleteGeneratedDocument(...args),
  downloadGeneratedDocumentAction: (...args: unknown[]) =>
    mockDownloadGeneratedDocument(...args),
}));

vi.mock("@/lib/format", () => ({
  formatDate: (d: string) => new Date(d).toLocaleDateString("en-US"),
}));

// Mock server-only (imported transitively via acceptance-actions -> api)
vi.mock("server-only", () => ({}));

const mockGetAcceptanceRequests = vi.fn();

vi.mock("@/lib/actions/acceptance-actions", () => ({
  getAcceptanceRequests: (...args: unknown[]) =>
    mockGetAcceptanceRequests(...args),
}));

vi.mock("@/components/acceptance/AcceptanceStatusBadge", () => ({
  AcceptanceStatusBadge: ({ status }: { status: string }) => (
    <span data-testid="acceptance-badge">{status}</span>
  ),
}));

vi.mock("@/components/acceptance/SendForAcceptanceDialog", () => ({
  SendForAcceptanceDialog: () => (
    <div data-testid="send-acceptance-dialog" />
  ),
}));

const SAMPLE_DOCS = [
  {
    id: "gen-1",
    templateName: "Engagement Letter",
    fileName: "engagement-letter.pdf",
    fileSize: 2048,
    generatedByName: "John Doe",
    generatedAt: "2026-01-15T10:00:00Z",
  },
  {
    id: "gen-2",
    templateName: "Invoice Template",
    fileName: "invoice.pdf",
    fileSize: 1048576,
    generatedByName: "Jane Smith",
    generatedAt: "2026-01-16T14:00:00Z",
  },
];

describe("GeneratedDocumentsList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders table with generated documents from API", async () => {
    mockFetchGeneratedDocuments.mockResolvedValue({
      success: true,
      data: SAMPLE_DOCS,
    });

    render(
      <GeneratedDocumentsList
        entityType="PROJECT"
        entityId="proj-1"
        slug="acme"
        isAdmin={true}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Engagement Letter")).toBeInTheDocument();
    });

    expect(screen.getByText("John Doe")).toBeInTheDocument();
    expect(screen.getByText("Jane Smith")).toBeInTheDocument();
    expect(screen.getByText("Invoice Template")).toBeInTheDocument();
    // Check file size formatting
    expect(screen.getByText("2.0 KB")).toBeInTheDocument();
    expect(screen.getByText("1.0 MB")).toBeInTheDocument();
  });

  it("shows empty state when no documents", async () => {
    mockFetchGeneratedDocuments.mockResolvedValue({
      success: true,
      data: [],
    });

    render(
      <GeneratedDocumentsList
        entityType="PROJECT"
        entityId="proj-1"
        slug="acme"
      />,
    );

    await waitFor(() => {
      expect(
        screen.getByText("No documents generated yet"),
      ).toBeInTheDocument();
    });
  });

  it("download button calls authenticated download and triggers file save", async () => {
    mockFetchGeneratedDocuments.mockResolvedValue({
      success: true,
      data: [SAMPLE_DOCS[0]],
    });

    mockDownloadGeneratedDocument.mockResolvedValue({
      success: true,
      pdfBase64: btoa("fake-pdf-content"),
    });

    // Mock URL.createObjectURL and URL.revokeObjectURL
    const createObjectURLSpy = vi
      .spyOn(URL, "createObjectURL")
      .mockReturnValue("blob:http://localhost/fake-blob");
    const revokeObjectURLSpy = vi
      .spyOn(URL, "revokeObjectURL")
      .mockImplementation(() => {});

    render(
      <GeneratedDocumentsList
        entityType="PROJECT"
        entityId="proj-1"
        slug="acme"
        isAdmin={false}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Engagement Letter")).toBeInTheDocument();
    });

    const user = userEvent.setup();
    await user.click(screen.getByTitle("Download"));

    await waitFor(() => {
      expect(mockDownloadGeneratedDocument).toHaveBeenCalledWith("gen-1");
    });

    await waitFor(() => {
      expect(createObjectURLSpy).toHaveBeenCalled();
    });

    createObjectURLSpy.mockRestore();
    revokeObjectURLSpy.mockRestore();
  });

  it("shows error message when download fails", async () => {
    mockFetchGeneratedDocuments.mockResolvedValue({
      success: true,
      data: [SAMPLE_DOCS[0]],
    });

    mockDownloadGeneratedDocument.mockResolvedValue({
      success: false,
      error: "Access denied",
    });

    render(
      <GeneratedDocumentsList
        entityType="PROJECT"
        entityId="proj-1"
        slug="acme"
        isAdmin={false}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Engagement Letter")).toBeInTheDocument();
    });

    const user = userEvent.setup();
    await user.click(screen.getByTitle("Download"));

    await waitFor(() => {
      expect(screen.getByText("Access denied")).toBeInTheDocument();
    });
  });

  it("delete button only visible to admin users", async () => {
    mockFetchGeneratedDocuments.mockResolvedValue({
      success: true,
      data: [SAMPLE_DOCS[0]],
    });

    // Non-admin: no delete button
    const { unmount } = render(
      <GeneratedDocumentsList
        entityType="PROJECT"
        entityId="proj-1"
        slug="acme"
        isAdmin={false}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Engagement Letter")).toBeInTheDocument();
    });

    expect(screen.queryByTitle("Delete")).not.toBeInTheDocument();

    unmount();
    cleanup();

    // Admin: delete button present
    render(
      <GeneratedDocumentsList
        entityType="PROJECT"
        entityId="proj-1"
        slug="acme"
        isAdmin={true}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Engagement Letter")).toBeInTheDocument();
    });

    expect(screen.getByTitle("Delete")).toBeInTheDocument();
  });

  it("confirms delete, calls API, and removes row", async () => {
    mockFetchGeneratedDocuments.mockResolvedValue({
      success: true,
      data: [...SAMPLE_DOCS],
    });

    mockDeleteGeneratedDocument.mockResolvedValue({ success: true });

    const user = userEvent.setup();

    render(
      <GeneratedDocumentsList
        entityType="PROJECT"
        entityId="proj-1"
        slug="acme"
        isAdmin={true}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Engagement Letter")).toBeInTheDocument();
    });

    // Find the delete button for the first document
    const rows = screen.getAllByRole("row");
    const firstDataRow = rows[1]; // first row is header
    const deleteBtn = within(firstDataRow).getByTitle("Delete");
    await user.click(deleteBtn);

    // Confirmation dialog should appear
    await waitFor(() => {
      expect(
        screen.getByText("Delete Generated Document"),
      ).toBeInTheDocument();
    });

    // Click confirm delete
    await user.click(screen.getByRole("button", { name: "Delete" }));

    await waitFor(() => {
      expect(mockDeleteGeneratedDocument).toHaveBeenCalledWith("gen-1");
    });

    // Row should be removed
    await waitFor(() => {
      expect(screen.queryByText("Engagement Letter")).not.toBeInTheDocument();
    });

    // Other row should still be there
    expect(screen.getByText("Invoice Template")).toBeInTheDocument();
  });

  it("refetches documents when refreshKey changes", async () => {
    mockFetchGeneratedDocuments.mockResolvedValue({
      success: true,
      data: SAMPLE_DOCS,
    });

    const { rerender } = render(
      <GeneratedDocumentsList
        entityType="PROJECT"
        entityId="proj-1"
        slug="acme"
        refreshKey={0}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Engagement Letter")).toBeInTheDocument();
    });

    expect(mockFetchGeneratedDocuments).toHaveBeenCalledTimes(1);

    rerender(
      <GeneratedDocumentsList
        entityType="PROJECT"
        entityId="proj-1"
        slug="acme"
        refreshKey={1}
      />,
    );

    await waitFor(() => {
      expect(mockFetchGeneratedDocuments).toHaveBeenCalledTimes(2);
    });
  });

  it('shows "Send for Acceptance" action button for admin users when customerId provided', async () => {
    mockFetchGeneratedDocuments.mockResolvedValue({
      success: true,
      data: [SAMPLE_DOCS[0]],
    });
    mockGetAcceptanceRequests.mockResolvedValue([]);

    render(
      <GeneratedDocumentsList
        entityType="CUSTOMER"
        entityId="proj-1"
        slug="acme"
        isAdmin={true}
        customerId="cust-1"
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Engagement Letter")).toBeInTheDocument();
    });

    expect(screen.getByTitle("Send for Acceptance")).toBeInTheDocument();
  });

  it("shows AcceptanceStatusBadge for documents with acceptance requests", async () => {
    mockFetchGeneratedDocuments.mockResolvedValue({
      success: true,
      data: [SAMPLE_DOCS[0]],
    });
    mockGetAcceptanceRequests.mockResolvedValue([
      {
        id: "req-1",
        generatedDocumentId: "gen-1",
        portalContactId: "contact-1",
        customerId: "cust-1",
        status: "SENT",
        sentAt: "2026-01-15T10:00:00Z",
        createdAt: "2026-01-15T10:00:00Z",
        updatedAt: "2026-01-15T10:00:00Z",
        contact: { id: "contact-1", displayName: "Jane", email: "jane@test.com" },
        document: { id: "gen-1", fileName: "test.pdf" },
      },
    ]);

    render(
      <GeneratedDocumentsList
        entityType="CUSTOMER"
        entityId="proj-1"
        slug="acme"
        isAdmin={true}
        customerId="cust-1"
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Engagement Letter")).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByTestId("acceptance-badge")).toBeInTheDocument();
      expect(screen.getByText("SENT")).toBeInTheDocument();
    });
  });
});
