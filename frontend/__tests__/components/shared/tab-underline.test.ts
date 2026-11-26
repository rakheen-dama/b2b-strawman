import { describe, it, expect } from "vitest";
import {
  TAB_EDGE_FADE_CLASS,
  TAB_EDGE_FADE_PX,
  TAB_STRIP_SCROLLBAR_CLASS,
  TAB_STRIP_SCROLL_AREA_CLASS,
  TAB_UNDERLINE_CURRENT,
  TAB_UNDERLINE_SELECTED,
  findScrollViewport,
  scrollItemClearOfFades,
} from "@/components/shared/tab-underline";

// Phase 91 664A: the 642C underline extracted from grouped-tab-bar.tsx. Each variant keys its
// active state on one attribute only; the grouped tab bar keeps rendering the same classes.

const classes = (value: string) => value.split(" ");

describe("tab-underline variants", () => {
  it("TAB_UNDERLINE_SELECTED keys on aria-selected only", () => {
    expect(classes(TAB_UNDERLINE_SELECTED)).toEqual(
      expect.arrayContaining(["aria-selected:text-foreground", "aria-selected:after:opacity-100"])
    );
    expect(TAB_UNDERLINE_SELECTED).not.toContain("aria-[current=page]");
    expect(TAB_UNDERLINE_SELECTED).not.toContain("data-[state=");
  });

  it("TAB_UNDERLINE_CURRENT keys on aria-current=page only", () => {
    expect(classes(TAB_UNDERLINE_CURRENT)).toEqual(
      expect.arrayContaining([
        "aria-[current=page]:text-foreground",
        "aria-[current=page]:after:opacity-100",
      ])
    );
    expect(TAB_UNDERLINE_CURRENT).not.toContain("aria-selected");
  });

  it("both draw one 2px underline at the bottom edge, hidden when inactive, with an inset ring", () => {
    for (const value of [TAB_UNDERLINE_SELECTED, TAB_UNDERLINE_CURRENT]) {
      expect(classes(value)).toEqual(
        expect.arrayContaining([
          "after:absolute",
          "after:inset-x-0",
          "after:bottom-0",
          "after:h-0.5",
          "after:bg-foreground",
          "after:opacity-0",
          "focus-visible:ring-inset",
        ])
      );
    }
  });

  it("the two variants differ only in the attribute they key on", () => {
    expect(TAB_UNDERLINE_CURRENT.split("aria-[current=page]:").join("aria-selected:")).toBe(
      TAB_UNDERLINE_SELECTED
    );
  });

  it("keeps the grouped tab bar's 642C trigger class byte-identical", () => {
    expect(TAB_UNDERLINE_SELECTED).toBe(
      [
        "relative shrink-0 cursor-pointer rounded-sm px-2 pt-2 pb-3 text-sm font-medium whitespace-nowrap outline-none transition-colors motion-reduce:transition-none",
        "text-muted-foreground hover:text-foreground aria-selected:text-foreground",
        "after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:rounded-full after:bg-foreground after:opacity-0 after:transition-opacity aria-selected:after:opacity-100 motion-reduce:after:transition-none",
        "focus-visible:ring-ring focus-visible:ring-2 focus-visible:ring-inset",
      ].join(" ")
    );
  });

  it("keeps the scroll area, scrollbar and fade recipes and the 32px fade width", () => {
    expect(TAB_STRIP_SCROLL_AREA_CLASS).toContain(
      "has-[[data-slot=scroll-area-scrollbar][data-orientation=horizontal]]:pb-1.5"
    );
    expect(TAB_STRIP_SCROLLBAR_CLASS).toBe("h-1.5");
    expect(classes(TAB_EDGE_FADE_CLASS)).toEqual(
      expect.arrayContaining(["pointer-events-none", "w-8", "data-[visible=true]:opacity-100"])
    );
    expect(TAB_EDGE_FADE_PX).toBe(32);
  });
});

describe("tab-underline DOM helpers", () => {
  function rect(left: number, width: number) {
    return { left, right: left + width, width } as DOMRect;
  }

  /** A 300px viewport at x=0 holding one item at [itemStart, itemStart + 100) in row coordinates. */
  function setup(itemStart: number, scrollLeft = 0) {
    const viewport = document.createElement("div");
    const item = document.createElement("a");
    viewport.appendChild(item);
    viewport.scrollLeft = scrollLeft;
    viewport.getBoundingClientRect = () => rect(0, 300);
    item.getBoundingClientRect = () => rect(itemStart - viewport.scrollLeft, 100);
    return { viewport, item };
  }

  it("findScrollViewport returns the scroll-area viewport, or null", () => {
    const root = document.createElement("div");
    expect(findScrollViewport(root)).toBeNull();
    expect(findScrollViewport(null)).toBeNull();
    const viewport = document.createElement("div");
    viewport.setAttribute("data-slot", "scroll-area-viewport");
    root.appendChild(viewport);
    expect(findScrollViewport(root)).toBe(viewport);
  });

  it("scrolls an item past the end edge clear of the end fade (scrollLeft only)", () => {
    const { viewport, item } = setup(400);
    scrollItemClearOfFades(viewport, item);
    expect(viewport.scrollLeft).toBe(500 - 300 + TAB_EDGE_FADE_PX);
  });

  it("scrolls an item past the start edge back clear of the start fade, clamped at 0", () => {
    const { viewport, item } = setup(100, 150);
    scrollItemClearOfFades(viewport, item);
    expect(viewport.scrollLeft).toBe(100 - TAB_EDGE_FADE_PX);

    const first = setup(0, 50);
    scrollItemClearOfFades(first.viewport, first.item);
    expect(first.viewport.scrollLeft).toBe(0);
  });

  it("leaves the row alone when the item is already clear of both fades", () => {
    const { viewport, item } = setup(100);
    scrollItemClearOfFades(viewport, item);
    expect(viewport.scrollLeft).toBe(0);
  });
});
