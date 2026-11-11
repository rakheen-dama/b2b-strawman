import fs from "node:fs";
import path from "node:path";

// Colour maths for the Phase 90 token tests (637A: token-contrast, chart-palette).
// Not a test file (no `.test.` suffix), so vitest never collects it.
// - OKLCH → sRGB: Björn Ottosson's OKLab (https://bottosson.github.io/posts/oklab/), per-channel
//   clip to [0, 1] — the same conversion the 2026-11-11 palette hexes were produced with.
// - WCAG 2.x relative luminance + contrast ratio.
// - ΔE = Euclidean OKLab distance × 100; CVD = Machado, Oliveira & Fernandes (2009) severity 1.0
//   in linear sRGB — a port of the dataviz skill's validate_palette.js (its thresholds are
//   calibrated to this exact model).
// No dependency: the colour maths is hand-written on purpose (no culori / colorjs.io).

export interface Oklch {
  L: number; // 0..1
  C: number;
  H: number; // degrees
  alpha: number;
}
export type Cvd = "protan" | "deutan";
export type TokenMap = Record<string, string>;

const D2R = Math.PI / 180;

// ---------- parsing ----------

/** `oklch(97.2% 0.004 260)`, `oklch(0.577 0.245 27.325)`, `oklch(100% 0 0 / 8%)`. */
export function parseOklch(value: string): Oklch | null {
  const m = value
    .trim()
    .match(/^oklch\(\s*([\d.]+)(%?)\s+([\d.]+)\s+([\d.]+)\s*(?:\/\s*([\d.]+)(%?))?\s*\)$/);
  if (!m) return null;
  const L = m[2] === "%" ? parseFloat(m[1]) / 100 : parseFloat(m[1]);
  const alpha = m[5] === undefined ? 1 : m[6] === "%" ? parseFloat(m[5]) / 100 : parseFloat(m[5]);
  return { L, C: parseFloat(m[3]), H: parseFloat(m[4]), alpha };
}

