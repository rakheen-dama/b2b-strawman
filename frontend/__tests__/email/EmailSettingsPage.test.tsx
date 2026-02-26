import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DeliveryLogTable } from "@/components/email/DeliveryLogTable";
import { EmailSettingsContent } from "@/components/email/EmailSettingsContent";

const mockGetEmailStats = vi.fn();
const mockGetDeliveryLog = vi.fn();

vi.mock("@/lib/actions/email", () => ({
  getEmailStats: (...args: unknown[]) => mockGetEmailStats(...args),
  getDeliveryLog: (...args: unknown[]) => mockGetDeliveryLog(...args),
}));

const sampleLogEntry = {
  id: "log-1",
  recipientEmail: "alice@example.com",
  templateName: "welcome",
  referenceType: "CUSTOMER",
  referenceId: "cust-1",
  status: "DELIVERED" as const,
  providerMessageId: "msg-abc123",
  providerSlug: "platform",
  errorMessage: null,
  createdAt: "2026-02-25T10:00:00Z",
  updatedAt: "2026-02-25T10:00:00Z",
};

const sampleStats = {
  sent24h: 42,
  bounced7d: 3,
  failed7d: 1,
  rateLimited7d: 2,
  currentHourUsage: 15,
  hourlyLimit: 200,
  providerSlug: "platform",
};

describe("DeliveryLogTable", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders table with delivery log entries", async () => {
    mockGetDeliveryLog.mockResolvedValue({
      success: true,
      data: {
        content: [sampleLogEntry],
        page: { totalElements: 1, totalPages: 1, size: 20, number: 0 },
      },
    });

    render(<DeliveryLogTable />);

    await waitFor(() => {
      expect(screen.getByText("alice@example.com")).toBeInTheDocument();
    });
    expect(screen.getByText("welcome")).toBeInTheDocument();
    expect(screen.getByText("DELIVERED")).toBeInTheDocument();
    expect(screen.getByText("msg-abc123")).toBeInTheDocument();
  });

  it("shows empty state when no entries", async () => {
    mockGetDeliveryLog.mockResolvedValue({
      success: true,
      data: {
        content: [],
        page: { totalElements: 0, totalPages: 0, size: 20, number: 0 },
      },
    });

    render(<DeliveryLogTable />);

    await waitFor(() => {
      expect(
        screen.getByText("No delivery log entries found.")
      ).toBeInTheDocument();
    });
  });

  it("status filter triggers re-fetch with status parameter", async () => {
    mockGetDeliveryLog.mockResolvedValue({
      success: true,
      data: {
        content: [],
        page: { totalElements: 0, totalPages: 0, size: 20, number: 0 },
      },
    });

    render(<DeliveryLogTable />);

    // Wait for initial load
    await waitFor(() => {
      expect(mockGetDeliveryLog).toHaveBeenCalledTimes(1);
    });

    // Initial call should not have a status filter
    expect(mockGetDeliveryLog).toHaveBeenCalledWith(
      expect.objectContaining({ status: undefined })
    );

    const user = userEvent.setup();

    // Open the status dropdown and select "Failed"
    const trigger = screen.getByRole("combobox");
    await user.click(trigger);
    const failedOption = screen.getByRole("option", { name: "Failed" });
    await user.click(failedOption);

    await waitFor(() => {
      expect(mockGetDeliveryLog).toHaveBeenCalledWith(
        expect.objectContaining({ status: "FAILED" })
      );
    });
  });
});

describe("EmailSettingsContent", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("shows delivery stats on the overview tab", async () => {
    mockGetEmailStats.mockResolvedValue({
      success: true,
      data: sampleStats,
    });
    mockGetDeliveryLog.mockResolvedValue({
      success: true,
      data: {
        content: [],
        page: { totalElements: 0, totalPages: 0, size: 20, number: 0 },
      },
    });

    render(<EmailSettingsContent />);

    await waitFor(() => {
      expect(screen.getByText("42")).toBeInTheDocument();
    });
    expect(screen.getByText("Sent (24h)")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("Bounced (7d)")).toBeInTheDocument();
    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByText("Failed (7d)")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText("Rate Limited (7d)")).toBeInTheDocument();
    expect(screen.getByText("15/200")).toBeInTheDocument();
    expect(screen.getByText("Current Hour Usage")).toBeInTheDocument();
    expect(screen.getByText("platform")).toBeInTheDocument();
    expect(screen.getByText("Provider")).toBeInTheDocument();
  });

  it("shows rate limit as usage/limit format", async () => {
    mockGetEmailStats.mockResolvedValue({
      success: true,
      data: {
        ...sampleStats,
        currentHourUsage: 99,
        hourlyLimit: 100,
      },
    });
    mockGetDeliveryLog.mockResolvedValue({
      success: true,
      data: {
        content: [],
        page: { totalElements: 0, totalPages: 0, size: 20, number: 0 },
      },
    });

    render(<EmailSettingsContent />);

    await waitFor(() => {
      expect(screen.getByText("99/100")).toBeInTheDocument();
    });
  });
});
