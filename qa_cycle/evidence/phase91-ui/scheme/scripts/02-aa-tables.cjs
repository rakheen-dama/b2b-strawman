// 661.4 — AA tables for the light-scheme candidates, computed with the repo's colour maths
// (frontend/__tests__/theme/color-math.ts, compiled by 02-aa-tables.sh), never hand numbers.
//
// Usage: node 02-aa-tables.cjs <compiled color-math.js> <prototype worktree root> <out-prefix>
// Reads the prototype branch's packages/ui/styles/globals.css and frontend/app/globals.css.
// Cascade modelled as the browser applies it on <html>:
//   today (light) = shared :root + app :root
//   candidate X   = today + every :root:not(.dark)[data-scheme-candidate="X"] block, in file order
//   dark          = today + shared .dark + app .dark (the candidate blocks never match under .dark;
//                   the script asserts that by re-resolving dark with the attribute "set")
// Translucent fills (--sidebar-accent white 6% / 8%) are composited over the rail in gamma-encoded
// sRGB (how browsers blend), then measured. Text tokens with an opacity modifier (rail group
// labels text-sidebar-foreground/50 app-sidebar.tsx:100, org name /80 :262, search pill /60 :275,
// user-footer email /60 sidebar-user-footer.tsx:60) are informational rows, composited the same way.
"use strict";
const fs = require("node:fs");
const path = require("node:path");

const [, , mathPath, root, outPrefix] = process.argv;
if (!mathPath || !root || !outPrefix) {
  console.error("usage: node 02-aa-tables.cjs <color-math.js> <worktree> <out-prefix>");
  process.exit(2);
}
const M = require(path.resolve(mathPath));
const sharedCss = fs.readFileSync(path.join(root, "packages/ui/styles/globals.css"), "utf8");
const appCss = fs.readFileSync(path.join(root, "frontend/app/globals.css"), "utf8");

// ---- cascade ----
const shared = { root: M.readBlock(sharedCss, ":root"), dark: M.readBlock(sharedCss, ".dark") };
const app = { root: M.readBlock(appCss, ":root"), dark: M.readBlock(appCss, ".dark") };
const today = { ...shared.root, ...app.root };
const dark = { ...today, ...shared.dark, ...app.dark };

/** Declarations of every top-level block whose selector list contains `selectorPart`. */
function candidateBlocks(css, id) {
  const clean = css.replace(/\/\*[\s\S]*?\*\//g, "");
  const out = {};
  for (const m of clean.matchAll(/(?:^|\n)([^{}\n][^{}]*?)\{([^}]*)\}/g)) {
    const selectors = m[1].split(",").map((s) => s.trim());
    const hit = selectors.some(
      (s) => s.startsWith(":root:not(.dark)") && s.includes(`[data-scheme-candidate="${id}"]`)
    );
    if (!hit) continue;
    for (const decl of m[2].matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) out[decl[1]] = decl[2].trim();
  }
  return out;
}

const CANDIDATES = ["a1", "a2", "b", "c"];
const schemes = { today };
const overrides = {};
for (const id of CANDIDATES) {
  overrides[id] = candidateBlocks(appCss, id);
  if (Object.keys(overrides[id]).length === 0) throw new Error(`no block for candidate ${id}`);
  schemes[id] = { ...today, ...overrides[id] };
}

// ---- helpers ----
const hex = (name, vars) => M.tokenHex(name, vars);
const alphaOf = (name, vars) => M.resolveColor(name, vars).alpha;
function composite(fgHex, alpha, bgHex) {
  if (alpha >= 1) return fgHex;
  const ch = (h, i) => parseInt(h.slice(1 + 2 * i, 3 + 2 * i), 16);
  return (
    "#" +
    [0, 1, 2]
      .map((i) =>
        Math.round(alpha * ch(fgHex, i) + (1 - alpha) * ch(bgHex, i))
          .toString(16)
          .padStart(2, "0")
      )
      .join("")
  );
}
/** Opaque colour of `name` as painted over `bgHex`. */
const painted = (name, vars, bgHex) => composite(hex(name, vars), alphaOf(name, vars), bgHex);
const r2 = (x) => Math.round(x * 100) / 100;

