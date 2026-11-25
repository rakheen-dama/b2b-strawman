// @vitest-environment node
import { describe, expect, it } from "vitest";
import {
  contrastRatio,
  hexHue,
  hueArc,
  hueDistance,
  loadThemes,
  oklchToHex,
  paintedHex,
  parseOklch,
  resolveColor,
  tokenHex,
  type TokenMap,
} from "./color-math";

// Phase 90 / 637A (architecture §15.2.5, §15.6.1; ADR-370, ADR-371): parses the real token files
// (`packages/ui/styles/globals.css` + the sidebar block of `frontend/app/globals.css`), resolves
// every var()/color-mix() to sRGB and checks WCAG ratios in BOTH themes — AA (4.5:1) for token
// pairs used as text on a surface, 3:1 for focus rings and chart marks. No value is hard-coded
// here: a later retune that breaks AA fails this file.

const { tailwind, light, dark, firmLight, shared, app } = loadThemes();
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
  "--button-accent",
  "--button-accent-foreground",
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
  "--sidebar-muted-foreground",
  "--sidebar-label-foreground",
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

  it("hue arc wraps around 0°/360° and leaves a non-wrapping set unchanged", () => {
    expect(hueArc([350, 10])).toBeCloseTo(20, 9);
    expect(hueArc([10, 350, 0, 5])).toBeCloseTo(20, 9);
    expect(hueArc([30, 50, 70])).toBeCloseTo(40, 9);
    expect(hueArc([0, 180])).toBeCloseTo(180, 9);
    expect(hueArc([42])).toBe(0);
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
      // Chroma of the unquantised mix (not the 8-bit hex): dark --warning-soft sits at ≈0.010, the
      // rounding noise floor, so the hex value would make a "not grey" guard flaky.
      expect(resolveColor(`--${s}-soft`, vars).C).toBeGreaterThan(0.005);
      const soft = tokenHex(`--${s}-soft`, vars);
      expect(hueDistance(hexHue(soft), hexHue(tokenHex(`--${s}`, vars)))).toBeLessThanOrEqual(30);
    });
  });
}

// Phase 91 / 661B (ADR-392; FD-1 ruled 2026-11-25: A1, dark inset option c; architecture
// §18.2.1, §18.6.1). The firm shell's light scheme is the scoped block FIRM_LIGHT_SELECTOR in
// frontend/app/globals.css. Ratios are compared unrounded (661A carry-over 7).

/** The A1 column of §18.2.1 + F-661A-1's two rail-text tokens — exactly what the block sets. */
const FIRM_LIGHT_TOKENS = [
  "--sidebar",
  "--sidebar-foreground",
  "--sidebar-primary",
  "--sidebar-primary-foreground",
  "--sidebar-accent",
  "--sidebar-accent-foreground",
  "--sidebar-border",
  "--sidebar-ring",
  "--sidebar-muted-foreground",
  "--sidebar-label-foreground",
  "--background",
  "--primary",
  "--primary-foreground",
  "--accent",
  "--accent-foreground",
  "--button-accent",
  "--button-accent-foreground",
];

interface Pair {
  name: string;
  floor: number;
  fg: (vars: TokenMap) => string;
  bg: (vars: TokenMap) => string;
}
const hex = (token: string) => (vars: TokenMap) => tokenHex(token, vars);
const rail = hex("--sidebar");
/** The active/hover fill as painted (dark's is white 6 % over the rail). */
const activeFill = (vars: TokenMap) => paintedHex("--sidebar-accent", vars, rail(vars));
const onRail = (token: string) => (vars: TokenMap) => paintedHex(token, vars, rail(vars));
const onFill = (token: string) => (vars: TokenMap) => paintedHex(token, vars, activeFill(vars));

/** §18.6.1 — every pair of the shell, plus F-661A-1's translucent rail text. */
const SHELL_PAIRS: Pair[] = [
  { name: "body text on canvas", floor: 4.5, fg: hex("--foreground"), bg: hex("--background") },
  { name: "body text on card", floor: 4.5, fg: hex("--card-foreground"), bg: hex("--card") },
  {
    name: "muted text on canvas",
    floor: 4.5,
    fg: hex("--muted-foreground"),
    bg: hex("--background"),
  },
  { name: "muted text on card", floor: 4.5, fg: hex("--muted-foreground"), bg: hex("--card") },
  {
    name: "rail text (--sidebar-foreground)",
    floor: 4.5,
    fg: onRail("--sidebar-foreground"),
    bg: rail,
  },
  {
    name: "active item text on its fill",
    floor: 4.5,
    fg: onFill("--sidebar-accent-foreground"),
    bg: activeFill,
  },
  {
    name: "rail group labels (--sidebar-label-foreground)",
    floor: 4.5,
    fg: onRail("--sidebar-label-foreground"),
    bg: rail,
  },
  {
    name: "user-footer email (--sidebar-muted-foreground)",
    floor: 4.5,
    fg: onRail("--sidebar-muted-foreground"),
    bg: rail,
  },
  {
    name: "search pill text (--sidebar-muted-foreground on --sidebar-accent)",
    floor: 4.5,
    fg: onFill("--sidebar-muted-foreground"),
    bg: activeFill,
  },
  {
    name: "primary-button label",
    floor: 4.5,
    fg: hex("--primary-foreground"),
    bg: hex("--primary"),
  },
  {
    // The §18.6.1 row: the bar against the bare rail.
    name: "active indicator bar vs rail (non-text, §18.6.1)",
    floor: 3,
    fg: onRail("--sidebar-primary"),
    bg: rail,
  },
  {
    // Where it is painted: a before: pseudo inside the active item, i.e. on its --sidebar-accent
    // fill (SIDEBAR_ACTIVE_BAR, components/shell/sidebar-styles.ts).
    name: "active indicator bar vs the active item fill (non-text)",
    floor: 3,
    fg: onFill("--sidebar-primary"),
    bg: activeFill,
  },
  { name: "--sidebar-ring vs rail (non-text)", floor: 3, fg: onRail("--sidebar-ring"), bg: rail },
  { name: "--ring vs rail (non-text)", floor: 3, fg: hex("--ring"), bg: rail },
  { name: "--ring vs canvas (non-text)", floor: 3, fg: hex("--ring"), bg: hex("--background") },
  { name: "--ring vs card (non-text)", floor: 3, fg: hex("--ring"), bg: hex("--card") },
];
/** K-04: white on teal-600 fails today; the firm block moves both fills to --kazi-teal-700. */
const ACCENT_PAIRS: Pair[] = [
  {
    name: "accent button label (--button-accent)",
    floor: 4.5,
    fg: hex("--button-accent-foreground"),
    bg: hex("--button-accent"),
  },
  {
    name: "focused menu/select item (--accent)",
    floor: 4.5,
    fg: hex("--accent-foreground"),
    bg: hex("--accent"),
  },
];