/** Custom-property declarations of every top-level `selector { … }` block (comments stripped). */
export function readBlock(css: string, selector: ":root" | ".dark"): TokenMap {
  const clean = css.replace(/\/\*[\s\S]*?\*\//g, "");
  const escaped = selector.replace(".", "\\.");
  const out: TokenMap = {};
  for (const block of clean.matchAll(new RegExp(`(?:^|\\n)${escaped}\\s*\\{([^}]*)\\}`, "g"))) {
    for (const decl of block[1].matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
      out[decl[1]] = decl[2].trim();
    }
  }
  return out;
}

export const FRONTEND_ROOT = path.resolve(__dirname, "../..");
export const SHARED_CSS = path.resolve(FRONTEND_ROOT, "../packages/ui/styles/globals.css");
export const APP_CSS = path.resolve(FRONTEND_ROOT, "app/globals.css");

export interface Themes {
  light: TokenMap;
  dark: TokenMap;
  /** The raw blocks, for "defined in both :root and .dark" checks. */
  shared: { root: TokenMap; dark: TokenMap };
  app: { root: TokenMap; dark: TokenMap };
}

/** The cascade as the browser sees it on <html>: light = :root; dark = :root + .dark. */
export function loadThemes(): Themes {
  const sharedCss = fs.readFileSync(SHARED_CSS, "utf8");
  const appCss = fs.readFileSync(APP_CSS, "utf8");
  const shared = { root: readBlock(sharedCss, ":root"), dark: readBlock(sharedCss, ".dark") };
  const app = { root: readBlock(appCss, ":root"), dark: readBlock(appCss, ".dark") };
  const light = { ...shared.root, ...app.root };
  const dark = { ...light, ...shared.dark, ...app.dark };
  return { light, dark, shared, app };
}

/** Resolve `var(--x)` chains and `color-mix(in oklab|oklch, var(--a) N%, var(--b))` to OKLCH. */
export function resolveColor(name: string, vars: TokenMap, depth = 0): Oklch {
  if (depth > 20) throw new Error(`var() cycle at ${name}`);
  const raw = vars[name];
  if (raw === undefined) throw new Error(`${name} is not defined`);
  const ref = raw.match(/^var\((--[\w-]+)\)$/);
  if (ref) return resolveColor(ref[1], vars, depth + 1);
  const mix = raw.match(
    /^color-mix\(\s*in\s+(oklab|oklch)\s*,\s*var\((--[\w-]+)\)\s+([\d.]+)%\s*,\s*var\((--[\w-]+)\)\s*\)$/
  );
  if (mix) {
    const p = parseFloat(mix[3]) / 100;
    return mixColors(
      mix[1] as "oklab" | "oklch",
      resolveColor(mix[2], vars, depth + 1),
      resolveColor(mix[4], vars, depth + 1),
      p
    );
  }
  const parsed = parseOklch(raw);
  if (!parsed) throw new Error(`${name}: cannot parse "${raw}"`);
  return parsed;
}

const toLab = ({ L, C, H }: Oklch): [number, number, number] => [
  L,
  C * Math.cos(H * D2R),
  C * Math.sin(H * D2R),
];

const normHue = (deg: number): number => ((deg % 360) + 360) % 360;

/** CSS Color 5 color-mix with opaque inputs: `a` weighted p, `b` weighted 1 − p. */
export function mixColors(space: "oklab" | "oklch", a: Oklch, b: Oklch, p: number): Oklch {
  if (space === "oklab") {
    const A = toLab(a);
    const B = toLab(b);
    const [L, x, y] = A.map((v, i) => v * p + B[i] * (1 - p));
    return { L, C: Math.hypot(x, y), H: normHue(Math.atan2(y, x) / D2R), alpha: 1 };
  }
  // oklch: kept so a reintroduced `in oklch` soft token resolves as the browser does (and the
  // soft-hue test fails loudly). Hue interpolated along the shorter arc (the CSS default).
  let h1 = a.H;
  let h2 = b.H;
  if (h2 - h1 > 180) h1 += 360;
  else if (h2 - h1 < -180) h2 += 360;
  return {
    L: a.L * p + b.L * (1 - p),
    C: a.C * p + b.C * (1 - p),
    H: normHue(h1 * p + h2 * (1 - p)),
    alpha: 1,
  };
}

// ---------- conversions ----------

/** OKLCH → linear sRGB (Ottosson's inverse matrices), unclipped. */
export function oklchToLinearSrgb(o: Oklch): [number, number, number] {
  const [L, a, b] = toLab(o);
  const l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const s = (L - 0.0894841775 * a - 1.291485548 * b) ** 3;
  return [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
  ];
}

const encode = (c: number): number => {
  const x = Math.min(1, Math.max(0, c)); // clip
  return x <= 0.0031308 ? 12.92 * x : 1.055 * x ** (1 / 2.4) - 0.055;
};
const decode = (c: number): number => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);

export function oklchToHex(o: Oklch): string {
  return (
    "#" +
    oklchToLinearSrgb(o)
      .map(encode)
      .map((v) =>
        Math.round(v * 255)
          .toString(16)
          .padStart(2, "0")
      )
      .join("")
  );
}

/** A token's resolved colour as sRGB hex (alpha ignored). */
export const tokenHex = (name: string, vars: TokenMap): string =>
  oklchToHex(resolveColor(name, vars));

