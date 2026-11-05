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
  markNotificationRead: (...args: unknown[]) => mockMarkNotificationRead(...args),
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

describe("NotificationItem", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockMarkNotificationRead.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders unread indicator for unread notification", () => {
    render(<NotificationItem notification={makeNotification({ isRead: false })} orgSlug="acme" />);

    expect(screen.getByText("Alice assigned you to Fix login bug")).toBeInTheDocument();
    expect(screen.getByLabelText("Unread")).toBeInTheDocument();
  });

  it("does not render unread indicator for read notification", () => {
    render(<NotificationItem notification={makeNotification({ isRead: true })} orgSlug="acme" />);

    expect(screen.getByText("Alice assigned you to Fix login bug")).toBeInTheDocument();
    expect(screen.queryByLabelText("Unread")).not.toBeInTheDocument();
  });

  it("calls markNotificationRead and navigates on click", async () => {
    const onRead = vi.fn();
    render(<NotificationItem notification={makeNotification()} orgSlug="acme" onRead={onRead} />);

    const user = userEvent.setup();
    const button = screen.getByRole("button");
    await user.click(button);

    expect(mockMarkNotificationRead).toHaveBeenCalledWith("n1");
    expect(mockPush).toHaveBeenCalledWith("/org/acme/matters/p1?tab=tasks");
  });

  describe("comment deep links (G88-M6)", () => {
    async function clickItem(notification: Notification, onNavigate?: () => void) {
      render(
        <NotificationItem notification={notification} orgSlug="acme" onNavigate={onNavigate} />
      );
      const user = userEvent.setup();
      await user.click(screen.getByRole("button"));
    }

    it("TASK comment → the task sheet on the comment", async () => {
      await clickItem(makeNotification({ type: "COMMENT_ADDED", referenceCommentId: "c1" }));

      expect(mockPush).toHaveBeenCalledWith(
        "/org/acme/matters/p1?tab=tasks&taskId=t1&commentId=c1"
      );
    });

    it("DOCUMENT comment → the documents tab expanded on the document", async () => {
      await clickItem(
        makeNotification({
          type: "COMMENT_ADDED",
          referenceEntityType: "DOCUMENT",
          referenceEntityId: "d1",
          referenceCommentId: "c1",
        })
      );

      expect(mockPush).toHaveBeenCalledWith(
        "/org/acme/matters/p1?tab=documents&documentId=d1&commentId=c1"
      );
    });

    it("PROJECT comment → the matter Comments tab on the comment", async () => {
      await clickItem(
        makeNotification({
          type: "COMMENT_ADDED",
          referenceEntityType: "PROJECT",
          referenceEntityId: "p1",
          referenceCommentId: "c1",
        })
      );

      expect(mockPush).toHaveBeenCalledWith("/org/acme/matters/p1?tab=comments&commentId=c1");
    });

    it("COMMENT_ADDED without a comment id (pre-V11 row) keeps the entity-level link", async () => {
      await clickItem(makeNotification({ type: "COMMENT_ADDED", referenceCommentId: null }));

      expect(mockPush).toHaveBeenCalledWith("/org/acme/matters/p1?tab=tasks");
    });

    it("PROJECT notification without a comment id links to the matter itself", async () => {
      await clickItem(
        makeNotification({
          type: "MATTER_CLOSED",
          referenceEntityType: "PROJECT",
          referenceEntityId: "p1",
          referenceCommentId: null,
        })
      );

      expect(mockPush).toHaveBeenCalledWith("/org/acme/matters/p1");
    });

    it("calls onNavigate after navigating (closes the bell popover)", async () => {
      const onNavigate = vi.fn();
      await clickItem(
        makeNotification({ type: "COMMENT_ADDED", referenceCommentId: "c1" }),
        onNavigate
      );

      expect(mockPush).toHaveBeenCalledTimes(1);
      expect(onNavigate).toHaveBeenCalledTimes(1);
    });
  });
});
