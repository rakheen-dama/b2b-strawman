import { describe, it, expect, vi, afterEach } from "vitest";
import { useRef } from "react";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { useEdgeFades } from "@/components/shared/use-edge-fades";

// Phase 91 664A: the resize branch of useEdgeFades (the 642C grouped-tab-bar behaviour, mirrored
// from the "keeps the active trigger in view when the viewport resizes" suite). A bare harness
// stands in for the ScrollArea: a `data-slot="scroll-area-viewport"` element around one content
// element holding five 100px tabs (a 500px row). Layout is stubbed; happy-dom does none.

const TAB_W = 100;
const TAB_COUNT = 5;
const VIEWPORT_LEFT = 24;
const EDGE_FADE_PX = 32;

const layout = { viewportWidth: 300 };

function Harness({ activeIndex }: { activeIndex: number }) {
  const ref = useRef<HTMLDivElement>(null);
  const { fades, onFocus } = useEdgeFades(ref, {
    activeSelector: '[role="tab"][aria-selected="true"]',
    itemSelector: '[role="tab"]',
    activeKey: String(activeIndex),
  });
  return (
    <div ref={ref} data-fade-start={fades.start} data-fade-end={fades.end} data-testid="area">
      <div data-slot="scroll-area-viewport">
        <div role="tablist" onFocus={onFocus}>
          {Array.from({ length: TAB_COUNT }, (_, i) => (
            <button
              key={i}
              type="button"
              role="tab"
              aria-selected={i === activeIndex}
              data-testid={`tab-${i}`}
            >
              {`Tab ${i}`}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

function isViewport(el: Element) {
  return el.getAttribute("data-slot") === "scroll-area-viewport";
}

function tabIndex(el: Element) {
  return el.getAttribute("role") === "tab" ? Number(el.getAttribute("data-testid")!.slice(4)) : -1;
}

function rect(left: number, width: number) {
  return {
    left,
    right: left + width,
    x: left,
    width,
    top: 0,
    bottom: 36,
    y: 0,
    height: 36,
    toJSON: () => ({}),
  } as DOMRect;
}

function stubLayout(viewportWidth: number) {
  layout.viewportWidth = viewportWidth;
  const getBoundingClientRect = Element.prototype.getBoundingClientRect;
  vi.spyOn(Element.prototype, "getBoundingClientRect").mockImplementation(function (this: Element) {
    if (isViewport(this)) return rect(VIEWPORT_LEFT, layout.viewportWidth);
    const viewport = this.closest<HTMLElement>('[data-slot="scroll-area-viewport"]');
    if (tabIndex(this) === -1 || !viewport) return getBoundingClientRect.call(this);
    return rect(VIEWPORT_LEFT + tabIndex(this) * TAB_W - viewport.scrollLeft, TAB_W);
  });
  const clientWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "clientWidth")!.get!;
  vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockImplementation(function (
    this: HTMLElement
  ) {
    return isViewport(this) ? layout.viewportWidth : (clientWidth.call(this) as number);
  });
  const scrollWidth = Object.getOwnPropertyDescriptor(Element.prototype, "scrollWidth")!.get!;
  vi.spyOn(Element.prototype, "scrollWidth", "get").mockImplementation(function (this: Element) {
    return isViewport(this) ? TAB_W * TAB_COUNT : (scrollWidth.call(this) as number);
  });
}

/** A ResizeObserver that reports on observe (as a browser does) and on `fireResize()`. */
const observers = new Set<CapturingResizeObserver>();
class CapturingResizeObserver {
  constructor(private readonly callback: ResizeObserverCallback) {}
  observe() {
    observers.add(this);
    this.report();
  }
  unobserve() {}
  disconnect() {
    observers.delete(this);
  }
  report() {
    this.callback([], this as unknown as ResizeObserver);
  }
}

function fireResize() {
  act(() => observers.forEach((observer) => observer.report()));
}

function getViewport(container: HTMLElement) {
  return container.querySelector<HTMLElement>('[data-slot="scroll-area-viewport"]')!;
}

afterEach(() => {
  cleanup();
  observers.clear();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("useEdgeFades on viewport resize", () => {
  it("scrolls the active item clear of the end fade when the viewport width changes", () => {
    vi.stubGlobal("ResizeObserver", CapturingResizeObserver);
    stubLayout(600); // the 500px row fits
    const { container } = render(<Harness activeIndex={4} />);
    const viewport = getViewport(container);
    expect(viewport.scrollLeft).toBe(0);
    expect(screen.getByTestId("area")).toHaveAttribute("data-fade-end", "false");

    // The window narrows: the active Tab 4 (400–500px) would be hidden past the end edge.
    layout.viewportWidth = 300;
    fireResize();
    expect(viewport.scrollLeft).toBe(500 - 300 + EDGE_FADE_PX);
  });

  it("does not move the row on a resize tick at an unchanged viewport width", () => {
    vi.stubGlobal("ResizeObserver", CapturingResizeObserver);
    stubLayout(300);
    const { container } = render(<Harness activeIndex={4} />);
    const viewport = getViewport(container);
    expect(viewport.scrollLeft).toBe(500 - 300 + EDGE_FADE_PX);

    // The user scrolls back to the start; the active tab is now off-screen.
    viewport.scrollLeft = 0;
    fireEvent.scroll(viewport);
    expect(screen.getByTestId("area")).toHaveAttribute("data-fade-end", "true");

    // A content resize at the same viewport width leaves the user's scroll alone.
    fireResize();
    expect(viewport.scrollLeft).toBe(0);
  });

  it("keeps the focused item clear of the fades instead of the active one", () => {
    vi.stubGlobal("ResizeObserver", CapturingResizeObserver);
    stubLayout(600);
    const { container } = render(<Harness activeIndex={4} />);
    const viewport = getViewport(container);

    // Keyboard focus sits on Tab 3 (300–400px), not on the active Tab 4.
    screen.getByTestId("tab-3").focus();
    expect(viewport.scrollLeft).toBe(0);

    layout.viewportWidth = 250;
    fireResize();
    expect(viewport.scrollLeft).toBe(400 - 250 + EDGE_FADE_PX);
  });

  it("falls back to the active item when focus is outside the row", () => {
    vi.stubGlobal("ResizeObserver", CapturingResizeObserver);
    stubLayout(600);
    const outside = document.createElement("button");
    document.body.appendChild(outside);
    const { container } = render(<Harness activeIndex={4} />);
    const viewport = getViewport(container);

    outside.focus();
    layout.viewportWidth = 250;
    fireResize();
    expect(viewport.scrollLeft).toBe(500 - 250 + EDGE_FADE_PX);
    outside.remove();
  });

  it("never calls scrollIntoView on resize", () => {
    vi.stubGlobal("ResizeObserver", CapturingResizeObserver);
    stubLayout(600);
    const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
    render(<Harness activeIndex={4} />);
    screen.getByTestId("tab-3").focus();

    layout.viewportWidth = 250;
    fireResize();
    expect(scrollIntoView).not.toHaveBeenCalled();
  });
});
