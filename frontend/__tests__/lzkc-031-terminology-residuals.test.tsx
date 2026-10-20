import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { TerminologyProvider } from "@/lib/terminology";
import { AssignedTaskList } from "@/components/my-work/assigned-task-list";
import { AvailableTaskList } from "@/components/my-work/available-task-list";
import { UrgencyTaskList } from "@/components/my-work/urgency-task-list";
import type { MyWorkTaskItem } from "@/lib/types/matter";

// Task lists navigate on row click via the app router.
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/org/test-org/my-work",
  useSearchParams: () => new URLSearchParams(),
}));

afterEach(() => {
  cleanup();
});

function withProfile(profile: string | null, ui: React.ReactElement) {
  return render(<TerminologyProvider verticalProfile={profile}>{ui}</TerminologyProvider>);
}

function task(overrides: Partial<MyWorkTaskItem> = {}): MyWorkTaskItem {
  return {
    id: "t-1",
    matterId: "p-1",
    matterName: "Alpha",
    title: "Draft the thing",
    status: "OPEN",
    priority: "HIGH",
    dueDate: null,
    totalTimeMinutes: 0,
    ...overrides,
  };
}

// ---- My Work task-table "Project" column headers ----

describe("LZKC-031: My Work task-table column headers", () => {
  it("AssignedTaskList renders 'Matter' column header for legal-za", () => {
    withProfile("legal-za", <AssignedTaskList tasks={[task()]} slug="test-org" />);
    expect(screen.getByText("Matter")).toBeTruthy();
    expect(screen.queryByText("Project")).toBeNull();
  });

  it("AssignedTaskList keeps 'Project' column header without a profile", () => {
    withProfile(null, <AssignedTaskList tasks={[task()]} slug="test-org" />);
    expect(screen.getByText("Project")).toBeTruthy();
  });

  it("AvailableTaskList renders 'Matter' column header for legal-za", () => {
    withProfile("legal-za", <AvailableTaskList tasks={[task({ id: "t-2" })]} slug="test-org" />);
    expect(screen.getByText("Matter")).toBeTruthy();
    expect(screen.queryByText("Project")).toBeNull();
  });

  it("UrgencyTaskList renders 'Matter' column header for legal-za", () => {
    withProfile("legal-za", <UrgencyTaskList tasks={[task({ id: "t-3" })]} slug="test-org" />);
    expect(screen.getAllByText("Matter").length).toBeGreaterThan(0);
    expect(screen.queryByText("Project")).toBeNull();
  });
});
