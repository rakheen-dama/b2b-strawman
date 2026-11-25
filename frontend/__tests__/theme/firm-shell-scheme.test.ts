// @vitest-environment node
import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { APP_CSS, FIRM_LIGHT_SELECTOR, FRONTEND_ROOT } from "./color-math";

// Phase 91 / 661B (ADR-392; FD-1 ruled 2026-11-25: A1 with dark inset option (c)). The inset
// canvas is drawn by light-only CSS in app/globals.css instead of <Sidebar variant="inset">, which
// is not theme-aware (it also inset dark mode, F-661A-3). This file proves the CSS cannot match in
// dark, that it still mirrors ui/sidebar.tsx's inset classes, and that the firm shell carries the
// marker the scheme hangs on.

const read = (rel: string) => fs.readFileSync(path.join(FRONTEND_ROOT, rel), "utf8");
const css = fs.readFileSync(APP_CSS, "utf8").replace(/\/\*[\s\S]*?\*\//g, "");

/** Innermost `selectors { declarations }` rules; a rule nested in @media yields its own selectors. */
const rules = [...css.matchAll(/([^{};]+)\{([^{}]*)\}/g)].map((m) => ({
  selectors: m[1].split(",").map((s) => s.replace(/\s+/g, " ").trim()),
  body: m[2].replace(/\s+/g, " "),
}));
const shellRules = rules.filter((r) => r.selectors.some((s) => s.includes("data-app-shell")));
const ruleFor = (part: string) => shellRules.find((r) => r.selectors.some((s) => s.includes(part)));

/** The text of the `@media (width >= 48rem) { … }` block (brace-matched). */
function mediaBlock(): string {
  const start = css.indexOf("@media (width >= 48rem)");
  expect(start, "the inset @media block").toBeGreaterThan(-1);
  let depth = 0;
  for (let i = css.indexOf("{", start); i < css.length; i++) {
    if (css[i] === "{") depth++;
    if (css[i] === "}" && --depth === 0) return css.slice(start, i + 1);
  }
  throw new Error("unbalanced @media block");
}

describe("firm shell scheme is light-only (FD-1 option c)", () => {
  it("every rule that names data-app-shell starts with :root:not(.dark)", () => {
    expect(shellRules.length).toBeGreaterThanOrEqual(7); // token block + 6 inset rules
    for (const { selectors } of shellRules) {
      for (const s of selectors) expect(s.startsWith(":root:not(.dark)"), s).toBe(true);
    }
  });

  it("the token block is the one FIRM_LIGHT_SELECTOR block", () => {
    const blocks = shellRules.filter((r) => r.selectors.includes(FIRM_LIGHT_SELECTOR));
    expect(blocks).toHaveLength(1);
  });

  it("no rule uses data-scheme-candidate (the 661A prototype hook)", () => {
    expect(css).not.toContain("data-scheme-candidate");
  });
});

describe('firm shell inset mirrors ui/sidebar.tsx\'s variant="inset" (desktop only)', () => {
  it("sits inside @media (width >= 48rem), like the md: inset classes", () => {
    const media = mediaBlock();
    for (const slot of ["sidebar-container", "sidebar-gap", "sidebar-inset"]) {
      expect(media, slot).toContain(`[data-slot="${slot}"]`);
    }
  });

  it("wrapper: the rail colour behind the card (has-data-[variant=inset]:bg-sidebar)", () => {
    const wrapper = shellRules.find((r) =>
      r.selectors.some((s) => s === ':root:not(.dark) [data-app-shell="firm"]')
    );
    expect(wrapper?.body).toContain("background-color: var(--sidebar)");
  });

  it("container: horizontal p-2, no border, inset icon-mode width", () => {
    expect(ruleFor('[data-slot="sidebar-container"]')?.body).toMatch(/padding: 0 0\.5rem/);
    expect(ruleFor('[data-slot="sidebar-container"]')?.body).toContain("border-right-width: 0");
    const icon = shellRules.find((r) =>
      r.selectors.some((s) =>
        s.includes('[data-collapsible="icon"] > [data-slot="sidebar-container"]')
      )
    );
    expect(icon?.body).toContain("width: calc(var(--sidebar-width-icon) + 1rem + 2px)");
    const gap = ruleFor('> [data-slot="sidebar-gap"]');
    expect(gap?.body).toContain("width: calc(var(--sidebar-width-icon) + 1rem)");
  });

  it("card: m-2 ml-0, rounded-xl, shadow-sm; ml-2 when collapsed", () => {
    const card = shellRules.find((r) =>
      r.selectors.some((s) => s.endsWith('[data-app-shell="firm"] > [data-slot="sidebar-inset"]'))
    );
    expect(card?.body).toContain("margin: 0.5rem 0.5rem 0.5rem 0");
    expect(card?.body).toContain("border-radius: calc(var(--radius) + 4px)");
    expect(card?.body).toContain("0 1px 3px 0 rgb(0 0 0 / 0.1)");
    const collapsed = ruleFor('[data-state="collapsed"] ~ [data-slot="sidebar-inset"]');
    expect(collapsed?.body).toContain("margin-left: 0.5rem");
  });

  it("drift guard: ui/sidebar.tsx still renders the inset classes this CSS copies", () => {
    // If a shadcn CLI upgrade changes these, re-derive the CSS in app/globals.css first.
    const sidebar = read("components/ui/sidebar.tsx");
    for (const cls of [
      "has-data-[variant=inset]:bg-sidebar",
      "group-data-[collapsible=icon]:w-[calc(var(--sidebar-width-icon)+(--spacing(4)))]",
      "p-2 group-data-[collapsible=icon]:w-[calc(var(--sidebar-width-icon)+(--spacing(4))+2px)]",
      "group-data-[collapsible=icon]:w-(--sidebar-width-icon) group-data-[side=left]:border-r",
      "md:peer-data-[variant=inset]:m-2 md:peer-data-[variant=inset]:ml-0 md:peer-data-[variant=inset]:rounded-xl md:peer-data-[variant=inset]:shadow-sm md:peer-data-[variant=inset]:peer-data-[state=collapsed]:ml-2",
      'const SIDEBAR_WIDTH_ICON = "3rem"',
    ]) {
      expect(sidebar, cls).toContain(cls);
    }
  });
});

describe("firm shell source facts", () => {
  it('the org layout marks the shell: <SidebarProvider … data-app-shell="firm">', () => {
    expect(read("app/(app)/org/[slug]/layout.tsx")).toMatch(
      /<SidebarProvider\b[^>]*\bdata-app-shell="firm"/
    );
  });

  it("F-661A-1: no rail text uses the /50 or /60 opacity modifier any more", () => {
    for (const rel of ["components/shell/app-sidebar.tsx", "components/sidebar-user-footer.tsx"]) {
      expect(read(rel), rel).not.toMatch(/text-sidebar-foreground\/(50|60)\b/);
    }
  });
});
