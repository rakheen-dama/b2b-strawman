import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent, waitFor } from "@testing-library/react";
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

// ---------------------------------------------------------------------------
// 642C review fixes: scrollbar available whenever the row overflows; active trigger kept in view.
// happy-dom has no layout, so the tests stub the layout getters on the prototypes before render
// (the effects run on mount): each tab is TAB_W wide, laid out left to right, and the viewport is
// VIEWPORT_W wide. Matter groups: details(0) overview(1) work(2) finance(3) client(4) schedule(5)
// activity(6), so the row is 700px wide in a 300px viewport.
// ---------------------------------------------------------------------------

const TAB_W = 100;
const VIEWPORT_W = 300;
const EDGE_FADE_PX = 32; // w-8 edge fade, cleared when the row scrolls a trigger into view

function isViewport(el: Element) {
  return el.getAttribute("data-slot") === "scroll-area-viewport";
}

function tabPosition(el: Element) {
  if (el.getAttribute("role") !== "tab" || !el.parentElement) return -1;
  return Array.from(el.parentElement.children).indexOf(el);
}

function rowWidth(viewport: Element) {
  return viewport.querySelectorAll('[role="tablist"] > [role="tab"]').length * TAB_W;
}

/** Stub a layout getter on `proto`, falling back to happy-dom's own value for unrelated elements. */
function stubGetter<T extends object>(
  proto: T,
  prop: "offsetLeft" | "offsetWidth" | "clientWidth" | "scrollWidth",
  value: (el: Element) => number | undefined
) {
  const original = Object.getOwnPropertyDescriptor(proto, prop)!.get!;
  vi.spyOn(proto as Element & HTMLElement, prop, "get").mockImplementation(function (
    this: Element
  ) {
    return value(this) ?? (original.call(this) as number);
  });
}

function stubTabLayout({ viewportWidth = VIEWPORT_W }: { viewportWidth?: number } = {}) {
  stubGetter(HTMLElement.prototype, "offsetLeft", (el) => {
    const i = tabPosition(el);
    return i === -1 ? undefined : i * TAB_W;
  });
  stubGetter(HTMLElement.prototype, "offsetWidth", (el) => {
    if (isViewport(el)) return viewportWidth;
    return tabPosition(el) === -1 ? undefined : TAB_W;
  });
  stubGetter(HTMLElement.prototype, "clientWidth", (el) =>
    isViewport(el) ? viewportWidth : undefined
  );
  stubGetter(Element.prototype, "scrollWidth", (el) => (isViewport(el) ? rowWidth(el) : undefined));
}

function getViewport(container: HTMLElement) {
  return container.querySelector<HTMLElement>('[data-slot="scroll-area-viewport"]')!;
}

function fadeVisibility(container: HTMLElement) {
  return {
    start: container
      .querySelector('[data-slot="grouped-tab-bar-fade-start"]')!
      .getAttribute("data-visible"),
    end: container
      .querySelector('[data-slot="grouped-tab-bar-fade-end"]')!
      .getAttribute("data-visible"),
  };
}

