import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { formatDateTime } from "@b2mash/shared/format";

// Mock api/acceptance module
const mockGetAcceptancePageData = vi.fn();
const mockSubmitAcceptance = vi.fn();
const mockGetAcceptancePdfUrl = vi.fn();
const mockDeclineAcceptance = vi.fn();

vi.mock("@/lib/api/acceptance", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/acceptance")>();
  return {
    // The real error class, so the page's 409 instanceof check sees the same constructor.
    DeclineConflictError: actual.DeclineConflictError,
    getAcceptancePageData: (...args: unknown[]) =>
      mockGetAcceptancePageData(...args),
    submitAcceptance: (...args: unknown[]) => mockSubmitAcceptance(...args),
    declineAcceptance: (...args: unknown[]) => mockDeclineAcceptance(...args),
    getAcceptancePdfUrl: (...args: unknown[]) =>
      mockGetAcceptancePdfUrl(...args),
  };
});

import { AcceptancePage } from "@/app/accept/[token]/acceptance-page";
import { DeclineConflictError } from "@/lib/api/acceptance";

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
  declinedAt: null,
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

const mockSentPageData = {
  ...mockPendingPageData,
  status: "SENT",
};

const mockViewedPageData = {
  ...mockPendingPageData,
  status: "VIEWED",
};

const DECLINED_AT = "2026-10-22T14:10:02Z";

const mockDeclinedPageData = {
  ...mockPendingPageData,
  status: "DECLINED",
  declinedAt: DECLINED_AT,
};

/** Waits for the PENDING accept form, then opens the decline dialog (desktop button). */
async function openDeclineDialog(user: ReturnType<typeof userEvent.setup>) {
  await waitFor(() => {
    expect(screen.getByLabelText("Full name")).toBeInTheDocument();
  });
  await user.click(screen.getByRole("button", { name: "Decline" }));
  return screen.findByRole("dialog", { name: "Decline this document?" });
}

