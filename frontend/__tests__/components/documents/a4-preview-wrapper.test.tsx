import { describe, it, expect, vi, afterEach, beforeAll } from "vitest";
import { act, cleanup, render, screen } from "@testing-library/react";
import { A4PreviewWrapper } from "@/components/documents/a4-preview-wrapper";

beforeAll(() => {
  global.ResizeObserver = class ResizeObserver {
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
    constructor() {}
  } as unknown as typeof globalThis.ResizeObserver;
});

describe("A4PreviewWrapper", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders iframe with srcDoc set to the provided html", () => {
    const testHtml = "<p>Test document content</p>";
    render(<A4PreviewWrapper html={testHtml} />);

    const iframe = screen.getByTitle("Document Preview");
    expect(iframe).toBeInTheDocument();
    expect(iframe).toHaveAttribute("srcdoc", testHtml);
  });

  it("applies data-testid attribute to the outer container", () => {
    render(<A4PreviewWrapper html="<p>Test</p>" />);

    const wrapper = screen.getByTestId("a4-preview-wrapper");
    expect(wrapper).toBeInTheDocument();
  });

  it("renders the dark surround container", () => {
    render(<A4PreviewWrapper html="<p>Test</p>" />);

    const wrapper = screen.getByTestId("a4-preview-wrapper");
    expect(wrapper.className).toContain("bg-slate-800");
  });

  it("renders the paper shadow effect", () => {
    render(<A4PreviewWrapper html="<p>Test</p>" />);

    const wrapper = screen.getByTestId("a4-preview-wrapper");
    const paperDiv = wrapper.querySelector(".shadow-xl");
    expect(paperDiv).toBeInTheDocument();
  });

  it("disconnects ResizeObserver on unmount", () => {
    const disconnectSpy = vi.fn();
    global.ResizeObserver = class {
      observe = vi.fn();
      unobserve = vi.fn();
      disconnect = disconnectSpy;
      constructor() {}
    } as unknown as typeof globalThis.ResizeObserver;
    const { unmount } = render(<A4PreviewWrapper html="<p>Test</p>" />);
    unmount();
    expect(disconnectSpy).toHaveBeenCalled();
  });
  // G-C4 (Phase 88 626F): the page must be sized from the wrapper's CONTENT box. Sizing it from the
  // padding-inclusive clientWidth made it 48px (p-6 × 2) wider than the box it sits in, so a host
  // whose width follows its min-content (the SoA dialog's <form> grid item) grew on every
  // ResizeObserver notification — the runaway preview.
  it("padded container: sizes the page to the content box and does not grow on repeat notifications", () => {
    let callback: ResizeObserverCallback | null = null;
    global.ResizeObserver = class {
      observe = vi.fn();
      unobserve = vi.fn();
      disconnect = vi.fn();
      constructor(cb: ResizeObserverCallback) {
        callback = cb;
      }
    } as unknown as typeof globalThis.ResizeObserver;

    render(<A4PreviewWrapper html="<p>Test</p>" />);
    const wrapper = screen.getByTestId("a4-preview-wrapper");
    // Border box 548px wide; p-6 leaves a 500px content box.
    Object.defineProperty(wrapper, "clientWidth", { configurable: true, value: 548 });
    const notify = (width: number) =>
      act(() =>
        callback!([{ contentRect: { width } } as ResizeObserverEntry], {} as ResizeObserver)
      );
    const page = () => wrapper.firstElementChild as HTMLElement; // the sized outer box

    notify(500);
    expect(parseFloat(page().style.width)).toBeCloseTo(500, 5); // content box, not clientWidth (548)
    // A host that follows its min-content re-notifies with the same content width: no growth.
    notify(500);
    notify(500);
    expect(parseFloat(page().style.width)).toBeCloseTo(500, 5);
    // The wrapper never widens its host.
    expect(wrapper.className).toContain("min-w-0");
    expect(wrapper.className).toContain("overflow-hidden");
  });
});
