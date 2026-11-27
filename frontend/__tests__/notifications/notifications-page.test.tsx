import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NotificationsPageClient } from "@/components/notifications/notifications-page-client";
import type { Notification } from "@/lib/actions/notifications";

const mockPush = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
    replace: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

const mockFetchNotifications = vi.fn();
const mockMarkAllNotificationsRead = vi.fn();

vi.mock("@/lib/actions/notifications", () => ({
  fetchNotifications: (...args: unknown[]) => mockFetchNotifications(...args),
  markAllNotificationsRead: (...args: unknown[]) => mockMarkAllNotificationsRead(...args),
  markNotificationRead: vi.fn().mockResolvedValue({ success: true }),
}));

function makeNotification(overrides: Partial<Notification> = {}): Notification {
  return {
    id: "n1",
    type: "TASK_ASSIGNED",
    title: "Alice assigned you to Fix login bug",
    body: null,
    referenceEntityType: "TASK",
    referenceEntityId: "t1",
    referenceMatterId: "p1",
    referenceCommentId: null,
    isRead: false,
    createdAt: "2026-02-12T14:30:00Z",
    ...overrides,
  };
}

describe("NotificationsPageClient", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchNotifications.mockResolvedValue({
      content: [],
      page: { totalElements: 0, totalPages: 0, size: 10, number: 0 },
    });
    mockMarkAllNotificationsRead.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders notification list with items", () => {
    const notifications = [
      makeNotification({ id: "n1", title: "Task assigned to you" }),
      makeNotification({
        id: "n2",
        title: "Comment on your task",
        type: "COMMENT_ADDED",
        isRead: true,
      }),
    ];

    render(
      <NotificationsPageClient
        initialNotifications={notifications}
        initialTotalPages={1}
        orgSlug="acme"
      />
    );

    expect(screen.getByText("Task assigned to you")).toBeInTheDocument();
    expect(screen.getByText("Comment on your task")).toBeInTheDocument();
  });

  it("shows empty state when no notifications", () => {
    render(
      <NotificationsPageClient initialNotifications={[]} initialTotalPages={0} orgSlug="acme" />
    );

    expect(screen.getByText("You're all caught up")).toBeInTheDocument();
  });

  it("renders the whole-page empty state as the default variant", () => {
    const { container } = render(
      <NotificationsPageClient initialNotifications={[]} initialTotalPages={0} orgSlug="acme" />
    );

    expect(container.querySelector('[data-slot="empty-state"]')).toHaveAttribute(
      "data-variant",
      "default"
    );
    // The empty state's title is not a level-1 heading (the page's PageHeader owns the h1).
    expect(screen.queryByRole("heading", { level: 1 })).toBeNull();
  });

  it("renders the list on a kit Card with divided rows", () => {
    const notifications = [
      makeNotification({ id: "n1", title: "Card row one" }),
      makeNotification({ id: "n2", title: "Card row two", isRead: true }),
    ];
    const { container } = render(
      <NotificationsPageClient
        initialNotifications={notifications}
        initialTotalPages={1}
        orgSlug="acme"
      />
    );

    const card = container.querySelector('[data-slot="card"]');
    expect(card).not.toBeNull();
    expect(card).toHaveTextContent("Card row one");
    expect(card).toHaveTextContent("Card row two");
    expect(card!.querySelector(".divide-y")).not.toBeNull();
    // The Card adds no button: one per notification row.
    expect(card!.querySelectorAll("button")).toHaveLength(2);
  });

  it("exposes the selected filter with aria-pressed and keeps the all / unread toggle", async () => {
    mockFetchNotifications.mockResolvedValue({
      content: [makeNotification({ id: "n1", title: "Still unread" })],
      page: { totalElements: 1, totalPages: 1, size: 10, number: 0 },
    });
    render(
      <NotificationsPageClient
        initialNotifications={[makeNotification({ id: "n1", title: "Still unread" })]}
        initialTotalPages={1}
        orgSlug="acme"
      />
    );

    const all = screen.getByRole("button", { name: "All" });
    const unread = screen.getByRole("button", { name: "Unread" });
    expect(all).toHaveAttribute("aria-pressed", "true");
    expect(unread).toHaveAttribute("aria-pressed", "false");

    const user = userEvent.setup();
    await user.click(unread);
    await waitFor(() => expect(unread).toHaveAttribute("aria-pressed", "true"));
    expect(all).toHaveAttribute("aria-pressed", "false");
    expect(mockFetchNotifications).toHaveBeenCalledWith(true, 0);

    await user.click(all);
    await waitFor(() => expect(all).toHaveAttribute("aria-pressed", "true"));
    expect(mockFetchNotifications).toHaveBeenLastCalledWith(false, 0);
  });

  it("filter toggle switches between all and unread", async () => {
    const notifications = [
      makeNotification({ id: "n1", title: "Unread notification", isRead: false }),
      makeNotification({ id: "n2", title: "Read notification", isRead: true }),
    ];

    const unreadOnly = [
      makeNotification({ id: "n1", title: "Unread notification", isRead: false }),
    ];

    mockFetchNotifications.mockResolvedValueOnce({
      content: unreadOnly,
      page: { totalElements: 1, totalPages: 1, size: 10, number: 0 },
    });

    render(
      <NotificationsPageClient
        initialNotifications={notifications}
        initialTotalPages={1}
        orgSlug="acme"
      />
    );

    // Both initially visible
    expect(screen.getByText("Unread notification")).toBeInTheDocument();
    expect(screen.getByText("Read notification")).toBeInTheDocument();

    // Click "Unread" filter
    const user = userEvent.setup();
    await user.click(screen.getByText("Unread"));

    await waitFor(() => {
      expect(mockFetchNotifications).toHaveBeenCalledWith(true, 0);
    });
  });

  it("mark all as read button calls server action", async () => {
    const notifications = [makeNotification({ id: "n1", isRead: false })];

    render(
      <NotificationsPageClient
        initialNotifications={notifications}
        initialTotalPages={1}
        orgSlug="acme"
      />
    );

    const user = userEvent.setup();
    const markAllButton = screen.getByRole("button", {
      name: /mark all as read/i,
    });
    await user.click(markAllButton);

    await waitFor(() => {
      expect(mockMarkAllNotificationsRead).toHaveBeenCalled();
    });
  });
});
