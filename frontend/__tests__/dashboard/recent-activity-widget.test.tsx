import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { RecentActivityWidget } from "@/components/dashboard/recent-activity-widget";
import type { CrossProjectActivityItem } from "@/lib/dashboard-types";

describe("RecentActivityWidget", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders null state message when items is null", () => {
    render(<RecentActivityWidget items={null} orgSlug="acme" />);
    expect(
      screen.getByText("Unable to load activity. Please try again.")
    ).toBeInTheDocument();
  });

  it("renders empty state message when items is empty", () => {
    render(<RecentActivityWidget items={[]} orgSlug="acme" />);
    expect(
      screen.getByText("No recent activity across your projects.")
    ).toBeInTheDocument();
  });

  it("renders activity items with actor names", () => {
    const items: CrossProjectActivityItem[] = [
      {
        eventId: "evt-1",
        eventType: "task.created",
        description: "Created task: Fix login bug",
        actorName: "Alice Smith",
        projectId: "proj-1",
        projectName: "Website Redesign",
        occurredAt: new Date().toISOString(),
      },
    ];

    render(<RecentActivityWidget items={items} orgSlug="acme" />);
    expect(screen.getByText("AS")).toBeInTheDocument(); // initials for "Alice Smith"
    expect(screen.getByText("Created task: Fix login bug")).toBeInTheDocument();
    expect(screen.getByText("Website Redesign")).toBeInTheDocument();
  });

  /**
   * Regression test for BUG-010: When actorName is null (e.g., portal user activity before
   * the actor_name denormalization fix), the widget should render "?" for the avatar initials
   * instead of crashing with a TypeError on null.split().
   */
  it("handles null actorName without crashing (BUG-010 regression)", () => {
    const items: CrossProjectActivityItem[] = [
      {
        eventId: "evt-2",
        eventType: "comment.created",
        description: "Added a comment on task: Review design",
        actorName: null as unknown as string, // simulate null from backend
        projectId: "proj-1",
        projectName: "Website Redesign",
        occurredAt: new Date().toISOString(),
      },
    ];

    // Should not throw — the getInitials function has a null guard
    render(<RecentActivityWidget items={items} orgSlug="acme" />);
    expect(screen.getByText("?")).toBeInTheDocument(); // fallback for null name
    expect(
      screen.getByText("Added a comment on task: Review design")
    ).toBeInTheDocument();
  });

  it("handles undefined actorName without crashing (BUG-010 regression)", () => {
    const items: CrossProjectActivityItem[] = [
      {
        eventId: "evt-3",
        eventType: "task.updated",
        description: "Updated task status",
        actorName: undefined as unknown as string, // simulate undefined
        projectId: "proj-2",
        projectName: "Mobile App",
        occurredAt: new Date().toISOString(),
      },
    ];

    render(<RecentActivityWidget items={items} orgSlug="acme" />);
    expect(screen.getByText("?")).toBeInTheDocument();
  });

  it("handles empty string actorName without crashing", () => {
    const items: CrossProjectActivityItem[] = [
      {
        eventId: "evt-4",
        eventType: "document.created",
        description: "Uploaded a document",
        actorName: "",
        projectId: "proj-1",
        projectName: "Website Redesign",
        occurredAt: new Date().toISOString(),
      },
    ];

    render(<RecentActivityWidget items={items} orgSlug="acme" />);
    expect(screen.getByText("?")).toBeInTheDocument();
  });
});
