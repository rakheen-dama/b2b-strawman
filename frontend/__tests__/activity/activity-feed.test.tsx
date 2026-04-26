import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ActivityItem } from "@/components/activity/activity-item";
import { ActivityFeedClient } from "@/components/activity/activity-feed-client";
import type { ActivityItem as ActivityItemType } from "@/lib/actions/activity";

const mockLoadMoreActivity = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/activity-actions", () => ({
  loadMoreActivity: (...args: unknown[]) => mockLoadMoreActivity(...args),
}));

function makeActivityItem(overrides: Partial<ActivityItemType> = {}): ActivityItemType {
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
    expect(screen.getByText("Alice created task Fix login bug")).toBeInTheDocument();
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
    render(<ActivityFeedClient projectId="p1" initialItems={[]} initialTotalPages={0} />);

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

    render(<ActivityFeedClient projectId="p1" initialItems={items} initialTotalPages={1} />);

    expect(screen.getByText("Alice created task Fix bug")).toBeInTheDocument();
    expect(screen.getByText("Bob uploaded document Spec.pdf")).toBeInTheDocument();

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

  it("populates the actor dropdown with distinct actors derived from events", async () => {
    const user = userEvent.setup();
    const items = [
      makeActivityItem({ id: "a1", actorName: "Alice Johnson" }),
      makeActivityItem({ id: "a2", actorName: "Bob Smith" }),
      // Duplicate of Alice — should not appear twice in the dropdown.
      makeActivityItem({ id: "a3", actorName: "Alice Johnson" }),
      makeActivityItem({ id: "a4", actorName: "Carol Lee" }),
    ];

    render(<ActivityFeedClient projectId="p1" initialItems={items} initialTotalPages={1} />);

    const trigger = screen.getByRole("combobox", { name: /filter by actor/i });
    await user.click(trigger);

    await waitFor(() => {
      expect(screen.getByRole("option", { name: "All actors" })).toBeInTheDocument();
    });
    expect(screen.getByRole("option", { name: "Alice Johnson" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Bob Smith" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Carol Lee" })).toBeInTheDocument();

    // No duplicate Alice entry.
    expect(screen.getAllByRole("option", { name: "Alice Johnson" })).toHaveLength(1);
  });

  it("hides events from other actors when an actor is selected", async () => {
    const user = userEvent.setup();
    const items = [
      makeActivityItem({
        id: "a1",
        message: "Alice created task A",
        actorName: "Alice Johnson",
      }),
      makeActivityItem({
        id: "a2",
        message: "Bob uploaded doc B",
        actorName: "Bob Smith",
      }),
    ];

    render(<ActivityFeedClient projectId="p1" initialItems={items} initialTotalPages={1} />);

    // Both visible initially
    expect(screen.getByText("Alice created task A")).toBeInTheDocument();
    expect(screen.getByText("Bob uploaded doc B")).toBeInTheDocument();

    const trigger = screen.getByRole("combobox", { name: /filter by actor/i });
    await user.click(trigger);
    await user.click(await screen.findByRole("option", { name: "Alice Johnson" }));

    // Only Alice's event remains visible
    await waitFor(() => {
      expect(screen.queryByText("Bob uploaded doc B")).not.toBeInTheDocument();
    });
    expect(screen.getByText("Alice created task A")).toBeInTheDocument();
  });

  it("re-derives the actor list when the events array changes", async () => {
    const user = userEvent.setup();
    const initial = [
      makeActivityItem({ id: "a1", actorName: "Alice Johnson", entityType: "task" }),
      makeActivityItem({ id: "a2", actorName: "Bob Smith", entityType: "comment" }),
    ];

    // When the user clicks a chip (e.g. "Tasks"), the component re-fetches and
    // calls setItems with a fresh array — triggering re-derivation of actors.
    mockLoadMoreActivity.mockResolvedValue({
      content: [
        makeActivityItem({ id: "a3", actorName: "Dana Pritchard", entityType: "task" }),
        makeActivityItem({ id: "a4", actorName: "Eli Vance", entityType: "task" }),
      ],
      page: { totalElements: 2, totalPages: 1, size: 20, number: 0 },
    });

    render(<ActivityFeedClient projectId="p1" initialItems={initial} initialTotalPages={1} />);

    // Confirm initial actor set in dropdown
    const trigger = screen.getByRole("combobox", { name: /filter by actor/i });
    await user.click(trigger);
    await waitFor(() => {
      expect(screen.getByRole("option", { name: "Alice Johnson" })).toBeInTheDocument();
    });
    expect(screen.getByRole("option", { name: "Bob Smith" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Dana Pritchard" })).not.toBeInTheDocument();

    // Close the dropdown then change the underlying events by clicking an
    // entity-type chip (which calls loadMoreActivity → setItems).
    await user.keyboard("{Escape}");
    await user.click(screen.getByText("Tasks"));

    await waitFor(() => {
      expect(mockLoadMoreActivity).toHaveBeenCalledWith("p1", "TASK", 0);
    });

    // Reopen dropdown — actor list should now reflect the new events array.
    await user.click(screen.getByRole("combobox", { name: /filter by actor/i }));
    await waitFor(() => {
      expect(screen.getByRole("option", { name: "Dana Pritchard" })).toBeInTheDocument();
    });
    expect(screen.getByRole("option", { name: "Eli Vance" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Alice Johnson" })).not.toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Bob Smith" })).not.toBeInTheDocument();
  });
});
