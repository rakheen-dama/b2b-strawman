// @vitest-environment node
import { describe, expect, it } from "vitest";
import {
  contrastRatio,
  hexChroma,
  hexHue,
  hueDistance,
  loadThemes,
  oklchToHex,
  parseOklch,
  tokenHex,
  type TokenMap,
} from "./color-math";

// Phase 90 / 637A (architecture §15.2.5, §15.6.1; ADR-370, ADR-371): parses the real token files
// (`packages/ui/styles/globals.css` + the sidebar block of `frontend/app/globals.css`), resolves
// every var()/color-mix() to sRGB and checks WCAG ratios in BOTH themes — AA (4.5:1) for token
// pairs used as text on a surface, 3:1 for focus rings and chart marks. No value is hard-coded
// here: a later retune that breaks AA fails this file.

const { light, dark, shared, app } = loadThemes();
const THEMES: ["light" | "dark", TokenMap][] = [
  ["light", light],
  ["dark", dark],
];
const STATUSES = ["success", "warning", "danger", "info"] as const;

const ratio = (fg: string, bg: string, vars: TokenMap): number =>
  contrastRatio(tokenHex(fg, vars), tokenHex(bg, vars));

/** Every §15.2.5 shared token is written in :root AND .dark (no silent light-only token). */
const SHARED_THEMED_TOKENS = [
  "--background",
  "--foreground",
  "--card",
  "--popover",
  "--muted",
  "--secondary",
  "--muted-foreground",
  "--border",
  "--input",
  "--ring",
  "--shadow-card",
  "--shadow-overlay",
  ...[1, 2, 3, 4, 5].map((i) => `--chart-${i}`),
  ...[100, 200, 300, 400, 500].map((n) => `--seq-${n}`),
  "--aging-current",
  "--aging-d30",
  "--aging-d60",
  "--aging-d90plus",
  "--div-pos",
  "--div-neg",
  ...STATUSES.flatMap((s) => [`--${s}`, `--${s}-soft`, `--${s}-foreground`]),
];
const SIDEBAR_TOKENS = [
  "--sidebar",
  "--sidebar-foreground",
  "--sidebar-primary",
  "--sidebar-primary-foreground",
  "--sidebar-accent",
  "--sidebar-accent-foreground",
  "--sidebar-border",
  "--sidebar-ring",
];

describe("colour-maths helper self-test", () => {
  // Guards against a broken conversion making every assertion below pass vacuously.
  it("WCAG contrast of white on black is 21:1 and of a colour on itself 1:1", () => {
    expect(contrastRatio("#ffffff", "#000000")).toBeCloseTo(21, 5);
    expect(contrastRatio("#009787", "#009787")).toBeCloseTo(1, 5);
  });

  it("OKLCH → sRGB hits the white point and a known out-of-gamut clip", () => {
    expect(oklchToHex(parseOklch("oklch(100% 0 0)")!)).toBe("#ffffff");
    expect(oklchToHex(parseOklch("oklch(0% 0 0)")!)).toBe("#000000");
    // oklch(60% 0.12 183) is just outside sRGB; per-channel clip gives the doc's #009787.
    expect(oklchToHex(parseOklch("oklch(60% 0.12 183)")!)).toBe("#009787");
  });
});

describe("token definitions", () => {
  it.each(SHARED_THEMED_TOKENS)("%s is defined in :root and .dark", (t) => {
    expect(shared.root[t], `${t} in :root`).toBeDefined();
    expect(shared.dark[t], `${t} in .dark`).toBeDefined();
  });

  it.each(SIDEBAR_TOKENS)("%s is defined in the app :root and .dark", (t) => {
    expect(app.root[t], `${t} in :root`).toBeDefined();
    expect(app.dark[t], `${t} in .dark`).toBeDefined();
  });

  it("--app-header-height is an app token (frontend only, not shared with the portal)", () => {
    expect(app.root["--app-header-height"]).toBe("3.5rem");
    expect(shared.root["--app-header-height"]).toBeUndefined();
  });
});

for (const [mode, vars] of THEMES) {
  describe(`${mode} theme — AA text contrast (≥ 4.5:1)`, () => {
    it.each([
      ["--foreground", "--background"],
      ["--foreground", "--card"],
      ["--card-foreground", "--card"],
      ["--popover-foreground", "--popover"],
      ["--muted-foreground", "--background"],
      ["--muted-foreground", "--card"],
    ])("%s on %s", (fg, bg) => {
      expect(ratio(fg, bg, vars)).toBeGreaterThanOrEqual(4.5);
    });

    it.each(STATUSES)("--%s-foreground on its -soft fill", (s) => {
      expect(ratio(`--${s}-foreground`, `--${s}-soft`, vars)).toBeGreaterThanOrEqual(4.5);
    });
  });

  describe(`${mode} theme — non-text contrast (≥ 3:1)`, () => {
    it.each(["--background", "--card", "--sidebar"])("--ring vs %s", (bg) => {
      expect(ratio("--ring", bg, vars)).toBeGreaterThanOrEqual(3);
    });

    it("--sidebar-ring vs --sidebar", () => {
      expect(ratio("--sidebar-ring", "--sidebar", vars)).toBeGreaterThanOrEqual(3);
    });

    it.each([1, 2, 3, 4, 5])("--chart-%i on --card", (i) => {
      expect(ratio(`--chart-${i}`, "--card", vars)).toBeGreaterThanOrEqual(3);
    });
  });

  describe(`${mode} theme — status soft fills`, () => {
    // A -soft badge fill must read as a tint of its own status hue. Mixing `in oklch` lets the
    // card's hue (260, at 86% weight) win: success/warning/danger fills drift 95–155° to
    // blue/lavender. Mixing `in oklab` (637A ruling 2) keeps them within 26° (the dark card's own
    // slight blue cast accounts for the largest drift), so 30° separates the two.
    it.each(STATUSES)("--%s-soft keeps its status hue (within 30°)", (s) => {
      const soft = tokenHex(`--${s}-soft`, vars);
      expect(hexChroma(soft)).toBeGreaterThan(0.01);
      expect(hueDistance(hexHue(soft), hexHue(tokenHex(`--${s}`, vars)))).toBeLessThanOrEqual(30);
    });
  });
}