describe("GroupedTabBar keeps the active trigger in view (642C review)", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("scrolls the row so an off-screen active trigger is fully visible on mount (deep link)", () => {
    stubTabLayout();
    const { container } = render(
      <GroupedTabBar groups={makeGroups()} activeTab="audit" onTabChange={() => {}} />
    );

    // Activity is the 7th trigger (600–700px); its end lands inside the 300px viewport, clear of
    // the end fade, and the browser would clamp at the 400px maximum.
    const viewport = getViewport(container);
    expect(viewport.scrollLeft).toBe(700 - VIEWPORT_W + EDGE_FADE_PX);
    // A browser fires `scroll` after a programmatic scrollLeft change (happy-dom doesn't, so it is
    // simulated); the fade listener then shows content hidden past the start edge only.
    fireEvent.scroll(viewport);
    expect(fadeVisibility(container)).toEqual({ start: "true", end: "false" });
  });

  it("leaves the scroll position alone when the active trigger is already visible", () => {
    stubTabLayout();
    const { container } = render(
      <GroupedTabBar groups={makeGroups()} activeTab="overview" onTabChange={() => {}} />
    );

    expect(getViewport(container).scrollLeft).toBe(0);
    expect(fadeVisibility(container)).toEqual({ start: "false", end: "true" });
  });

  it("re-runs when activeTab changes, scrolling either way only as far as needed", () => {
    stubTabLayout();
    const { container, rerender } = render(
      <GroupedTabBar groups={makeGroups()} activeTab="overview" onTabChange={() => {}} />
    );
    const viewport = getViewport(container);

    // Fees (300–400px) is past the end edge → its end is brought in.
    rerender(
      <GroupedTabBar groups={makeGroups()} activeTab="disbursements" onTabChange={() => {}} />
    );
    expect(viewport.scrollLeft).toBe(400 - VIEWPORT_W + EDGE_FADE_PX);

    // Details (0–100px) is now past the start edge → scrolled back (clamped at 0).
    rerender(<GroupedTabBar groups={makeGroups()} activeTab="details" onTabChange={() => {}} />);
    expect(viewport.scrollLeft).toBe(0);

    // Diary (500–600px) → its end is brought in again.
    rerender(
      <GroupedTabBar groups={makeGroups()} activeTab="court-dates" onTabChange={() => {}} />
    );
    expect(viewport.scrollLeft).toBe(600 - VIEWPORT_W + EDGE_FADE_PX);
  });

  it("re-runs on a sub-tab change within the active group (the trigger label changes width)", () => {
    stubTabLayout();
    const { container, rerender } = render(
      <GroupedTabBar groups={makeGroups()} activeTab="time" onTabChange={() => {}} />
    );
    const viewport = getViewport(container);
    expect(viewport.scrollLeft).toBe(400 - VIEWPORT_W + EDGE_FADE_PX);

    // The user scrolls Fees back out of view, then picks another Fees sub-tab.
    viewport.scrollLeft = 0;
    fireEvent.scroll(viewport);
    rerender(<GroupedTabBar groups={makeGroups()} activeTab="trust" onTabChange={() => {}} />);
    expect(viewport.scrollLeft).toBe(400 - VIEWPORT_W + EDGE_FADE_PX);
  });

  it("never calls scrollIntoView (it would also scroll the page vertically)", () => {
    stubTabLayout();
    const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
    render(<GroupedTabBar groups={makeGroups()} activeTab="audit" onTabChange={() => {}} />);

    expect(scrollIntoView).not.toHaveBeenCalled();
  });
});

