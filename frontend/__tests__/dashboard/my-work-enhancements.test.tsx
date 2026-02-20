import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, fireEvent } from "@testing-library/react";
import { UrgencyTaskList } from "@/components/my-work/urgency-task-list";
import { AvailableTaskList } from "@/components/my-work/available-task-list";
import type { MyWorkTaskItem } from "@/lib/types";

// Mock next/navigation
const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => "/org/acme/my-work",
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: {
    href: string;
    children: React.ReactNode;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

// Mock task-actions and time-entry-actions (used by TaskDetailSheet)
vi.mock("@/app/(app)/org/[slug]/projects/[id]/task-actions", () => ({
  fetchTask: vi.fn().mockResolvedValue({
    id: "task-1",
    projectId: "proj-1",
    title: "Test Task",
    status: "IN_PROGRESS",
    priority: "MEDIUM",
    dueDate: null,
    assigneeId: null,
    assigneeName: null,
    type: null,
    description: null,
    createdByName: null,
    createdAt: "2026-01-01T00:00:00Z",
    tags: [],
    customFields: {},
    appliedFieldGroups: [],
  }),
  updateTask: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/projects/[id]/time-entry-actions", () => ({
  fetchTimeEntries: vi.fn().mockResolvedValue([]),
}));

function makeTask(
  overrides: Partial<MyWorkTaskItem> & { id: string },
): MyWorkTaskItem {
  return {
    projectId: "proj-1",
    projectName: "Test Project",
    title: `Task ${overrides.id}`,
    status: "IN_PROGRESS",
    priority: "MEDIUM",
    dueDate: null,
    totalTimeMinutes: 0,
    ...overrides,
  };
}

describe("UrgencyTaskList — onTaskClick prop", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    mockPush.mockClear();
  });

  it("calls onTaskClick with task id when a row is clicked", () => {
    const onTaskClick = vi.fn();
    const tasks = [makeTask({ id: "task-1", title: "Click Me Task" })];

    render(
      <UrgencyTaskList tasks={tasks} slug="acme" onTaskClick={onTaskClick} />,
    );

    const row = screen.getByText("Click Me Task").closest("tr");
    expect(row).not.toBeNull();
    fireEvent.click(row!);

    expect(onTaskClick).toHaveBeenCalledWith("task-1");
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("falls back to router.push when onTaskClick is not provided", () => {
    const tasks = [makeTask({ id: "task-2", title: "Navigate Task" })];

    render(<UrgencyTaskList tasks={tasks} slug="acme" />);

    const row = screen.getByText("Navigate Task").closest("tr");
    fireEvent.click(row!);

    expect(mockPush).toHaveBeenCalledWith("/org/acme/projects/proj-1");
  });
});

describe("AvailableTaskList — onTaskClick prop", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    mockPush.mockClear();
  });

  it("calls onTaskClick with task id when a row is clicked", () => {
    const onTaskClick = vi.fn();
    const tasks = [makeTask({ id: "avail-1", title: "Available Task" })];

    render(
      <AvailableTaskList
        tasks={tasks}
        slug="acme"
        onTaskClick={onTaskClick}
      />,
    );

    const row = screen.getByText("Available Task").closest("tr");
    expect(row).not.toBeNull();
    fireEvent.click(row!);

    expect(onTaskClick).toHaveBeenCalledWith("avail-1");
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("falls back to router.push when onTaskClick is not provided", () => {
    const tasks = [makeTask({ id: "avail-2", title: "Navigate Available" })];

    render(<AvailableTaskList tasks={tasks} slug="acme" />);

    const row = screen.getByText("Navigate Available").closest("tr");
    fireEvent.click(row!);

    expect(mockPush).toHaveBeenCalledWith("/org/acme/projects/proj-1");
  });
});
