// @vitest-environment node
import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { CHART_THEME } from "@/lib/chart-theme";
import {
  FRONTEND_ROOT,
  adjacentPairs,
  allPairs,
  contrastRatio,
  hexChroma,
  hexLightness,
  hueSpread,
  loadThemes,
  tokenHex,
  worstCvd,
  worstNormal,
  type TokenMap,
} from "./color-math";

// Phase 90 / 637A (architecture §15.2.5, ADR-370): the Kazi categorical palette as validated on
// 2026-11-11 with the dataviz skill's validate_palette.js (its maths ported in ./color-math.ts).
// The `--chart-n` OKLCH values are read from packages/ui/styles/globals.css, converted to sRGB and
// pinned to the validated hexes; the validator's thresholds (the contract) are then re-run on them,
// so a retune either keeps the validated palette or fails here.

const { light, dark } = loadThemes();
type Mode = "light" | "dark";
const THEMES: [Mode, TokenMap][] = [
  ["light", light],
  ["dark", dark],
];

/** The 2026-11-11 validated slot hexes (teal, orange, blue, amber, violet). */
const PINNED: Record<Mode, string[]> = {
  light: ["#009787", "#e06623", "#096acb", "#c8800d", "#894ed6"],
  dark: ["#00aa99", "#df6c32", "#3986e4", "#bd891c", "#9d6fe3"],
};
/** validate_palette.js lightness band per mode; chroma floor 0.10. */
const BAND: Record<Mode, [number, number]> = { light: [0.43, 0.77], dark: [0.48, 0.67] };

const slots = (vars: TokenMap): string[] =>
  [1, 2, 3, 4, 5].map((i) => tokenHex(`--chart-${i}`, vars));

/** validate_palette.js --ordinal: monotone L, adjacent ΔL ≥ 0.06, one hue (spread ≤ 40°). */
function expectOrdinalRamp(hexes: string[]): number[] {
  const L = hexes.map(hexLightness);
  const gaps = L.slice(1).map((l, i) => l - L[i]);
  const direction = Math.sign(gaps[0]);
  expect(direction).not.toBe(0);
  for (const g of gaps) {
    expect(Math.sign(g)).toBe(direction);
    expect(Math.abs(g)).toBeGreaterThanOrEqual(0.06);
  }
  expect(hueSpread(hexes)).toBeLessThanOrEqual(40);
  return L;
}

for (const [mode, vars] of THEMES) {
  describe(`${mode} theme — categorical --chart-1..5`, () => {
    const palette = slots(vars);

    it("the CSS OKLCH values convert to the 2026-11-11 validated hexes", () => {
      expect(palette).toEqual(PINNED[mode]);
    });

    it("every slot sits in the lightness band with chroma ≥ 0.10", () => {
      for (const hex of palette) {
        expect(hexLightness(hex), hex).toBeGreaterThanOrEqual(BAND[mode][0]);
        expect(hexLightness(hex), hex).toBeLessThanOrEqual(BAND[mode][1]);
        expect(hexChroma(hex), hex).toBeGreaterThanOrEqual(0.1);
      }
    });

    it("worst adjacent CVD ΔE (protan/deutan) ≥ 8", () => {
      expect(worstCvd(palette, adjacentPairs(5))).toBeGreaterThanOrEqual(8);
    });

    it("worst adjacent normal-vision ΔE ≥ 15", () => {
      expect(worstNormal(palette, adjacentPairs(5))).toBeGreaterThanOrEqual(15);
    });

    it("the first three slots pass all-pairs (scatter-like forms)", () => {
      const three = palette.slice(0, 3);
      expect(worstCvd(three, allPairs(3))).toBeGreaterThanOrEqual(8);
      expect(worstNormal(three, allPairs(3))).toBeGreaterThanOrEqual(15);
    });
  });

  describe(`${mode} theme — sequential and ordinal ramps`, () => {
    it("--seq-100…500 is one hue, monotone, adjacent ΔL ≥ 0.06", () => {
      expectOrdinalRamp([100, 200, 300, 400, 500].map((n) => tokenHex(`--seq-${n}`, vars)));
    });

    it("--aging-* is one hue, monotone, ΔL ≥ 0.06, light end ≥ 2:1 on the card", () => {
      const hexes = ["current", "d30", "d60", "d90plus"].map((k) => tokenHex(`--aging-${k}`, vars));
      const L = expectOrdinalRamp(hexes);
      // validator rule: the "light end" is the lightest step in light mode, the darkest in dark
      const endIndex = L.indexOf(mode === "light" ? Math.max(...L) : Math.min(...L));
      expect(contrastRatio(hexes[endIndex], tokenHex("--card", vars))).toBeGreaterThanOrEqual(2);
    });
  });
}

describe("lib/chart-theme.ts", () => {
  const SOURCE = fs.readFileSync(path.join(FRONTEND_ROOT, "lib/chart-theme.ts"), "utf8");
  /** The colour roles only — non-colour keys (fontFamily, radii, dash arrays …) are out of scope. */
  const COLOUR_ROLES: string[] = [
    ...CHART_THEME.categorical,
    ...CHART_THEME.sequential,
    ...Object.values(CHART_THEME.aging),
    ...Object.values(CHART_THEME.diverging),
    ...Object.values(CHART_THEME.status),
    CHART_THEME.grid.stroke,
    CHART_THEME.axis,
    CHART_THEME.tooltip.background,
    CHART_THEME.tooltip.text,
    CHART_THEME.tooltip.boxShadow,
  ];

  it("contains no hex or rgb() colour literal", () => {
    expect(SOURCE).not.toMatch(/#[0-9a-fA-F]{3,8}\b/);
    expect(SOURCE).not.toMatch(/\brgba?\(/);
  });

  it("categorical slots are var(--chart-1..5) in order", () => {
    expect([...CHART_THEME.categorical]).toEqual([1, 2, 3, 4, 5].map((i) => `var(--chart-${i})`));
  });

  it("every colour role is a single var(--…) reference", () => {
    for (const value of COLOUR_ROLES) expect(value).toMatch(/^var\(--[a-z0-9-]+\)$/);
  });

  it("every colour token referenced (roles + the tooltip border) is defined in both themes", () => {
    const names = [...COLOUR_ROLES, CHART_THEME.tooltip.border].flatMap((v) =>
      [...v.matchAll(/var\((--[a-z0-9-]+)\)/g)].map((m) => m[1])
    );
    for (const name of names) {
      expect(light[name], `${name} (light)`).toBeDefined();
      expect(dark[name], `${name} (dark)`).toBeDefined();
    }
  });
});
