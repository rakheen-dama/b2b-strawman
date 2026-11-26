import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/settings/firm",
}));

import { SettingsFamilyTabs } from "@/components/settings/settings-family-tabs";
import { TAB_EDGE_FADE_PX } from "@/components/shared/tab-underline";

// Phase 91 664A (K-101): the family tab strip on the shared 642C underline in a horizontal
// ScrollArea with edge fades. Roles, manual activation and focus are pinned in
// __tests__/settings/firm-family.test.tsx; this file pins the strip's own structure.

const TABS = [
  { id: "general", label: "General" },
  { id: "roles", label: "Roles" },
  { id: "audit-trail", label: "Audit trail" },
  { id: "data-protection", label: "Data protection" },
  { id: "subscription", label: "Subscription" },
];

function renderStrip(activeTab = "general") {
  return render(
    <SettingsFamilyTabs label="Strip settings" tabs={TABS} activeTab={activeTab}>
      <p>strip panel</p>
    </SettingsFamilyTabs>
  );
}

function viewportOf(container: HTMLElement) {
  return container.querySelector<HTMLElement>('[data-slot="scroll-area-viewport"]')!;
}

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("SettingsFamilyTabs underline strip (664A)", () => {
  it("renders the tablist inside a ScrollArea viewport, as a line list (no pill tray)", () => {
    renderStrip();
    const list = screen.getByRole("tablist", { name: "Strip settings" });
    expect(list.closest('[data-slot="scroll-area-viewport"]')).not.toBeNull();
    expect(list).toHaveAttribute("data-variant", "line");
    expect(list).not.toHaveClass("bg-muted");
  });

  it("draws exactly one underline per trigger, at the bottom edge, keyed on aria-selected", () => {
    renderStrip("roles");
    for (const tab of screen.getAllByRole("tab")) {
      expect(tab).toHaveClass("after:bottom-0", "after:h-0.5", "aria-selected:after:opacity-100");
      // The shadcn trigger's own underline sits 5px below the trigger, where the scroll viewport
      // would clip it; it must be overridden, not doubled.
      expect(tab.className).not.toContain("bottom-[-5px]");
      // Outside outline would be clipped by the viewport: the ring is inset instead.
      expect(tab).toHaveClass("focus-visible:ring-inset", "focus-visible:outline-none");
      expect(tab).not.toHaveClass("flex-1");
    }
    expect(screen.getByRole("tab", { name: "Roles" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tab", { name: "General" })).toHaveAttribute("aria-selected", "false");
  });

  it("keeps the fades decorative, out of the hit-test path, and only at an overflowing edge", () => {
    const { container } = renderStrip();
    const fade = (edge: string) =>
      container.querySelector(`[data-slot="settings-family-tabs-fade-${edge}"]`)!;
    for (const edge of ["start", "end"]) {
      expect(fade(edge)).toHaveAttribute("aria-hidden", "true");
      expect(fade(edge)).toHaveAttribute("data-visible", "false");
      expect(fade(edge)).toHaveClass("pointer-events-none");
    }

    const viewport = viewportOf(container);
    Object.defineProperty(viewport, "scrollWidth", { configurable: true, value: 700 });
    Object.defineProperty(viewport, "clientWidth", { configurable: true, value: 300 });
    viewport.scrollLeft = 200;
    fireEvent.scroll(viewport);
    expect(fade("start")).toHaveAttribute("data-visible", "true");
    expect(fade("end")).toHaveAttribute("data-visible", "true");
  });

  it("is not sticky (ADR-394)", () => {
    const { container } = renderStrip();
    const list = screen.getByRole("tablist", { name: "Strip settings" });
    for (let el: HTMLElement | null = list; el && el !== container; el = el.parentElement) {
      expect(el).not.toHaveClass("sticky");
    }
  });

  describe("keeps the active tab clear of the edge fades (scrollLeft only)", () => {
    // happy-dom has no layout: five 100px triggers left to right in a 300px viewport.
    const TAB_W = 100;
    const VIEWPORT_W = 300;

    function stubLayout() {
      const original = Element.prototype.getBoundingClientRect;
      vi.spyOn(Element.prototype, "getBoundingClientRect").mockImplementation(function (
        this: Element
      ) {
        const viewport = this.closest<HTMLElement>('[data-slot="scroll-area-viewport"]');
        const box = (left: number, width: number) =>
          ({ left, right: left + width, width, top: 0, bottom: 36, height: 36 }) as DOMRect;
        if (this.getAttribute("data-slot") === "scroll-area-viewport") return box(0, VIEWPORT_W);
        if (this.getAttribute("role") !== "tab" || !viewport) return original.call(this);
        const index = Array.from(viewport.querySelectorAll('[role="tab"]')).indexOf(this);
        return box(index * TAB_W - viewport.scrollLeft, TAB_W);
      });
    }

    it("scrolls a deep-linked active tab into view on mount, never with scrollIntoView", () => {
      stubLayout();
      const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
      const { container } = renderStrip("subscription"); // 400–500px
      expect(viewportOf(container).scrollLeft).toBe(500 - VIEWPORT_W + TAB_EDGE_FADE_PX);
      expect(scrollIntoView).not.toHaveBeenCalled();
    });

    it("scrolls a keyboard-focused tab clear of the end fade", () => {
      stubLayout();
      const { container } = renderStrip("general");
      expect(viewportOf(container).scrollLeft).toBe(0);
      screen.getByRole("tab", { name: "Data protection" }).focus(); // 300–400px
      expect(viewportOf(container).scrollLeft).toBe(400 - VIEWPORT_W + TAB_EDGE_FADE_PX);
    });
  });
});
