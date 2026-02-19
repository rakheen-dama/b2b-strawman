import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ActivityItem } from "@/components/activity/activity-item";
import { ActivityFeedClient } from "@/components/activity/activity-feed-client";
import type { ActivityItem as ActivityItemType } from "@/lib/actions/activity";

const mockLoadMoreActivity = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/projects/[id]/activity-actions",
  () => ({
    loadMoreActivity: (...args: unknown[]) => mockLoadMoreActivity(...args),
  })
);

function makeActivityItem(
  overrides: Partial<ActivityItemType> = {}
): ActivityItemType {
  return {
    id: "a1",
    message: "Alice created task Fix login bug",
    actorName: "Alice Johnson",
    actorAvatarUrl: null,
    entityType: "task",
    entityId: "t1",
    entityName: "Fix login bug",
    eventType: "task.created",
    occurredAt: "2026-02-12T14:30:00Z",
    ...overrides,
  };
}

describe("ActivityItem", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders activity item with avatar initials, message, and timestamp", () => {
    render(<ActivityItem item={makeActivityItem()} />);

    expect(screen.getByText("AJ")).toBeInTheDocument();
    expect(
      screen.getByText("Alice created task Fix login bug")
    ).toBeInTheDocument();
  });

  it("renders avatar image when actorAvatarUrl is provided", () => {
    render(
      <ActivityItem
        item={makeActivityItem({
          actorAvatarUrl: "https://example.com/avatar.jpg",
        })}
      />
    );

    const img = screen.getByAltText("Alice Johnson");
    expect(img).toBeInTheDocument();
    expect(img).toHaveAttribute("src", "https://example.com/avatar.jpg");
  });
});

describe("ActivityFeedClient", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("shows empty state when no activity items", () => {
    render(
      <ActivityFeedClient
        projectId="p1"
        initialItems={[]}
        initialTotalPages={0}
      />
    );

    expect(screen.getByText("No activity yet")).toBeInTheDocument();
  });

  it("renders activity items and filter chips", () => {
    const items = [
      makeActivityItem({ id: "a1", message: "Alice created task Fix bug" }),
      makeActivityItem({
        id: "a2",
        message: "Bob uploaded document Spec.pdf",
        entityType: "document",
      }),
    ];

    render(
      <ActivityFeedClient
        projectId="p1"
        initialItems={items}
        initialTotalPages={1}
      />
    );

    expect(
      screen.getByText("Alice created task Fix bug")
    ).toBeInTheDocument();
    expect(
      screen.getByText("Bob uploaded document Spec.pdf")
    ).toBeInTheDocument();

    // Filter chips are rendered
    expect(screen.getByText("All")).toBeInTheDocument();
    expect(screen.getByText("Tasks")).toBeInTheDocument();
    expect(screen.getByText("Documents")).toBeInTheDocument();
    expect(screen.getByText("Comments")).toBeInTheDocument();
    expect(screen.getByText("Members")).toBeInTheDocument();
    expect(screen.getByText("Time")).toBeInTheDocument();
  });

  it("filters activity by entity type when filter chip is clicked", async () => {
    const user = userEvent.setup();
    const taskItem = makeActivityItem({
      id: "a1",
      message: "Alice created task Fix bug",
      entityType: "task",
    });

    mockLoadMoreActivity.mockResolvedValue({
      content: [taskItem],
      page: {
        totalElements: 1,
        totalPages: 1,
        size: 20,
        number: 0,
      },
    });

    render(
      <ActivityFeedClient
        projectId="p1"
        initialItems={[
          taskItem,
          makeActivityItem({
            id: "a2",
            message: "Bob added comment",
            entityType: "comment",
          }),
        ]}
        initialTotalPages={1}
      />
    );

    await user.click(screen.getByText("Tasks"));

    await waitFor(() => {
      expect(mockLoadMoreActivity).toHaveBeenCalledWith("p1", "TASK", 0);
    });
  });
});
