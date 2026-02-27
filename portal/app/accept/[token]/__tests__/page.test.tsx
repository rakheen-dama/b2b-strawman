import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock api/acceptance module
const mockGetAcceptancePageData = vi.fn();
const mockSubmitAcceptance = vi.fn();
const mockGetAcceptancePdfUrl = vi.fn();

vi.mock("@/lib/api/acceptance", () => ({
  getAcceptancePageData: (...args: unknown[]) =>
    mockGetAcceptancePageData(...args),
  submitAcceptance: (...args: unknown[]) => mockSubmitAcceptance(...args),
  getAcceptancePdfUrl: (...args: unknown[]) => mockGetAcceptancePdfUrl(...args),
}));

import { AcceptancePage } from "@/app/accept/[token]/acceptance-page";

const mockPendingPageData = {
  requestId: "req-1",
  status: "PENDING",
  documentTitle: "Service Agreement 2026",
  documentFileName: "service-agreement-2026.pdf",
  expiresAt: "2026-03-15T00:00:00Z",
  orgName: "Smith & Associates",
  orgLogo: "https://example.com/logo.png",
  brandColor: "#14B8A6",
  acceptedAt: null,
  acceptorName: null,
};

const mockAcceptedPageData = {
  ...mockPendingPageData,
  status: "ACCEPTED",
  acceptedAt: "2026-02-27T14:30:00Z",
  acceptorName: "Jane Doe",
};

const mockExpiredPageData = {
  ...mockPendingPageData,
  status: "EXPIRED",
};

const mockRevokedPageData = {
  ...mockPendingPageData,
  status: "REVOKED",
};

describe("AcceptancePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetAcceptancePdfUrl.mockReturnValue(
      "http://localhost:8080/api/portal/acceptance/test-token/pdf",
    );
  });

  afterEach(() => {
    cleanup();
  });

  it("renders pdf viewer and form after loading", async () => {
    mockGetAcceptancePageData.mockResolvedValue(mockPendingPageData);

    render(<AcceptancePage token="test-token" />);

    await waitFor(() => {
      expect(screen.getByTitle("Document PDF")).toBeInTheDocument();
    });

    expect(screen.getByTitle("Document PDF")).toHaveAttribute(
      "src",
      "http://localhost:8080/api/portal/acceptance/test-token/pdf",
    );
    expect(screen.getByLabelText("Full name")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "I Accept" })).toBeInTheDocument();
    expect(
      screen.getByText("Service Agreement 2026"),
    ).toBeInTheDocument();
  });

  it("disables accept button without name", async () => {
    mockGetAcceptancePageData.mockResolvedValue(mockPendingPageData);

    render(<AcceptancePage token="test-token" />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "I Accept" })).toBeInTheDocument();
    });

    expect(screen.getByRole("button", { name: "I Accept" })).toBeDisabled();
  });

  it("enables accept button with valid name", async () => {
    const user = userEvent.setup();
    mockGetAcceptancePageData.mockResolvedValue(mockPendingPageData);

    render(<AcceptancePage token="test-token" />);

    await waitFor(() => {
      expect(screen.getByLabelText("Full name")).toBeInTheDocument();
    });

    await user.type(screen.getByLabelText("Full name"), "Jane Doe");

    expect(screen.getByRole("button", { name: "I Accept" })).toBeEnabled();
  });

  it("submits acceptance and shows confirmation", async () => {
    const user = userEvent.setup();
    mockGetAcceptancePageData.mockResolvedValue(mockPendingPageData);
    mockSubmitAcceptance.mockResolvedValue({
      status: "ACCEPTED",
      acceptedAt: "2026-02-27T14:30:00Z",
      acceptorName: "Jane Doe",
    });

    render(<AcceptancePage token="test-token" />);

    await waitFor(() => {
      expect(screen.getByLabelText("Full name")).toBeInTheDocument();
    });

    await user.type(screen.getByLabelText("Full name"), "Jane Doe");
    await user.click(screen.getByRole("button", { name: "I Accept" }));

    await waitFor(() => {
      expect(
        screen.getByText(/Jane Doe accepted this document/),
      ).toBeInTheDocument();
    });

    // PDF should still be visible
    expect(screen.getByTitle("Document PDF")).toBeInTheDocument();

    // Form should be gone
    expect(screen.queryByLabelText("Full name")).not.toBeInTheDocument();
  });

  it("shows expired message", async () => {
    mockGetAcceptancePageData.mockResolvedValue(mockExpiredPageData);

    render(<AcceptancePage token="test-token" />);

    await waitFor(() => {
      expect(
        screen.getByText(
          /This acceptance request has expired\. Please contact Smith & Associates for a new link\./,
        ),
      ).toBeInTheDocument();
    });

    // PDF should still be visible
    expect(screen.getByTitle("Document PDF")).toBeInTheDocument();

    // Form should not be shown
    expect(screen.queryByLabelText("Full name")).not.toBeInTheDocument();
  });

  it("shows revoked message", async () => {
    mockGetAcceptancePageData.mockResolvedValue(mockRevokedPageData);

    render(<AcceptancePage token="test-token" />);

    await waitFor(() => {
      expect(
        screen.getByText(
          /This acceptance request has been revoked by Smith & Associates\./,
        ),
      ).toBeInTheDocument();
    });

    // PDF should still be visible
    expect(screen.getByTitle("Document PDF")).toBeInTheDocument();

    // Form should not be shown
    expect(screen.queryByLabelText("Full name")).not.toBeInTheDocument();
  });

  it("renders org branding", async () => {
    mockGetAcceptancePageData.mockResolvedValue(mockPendingPageData);

    render(<AcceptancePage token="test-token" />);

    await waitFor(() => {
      expect(screen.getByText("Smith & Associates")).toBeInTheDocument();
    });

    expect(
      screen.getByAltText("Smith & Associates logo"),
    ).toBeInTheDocument();
    expect(screen.getByAltText("Smith & Associates logo")).toHaveAttribute(
      "src",
      "https://example.com/logo.png",
    );
  });
});
