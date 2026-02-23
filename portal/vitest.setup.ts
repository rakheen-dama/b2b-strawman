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
