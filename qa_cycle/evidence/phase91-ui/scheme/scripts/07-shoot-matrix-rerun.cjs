// RE-RUN of 05-shoot-matrix.cjs, byte-identical below this header except the two usage lines: run 1's
// Home showed one fee-earner row instead of the WIP chart (see 06-topup-second-earner.cjs); this run
// overwrites the 110 PNGs and writes shoot-manifest.json. Run 1's log: shoot-matrix-run1.txt.
// 661.3 — shoot the scheme matrix on the :3001 mock-auth stack built from the prototype branch.
// Playwright library script (headless Chromium from the frontend's devDependencies), not MCP.
//
// Usage: node 07-shoot-matrix-rerun.cjs <prototype frontend dir> <evidence dir ($EV/scheme)>
//   5 schemes (today, a1, a2, b, c) × 5 pages × 2 widths (1440×900, 1024×768) × 2 rail states
//   (expanded, collapsed) = 100 viewport PNGs at <evidence>/<scheme>/<page>-<width>-<state>.png;
//   then the firm brand colour is set through Settings › Firm › General (UI, never SQL) to burgundy
//   #7b2432, then gold #e7b643, and the sidebar header (rail + org-brand-accent bar + org name) is
//   cropped per scheme at 1440 expanded, 2× DPR → <evidence>/<scheme>/brand-<colour>.png (10);
//   finally the original brand colour is restored through the same form.
// Scheme selection: ?scheme=<id> on every URL (?scheme=today clears the candidate). Rail state: the
// shadcn `sidebar_state` cookie the org layout reads server-side. Writes shoot-manifest.json
// (single line) and logs to stdout.
"use strict";
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const [, , feDir, evDir] = process.argv;
if (!feDir || !evDir) {
  console.error("usage: node 07-shoot-matrix-rerun.cjs <frontend dir> <evidence dir>");
  process.exit(2);
}
const { chromium } = require(require.resolve("@playwright/test", { paths: [path.resolve(feDir)] }));

const BASE = "http://localhost:3001";
const SLUG = "e2e-test-org";
const ORG = `${BASE}/org/${SLUG}`;
const SCHEMES = ["today", "a1", "a2", "b", "c"];
const WIDTHS = { 1440: { width: 1440, height: 900 }, 1024: { width: 1024, height: 768 } };
const STATES = ["expanded", "collapsed"];
const BRANDS = { burgundy: "#7b2432", gold: "#e7b643" };

const manifest = { startedAt: new Date().toISOString(), base: BASE, shots: [], brand: {}, errors: [] };
const log = (...a) => console.log(new Date().toISOString(), ...a);

function withScheme(url, scheme) {
  const u = new URL(url);
  u.searchParams.set("scheme", scheme);
  return u.toString();
}

async function login(page) {
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/org\/[^/]+\//, { timeout: 60_000 });
  log("logged in at", page.url());
}

async function settle(page, scheme) {
  await page.locator("#main-content").waitFor({ state: "visible", timeout: 60_000 });
  if (scheme === "today") {
    await page.waitForFunction(() => !document.documentElement.hasAttribute("data-scheme-candidate"));
  } else {
    await page.waitForFunction(
      (s) => document.documentElement.getAttribute("data-scheme-candidate") === s,
      scheme
    );
  }
  const variant = scheme === "a1" || scheme === "a2" ? "inset" : "sidebar";
  await page.locator(`[data-slot="sidebar"][data-variant="${variant}"]`).first().waitFor({
    state: "attached",
    timeout: 15_000,
  });
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
  await page.waitForTimeout(1500); // chart entry animations, fonts
}

async function probe(page) {
  return page.evaluate(() => {
    const cs = getComputedStyle(document.documentElement);
    const inner = document.querySelector('[data-slot="sidebar-inner"]');
    const btn = document.querySelector('[data-slot="button"][data-variant="accent"]');
    return {
      attr: document.documentElement.getAttribute("data-scheme-candidate"),
      sidebarToken: cs.getPropertyValue("--sidebar").trim(),
      backgroundToken: cs.getPropertyValue("--background").trim(),
      buttonAccentToken: cs.getPropertyValue("--button-accent").trim(),
      railPaint: inner ? getComputedStyle(inner).backgroundColor : null,
      sidebarVariant: document.querySelector('[data-slot="sidebar"]')?.getAttribute("data-variant") ?? null,
      sidebarState: document.querySelector('[data-slot="sidebar"]')?.getAttribute("data-state") ?? null,
      accentButtonPaint: btn ? getComputedStyle(btn).backgroundColor : null,
      h1: document.querySelector("#main-content h1")?.textContent?.trim() ?? null,
    };
  });
}

async function resolveMatterUrl(page) {
  const runFile = path.join(os.tmpdir(), "kazi-legal-lifecycle-run.json");
  if (fs.existsSync(runFile)) {
    const run = JSON.parse(fs.readFileSync(runFile, "utf8"));
    if (run.matters && run.matters.sipho) return { url: `${ORG}/matters/${run.matters.sipho}`, from: "run-file sipho" };
  }
  await page.goto(withScheme(`${ORG}/matters`, "today"), { waitUntil: "load" });
  await settle(page, "today");
  const href = await page
    .locator('#main-content a[href*="/matters/"]')
    .evaluateAll((as) => as.map((a) => a.getAttribute("href")).find((h) => /\/matters\/[0-9a-f-]{36}$/.test(h)));
  if (!href) throw new Error("no matter link found on the matters list");
  return { url: new URL(href, BASE).toString(), from: "first matter link on the list" };
}

