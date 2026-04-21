import "@testing-library/jest-dom/vitest";

// Polyfill pointer capture methods for Radix UI components in happy-dom
if (typeof Element !== "undefined") {
  Element.prototype.hasPointerCapture =
    Element.prototype.hasPointerCapture || (() => false);
  Element.prototype.setPointerCapture =
    Element.prototype.setPointerCapture || (() => {});
  Element.prototype.releasePointerCapture =
    Element.prototype.releasePointerCapture || (() => {});
}

// Polyfill scrollIntoView for Radix components in happy-dom
if (typeof Element !== "undefined") {
  Element.prototype.scrollIntoView =
    Element.prototype.scrollIntoView || (() => {});
}

// Polyfill matchMedia for components that read CSS breakpoints via JS.
// Default: mobile-first — queries evaluate as false (narrow viewport).
// Tests can override via vi.stubGlobal or by reassigning window.matchMedia.
if (typeof window !== "undefined" && !window.matchMedia) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    configurable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }),
  });
}
