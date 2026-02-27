import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SendForAcceptanceDialog } from "@/components/acceptance/SendForAcceptanceDialog";

// Mock server-only (imported transitively via acceptance-actions -> api)
vi.mock("server-only", () => ({}));

const mockFetchPortalContacts = vi.fn();
const mockSendForAcceptance = vi.fn();

vi.mock("@/lib/actions/acceptance-actions", () => ({
  fetchPortalContacts: (...args: unknown[]) => mockFetchPortalContacts(...args),
  sendForAcceptance: (...args: unknown[]) => mockSendForAcceptance(...args),
}));

describe("SendForAcceptanceDialog", () => {
  const baseProps = {
    generatedDocumentId: "doc-1",
    customerId: "cust-1",
    documentName: "Engagement Letter",
    open: true,
    onOpenChange: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders dialog title with document name", async () => {
    mockFetchPortalContacts.mockResolvedValue([]);

    render(<SendForAcceptanceDialog {...baseProps} />);

    await waitFor(() => {
      expect(
        screen.getByText("Send for Acceptance: Engagement Letter"),
      ).toBeInTheDocument();
    });
  });

  it("loads and shows portal contacts in select dropdown", async () => {
    mockFetchPortalContacts.mockResolvedValue([
      { id: "contact-1", displayName: "Jane Smith", email: "jane@client.com" },
      { id: "contact-2", displayName: "Bob Jones", email: "bob@client.com" },
    ]);

    render(<SendForAcceptanceDialog {...baseProps} />);

    // Wait for contacts to load
    await waitFor(() => {
      expect(screen.getByText("Select a contact")).toBeInTheDocument();
    });

    // Open the select dropdown
    const user = userEvent.setup();
    await user.click(screen.getByRole("combobox"));

    // Contacts should be visible
    await waitFor(() => {
      expect(
        screen.getByText("Jane Smith (jane@client.com)"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("Bob Jones (bob@client.com)"),
      ).toBeInTheDocument();
    });
  });

  it('shows "no contacts" message when no contacts exist', async () => {
    mockFetchPortalContacts.mockResolvedValue([]);

    render(<SendForAcceptanceDialog {...baseProps} />);

    await waitFor(() => {
      expect(
        screen.getByText("No portal contacts configured for this customer."),
      ).toBeInTheDocument();
    });
  });

  it("calls sendForAcceptance and shows success message on submit", async () => {
    mockFetchPortalContacts.mockResolvedValue([
      { id: "contact-1", displayName: "Jane Smith", email: "jane@client.com" },
    ]);
    mockSendForAcceptance.mockResolvedValue({
      success: true,
      data: { id: "req-1", status: "SENT" },
    });

    render(<SendForAcceptanceDialog {...baseProps} />);

    const user = userEvent.setup();

    // Wait for contacts to load and select one
    await waitFor(() => {
      expect(screen.getByText("Select a contact")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("combobox"));
    await waitFor(() => {
      expect(
        screen.getByText("Jane Smith (jane@client.com)"),
      ).toBeInTheDocument();
    });
    await user.click(screen.getByText("Jane Smith (jane@client.com)"));

    // Click send
    await user.click(screen.getByRole("button", { name: "Send" }));

    await waitFor(() => {
      expect(mockSendForAcceptance).toHaveBeenCalledWith(
        "doc-1",
        "contact-1",
        undefined,
      );
    });

    await waitFor(() => {
      expect(screen.getByText("Acceptance request sent.")).toBeInTheDocument();
    });
  });

  it("shows error message when send fails", async () => {
    mockFetchPortalContacts.mockResolvedValue([
      { id: "contact-1", displayName: "Jane Smith", email: "jane@client.com" },
    ]);
    mockSendForAcceptance.mockResolvedValue({
      success: false,
      error: "Permission denied",
    });

    render(<SendForAcceptanceDialog {...baseProps} />);

    const user = userEvent.setup();

    // Wait for contacts to load and select one
    await waitFor(() => {
      expect(screen.getByText("Select a contact")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("combobox"));
    await waitFor(() => {
      expect(
        screen.getByText("Jane Smith (jane@client.com)"),
      ).toBeInTheDocument();
    });
    await user.click(screen.getByText("Jane Smith (jane@client.com)"));

    // Click send
    await user.click(screen.getByRole("button", { name: "Send" }));

    await waitFor(() => {
      expect(screen.getByText("Permission denied")).toBeInTheDocument();
    });
  });
});