// ---- the §18.6.1 pairs (+ informational rows) ----
function pairs(vars) {
  const rail = hex("--sidebar", vars);
  const canvas = hex("--background", vars);
  const card = hex("--card", vars);
  const activeFill = painted("--sidebar-accent", vars, rail);
  const railText = painted("--sidebar-foreground", vars, rail);
  const rows = [
    ["body text on canvas (--foreground / --background)", 4.5, hex("--foreground", vars), canvas],
    ["body text on card (--card-foreground / --card)", 4.5, hex("--card-foreground", vars), card],
    ["muted text on canvas (--muted-foreground / --background)", 4.5, hex("--muted-foreground", vars), canvas],
    ["muted text on card (--muted-foreground / --card)", 4.5, hex("--muted-foreground", vars), card],
    ["rail text (--sidebar-foreground / --sidebar)", 4.5, railText, rail],
    ["active item text on its fill (--sidebar-accent-foreground / --sidebar-accent over rail)", 4.5,
      painted("--sidebar-accent-foreground", vars, activeFill), activeFill],
    ["active indicator bar vs rail (--sidebar-primary / --sidebar, non-text)", 3.0, painted("--sidebar-primary", vars, rail), rail],
    ["primary-button label (--primary-foreground / --primary)", 4.5, hex("--primary-foreground", vars), hex("--primary", vars)],
    ["accent button label (--button-accent-foreground / --button-accent)", 4.5,
      hex("--button-accent-foreground", vars), hex("--button-accent", vars)],
    ["focused menu/select item (--accent-foreground / --accent)", 4.5, hex("--accent-foreground", vars), hex("--accent", vars)],
    ["focus ring on rail (--sidebar-ring / --sidebar, non-text)", 3.0, painted("--sidebar-ring", vars, rail), rail],
    ["focus ring (--ring) vs rail (non-text)", 3.0, hex("--ring", vars), rail],
    ["focus ring vs canvas (--ring / --background, non-text)", 3.0, hex("--ring", vars), canvas],
    ["focus ring vs card (--ring / --card, non-text)", 3.0, hex("--ring", vars), card],
    ["[info] rail group label (text-sidebar-foreground/50 on rail)", 4.5,
      composite(railText, 0.5, rail), rail],
    ["[info] rail org name (text-sidebar-foreground/80 on rail)", 4.5, composite(railText, 0.8, rail), rail],
    ["[info] search pill text (text-sidebar-foreground/60 on --sidebar-accent over rail)", 4.5,
      composite(railText, 0.6, activeFill), activeFill],
    ["[info] user-footer email (text-sidebar-foreground/60 on rail)", 4.5, composite(railText, 0.6, rail), rail],
    ["[info] rail vs canvas separation (--sidebar / --background, luminance only)", null, rail, canvas],
    ["[info] canvas vs card (--background / --card)", null, canvas, card],
  ];
  return rows.map(([pair, floor, fg, bg]) => {
    const ratio = r2(M.contrastRatio(fg, bg));
    return { pair, floor, fg, bg, ratio, pass: floor === null ? null : ratio >= floor };
  });
}

const BRAND = { burgundy: "#7b2432", gold: "#e7b643" };
function brand(vars) {
  const rail = hex("--sidebar", vars);
  return Object.fromEntries(
    Object.entries(BRAND).map(([k, v]) => [k, { brand: v, rail, ratio: r2(M.contrastRatio(v, rail)) }])
  );
}