describe("GroupedTabBar keeps the focused trigger clear of the edge fades (642C OBS)", () => {
  // 350px viewport over the 700px row: Work (200–300px) is fully clear of the end fade (318px);
  // Fees (300–400px) is 50px visible, the last 32px of which is under the end fade.
  const NARROW_W = 350;

  afterEach(() => {
    vi.restoreAllMocks();
  });

  function renderNarrow() {
    stubTabLayout({ viewportWidth: NARROW_W });
    const { container } = render(
      <GroupedTabBar groups={makeGroups()} activeTab="overview" onTabChange={() => {}} />
    );
    const viewport = getViewport(container);
    expect(viewport.scrollLeft).toBe(0); // Overview (100–200px) is the active tab and in view
    return viewport;
  }

  it("scrolls a partly hidden trigger fully clear of the end fade when it gets focus", () => {
    const viewport = renderNarrow();

    screen.getByTestId("tab-group-finance").focus();
    expect(viewport.scrollLeft).toBe(400 - NARROW_W + EDGE_FADE_PX);
  });

  it("scrolls a trigger that is inside the viewport but under the end fade", () => {
    stubTabLayout({ viewportWidth: 310 });
    const { container } = render(
      <GroupedTabBar groups={makeGroups()} activeTab="overview" onTabChange={() => {}} />
    );
    const viewport = getViewport(container);

    // Work (200–300px) ends inside the 310px viewport, but 22px of it is under the end fade.
    screen.getByTestId("tab-group-work").focus();
    expect(viewport.scrollLeft).toBe(300 - 310 + EDGE_FADE_PX);
  });

  it("scrolls a trigger partly hidden past the start edge back clear of the start fade", () => {
    const viewport = renderNarrow();
    viewport.scrollLeft = 150; // the user scrolled; Overview (100–200px) is half hidden
    fireEvent.scroll(viewport);

    screen.getByTestId("tab-group-overview").focus();
    expect(viewport.scrollLeft).toBe(100 - EDGE_FADE_PX);
  });

  it("leaves the scroll position alone when the focused trigger is already fully clear", () => {
    const viewport = renderNarrow();

    screen.getByTestId("tab-group-work").focus();
    expect(viewport.scrollLeft).toBe(0);
  });

  it("ArrowRight onto a partly hidden trigger scrolls it clear of the end fade", () => {
    const viewport = renderNarrow();
    screen.getByTestId("tab-group-work").focus();
    expect(viewport.scrollLeft).toBe(0);

    fireEvent.keyDown(screen.getByTestId("grouped-tab-bar"), { key: "ArrowRight" });
    expect(document.activeElement).toBe(screen.getByTestId("tab-group-finance"));
    expect(viewport.scrollLeft).toBe(400 - NARROW_W + EDGE_FADE_PX);
  });

  it("does not scroll on focus from a mouse click (not :focus-visible), so the click lands", () => {
    const viewport = renderNarrow();
    const matches = Element.prototype.matches;
    vi.spyOn(Element.prototype, "matches").mockImplementation(function (
      this: Element,
      selector: string
    ) {
      return selector === ":focus-visible" ? false : matches.call(this, selector);
    });

    screen.getByTestId("tab-group-finance").focus();
    expect(document.activeElement).toBe(screen.getByTestId("tab-group-finance"));
    expect(viewport.scrollLeft).toBe(0);
  });

  it("never calls scrollIntoView when a trigger gets focus", () => {
    const viewport = renderNarrow();
    const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");

    screen.getByTestId("tab-group-finance").focus();
    fireEvent.keyDown(screen.getByTestId("grouped-tab-bar"), { key: "ArrowRight" });
    expect(document.activeElement).toBe(screen.getByTestId("tab-group-client"));
    expect(viewport.scrollLeft).toBe(500 - NARROW_W + EDGE_FADE_PX);
    expect(scrollIntoView).not.toHaveBeenCalled();
  });
});

describe("GroupedTabBar horizontal scrollbar (642C review)", () => {
  const HORIZONTAL_SCROLLBAR = '[data-slot="scroll-area-scrollbar"][data-orientation="horizontal"]';

  /** A ResizeObserver that reports once on observe, as a browser does for a laid-out element. */
  class ReportingResizeObserver {
    constructor(private readonly callback: ResizeObserverCallback) {}
    observe() {
      this.callback([], this as unknown as ResizeObserver);
    }
    unobserve() {}
    disconnect() {}
  }

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("mounts the scrollbar as soon as the row overflows, without any scroll (wheel-only mice)", async () => {
    vi.stubGlobal("ResizeObserver", ReportingResizeObserver);
    stubTabLayout(); // 700px row in a 300px viewport
    const { container } = render(
      <GroupedTabBar groups={makeGroups()} activeTab="overview" onTabChange={() => {}} />
    );

    // No scroll event is fired: with `type="scroll"` the bar would stay unmounted until one is.
    await waitFor(() => expect(container.querySelector(HORIZONTAL_SCROLLBAR)).not.toBeNull());
    expect(container.querySelector(HORIZONTAL_SCROLLBAR)).toHaveAttribute("data-state", "visible");
  });

  it("does not mount the scrollbar when the row fits", async () => {
    vi.stubGlobal("ResizeObserver", ReportingResizeObserver);
    stubTabLayout({ viewportWidth: 700 }); // 700px row in a 700px viewport
    const { container } = render(
      <GroupedTabBar groups={makeGroups()} activeTab="overview" onTabChange={() => {}} />
    );

    // Radix debounces its overflow check (rAF + 10ms); give it time to (not) appear.
    await new Promise((resolve) => setTimeout(resolve, 60));
    expect(container.querySelector(HORIZONTAL_SCROLLBAR)).toBeNull();
  });
});
