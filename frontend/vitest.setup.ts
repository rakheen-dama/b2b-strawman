import "@testing-library/jest-dom/vitest";
import { vi } from "vitest";
import React from "react";

// Mock next/dynamic to use React.lazy + Suspense in tests.
// Tests that render dynamically-imported components should use
// findBy* queries or waitFor() to handle the async resolution.
vi.mock("next/dynamic", () => ({
  __esModule: true,
  default: (importFn: () => Promise<unknown>) => {
    const LazyComponent = React.lazy(() =>
      importFn().then((mod) => {
        if (typeof mod === "function") {
          return { default: mod as React.ComponentType };
        }
        const m = mod as Record<string, unknown>;
        return { default: (m.default ?? mod) as unknown as React.ComponentType };
      })
    );

    const DynamicComponent = (props: Record<string, unknown>) =>
      React.createElement(
        React.Suspense,
        { fallback: null },
        React.createElement(LazyComponent, props)
      );
    DynamicComponent.displayName = "DynamicComponent";
    return DynamicComponent;
  },
}));

// Polyfill pointer capture methods for Radix UI components (Select, etc.) in happy-dom
if (typeof Element !== "undefined") {
  Element.prototype.hasPointerCapture = Element.prototype.hasPointerCapture || (() => false);
  Element.prototype.setPointerCapture = Element.prototype.setPointerCapture || (() => {});
  Element.prototype.releasePointerCapture = Element.prototype.releasePointerCapture || (() => {});
}

// Polyfill scrollIntoView for Radix Select items in happy-dom
if (typeof Element !== "undefined") {
  Element.prototype.scrollIntoView = Element.prototype.scrollIntoView || (() => {});
}
