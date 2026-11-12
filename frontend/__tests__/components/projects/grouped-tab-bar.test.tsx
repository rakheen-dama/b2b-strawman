import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent } from "@testing-library/react";
import { GroupedTabBar } from "@/components/shared/grouped-tab-bar";
import { MATTER_TAB_GROUPS } from "@/lib/constants/matter-tab-groups";
import { resolveTabFromUrl, type TabGroup } from "@/lib/constants/tab-group-types";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeGroups(overrides?: Partial<Record<string, boolean>>): TabGroup[] {
  return MATTER_TAB_GROUPS.map((g) => ({
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
    render(<GroupedTabBar groups={groups} activeTab="overview" onTabChange={() => {}} />);

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
    render(<GroupedTabBar groups={groups} activeTab="overview" onTabChange={() => {}} />);

    expect(screen.queryByTestId("tab-group-finance")).not.toBeInTheDocument();
    expect(screen.queryByTestId("tab-group-schedule")).not.toBeInTheDocument();
    // Other groups still visible
    expect(screen.getByTestId("tab-group-overview")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-work")).toBeInTheDocument();
  });

  it("renders standalone tab (no dropdown) for single-sub-tab group", () => {
    const groups = makeGroups();
    const onTabChange = vi.fn();
    render(<GroupedTabBar groups={groups} activeTab="overview" onTabChange={onTabChange} />);

    // Overview has exactly 1 sub-tab — should render as a plain button
    const overviewTrigger = screen.getByTestId("tab-group-overview");
    expect(overviewTrigger.tagName).toBe("BUTTON");

    // No chevron icon in standalone tab
    expect(overviewTrigger.querySelector("svg")).toBeNull();

    // Clicking should trigger tab change directly
    fireEvent.click(overviewTrigger);
    expect(onTabChange).toHaveBeenCalledWith("overview");
  });

  it('shows active sub-tab label in group trigger ("Fees · Time recording")', () => {
    const groups = makeGroups();
    render(<GroupedTabBar groups={groups} activeTab="time" onTabChange={() => {}} />);

    const financeTrigger = screen.getByTestId("tab-group-finance");
    expect(financeTrigger.textContent).toContain("Fees");
    expect(financeTrigger.textContent).toContain("·");
    expect(financeTrigger.textContent).toContain("Time recording");
  });

  it("clicking group with multiple tabs and no active sub-tab navigates to first tab", () => {
    const groups = makeGroups();
    const onTabChange = vi.fn();
    render(<GroupedTabBar groups={groups} activeTab="overview" onTabChange={onTabChange} />);

    // Work group has multiple tabs but overview is active → click navigates to first work tab
    fireEvent.click(screen.getByTestId("tab-group-work"));
    expect(onTabChange).toHaveBeenCalledWith("tasks");
    expect(onTabChange).toHaveBeenCalledTimes(1);
  });

  it("clicking active multi-tab group trigger opens dropdown", () => {
    const groups = makeGroups();
    const onTabChange = vi.fn();
    render(<GroupedTabBar groups={groups} activeTab="tasks" onTabChange={onTabChange} />);

    const workTrigger = screen.getByTestId("tab-group-work");

    // Work group is active (tasks is in work group) → click should open dropdown, not navigate
    // Simulate full pointer interaction sequence that Radix listens for
    fireEvent.pointerDown(workTrigger, { button: 0, pointerType: "mouse" });
    fireEvent.click(workTrigger);

    // onTabChange should NOT have been called (group is already active)
    expect(onTabChange).not.toHaveBeenCalled();

    // Dropdown content should be visible with sub-tab items
    expect(screen.getByTestId("tab-item-tasks")).toBeInTheDocument();
    expect(screen.getByTestId("tab-item-documents")).toBeInTheDocument();
    expect(screen.getByTestId("tab-item-generated")).toBeInTheDocument();
    expect(screen.queryByTestId("tab-item-staffing")).not.toBeInTheDocument();
  });
});

describe("resolveTabFromUrl", () => {
  it("returns overview for null param", () => {
    const result = resolveTabFromUrl(null, MATTER_TAB_GROUPS);
    expect(result).toEqual({ groupId: "overview", tabId: "overview" });
  });

  it("returns overview for unknown param (members alias applied by caller)", () => {
    // resolveTabFromUrl is entity-agnostic: the raw "members" id is not a tab, so it falls through
    // to the overview default. MatterTabs applies aliasLegacyTabId first (members → team, ADR-346).
    const result = resolveTabFromUrl("members", MATTER_TAB_GROUPS);
    expect(result).toEqual({ groupId: "overview", tabId: "overview" });
  });

  it("finds correct group for known tab id (time → finance)", () => {
    const result = resolveTabFromUrl("time", MATTER_TAB_GROUPS);
    expect(result).toEqual({ groupId: "finance", tabId: "time" });
  });

  it("handles group-level param → first sub-tab (finance → time)", () => {
    const result = resolveTabFromUrl("finance", MATTER_TAB_GROUPS);
    expect(result).toEqual({ groupId: "finance", tabId: "time" });
  });

  it("returns overview for unknown param", () => {
    const result = resolveTabFromUrl("nonexistent", MATTER_TAB_GROUPS);
    expect(result).toEqual({ groupId: "overview", tabId: "overview" });
  });
});

describe("GroupedTabBar keyboard navigation", () => {
  it("ArrowRight moves focus to next group trigger", () => {
    const groups = makeGroups();
    render(<GroupedTabBar groups={groups} activeTab="overview" onTabChange={() => {}} />);

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

// ---------------------------------------------------------------------------
// Phase 90 642C (E6C): underline restyle — state, structure, keyboard, menu, sticky, edge fades.
// Assert state (aria-selected, data-*, text), never colour classes.
// ---------------------------------------------------------------------------

/** Visible matter groups, in MATTER_TAB_GROUPS order. */
const MATTER_GROUP_TESTIDS = MATTER_TAB_GROUPS.map((g) => `tab-group-${g.id}`);

function openActiveGroup(testId: string) {
  const trigger = screen.getByTestId(testId);
  fireEvent.pointerDown(trigger, { button: 0, pointerType: "mouse" });
  fireEvent.click(trigger);
}

describe("GroupedTabBar active state (642C)", () => {
  it("marks only the active group aria-selected (time → Fees)", () => {
    render(<GroupedTabBar groups={makeGroups()} activeTab="time" onTabChange={() => {}} />);

    for (const testId of MATTER_GROUP_TESTIDS) {
      expect(screen.getByTestId(testId), testId).toHaveAttribute(
        "aria-selected",
        testId === "tab-group-finance" ? "true" : "false"
      );
    }
    expect(screen.getByTestId("tab-group-finance")).toHaveTextContent("Fees · Time recording");
  });

  it("shows no sub-tab suffix on inactive or single-tab groups", () => {
    render(<GroupedTabBar groups={makeGroups()} activeTab="overview" onTabChange={() => {}} />);

    expect(screen.getByTestId("tab-group-overview").textContent).toBe("Overview");
    expect(screen.getByTestId("tab-group-finance").textContent).toBe("Fees");
  });

  it("renders a chevron on multi-tab groups only", () => {
    render(<GroupedTabBar groups={makeGroups()} activeTab="overview" onTabChange={() => {}} />);

    expect(screen.getByTestId("tab-group-overview").querySelector("svg")).toBeNull();
    expect(screen.getByTestId("tab-group-schedule").querySelector("svg")).toBeNull();
    expect(screen.getByTestId("tab-group-work").querySelectorAll("svg")).toHaveLength(1);
  });
});

describe("GroupedTabBar structure (642C)", () => {
  it("keeps role=tablist on the grouped-tab-bar row with the groups in constant order", () => {
    render(<GroupedTabBar groups={makeGroups()} activeTab="overview" onTabChange={() => {}} />);

    const tablist = screen.getByRole("tablist");
    expect(tablist).toBe(screen.getByTestId("grouped-tab-bar"));
    expect(screen.getAllByRole("tab").map((t) => t.dataset.testid)).toEqual(MATTER_GROUP_TESTIDS);
    for (const tab of screen.getAllByRole("tab")) {
      expect(tab.parentElement).toBe(tablist);
    }
  });

  it("puts the row inside the scroll viewport and the scroll area inside the sticky bar", () => {
    render(<GroupedTabBar groups={makeGroups()} activeTab="overview" onTabChange={() => {}} />);

    const tablist = screen.getByTestId("grouped-tab-bar");
    const viewport = tablist.closest('[data-slot="scroll-area-viewport"]');
    const scrollArea = tablist.closest('[data-slot="scroll-area"]');
    const sticky = tablist.closest('[data-slot="grouped-tab-bar-sticky"]');

    expect(viewport).not.toBeNull();
    expect(scrollArea).not.toBeNull();
    expect(sticky).not.toBeNull();
    // The scroller sits inside the sticky element, never around it (an overflow ancestor would
    // become the sticky scrollport).
    expect(sticky!.contains(scrollArea)).toBe(true);
    expect(scrollArea!.contains(sticky)).toBe(false);
    expect(sticky).toHaveClass("sticky");
  });

  it("renders exactly one tab bar", () => {
    render(<GroupedTabBar groups={makeGroups()} activeTab="overview" onTabChange={() => {}} />);

    expect(screen.getAllByTestId("grouped-tab-bar")).toHaveLength(1);
    expect(screen.getAllByRole("tablist")).toHaveLength(1);
  });
});

describe("GroupedTabBar keyboard navigation (642C)", () => {
  function focusAndPress(testId: string, key: string) {
    const trigger = screen.getByTestId(testId);
    trigger.focus();
    expect(document.activeElement).toBe(trigger);
    fireEvent.keyDown(screen.getByTestId("grouped-tab-bar"), { key });
  }

  it("ArrowLeft moves focus to the previous group trigger", () => {
    render(<GroupedTabBar groups={makeGroups()} activeTab="overview" onTabChange={() => {}} />);

    focusAndPress("tab-group-work", "ArrowLeft");
    expect(document.activeElement).toBe(screen.getByTestId("tab-group-overview"));
  });

  it("ArrowLeft on the first group wraps to the last", () => {
    render(<GroupedTabBar groups={makeGroups()} activeTab="overview" onTabChange={() => {}} />);

    focusAndPress("tab-group-details", "ArrowLeft");
    expect(document.activeElement).toBe(screen.getByTestId("tab-group-activity"));
  });

  it("ArrowRight on the last group wraps to the first", () => {
    render(<GroupedTabBar groups={makeGroups()} activeTab="overview" onTabChange={() => {}} />);

    focusAndPress("tab-group-activity", "ArrowRight");
    expect(document.activeElement).toBe(screen.getByTestId("tab-group-details"));
  });

  it("other keys do not move focus or change the tab", () => {
    const onTabChange = vi.fn();
    render(<GroupedTabBar groups={makeGroups()} activeTab="overview" onTabChange={onTabChange} />);

    focusAndPress("tab-group-overview", "ArrowDown");
    expect(document.activeElement).toBe(screen.getByTestId("tab-group-overview"));
    fireEvent.keyDown(screen.getByTestId("grouped-tab-bar"), { key: "Home" });
    expect(document.activeElement).toBe(screen.getByTestId("tab-group-overview"));
    expect(onTabChange).not.toHaveBeenCalled();
  });
});

describe("GroupedTabBar dropdown menu (642C)", () => {
  it("selecting a menu item changes the tab and closes the menu", () => {
    const onTabChange = vi.fn();
    render(<GroupedTabBar groups={makeGroups()} activeTab="tasks" onTabChange={onTabChange} />);

    openActiveGroup("tab-group-work");
    fireEvent.click(screen.getByTestId("tab-item-documents"));

    expect(onTabChange).toHaveBeenCalledTimes(1);
    expect(onTabChange).toHaveBeenCalledWith("documents");
    expect(screen.queryByTestId("tab-item-documents")).not.toBeInTheDocument();
  });

  it("marks the current sub-tab in the open menu with data-active", () => {
    render(<GroupedTabBar groups={makeGroups()} activeTab="tasks" onTabChange={() => {}} />);

    openActiveGroup("tab-group-work");

    expect(screen.getByTestId("tab-item-tasks")).toHaveAttribute("data-active", "true");
    expect(screen.getByTestId("tab-item-documents")).not.toHaveAttribute("data-active");
    expect(screen.getByTestId("tab-item-tasks")).toHaveAttribute("role", "menuitem");
  });
});

describe("GroupedTabBar edge fades (642C)", () => {
  function stubViewport(viewport: HTMLElement, scrollWidth: number, clientWidth: number) {
    Object.defineProperty(viewport, "scrollWidth", { configurable: true, value: scrollWidth });
    Object.defineProperty(viewport, "clientWidth", { configurable: true, value: clientWidth });
  }

  function fadeState(container: HTMLElement) {
    const start = container.querySelector('[data-slot="grouped-tab-bar-fade-start"]')!;
    const end = container.querySelector('[data-slot="grouped-tab-bar-fade-end"]')!;
    return { start, end };
  }

  it("hides both fades when nothing overflows", () => {
    const { container } = render(
      <GroupedTabBar groups={makeGroups()} activeTab="overview" onTabChange={() => {}} />
    );

    const { start, end } = fadeState(container);
    expect(start).toHaveAttribute("data-visible", "false");
    expect(end).toHaveAttribute("data-visible", "false");
    expect(start).toHaveAttribute("aria-hidden", "true");
    expect(end).toHaveAttribute("aria-hidden", "true");
  });

  it("shows the end fade at the start of an overflowing row and the start fade at its end", () => {
    const { container } = render(
      <GroupedTabBar groups={makeGroups()} activeTab="overview" onTabChange={() => {}} />
    );
    const viewport = container.querySelector<HTMLElement>('[data-slot="scroll-area-viewport"]')!;
    stubViewport(viewport, 800, 300);

    viewport.scrollLeft = 0;
    fireEvent.scroll(viewport);
    expect(fadeState(container).start).toHaveAttribute("data-visible", "false");
    expect(fadeState(container).end).toHaveAttribute("data-visible", "true");

    viewport.scrollLeft = 200;
    fireEvent.scroll(viewport);
    expect(fadeState(container).start).toHaveAttribute("data-visible", "true");
    expect(fadeState(container).end).toHaveAttribute("data-visible", "true");

    viewport.scrollLeft = 500;
    fireEvent.scroll(viewport);
    expect(fadeState(container).start).toHaveAttribute("data-visible", "true");
    expect(fadeState(container).end).toHaveAttribute("data-visible", "false");
  });

  it("keeps the fades out of the hit-test path", () => {
    const { container } = render(
      <GroupedTabBar groups={makeGroups()} activeTab="overview" onTabChange={() => {}} />
    );

    // pointer-events-none is the contract that keeps Playwright clicks on the edge triggers
    // landing (an overlay that intercepts pointer events fails the actionability check).
    const { start, end } = fadeState(container);
    expect(start).toHaveClass("pointer-events-none");
    expect(end).toHaveClass("pointer-events-none");
  });
});