describe("firm light scheme — the scoped block (ADR-392, FD-1 = A1)", () => {
  it("exists and sets exactly the A1 tokens", () => {
    expect(Object.keys(app.firmLight).sort()).toEqual([...FIRM_LIGHT_TOKENS].sort());
  });

  it("keeps --card, --border, --ring, --foreground and --muted-foreground at the shared light values", () => {
    for (const t of ["--card", "--border", "--ring", "--foreground", "--muted-foreground"]) {
      expect(app.firmLight[t], t).toBeUndefined();
    }
  });

  it("is A1: light rail, card-coloured canvas, slate-900 primary, Kazi teal-700 accents", () => {
    expect(tokenHex("--sidebar", firmLight)).toBe("#f1f3f7");
    expect(tokenHex("--background", firmLight)).toBe(tokenHex("--card", firmLight));
    expect(app.firmLight["--primary"]).toBe("var(--slate-900)");
    for (const t of ["--accent", "--button-accent", "--sidebar-primary"]) {
      expect(tokenHex(t, firmLight), t).toBe("#00776a");
    }
  });

  it("uses the Kazi-tuned --kazi-teal-* steps (app :root), never Tailwind's stock teal", () => {
    expect(app.root["--kazi-teal-50"]).toBe("oklch(96.5% 0.022 183)");
    expect(app.root["--kazi-teal-100"]).toBe("oklch(92.5% 0.04 183)");
    expect(app.root["--kazi-teal-700"]).toBe("oklch(50% 0.11 184)");
    expect(app.root["--kazi-teal-950"]).toBe("oklch(28% 0.05 190)");
    for (const value of Object.values(app.firmLight)) {
      expect(value).not.toMatch(/--(color-)?teal-(50|100|700|950)\b/);
    }
  });
});

describe("firm light scheme — §18.6.1 pairs (text ≥ 4.5:1, non-text ≥ 3:1)", () => {
  it.each([...SHELL_PAIRS, ...ACCENT_PAIRS].map((p) => [p.name, p] as const))("%s", (_, p) => {
    expect(contrastRatio(p.fg(firmLight), p.bg(firmLight))).toBeGreaterThanOrEqual(p.floor);
  });
});

describe("dark theme — §18.6.1 shell pairs (unchanged by Phase 91)", () => {
  it.each(SHELL_PAIRS.map((p) => [p.name, p] as const))("%s", (_, p) => {
    expect(contrastRatio(p.fg(dark), p.bg(dark))).toBeGreaterThanOrEqual(p.floor);
  });
});

describe("pixel identity outside the firm light block (R1, FD-2, FD-1 option c)", () => {
  it("--button-accent is today's accent-button paint (teal-600 / white) in shared :root and .dark", () => {
    for (const block of [shared.root, shared.dark]) {
      expect(block["--button-accent"]).toBe("var(--teal-600)");
      expect(block["--button-accent-foreground"]).toBe("var(--color-white)");
    }
  });

  it("the accent label is exactly what text-white compiled to: var(--color-white), Tailwind's #fff", () => {
    // .text-white { color: var(--color-white) }. oklch(100% 0 0) resolves to the same #ffffff but
    // computes to lab(100 0 0), which moved glyph-edge anti-aliasing by 1/255 (661B OBS d/e).
    expect(tailwind["--color-white"]).toBe("#fff");
    expect(tokenHex("--button-accent-foreground", light)).toBe("#ffffff");
    expect(tokenHex("--button-accent-foreground", dark)).toBe("#ffffff");
  });

  it("the rail-text tokens are today's white /60 and /50 in the app :root and .dark", () => {
    for (const block of [app.root, app.dark]) {
      expect(block["--sidebar-muted-foreground"]).toBe("oklch(100% 0 0 / 60%)");
      expect(block["--sidebar-label-foreground"]).toBe("oklch(100% 0 0 / 50%)");
    }
  });

  it("K-21 stays in the backlog (FD-1): dark --button-accent teal-600, dark --accent teal-500", () => {
    // White on teal-600 is 3.16:1 and white on teal-500 2.25:1 (K-21, recorded, not fixed here).
    // Pinned as values so the eventual fix updates this test on purpose.
    expect(dark["--button-accent"]).toBe("var(--teal-600)");
    expect(dark["--accent"]).toBe("var(--teal-500)");
    expect(dark["--accent-foreground"]).toBe("oklch(100% 0 0)");
  });
});
