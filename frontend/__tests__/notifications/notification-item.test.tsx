import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NotificationItem } from "@/components/notifications/notification-item";
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

const mockMarkNotificationRead = vi.fn();

vi.mock("@/lib/actions/notifications", () => ({
  markNotificationRead: (...args: unknown[]) =>
    mockMarkNotificationRead(...args),
}));

function makeNotification(
  overrides: Partial<Notification> = {}
): Notification {
  return {
    id: "n1",
    type: "TASK_ASSIGNED",
    title: "Alice assigned you to Fix login bug",
    body: null,
    referenceEntityType: "TASK",
    referenceEntityId: "t1",
    referenceProjectId: "p1",
    isRead: false,
    createdAt: "2026-02-12T14:30:00Z",
    ...overrides,
  };
}

describe("NotificationItem", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockMarkNotificationRead.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders unread indicator for unread notification", () => {
    render(
      <NotificationItem
        notification={makeNotification({ isRead: false })}
        orgSlug="acme"
      />
    );

    expect(
      screen.getByText("Alice assigned you to Fix login bug")
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Unread")).toBeInTheDocument();
  });

  it("does not render unread indicator for read notification", () => {
    render(
      <NotificationItem
        notification={makeNotification({ isRead: true })}
        orgSlug="acme"
      />
    );

    expect(
      screen.getByText("Alice assigned you to Fix login bug")
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("Unread")).not.toBeInTheDocument();
  });

  it("calls markNotificationRead and navigates on click", async () => {
    const onRead = vi.fn();
    render(
      <NotificationItem
        notification={makeNotification()}
        orgSlug="acme"
        onRead={onRead}
      />
    );

    const user = userEvent.setup();
    const button = screen.getByRole("button");
    await user.click(button);

    expect(mockMarkNotificationRead).toHaveBeenCalledWith("n1");
    expect(mockPush).toHaveBeenCalledWith("/org/acme/projects/p1?tab=tasks");
  });
});
