import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Phase 91 668A: the Timesheet and Notifications server pages render the kit PageHeader, so each
// has exactly one level-1 heading — empty data included (sidebar-navigation.spec.ts:38-39,
// notification-bell.spec.ts:112,138). The grid is stubbed: the slice leaves it untouched.

const { mockApiGet, mockFetchNotifications, gridProps } = vi.hoisted(() => ({
  mockApiGet: vi.fn(),
  mockFetchNotifications: vi.fn(),
  gridProps: { last: null as null | { tasks: unknown[]; allTasks: unknown[] } },
}));

vi.mock("@/lib/api", () => ({ api: { get: mockApiGet } }));

vi.mock("@/components/time-tracking/weekly-time-grid", () => ({
  WeeklyTimeGrid: (props: { tasks: unknown[]; allTasks: unknown[] }) => {
    gridProps.last = props;
    return <div data-testid="weekly-time-grid-stub" />;
  },
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

vi.mock("@/lib/actions/notifications", () => ({
  fetchNotifications: (...args: unknown[]) => mockFetchNotifications(...args),
  markAllNotificationsRead: vi.fn(),
  markNotificationRead: vi.fn(),
}));

import TimesheetPage from "@/app/(app)/org/[slug]/my-work/timesheet/page";
import NotificationsPage from "@/app/(app)/org/[slug]/notifications/page";

afterEach(() => cleanup());

beforeEach(() => {
  mockApiGet.mockReset();
  mockFetchNotifications.mockReset();
  gridProps.last = null;
});

describe("Timesheet page header", () => {
  it("renders one h1 'Timesheet' with its subtitle via the kit PageHeader", async () => {
    mockApiGet.mockResolvedValueOnce({ assigned: [], unassigned: [] }).mockResolvedValueOnce([]);

    const { container } = render(
      await TimesheetPage({
        params: Promise.resolve({ slug: "acme" }),
        searchParams: Promise.resolve({ week: "2026-11-23" }),
      })
    );

    expect(container.querySelector('[data-slot="page-header"]')).toBeInTheDocument();
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.getByRole("heading", { level: 1, name: "Timesheet" })).toBeInTheDocument();
    expect(screen.getByText("Log your weekly hours across all tasks.")).toBeInTheDocument();
    expect(screen.getByTestId("weekly-time-grid-stub")).toBeInTheDocument();
  });

  it("still builds the grid rows from assigned tasks only (K-88 held)", async () => {
    const assigned = [
      { id: "a1", matterId: "m1", matterName: "Khumalo", title: "Heads of argument" },
    ];
    mockApiGet.mockResolvedValueOnce({ assigned, unassigned: [] }).mockResolvedValueOnce([
      {
        id: "e1",
        taskId: "unassigned-task",
        taskTitle: "Draft transfer documents",
        matterId: "m2",
        matterName: "Ndlovu",
        date: "2026-11-27",
        durationMinutes: 240,
      },
    ]);

    render(
      await TimesheetPage({
        params: Promise.resolve({ slug: "acme" }),
        searchParams: Promise.resolve({ week: "2026-11-23" }),
      })
    );

    const row = { id: "a1", projectId: "m1", projectName: "Khumalo", title: "Heads of argument" };
    expect(gridProps.last?.tasks).toEqual([row]);
    expect(gridProps.last?.allTasks).toEqual([row]);
  });
});

describe("Notifications page header", () => {
  it("renders one h1 'Notifications' over the empty state", async () => {
    mockFetchNotifications.mockResolvedValue({
      content: [],
      page: { totalElements: 0, totalPages: 0, size: 10, number: 0 },
    });

    const { container } = render(
      await NotificationsPage({ params: Promise.resolve({ slug: "acme" }) })
    );

    expect(container.querySelector('[data-slot="page-header"]')).toBeInTheDocument();
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.getByRole("heading", { level: 1, name: "Notifications" })).toBeInTheDocument();
    expect(screen.getByText("You're all caught up")).toBeInTheDocument();
    expect(container.querySelector('[data-slot="empty-state"]')).toHaveAttribute(
      "data-variant",
      "default"
    );
  });
});
