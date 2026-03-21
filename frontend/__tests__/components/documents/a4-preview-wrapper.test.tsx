import { describe, it, expect, vi, afterEach, beforeAll } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
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
    } as any;
    const { unmount } = render(<A4PreviewWrapper html="<p>Test</p>" />);
    unmount();
    expect(disconnectSpy).toHaveBeenCalled();
  });
});