describe("AcceptancePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDeclineAcceptance.mockReset();
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
    expect(
      screen.getByRole("button", { name: "I Accept" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Service Agreement 2026")).toBeInTheDocument();
  });

  it("disables accept button without name", async () => {
    mockGetAcceptancePageData.mockResolvedValue(mockPendingPageData);

    render(<AcceptancePage token="test-token" />);

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "I Accept" }),
      ).toBeInTheDocument();
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

  it("renders accept form when status is SENT", async () => {
    mockGetAcceptancePageData.mockResolvedValue(mockSentPageData);

    render(<AcceptancePage token="test-token" />);

    await waitFor(() => {
      expect(screen.getByTitle("Document PDF")).toBeInTheDocument();
    });

    expect(screen.getByLabelText("Full name")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "I Accept" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/Unable to process this acceptance request/),
    ).not.toBeInTheDocument();
  });

  it("renders accept form when status is VIEWED", async () => {
    mockGetAcceptancePageData.mockResolvedValue(mockViewedPageData);

    render(<AcceptancePage token="test-token" />);

    await waitFor(() => {
      expect(screen.getByTitle("Document PDF")).toBeInTheDocument();
    });

    expect(screen.getByLabelText("Full name")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "I Accept" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/Unable to process this acceptance request/),
    ).not.toBeInTheDocument();
  });

  it("shows error when page data fails to load", async () => {
    mockGetAcceptancePageData.mockRejectedValue(
      new Error("Failed to load acceptance data. Please try again."),
    );

    render(<AcceptancePage token="test-token" />);

    await waitFor(() => {
      expect(
        screen.getByText("Failed to load acceptance data. Please try again."),
      ).toBeInTheDocument();
    });

    // Form and PDF should not be visible
    expect(screen.queryByLabelText("Full name")).not.toBeInTheDocument();
    expect(screen.queryByTitle("Document PDF")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "Open in new tab" }),
    ).not.toBeInTheDocument();
    // No decline affordance on the ERROR card
    expect(
      screen.queryByRole("button", { name: /Decline/ }),
    ).not.toBeInTheDocument();
  });

  it("shows error and stays on form when submission fails", async () => {
    const user = userEvent.setup();
    mockGetAcceptancePageData.mockResolvedValue(mockPendingPageData);
    mockSubmitAcceptance.mockRejectedValue(
      new Error("Failed to submit acceptance. Please try again."),
    );

    render(<AcceptancePage token="test-token" />);

    await waitFor(() => {
      expect(screen.getByLabelText("Full name")).toBeInTheDocument();
    });

    await user.type(screen.getByLabelText("Full name"), "Jane Doe");
    await user.click(screen.getByRole("button", { name: "I Accept" }));

    await waitFor(() => {
      expect(
        screen.getByText("Failed to submit acceptance. Please try again."),
      ).toBeInTheDocument();
    });

    // Form should still be visible (state reverted to PENDING)
    expect(screen.getByLabelText("Full name")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "I Accept" }),
    ).toBeInTheDocument();
  });

  it("renders org branding", async () => {
    mockGetAcceptancePageData.mockResolvedValue(mockPendingPageData);

    render(<AcceptancePage token="test-token" />);

    await waitFor(() => {
      expect(screen.getByText("Smith & Associates")).toBeInTheDocument();
    });

    expect(screen.getByAltText("Smith & Associates logo")).toBeInTheDocument();
    expect(screen.getByAltText("Smith & Associates logo")).toHaveAttribute(
      "src",
      "https://example.com/logo.png",
    );
  });

  it("renders an Open in new tab link to the same PDF URL as the iframe", async () => {
    mockGetAcceptancePageData.mockResolvedValue(mockPendingPageData);

    render(<AcceptancePage token="test-token" />);

    const link = await screen.findByRole("link", { name: "Open in new tab" });
    expect(link).toHaveAttribute(
      "href",
      "http://localhost:8080/api/portal/acceptance/test-token/pdf",
    );
    expect(link).toHaveAttribute(
      "href",
      screen.getByTitle("Document PDF").getAttribute("src"),
    );
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
    expect(mockGetAcceptancePdfUrl).toHaveBeenCalledWith("test-token");
  });

  it("keeps the Open in new tab link after the document is accepted", async () => {
    mockGetAcceptancePageData.mockResolvedValue(mockAcceptedPageData);

    render(<AcceptancePage token="test-token" />);

    await waitFor(() => {
      expect(
        screen.getByText(/Jane Doe accepted this document/),
      ).toBeInTheDocument();
    });
    expect(
      screen.getByRole("link", { name: "Open in new tab" }),
    ).toBeInTheDocument();
  });

  // ── Phase 88 624H: G88-M3 accepted timestamp ──────────────────────────

  it("shows the date and time in the accepted state (G88-M3)", async () => {
    mockGetAcceptancePageData.mockResolvedValue(mockAcceptedPageData);

    render(<AcceptancePage token="test-token" />);

    const message = await screen.findByText(/Jane Doe accepted this document/);
    // en-ZA, pinned to Africa/Johannesburg: 14:30Z → 16:30
    expect(message).toHaveTextContent(
      `Jane Doe accepted this document on ${formatDateTime("2026-02-27T14:30:00Z")}.`,
    );
    expect(message).toHaveTextContent(/16:30/);
  });

  // ── Phase 88 624H: Decline (G-B19 / U23) ─────────────────────────────

  it("renders the declined state for a DECLINED token (not the error card)", async () => {
    mockGetAcceptancePageData.mockResolvedValue(mockDeclinedPageData);

    render(<AcceptancePage token="test-token" />);

    const message = await screen.findByText(/You declined this document/);
    expect(message).toHaveTextContent(
      `You declined this document on ${formatDateTime(DECLINED_AT)}.`,
    );
    expect(
      screen.getByText("Smith & Associates has been notified."),
    ).toBeInTheDocument();
    // No reason after reload — PortalPageData carries none (R-6)
    expect(screen.queryByText(/Your reason/)).not.toBeInTheDocument();
    expect(
      screen.queryByText(/Unable to process this acceptance request/),
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Full name")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Decline/ }),
    ).not.toBeInTheDocument();
    // 618B link survives in the DECLINED state
    expect(screen.getByTitle("Document PDF")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Open in new tab" }),
    ).toHaveAttribute(
      "href",
      "http://localhost:8080/api/portal/acceptance/test-token/pdf",
    );
  });

  it("offers Decline on desktop and in the mobile sticky bar while pending", async () => {
    mockGetAcceptancePageData.mockResolvedValue(mockSentPageData);

    render(<AcceptancePage token="test-token" />);

    await waitFor(() => {
      expect(screen.getByLabelText("Full name")).toBeInTheDocument();
    });

    expect(screen.getByRole("button", { name: "Decline" })).toBeEnabled();
    const stickyBar = screen.getByTestId("sticky-action-bar");
    expect(
      within(stickyBar).getByRole("button", { name: "Accept document" }),
    ).toBeInTheDocument();
    expect(
      within(stickyBar).getByRole("button", { name: "Decline this document" }),
    ).toBeEnabled();
  });

  it("declines with a reason and shows the declined state", async () => {
    const user = userEvent.setup();
    mockGetAcceptancePageData.mockResolvedValue(mockViewedPageData);
    mockDeclineAcceptance.mockResolvedValue({
      status: "DECLINED",
      declinedAt: DECLINED_AT,
    });

    render(<AcceptancePage token="test-token" />);

    const dialog = await openDeclineDialog(user);
    expect(
      within(dialog).getByText(
        "Smith & Associates will be told that you declined. Adding a reason is optional.",
      ),
    ).toBeInTheDocument();
    const reason = within(dialog).getByLabelText("Reason (optional)");
    expect(reason).toHaveAttribute("maxLength", "2000");

    await user.type(reason, "Price too high");
    await user.click(
      within(dialog).getByRole("button", { name: "Decline document" }),
    );

    expect(mockDeclineAcceptance).toHaveBeenCalledWith(
      "test-token",
      "Price too high",
    );

    const message = await screen.findByText(/You declined this document/);
    expect(message).toHaveTextContent(
      `You declined this document on ${formatDateTime(DECLINED_AT)}.`,
    );
    expect(
      screen.getByText("Your reason: \u201cPrice too high\u201d"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Smith & Associates has been notified."),
    ).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(screen.queryByLabelText("Full name")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Decline/ }),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId("sticky-action-bar")).not.toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Open in new tab" }),
    ).toBeInTheDocument();
  });

  it("declines without a reason", async () => {
    const user = userEvent.setup();
    mockGetAcceptancePageData.mockResolvedValue(mockPendingPageData);
    mockDeclineAcceptance.mockResolvedValue({
      status: "DECLINED",
      declinedAt: DECLINED_AT,
    });

    render(<AcceptancePage token="test-token" />);

    const dialog = await openDeclineDialog(user);
    await user.click(
      within(dialog).getByRole("button", { name: "Decline document" }),
    );

    expect(mockDeclineAcceptance).toHaveBeenCalledWith("test-token", "");
    expect(
      await screen.findByText(/You declined this document/),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Your reason/)).not.toBeInTheDocument();
  });

  it("closes the dialog on Cancel without declining and returns focus to Decline", async () => {
    const user = userEvent.setup();
    mockGetAcceptancePageData.mockResolvedValue(mockPendingPageData);

    render(<AcceptancePage token="test-token" />);

    const dialog = await openDeclineDialog(user);
    await user.click(within(dialog).getByRole("button", { name: "Cancel" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(mockDeclineAcceptance).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Full name")).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Decline" })).toHaveFocus();
    });
  });

  it("disables both actions while the decline is in flight", async () => {
    const user = userEvent.setup();
    mockGetAcceptancePageData.mockResolvedValue(mockPendingPageData);
    mockDeclineAcceptance.mockReturnValue(new Promise(() => {}));

    render(<AcceptancePage token="test-token" />);

    const dialog = await openDeclineDialog(user);
    await user.click(
      within(dialog).getByRole("button", { name: "Decline document" }),
    );

    await waitFor(() => {
      expect(
        within(dialog).getByRole("button", { name: /Declining/ }),
      ).toBeDisabled();
    });
    expect(
      within(dialog).getByRole("button", { name: "Cancel" }),
    ).toBeDisabled();
    const stickyBar = screen.getByTestId("sticky-action-bar");
    expect(
      within(stickyBar).getByRole("button", {
        name: "Accept document",
        hidden: true,
      }),
    ).toBeDisabled();
    expect(
      within(stickyBar).getByRole("button", {
        name: "Decline this document",
        hidden: true,
      }),
    ).toBeDisabled();
  });

  it("on 409 closes the dialog, re-fetches the page data and shows the message", async () => {
    const user = userEvent.setup();
    mockGetAcceptancePageData
      .mockResolvedValueOnce(mockPendingPageData)
      .mockResolvedValueOnce(mockDeclinedPageData);
    mockDeclineAcceptance.mockRejectedValue(new DeclineConflictError());

    render(<AcceptancePage token="test-token" />);

    const dialog = await openDeclineDialog(user);
    await user.click(
      within(dialog).getByRole("button", { name: "Decline document" }),
    );

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(
        "This request can no longer be declined.",
      );
    });
    await waitFor(() => {
      expect(mockGetAcceptancePageData).toHaveBeenCalledTimes(2);
    });
    expect(
      await screen.findByText(/You declined this document/),
    ).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Full name")).not.toBeInTheDocument();
    // Shown once, not duplicated
    expect(
      screen.getAllByText("This request can no longer be declined."),
    ).toHaveLength(1);
  });

  it("keeps the dialog open with an inline error on other failures", async () => {
    const user = userEvent.setup();
    mockGetAcceptancePageData.mockResolvedValue(mockPendingPageData);
    mockDeclineAcceptance.mockRejectedValue(
      new Error("Failed to decline. Please try again."),
    );

    render(<AcceptancePage token="test-token" />);

    const dialog = await openDeclineDialog(user);
    await user.click(
      within(dialog).getByRole("button", { name: "Decline document" }),
    );

    await waitFor(() => {
      expect(within(dialog).getByRole("alert")).toHaveTextContent(
        "Failed to decline. Please try again.",
      );
    });
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(
      within(dialog).getByRole("button", { name: "Decline document" }),
    ).toBeEnabled();
    expect(mockGetAcceptancePageData).toHaveBeenCalledTimes(1);
  });
});
