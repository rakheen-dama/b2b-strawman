import { vi } from "vitest";
import "@testing-library/jest-dom/vitest";

// Mock next-auth — it requires next/server which isn't available in vitest/happy-dom
vi.mock("next-auth", () => ({
  default: () => ({
    handlers: { GET: vi.fn(), POST: vi.fn() },
    auth: vi.fn().mockResolvedValue(null),
    signIn: vi.fn(),
    signOut: vi.fn(),
  }),
}));

vi.mock("next-auth/providers/keycloak", () => ({
  default: vi.fn(() => ({})),
}));

// Polyfill pointer capture methods for Radix UI components (Select, etc.) in happy-dom
if (typeof Element !== "undefined") {
  Element.prototype.hasPointerCapture =
    Element.prototype.hasPointerCapture || (() => false);
  Element.prototype.setPointerCapture =
    Element.prototype.setPointerCapture || (() => {});
  Element.prototype.releasePointerCapture =
    Element.prototype.releasePointerCapture || (() => {});
}

// Polyfill scrollIntoView for Radix Select items in happy-dom
if (typeof Element !== "undefined") {
  Element.prototype.scrollIntoView =
    Element.prototype.scrollIntoView || (() => {});
}