async function newContext(browser, size, state, dpr = 1) {
  const ctx = await browser.newContext({ viewport: size, deviceScaleFactor: dpr, reducedMotion: "reduce" });
  await ctx.addCookies([{ name: "sidebar_state", value: state === "collapsed" ? "false" : "true", domain: "localhost", path: "/" }]);
  return ctx;
}

async function setBrand(page, hex) {
  await page.goto(withScheme(`${ORG}/settings/firm?tab=general`, "today"), { waitUntil: "load" });
  await settle(page, "today");
  const input = page.locator("#brand-color");
  await input.waitFor({ state: "visible", timeout: 30_000 });
  const before = await input.inputValue();
  await input.fill(hex);
  await page.getByRole("button", { name: "Save Settings" }).click();
  let saved = true;
  await page.getByText("Settings saved successfully.").waitFor({ timeout: 45_000 }).catch(() => (saved = false));
  await page.reload({ waitUntil: "load" });
  await page.locator("#brand-color").waitFor({ state: "visible", timeout: 30_000 });
  const after = await page.locator("#brand-color").inputValue();
  log(`brand: ${before} -> ${hex}; confirmation seen=${saved}; after reload=${after}`);
  if (after.toLowerCase() !== hex.toLowerCase()) throw new Error(`brand colour did not persist: ${after}`);
  return { before, after, confirmationSeen: saved };
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  try {
    // ---- the 100-shot matrix ----
    let matter = null;
    for (const [w, size] of Object.entries(WIDTHS)) {
      for (const state of STATES) {
        const ctx = await newContext(browser, size, state);
        const page = await ctx.newPage();
        page.on("console", (m) => {
          if (m.type() === "error") manifest.errors.push({ w, state, url: page.url(), text: m.text().slice(0, 300) });
        });
        await login(page);
        if (!matter) {
          matter = await resolveMatterUrl(page);
          manifest.matter = matter;
          log("matter detail:", matter);
        }
        const PAGES = {
          home: `${ORG}/dashboard`,
          matter: matter.url,
          "settings-firm-general": `${ORG}/settings/firm?tab=general`,
          "fee-notes": `${ORG}/invoices`,
          matters: `${ORG}/matters`,
        };
        for (const scheme of SCHEMES) {
          fs.mkdirSync(path.join(evDir, scheme), { recursive: true });
          for (const [name, url] of Object.entries(PAGES)) {
            const target = withScheme(url, scheme);
            await page.goto(target, { waitUntil: "load" });
            await settle(page, scheme);
            const file = path.join(evDir, scheme, `${name}-${w}-${state}.png`);
            await page.screenshot({ path: file });
            const p = await probe(page);
            manifest.shots.push({ file: path.relative(evDir, file), url: target, ...p });
            log("shot", path.relative(evDir, file), JSON.stringify(p));
          }
        }
        await ctx.close();
      }
    }

    // ---- brand-colour crops ----
    const ctx = await newContext(browser, WIDTHS[1440], "expanded", 2);
    const page = await ctx.newPage();
    await login(page);
    let original = null;
    for (const [colour, hex] of Object.entries(BRANDS)) {
      const r = await setBrand(page, hex);
      if (original === null) original = r.before;
      manifest.brand[colour] = r;
      for (const scheme of SCHEMES) {
        await page.goto(withScheme(`${ORG}/dashboard`, scheme), { waitUntil: "load" });
        await settle(page, scheme);
        const bar = page.getByTestId("org-brand-accent");
        const barColour = await bar.evaluate((el) => getComputedStyle(el).backgroundColor);
        const header = page.locator('[data-slot="sidebar-header"]').first();
        const file = path.join(evDir, scheme, `brand-${colour}.png`);
        await header.screenshot({ path: file });
        const p = await probe(page);
        manifest.shots.push({ file: path.relative(evDir, file), brand: hex, barColour, ...p });
        log("crop", path.relative(evDir, file), barColour, p.railPaint);
      }
    }
    // ---- restore ----
    manifest.brand.originalValue = original;
    if (original && /^#[0-9a-f]{6}$/i.test(original)) {
      manifest.brand.restore = await setBrand(page, original);
    } else {
      manifest.brand.restore = { skipped: true, reason: `original value "${original}" cannot be set through the form` };
      log("RESTORE SKIPPED — original brand colour was", JSON.stringify(original));
    }
    await ctx.close();
  } finally {
    await browser.close();
    manifest.finishedAt = new Date().toISOString();
    fs.writeFileSync(path.join(__dirname, "shoot-manifest.json"), JSON.stringify(manifest) + "\n");
    log(`shots: ${manifest.shots.length}; console errors: ${manifest.errors.length}`);
  }
})().catch((e) => {
  console.error("FAILED:", e);
  process.exit(1);
});
