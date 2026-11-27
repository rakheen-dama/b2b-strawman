import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AvailableTaskList } from "@/components/my-work/available-task-list";
import { UrgencyTaskList } from "@/components/my-work/urgency-task-list";
import type { MyWorkTaskItem } from "@/lib/types";

const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => "/org/acme/my-work",
  useSearchParams: () => new URLSearchParams(),
}));

function makeTask(overrides: Partial<MyWorkTaskItem> & { id: string }): MyWorkTaskItem {
  return {
    matterId: "proj-1",
    matterName: "Khumalo — lease dispute",
    title: `Task ${overrides.id}`,
    status: "IN_PROGRESS",
    priority: "MEDIUM",
    dueDate: null,
    totalTimeMinutes: 0,
    ...overrides,
  };
}

function makeTasks(count: number): MyWorkTaskItem[] {
  return Array.from({ length: count }, (_, i) =>
    makeTask({ id: `avail-${i + 1}`, title: `Available ${i + 1}` })
  );
}

afterEach(() => {
  cleanup();
});

beforeEach(() => {
  mockPush.mockClear();
});

describe("AvailableTaskList — client pager (K-97)", () => {
  it("shows ten rows per page with the kit pager words", async () => {
    render(<AvailableTaskList tasks={makeTasks(11)} slug="acme" />);

    // The count badge keeps the total.
    expect(screen.getByRole("button", { name: /Available Tasks/ })).toHaveTextContent("11");
    expect(screen.getByText("Page 1 of 2")).toBeInTheDocument();
    expect(screen.getByText("Available 1")).toBeInTheDocument();
    expect(screen.getByText("Available 10")).toBeInTheDocument();
    expect(screen.queryByText("Available 11")).toBeNull();
    expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
    // No local-pager chrome.
    expect(screen.queryByText("Rows per page")).toBeNull();

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.getByText("Page 2 of 2")).toBeInTheDocument();
    expect(screen.getByText("Available 11")).toBeInTheDocument();
    expect(screen.queryByText("Available 1")).toBeNull();
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Previous" }));
    expect(screen.getByText("Page 1 of 2")).toBeInTheDocument();
    expect(screen.getByText("Available 1")).toBeInTheDocument();
  });

  it("keeps the page across collapsing and expanding the section", async () => {
    render(<AvailableTaskList tasks={makeTasks(11)} slug="acme" />);
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Next" }));
    const toggle = screen.getByRole("button", { name: /Available Tasks/ });
    await user.click(toggle);
    expect(screen.queryByRole("table")).toBeNull();
    await user.click(toggle);
    expect(screen.getByText("Page 2 of 2")).toBeInTheDocument();
    expect(screen.getByText("Available 11")).toBeInTheDocument();
  });

  it("shows no pager for ten tasks or fewer", () => {
    render(<AvailableTaskList tasks={makeTasks(10)} slug="acme" />);
    expect(screen.getAllByRole("row")).toHaveLength(11); // header + 10
    expect(screen.queryByText(/Page \d+ of/)).toBeNull();
    expect(screen.queryByRole("button", { name: "Next" })).toBeNull();
  });

  it("clamps the page when a refresh shrinks the list", async () => {
    const { rerender } = render(<AvailableTaskList tasks={makeTasks(11)} slug="acme" />);
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Next" }));
    rerender(<AvailableTaskList tasks={makeTasks(5)} slug="acme" />);
    expect(screen.getByText("Available 1")).toBeInTheDocument();
    expect(screen.getByText("Available 5")).toBeInTheDocument();
    expect(screen.queryByText(/Page \d+ of/)).toBeNull();
  });
});

describe("Task tables — row activation without a kit onRowClick (DA-9)", () => {
  it("renders the title as a real button that opens the task (keyboard path)", async () => {
    const onTaskClick = vi.fn();
    render(
      <UrgencyTaskList
        tasks={[makeTask({ id: "task-k", title: "Keyboard task" })]}
        slug="acme"
        onTaskClick={onTaskClick}
      />
    );

    const button = screen.getByRole("button", { name: "Keyboard task" });
    expect(button).toHaveAttribute("type", "button");
    expect(button).toHaveAttribute("title", "Keyboard task");
    button.focus();
    const user = userEvent.setup();
    await user.keyboard("{Enter}");
    expect(onTaskClick).toHaveBeenCalledTimes(1);
    expect(onTaskClick).toHaveBeenCalledWith("task-k");
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("opens the task once from a click on the title button or on a plain cell", () => {
    const onTaskClick = vi.fn();
    render(
      <AvailableTaskList
        tasks={[makeTask({ id: "avail-x", title: "Delegated task", priority: "HIGH" })]}
        slug="acme"
        onTaskClick={onTaskClick}
      />
    );

    fireEvent.click(screen.getByRole("button", { name: "Delegated task" }));
    expect(onTaskClick).toHaveBeenCalledTimes(1);

    // A plain cell (the priority badge) re-targets to the row's title button.
    fireEvent.click(screen.getByText("High"));
    expect(onTaskClick).toHaveBeenCalledTimes(2);
    expect(onTaskClick).toHaveBeenLastCalledWith("avail-x");
  });

  it("ignores header clicks, non-left buttons and pager clicks", async () => {
    const onTaskClick = vi.fn();
    render(<AvailableTaskList tasks={makeTasks(11)} slug="acme" onTaskClick={onTaskClick} />);

    fireEvent.click(screen.getByRole("columnheader", { name: "Matter" }));
    const row = screen.getByText("Available 2").closest("tr")!;
    fireEvent.click(row, { button: 2 });
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(onTaskClick).not.toHaveBeenCalled();
  });

  it("uses the kit rowHref (no title button) when no onTaskClick is given", () => {
    render(
      <UrgencyTaskList
        tasks={[makeTask({ id: "task-n", title: "Matter link task" })]}
        slug="acme"
      />
    );
    expect(screen.queryByRole("button", { name: "Matter link task" })).toBeNull();
    fireEvent.click(screen.getByText("Matter link task").closest("tr")!);
    expect(mockPush).toHaveBeenCalledTimes(1);
    expect(mockPush).toHaveBeenCalledWith("/org/acme/matters/proj-1");
  });
});

describe("UrgencyTaskList — Overdue group on the kit table", () => {
  it("tints the Overdue group's rows through its wrapper and shows the overdue date", () => {
    const tasks = [
      makeTask({ id: "o1", title: "Late filing", dueDate: "2020-01-01" }),
      makeTask({ id: "f1", title: "Later filing", dueDate: "2099-01-01" }),
    ];
    render(<UrgencyTaskList tasks={tasks} slug="acme" />);

    const [overdueTable, upcomingTable] = screen.getAllByRole("table");
    const overdueWrapper = overdueTable.closest('[data-slot="data-table"]')!.parentElement!;
    expect(overdueWrapper.className).toContain("[&_tbody_tr]:bg-red-50/30");
    const upcomingWrapper = upcomingTable.closest('[data-slot="data-table"]')!.parentElement!;
    expect(upcomingWrapper.className).not.toContain("bg-red-50");

    const overdueDate = within(overdueTable).getByText("1 Jan 2020");
    expect(overdueDate).toHaveClass("text-destructive");
    expect(overdueDate.querySelector('svg[aria-hidden="true"]')).not.toBeNull();
    expect(within(upcomingTable).getByText("1 Jan 2099")).toHaveClass("text-muted-foreground");
    // Exactly one "Overdue" text (no table caption duplicates the group heading).
    expect(screen.getByText("Overdue")).toBeInTheDocument();
  });
});
