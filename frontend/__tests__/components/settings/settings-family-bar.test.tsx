import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";

vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/org/acme/settings/firm"),
}));

import { usePathname } from "next/navigation";
import { SettingsFamilyBar } from "@/components/settings/settings-family-bar";
import { SETTINGS_FAMILIES } from "@/lib/settings-nav";
import { TAB_EDGE_FADE_PX } from "@/components/shared/tab-underline";

// Phase 91 664A (K-02): the below-`md` settings family switch. Assert roles, names, hrefs and
// state attributes, never colour classes.

const mockUsePathname = vi.mocked(usePathname);

function renderBar(pathname: string) {
  mockUsePathname.mockReturnValue(pathname);
  return render(<SettingsFamilyBar slug="acme" />);
}

const bar = () => screen.getByRole("navigation", { name: "Settings navigation" });

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("SettingsFamilyBar", () => {
  it("lists the five families as links, in SETTINGS_FAMILIES order, inside one named nav", () => {
    renderBar("/org/acme/settings/firm");
    expect(screen.getAllByRole("navigation")).toHaveLength(1);
    const links = within(bar()).getAllByRole("link");
    expect(links.map((a) => a.textContent)).toEqual([
      "Firm",
      "Matters",
      "Documents",
      "Fees & Trust",
      "Connections",
    ]);
    expect(links.map((a) => a.getAttribute("href"))).toEqual(
      SETTINGS_FAMILIES.map((family) => family.href("acme"))
    );
    expect(links.map((a) => a.getAttribute("href"))).toEqual([
      "/org/acme/settings/firm",
      "/org/acme/settings/matters",
      "/org/acme/settings/documents",
      "/org/acme/settings/fees",
      "/org/acme/settings/connections",
    ]);
  });

  it("is a list of links — no tabs, no headings", () => {
    renderBar("/org/acme/settings/firm");
    expect(within(bar()).getByRole("list")).toBeInTheDocument();
    expect(within(bar()).queryAllByRole("tab")).toEqual([]);
    expect(within(bar()).queryAllByRole("tablist")).toEqual([]);
    expect(within(bar()).queryAllByRole("heading")).toEqual([]);
  });

  it("marks only the active family aria-current=page", () => {
    renderBar("/org/acme/settings/fees");
    expect(screen.getByRole("link", { name: "Fees & Trust" })).toHaveAttribute(
      "aria-current",
      "page"
    );
    const current = within(bar())
      .getAllByRole("link")
      .filter((a) => a.hasAttribute("aria-current"));
    expect(current).toHaveLength(1);
  });

  it("marks the family on a kept sub-route (connections/xero/sync-log)", () => {
    renderBar("/org/acme/settings/connections/xero/sync-log");
    expect(screen.getByRole("link", { name: "Connections" })).toHaveAttribute(
      "aria-current",
      "page"
    );
  });

  it("marks no family on the settings index", () => {
    renderBar("/org/acme/settings");
    for (const a of within(bar()).getAllByRole("link")) {
      expect(a).not.toHaveAttribute("aria-current");
    }
  });

  it("shows every family to everyone — it needs no capability context", () => {
    // Rendered without CapabilityProvider / OrgProfileProvider: the bar applies no gate.
    renderBar("/org/acme/settings/matters");
    expect(within(bar()).getAllByRole("link")).toHaveLength(SETTINGS_FAMILIES.length);
  });

  it("keeps the edge fades decorative and out of the hit-test path", () => {
    const { container } = renderBar("/org/acme/settings/firm");
    for (const slot of ["settings-family-bar-fade-start", "settings-family-bar-fade-end"]) {
      const fade = container.querySelector(`[data-slot="${slot}"]`)!;
      expect(fade).toHaveAttribute("aria-hidden", "true");
      expect(fade).toHaveAttribute("data-visible", "false");
      expect(fade).toHaveClass("pointer-events-none");
    }
  });

  it("shows a fade only at an edge with links hidden past it", () => {
    const { container } = renderBar("/org/acme/settings/firm");
    const viewport = container.querySelector<HTMLElement>('[data-slot="scroll-area-viewport"]')!;
    Object.defineProperty(viewport, "scrollWidth", { configurable: true, value: 600 });
    Object.defineProperty(viewport, "clientWidth", { configurable: true, value: 300 });
    const fade = (edge: string) =>
      container
        .querySelector(`[data-slot="settings-family-bar-fade-${edge}"]`)!
        .getAttribute("data-visible");

    viewport.scrollLeft = 0;
    fireEvent.scroll(viewport);
    expect([fade("start"), fade("end")]).toEqual(["false", "true"]);

    viewport.scrollLeft = 300;
    fireEvent.scroll(viewport);
    expect([fade("start"), fade("end")]).toEqual(["true", "false"]);
  });

  describe("keeps the active family in view (scrollLeft only)", () => {
    // happy-dom has no layout: five 100px links laid out left to right in a 300px viewport.
    const LINK_W = 100;
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
        if (this.tagName !== "A" || !viewport) return original.call(this);
        const index = Array.from(viewport.querySelectorAll("a")).indexOf(this as HTMLAnchorElement);
        return box(index * LINK_W - viewport.scrollLeft, LINK_W);
      });
    }

    it("scrolls an off-screen active family clear of the end fade on mount", () => {
      stubLayout();
      const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
      const { container } = renderBar("/org/acme/settings/connections");
      const viewport = container.querySelector<HTMLElement>('[data-slot="scroll-area-viewport"]')!;
      // Connections is the 5th link (400–500px).
      expect(viewport.scrollLeft).toBe(500 - VIEWPORT_W + TAB_EDGE_FADE_PX);
      expect(scrollIntoView).not.toHaveBeenCalled();
    });

    it("leaves the row alone when the active family is already in view", () => {
      stubLayout();
      const { container } = renderBar("/org/acme/settings/firm");
      const viewport = container.querySelector<HTMLElement>('[data-slot="scroll-area-viewport"]')!;
      expect(viewport.scrollLeft).toBe(0);
    });

    it("scrolls a keyboard-focused family clear of the end fade", () => {
      stubLayout();
      const { container } = renderBar("/org/acme/settings/firm");
      const viewport = container.querySelector<HTMLElement>('[data-slot="scroll-area-viewport"]')!;
      screen.getByRole("link", { name: "Fees & Trust" }).focus(); // 300–400px
      expect(viewport.scrollLeft).toBe(400 - VIEWPORT_W + TAB_EDGE_FADE_PX);
    });
  });
});
