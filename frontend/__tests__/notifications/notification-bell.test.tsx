import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NotificationBell } from "@/components/notifications/notification-bell";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

const mockFetchUnreadCount = vi.fn();
const mockFetchNotifications = vi.fn();
const mockMarkAllNotificationsRead = vi.fn();

vi.mock("@/lib/actions/notifications", () => ({
  fetchUnreadCount: (...args: unknown[]) => mockFetchUnreadCount(...args),
  fetchNotifications: (...args: unknown[]) => mockFetchNotifications(...args),
  markNotificationRead: vi.fn().mockResolvedValue({ success: true }),
  markAllNotificationsRead: (...args: unknown[]) =>
    mockMarkAllNotificationsRead(...args),
  dismissNotification: vi.fn().mockResolvedValue({ success: true }),
}));

describe("NotificationBell", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchUnreadCount.mockResolvedValue({ count: 0 });
    mockFetchNotifications.mockResolvedValue({ content: [], page: { totalElements: 0, totalPages: 0, size: 10, number: 0 } });
    mockMarkAllNotificationsRead.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders bell icon without badge when unread count is 0", async () => {
    mockFetchUnreadCount.mockResolvedValue({ count: 0 });

    render(<NotificationBell orgSlug="acme" />);

    const button = screen.getByRole("button", { name: /notifications/i });
    expect(button).toBeInTheDocument();

    // Wait for the polling to settle
    await waitFor(() => {
      expect(mockFetchUnreadCount).toHaveBeenCalled();
    });

    // No badge should be visible
    expect(screen.queryByText("0")).not.toBeInTheDocument();
  });

  it("renders badge with count when unread count is greater than 0", async () => {
    mockFetchUnreadCount.mockResolvedValue({ count: 5 });

    render(<NotificationBell orgSlug="acme" />);

    await waitFor(() => {
      expect(screen.getByText("5")).toBeInTheDocument();
    });
  });

  it("renders 99+ when unread count exceeds 99", async () => {
    mockFetchUnreadCount.mockResolvedValue({ count: 150 });

    render(<NotificationBell orgSlug="acme" />);

    await waitFor(() => {
      expect(screen.getByText("99+")).toBeInTheDocument();
    });
  });

  it("opens dropdown when bell is clicked", async () => {
    mockFetchUnreadCount.mockResolvedValue({ count: 3 });

    render(<NotificationBell orgSlug="acme" />);

    await waitFor(() => {
      expect(screen.getByText("3")).toBeInTheDocument();
    });

    const user = userEvent.setup();
    const button = screen.getByRole("button", { name: /notifications/i });
    await user.click(button);

    // Dropdown should be visible with "Notifications" header
    await waitFor(() => {
      expect(screen.getByText("Notifications")).toBeInTheDocument();
    });
  });
});