export function hexToLinear(hex: string): [number, number, number] {
  const h = hex.replace(/^#/, "");
  return [0, 2, 4].map((i) => decode(parseInt(h.slice(i, i + 2), 16) / 255)) as [
    number,
    number,
    number,
  ];
}

/** Linear sRGB → OKLab (Ottosson's forward matrices). */
export function linearToOklab([r, g, b]: [number, number, number]): [number, number, number] {
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  return [
    0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
    1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
  ];
}

/** OKLab lightness / chroma / hue of a hex (what the validator's bands and ramps check). */
export function hexLightness(hex: string): number {
  return linearToOklab(hexToLinear(hex))[0];
}
export function hexChroma(hex: string): number {
  const [, a, b] = linearToOklab(hexToLinear(hex));
  return Math.hypot(a, b);
}
export function hexHue(hex: string): number {
  const [, a, b] = linearToOklab(hexToLinear(hex));
  return normHue(Math.atan2(b, a) / D2R);
}

/** Smallest angle between two hues, in degrees (0..180). */
export function hueDistance(a: number, b: number): number {
  const d = Math.abs(normHue(a) - normHue(b));
  return d > 180 ? 360 - d : d;
}

/** Hue spread of a ramp: the smallest circular arc (degrees) that contains every hue. */
export function hueSpread(hexes: string[]): number {
  return hueArc(hexes.map(hexHue));
}

/** Smallest circular arc containing all `hues`: 360° minus the largest gap between neighbours
 *  (the wraparound gap first + 360 − last included). [350, 10] → 20, not 340. */
export function hueArc(hues: number[]): number {
  if (hues.length < 2) return 0;
  const sorted = hues.map(normHue).sort((x, y) => x - y);
  let largestGap = sorted[0] + 360 - sorted[sorted.length - 1];
  for (let i = 1; i < sorted.length; i++) {
    largestGap = Math.max(largestGap, sorted[i] - sorted[i - 1]);
  }
  return 360 - largestGap;
}

// ---------- WCAG ----------

export function relativeLuminance(hex: string): number {
  const [r, g, b] = hexToLinear(hex);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
export function contrastRatio(a: string, b: string): number {
  const [hi, lo] = [relativeLuminance(a), relativeLuminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

// ---------- ΔE + CVD (port of dataviz validate_palette.js) ----------

// Machado, Oliveira & Fernandes (2009) CVD transforms at severity 1.0 (linear RGB).
const MACHADO: Record<Cvd, number[][]> = {
  protan: [
    [0.152286, 1.052583, -0.204868],
    [0.114503, 0.786281, 0.099216],
    [-0.003882, -0.048116, 1.051998],
  ],
  deutan: [
    [0.367322, 0.860646, -0.227968],
    [0.280085, 0.672501, 0.047413],
    [-0.01182, 0.04294, 0.968881],
  ],
};

export function simulate(hex: string, kind: Cvd): [number, number, number] {
  const [r, g, b] = hexToLinear(hex);
  const M = MACHADO[kind];
  const clamp = (c: number) => Math.max(0, Math.min(1, c));
  return [0, 1, 2].map((i) => clamp(M[i][0] * r + M[i][1] * g + M[i][2] * b)) as [
    number,
    number,
    number,
  ];
}

/** Euclidean OKLab distance × 100; no `kind` = normal vision. */
export function deltaE(a: string, b: string, kind?: Cvd): number {
  const A = linearToOklab(kind ? simulate(a, kind) : hexToLinear(a));
  const B = linearToOklab(kind ? simulate(b, kind) : hexToLinear(b));
  return 100 * Math.hypot(A[0] - B[0], A[1] - B[1], A[2] - B[2]);
}

export const adjacentPairs = (n: number): [number, number][] =>
  Array.from({ length: n - 1 }, (_, i) => [i, i + 1] as [number, number]);

export const allPairs = (n: number): [number, number][] =>
  Array.from({ length: n }, (_, i) =>
    Array.from({ length: n - i - 1 }, (_, k) => [i, i + 1 + k] as [number, number])
  ).flat();

/** min over pairs of min(protan, deutan) ΔE — the validator's CVD gate (it reports tritan only). */
export function worstCvd(palette: string[], pairs: [number, number][]): number {
  return Math.min(
    ...pairs.flatMap(([i, j]) =>
      (["protan", "deutan"] as const).map((k) => deltaE(palette[i], palette[j], k))
    )
  );
}

/** min over pairs of the normal-vision ΔE. */
export function worstNormal(palette: string[], pairs: [number, number][]): number {
  return Math.min(...pairs.map(([i, j]) => deltaE(palette[i], palette[j])));
}
