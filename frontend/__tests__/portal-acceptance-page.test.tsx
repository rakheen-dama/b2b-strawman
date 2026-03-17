import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock the API module before importing the component
const mockGetAcceptancePageData = vi.fn();
const mockGetAcceptancePdf = vi.fn();
const mockAcceptDocument = vi.fn();

vi.mock("@/lib/api/portal-acceptance", () => ({
  getAcceptancePageData: (...args: unknown[]) =>
    mockGetAcceptancePageData(...args),
  getAcceptancePdf: (...args: unknown[]) => mockGetAcceptancePdf(...args),
  acceptDocument: (...args: unknown[]) => mockAcceptDocument(...args),
}));

// Mock URL.createObjectURL / revokeObjectURL
const mockCreateObjectURL = vi.fn(() => "blob:http://localhost/fake-pdf-url");
const mockRevokeObjectURL = vi.fn();
globalThis.URL.createObjectURL = mockCreateObjectURL;
globalThis.URL.revokeObjectURL = mockRevokeObjectURL;

// Import the inner content component (bypasses React.use(params) suspension)
import { AcceptancePageContent } from "@/app/accept/[token]/page";

function renderPage(token = "test-token-123") {
  return render(<AcceptancePageContent token={token} />);
}

describe("AcceptancePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders loading state initially", () => {
    // Never-resolving promise to keep loading state
    mockGetAcceptancePageData.mockReturnValue(new Promise(() => {}));

    renderPage();

    const spinner = document.querySelector(".animate-spin");
    expect(spinner).toBeInTheDocument();
  });

  it("renders not found error when API returns 404", async () => {
    mockGetAcceptancePageData.mockRejectedValue(new Error("not_found"));

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Link Not Valid")).toBeInTheDocument();
    });

    expect(screen.getByText(/This link is not valid/)).toBeInTheDocument();
  });

  it("renders expired state with formatted date", async () => {
    mockGetAcceptancePageData.mockResolvedValue({
      requestId: "req-1",
      status: "EXPIRED",
      documentTitle: "Engagement Letter",
      documentFileName: "engagement.pdf",
      expiresAt: "2026-03-01T12:00:00Z",
      orgName: "Acme Corp",
      orgLogo: null,
      brandColor: null,
      acceptedAt: null,
      acceptorName: null,
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Request Expired")).toBeInTheDocument();
    });

    expect(
      screen.getByText(/This acceptance request expired on/),
    ).toBeInTheDocument();
    // "Acme Corp" appears in both the branding header and the body copy
    expect(screen.getAllByText(/Acme Corp/).length).toBeGreaterThanOrEqual(1);
  });

  it("renders revoked state", async () => {
    mockGetAcceptancePageData.mockResolvedValue({
      requestId: "req-1",
      status: "REVOKED",
      documentTitle: "Contract",
      documentFileName: "contract.pdf",
      expiresAt: null,
      orgName: "Acme Corp",
      orgLogo: null,
      brandColor: null,
      acceptedAt: null,
      acceptorName: null,
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Request Withdrawn")).toBeInTheDocument();
    });

    expect(
      screen.getByText(/This acceptance request has been withdrawn/),
    ).toBeInTheDocument();
  });

  it("renders acceptance form for VIEWED status with PDF iframe", async () => {
    mockGetAcceptancePageData.mockResolvedValue({
      requestId: "req-1",
      status: "VIEWED",
      documentTitle: "Engagement Letter",
      documentFileName: "engagement.pdf",
      expiresAt: "2026-04-01T12:00:00Z",
      orgName: "Acme Corp",
      orgLogo: null,
      brandColor: null,
      acceptedAt: null,
      acceptorName: null,
    });

    const fakeBlob = new Blob(["fake-pdf"], { type: "application/pdf" });
    mockGetAcceptancePdf.mockResolvedValue(fakeBlob);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Engagement Letter")).toBeInTheDocument();
    });

    // PDF iframe should be present
    const iframe = document.querySelector(
      'iframe[title="Document preview"]',
    ) as HTMLIFrameElement;
    expect(iframe).toBeInTheDocument();
    expect(iframe.src).toContain("blob:");

    // Acceptance form elements
    expect(screen.getByLabelText("Full Legal Name")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "I Accept This Document" }),
    ).toBeInTheDocument();
  });

  it("submits acceptance with typed name and shows confirmation", async () => {
    const user = userEvent.setup();

    mockGetAcceptancePageData.mockResolvedValue({
      requestId: "req-1",
      status: "SENT",
      documentTitle: "Proposal",
      documentFileName: "proposal.pdf",
      expiresAt: null,
      orgName: "Acme Corp",
      orgLogo: null,
      brandColor: null,
      acceptedAt: null,
      acceptorName: null,
    });

    const fakeBlob = new Blob(["fake-pdf"], { type: "application/pdf" });
    mockGetAcceptancePdf.mockResolvedValue(fakeBlob);

    mockAcceptDocument.mockResolvedValue({
      status: "ACCEPTED",
      acceptedAt: "2026-03-17T10:00:00Z",
      acceptorName: "John Doe",
    });

    renderPage();

    // Wait for the form to appear
    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "I Accept This Document" }),
      ).toBeInTheDocument();
    });

    // Type name
    const input = screen.getByLabelText("Full Legal Name");
    await user.type(input, "John Doe");

    // Submit
    await user.click(
      screen.getByRole("button", { name: "I Accept This Document" }),
    );

    // Should show accepted confirmation
    await waitFor(() => {
      expect(screen.getByText("Document Accepted")).toBeInTheDocument();
    });

    expect(mockAcceptDocument).toHaveBeenCalledWith(
      "test-token-123",
      "John Doe",
    );
  });

  it("disables submit button when name is too short", async () => {
    const user = userEvent.setup();

    mockGetAcceptancePageData.mockResolvedValue({
      requestId: "req-1",
      status: "SENT",
      documentTitle: "Document",
      documentFileName: "doc.pdf",
      expiresAt: null,
      orgName: "Acme Corp",
      orgLogo: null,
      brandColor: null,
      acceptedAt: null,
      acceptorName: null,
    });

    const fakeBlob = new Blob(["fake-pdf"], { type: "application/pdf" });
    mockGetAcceptancePdf.mockResolvedValue(fakeBlob);

    renderPage();

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "I Accept This Document" }),
      ).toBeInTheDocument();
    });

    // Button should be disabled initially (empty name)
    const button = screen.getByRole("button", {
      name: "I Accept This Document",
    });
    expect(button).toBeDisabled();

    // Type single character — still disabled
    const input = screen.getByLabelText("Full Legal Name");
    await user.type(input, "A");
    expect(button).toBeDisabled();

    // Type second character — should be enabled
    await user.type(input, "B");
    expect(button).toBeEnabled();
  });
});