const result = { generatedAt: new Date().toISOString(), sources: { root }, schemes: {}, dark: null };
for (const [id, vars] of Object.entries(schemes)) {
  result.schemes[id] = {
    overrides: id === "today" ? {} : overrides[id],
    tokens: {
      sidebar: hex("--sidebar", vars),
      background: hex("--background", vars),
      card: hex("--card", vars),
      primary: hex("--primary", vars),
      buttonAccent: hex("--button-accent", vars),
      accent: hex("--accent", vars),
      sidebarPrimary: hex("--sidebar-primary", vars),
      ring: hex("--ring", vars),
    },
    pairs: pairs(vars),
    brand: brand(vars),
  };
}
// Dark column: the candidate blocks are :not(.dark), so dark = today's dark for every candidate.
const darkPairs = pairs(dark);
result.dark = {
  tokens: {
    sidebar: hex("--sidebar", dark),
    background: hex("--background", dark),
    card: hex("--card", dark),
    buttonAccent: hex("--button-accent", dark),
    accent: hex("--accent", dark),
  },
  pairs: darkPairs,
  brand: brand(dark),
  k21: {
    accentButton: r2(M.contrastRatio(hex("--button-accent-foreground", dark), hex("--button-accent", dark))),
    focusedMenuItem: r2(M.contrastRatio(hex("--accent-foreground", dark), hex("--accent", dark))),
  },
  // every block that both names data-scheme-candidate and declares a custom property must be
  // :root:not(.dark)-guarded, otherwise a candidate could leak into dark mode
  unchangedByCandidates: [...appCss.replace(/\/\*[\s\S]*?\*\//g, "").matchAll(/([^{}]+)\{([^}]*)\}/g)]
    .filter((m) => m[1].includes("data-scheme-candidate") && /--[\w-]+\s*:/.test(m[2]))
    .every((m) => m[1].split(",").every((s) => s.trim().startsWith(":root:not(.dark)"))),
};
// Kazi-tuned teal steps as hex (for the README)
result.kaziTeal = Object.fromEntries(
  ["--kazi-teal-50", "--kazi-teal-100", "--kazi-teal-700", "--kazi-teal-950", "--teal-500", "--teal-600"].map(
    (t) => [t, hex(t, today)]
  )
);
// Chart slots per theme (input to validate_palette.js)
result.chart = {
  light: [1, 2, 3, 4, 5].map((i) => hex(`--chart-${i}`, today)),
  dark: [1, 2, 3, 4, 5].map((i) => hex(`--chart-${i}`, dark)),
};

fs.writeFileSync(`${outPrefix}.json`, JSON.stringify(result) + "\n");

// ---- human-readable table ----
const ids = ["today", ...CANDIDATES];
const lines = [];
lines.push(`AA tables — generated ${result.generatedAt} from ${root}`);
lines.push("");
lines.push(["pair", "floor", ...ids, "dark (all)"].join(" | "));
const nPairs = result.schemes.today.pairs.length;
for (let i = 0; i < nPairs; i++) {
  const cell = (p) => `${p.ratio}${p.pass === false ? " FAIL" : ""} (${p.fg} on ${p.bg})`;
  lines.push(
    [
      result.schemes.today.pairs[i].pair,
      result.schemes.today.pairs[i].floor ?? "-",
      ...ids.map((id) => cell(result.schemes[id].pairs[i])),
      cell(darkPairs[i]),
    ].join(" | ")
  );
}
lines.push("");
lines.push("Brand colour vs rail (decorative 3px bar; no floor):");
for (const id of ids) {
  const b = result.schemes[id].brand;
  lines.push(`  ${id}: burgundy ${b.burgundy.ratio} · gold ${b.gold.ratio} (rail ${b.burgundy.rail})`);
}
lines.push(`  dark: burgundy ${result.dark.brand.burgundy.ratio} · gold ${result.dark.brand.gold.ratio}`);
lines.push("");
lines.push(`K-21 (dark): accent button ${result.dark.k21.accentButton} · focused menu item ${result.dark.k21.focusedMenuItem}`);
lines.push(`Kazi teal steps: ${JSON.stringify(result.kaziTeal)}`);
lines.push(`Chart light: ${result.chart.light.join(",")}`);
lines.push(`Chart dark: ${result.chart.dark.join(",")}`);
fs.writeFileSync(`${outPrefix}.txt`, lines.join("\n") + "\n");
console.log(lines.join("\n"));
