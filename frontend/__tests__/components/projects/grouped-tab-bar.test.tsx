import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent } from "@testing-library/react";
import { GroupedTabBar } from "@/components/projects/grouped-tab-bar";
import {
  TAB_GROUPS,
  resolveTabFromUrl,
  type TabGroup,
} from "@/lib/constants/tab-groups";

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

// Mock motion/react to avoid layout animation in tests
vi.mock("motion/react", () => ({
  motion: {
    span: "span",
  },
}));

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeGroups(overrides?: Partial<Record<string, boolean>>): TabGroup[] {
  return TAB_GROUPS.map((g) => ({
    ...g,
    visible: overrides?.[g.id] ?? true,
  }));
}

// ---------------------------------------------------------------------------
// Cleanup
// ---------------------------------------------------------------------------

afterEach(() => {
  cleanup();
});

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("GroupedTabBar", () => {
  it("renders all visible groups", () => {
    const groups = makeGroups();
    render(
      <GroupedTabBar groups={groups} activeTab="overview" onTabChange={() => {}} />,
    );

    expect(screen.getByTestId("grouped-tab-bar")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-overview")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-work")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-finance")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-client")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-schedule")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-activity")).toBeInTheDocument();
  });

  it("hides groups with visible: false", () => {
    const groups = makeGroups({ finance: false, schedule: false });
    render(
      <GroupedTabBar groups={groups} activeTab="overview" onTabChange={() => {}} />,
    );

    expect(screen.queryByTestId("tab-group-finance")).not.toBeInTheDocument();
    expect(screen.queryByTestId("tab-group-schedule")).not.toBeInTheDocument();
    // Other groups still visible
    expect(screen.getByTestId("tab-group-overview")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-work")).toBeInTheDocument();
  });

  it("renders standalone tab (no dropdown) for single-sub-tab group", () => {
    const groups = makeGroups();
    const onTabChange = vi.fn();
    render(
      <GroupedTabBar groups={groups} activeTab="overview" onTabChange={onTabChange} />,
    );

    // Overview has exactly 1 sub-tab — should render as a plain button
    const overviewTrigger = screen.getByTestId("tab-group-overview");
    expect(overviewTrigger.tagName).toBe("BUTTON");

    // No chevron icon in standalone tab
    expect(overviewTrigger.querySelector("svg")).toBeNull();

    // Clicking should trigger tab change directly
    fireEvent.click(overviewTrigger);
    expect(onTabChange).toHaveBeenCalledWith("overview");
  });

  it('shows active sub-tab label in group trigger ("Finance · Time")', () => {
    const groups = makeGroups();
    render(
      <GroupedTabBar groups={groups} activeTab="time" onTabChange={() => {}} />,
    );

    const financeTrigger = screen.getByTestId("tab-group-finance");
    expect(financeTrigger.textContent).toContain("Finance");
    expect(financeTrigger.textContent).toContain("·");
    expect(financeTrigger.textContent).toContain("Time");
  });

  it("clicking group with multiple tabs and no active sub-tab navigates to first tab", () => {
    const groups = makeGroups();
    const onTabChange = vi.fn();
    render(
      <GroupedTabBar groups={groups} activeTab="overview" onTabChange={onTabChange} />,
    );

    // Work group has multiple tabs but overview is active → click navigates to first work tab
    fireEvent.click(screen.getByTestId("tab-group-work"));
    expect(onTabChange).toHaveBeenCalledWith("tasks");
  });
});

describe("resolveTabFromUrl", () => {
  it("returns overview for null param", () => {
    const result = resolveTabFromUrl(null, TAB_GROUPS);
    expect(result).toEqual({ groupId: "overview", tabId: "overview" });
  });

  it("maps 'members' to staffing via redirect", () => {
    const result = resolveTabFromUrl("members", TAB_GROUPS);
    expect(result).toEqual({ groupId: "work", tabId: "staffing" });
  });

  it("finds correct group for known tab id (time → finance)", () => {
    const result = resolveTabFromUrl("time", TAB_GROUPS);
    expect(result).toEqual({ groupId: "finance", tabId: "time" });
  });

  it("handles group-level param → first sub-tab (finance → time)", () => {
    const result = resolveTabFromUrl("finance", TAB_GROUPS);
    expect(result).toEqual({ groupId: "finance", tabId: "time" });
  });

  it("returns overview for unknown param", () => {
    const result = resolveTabFromUrl("nonexistent", TAB_GROUPS);
    expect(result).toEqual({ groupId: "overview", tabId: "overview" });
  });
});

describe("GroupedTabBar keyboard navigation", () => {
  it("ArrowRight moves focus to next group trigger", () => {
    const groups = makeGroups();
    render(
      <GroupedTabBar groups={groups} activeTab="overview" onTabChange={() => {}} />,
    );

    const overviewTrigger = screen.getByTestId("tab-group-overview");
    const workTrigger = screen.getByTestId("tab-group-work");

    // Focus the overview trigger first
    overviewTrigger.focus();
    expect(document.activeElement).toBe(overviewTrigger);

    // Press ArrowRight → focus should move to work
    fireEvent.keyDown(screen.getByTestId("grouped-tab-bar"), {
      key: "ArrowRight",
    });
    expect(document.activeElement).toBe(workTrigger);
  });
});
