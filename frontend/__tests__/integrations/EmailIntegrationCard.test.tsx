import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EmailIntegrationCard } from "@/components/integrations/EmailIntegrationCard";

const mockGetEmailStats = vi.fn();
const mockSendTestEmail = vi.fn();

vi.mock("@/lib/actions/email", () => ({
  getEmailStats: (...args: unknown[]) => mockGetEmailStats(...args),
  sendTestEmail: (...args: unknown[]) => mockSendTestEmail(...args),
}));

describe("EmailIntegrationCard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("shows Active badge and Email Delivery title", async () => {
    mockGetEmailStats.mockResolvedValue({
      success: true,
      data: {
        sent24h: 42,
        bounced7d: 1,
        failed7d: 0,
        rateLimited7d: 0,
        currentHourUsage: 5,
        hourlyLimit: 100,
        providerSlug: "platform",
      },
    });

    render(<EmailIntegrationCard />);

    expect(screen.getByText("Email Delivery")).toBeInTheDocument();
    // Two "Active" badges: one in header, one in platform email info row
    const badges = screen.getAllByText("Active");
    expect(badges).toHaveLength(2);
  });

  it("shows stats when loaded", async () => {
    mockGetEmailStats.mockResolvedValue({
      success: true,
      data: {
        sent24h: 42,
        bounced7d: 3,
        failed7d: 0,
        rateLimited7d: 0,
        currentHourUsage: 15,
        hourlyLimit: 200,
        providerSlug: "sendgrid",
      },
    });

    render(<EmailIntegrationCard />);

    await waitFor(() => {
      expect(screen.getByText("42")).toBeInTheDocument();
    });
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("15/200")).toBeInTheDocument();
    expect(screen.getByText("Sent (24h)")).toBeInTheDocument();
    expect(screen.getByText("Bounced (7d)")).toBeInTheDocument();
    expect(screen.getByText("Rate Limit")).toBeInTheDocument();
  });

  it("shows provider info from stats", async () => {
    mockGetEmailStats.mockResolvedValue({
      success: true,
      data: {
        sent24h: 10,
        bounced7d: 0,
        failed7d: 0,
        rateLimited7d: 0,
        currentHourUsage: 2,
        hourlyLimit: 100,
        providerSlug: "sendgrid",
      },
    });

    render(<EmailIntegrationCard />);

    await waitFor(() => {
      expect(screen.getByText("Provider: sendgrid")).toBeInTheDocument();
    });
  });

  it("Send Test Email button calls sendTestEmail action", async () => {
    mockGetEmailStats.mockResolvedValue({
      success: true,
      data: {
        sent24h: 0,
        bounced7d: 0,
        failed7d: 0,
        rateLimited7d: 0,
        currentHourUsage: 0,
        hourlyLimit: 100,
        providerSlug: null,
      },
    });
    mockSendTestEmail.mockResolvedValue({ success: true });

    render(<EmailIntegrationCard />);

    const user = userEvent.setup();

    // Wait for stats to load
    await waitFor(() => {
      expect(screen.getByText("0/100")).toBeInTheDocument();
    });

    const testButton = screen.getByRole("button", {
      name: /send test email/i,
    });
    await user.click(testButton);

    await waitFor(() => {
      expect(mockSendTestEmail).toHaveBeenCalledTimes(1);
    });

    await waitFor(() => {
      expect(
        screen.getByText("Test email sent successfully.")
      ).toBeInTheDocument();
    });
  });
});
