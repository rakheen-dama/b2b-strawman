// 665D OBS step 3: module gates, through the UI as Alice. Settings › Firm › Features lists the
// HORIZONTAL modules only (GET /api/settings/modules; ModuleSettingsController: "Vertical modules …
// are NOT exposed here"). `bulk_billing` (gates Fee runs) is horizontal → toggled OFF here through its
// instant switch, the Fees family captured (tab strip + settings rail at 1440 / 1024 light, the
// `?tab=fee-runs` deep link, `/settings/fees` bare), then switched back ON (in `finally`) and verified by
// API GET. `trust_accounting` (gates Trust accounting) is a legal-za VERTICAL module: there is no
// switch for it on the Features page, so it is NOT toggled (no SQL, no vertical-profile PATCH) — the
// script records the switch's absence.
// Writes $RAW/modules.json + tiles; 05-assemble merges them.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const out = { at: new Date().toISOString(), steps: [] };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };
const log = (o) => { o.at = new Date().toISOString(); out.steps.push(o); console.log(JSON.stringify(o).slice(0, 700)); };
const shot = async (p, key, caption, opt = {}) => {
  const f = path.join(L.RAW, `${key}.png`);
  await p.screenshot({ path: f, ...opt });
  tiles.push([key, f, caption]);
};
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(400); };
const PROBE = (TABLIST) => {
  const main = document.querySelector("main#main-content");
  const t = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
  const strip = main && main.querySelector(`[role="tablist"][aria-label="${TABLIST}"]`);
  const nav = document.querySelector('nav[aria-label="Settings navigation"]');
  const notice = main && [...main.querySelectorAll('p[role="status"]')].find((p) => /have access to/.test(p.textContent));
  return {
    url: location.pathname + location.search,
    title: document.title,
    tabs: strip ? [...strip.querySelectorAll('[role="tab"]')].map((x) => `${t(x)}${x.getAttribute("aria-selected") === "true" ? "*" : ""}`) : null,
    rail: nav ? [...nav.querySelectorAll('a[href*="/settings/fees"]')].map((a) => `${t(a)}${a.getAttribute("aria-current") ? "*" : ""}`) : null,
    notice: notice ? t(notice) : null,
    h1: main ? [...main.querySelectorAll("h1")].map(t) : [],
    hOverflow: Math.max(0, document.scrollingElement.scrollWidth - document.scrollingElement.clientWidth),
  };
};

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const a = await L.idpToken("alice");
  const mods = async () => { const h = ((await L.apiGet(a, "/api/settings/modules")).json || {}).modules || []; const s = (await L.apiGet(a, "/api/settings")).json || {}; return { horizontal: h.map((m) => `${m.id}:${m.enabled}`), enabledModules: s.enabledModules }; };
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  const features = async () => { await p.goto(`${L.BASE}/settings/firm?tab=features`, { waitUntil: "load" }); await settle(p); };
  const toggle = async (id, want) => {
    const sw = p.locator(`#feature-switch-${id}`);
    const before = await sw.getAttribute("aria-checked");
    if (before !== String(want)) {
      await sw.click();
      await p.waitForFunction(([sel, w]) => { const e = document.querySelector(sel); return e && e.getAttribute("aria-checked") === w && !e.disabled; }, [`#feature-switch-${id}`, String(want)], { timeout: 15000 });
      await settle(p);
    }
    return { before, after: await sw.getAttribute("aria-checked"), label: await sw.getAttribute("aria-label") };
  };
  let switchedOff = false;
  try {
    lab.label = "modules|features";
    await features();
    const switches = await p.locator('[id^="feature-switch-"]').evaluateAll((els) => els.map((e) => `${e.id.replace("feature-switch-", "")}:${e.getAttribute("aria-checked")} (${e.getAttribute("aria-label")})`));
    log({ step: "features-page", switches, trustAccountingSwitch: await p.locator("#feature-switch-trust_accounting").count(), api: await mods() });
    await shot(p, "mod__features-before", `Alice 1440 light — Settings › Firm › Features before: switches ${switches.join(", ")}; no trust_accounting switch (vertical module)`);

    lab.label = "modules|bulk_billing off";
    const off = await toggle("bulk_billing", false);
    switchedOff = true;
    log({ step: "bulk_billing OFF (UI switch)", ...off, api: await mods() });
    await shot(p, "mod__features-bulk-off", `Alice 1440 light — Features: "${off.label}" switched OFF (${off.before} → ${off.after})`);

    for (const [w, h] of [[1440, 900], [1024, 768]]) {
      await p.setViewportSize({ width: w, height: h });
      for (const [k, u] of [["bare", "/settings/fees"], ["fee-runs", "/settings/fees?tab=fee-runs"], ["trust-accounting", "/settings/fees?tab=trust-accounting"]]) {
        lab.label = `modules|bulk-off|${k}|${w}`;
        await p.goto(`${L.BASE}${u}`, { waitUntil: "load" });
        await settle(p);
        const pr = await p.evaluate(PROBE, L.TABLIST);
        log({ step: `bulk_billing OFF → ${u} @${w}`, ...pr });
        if (k !== "trust-accounting") await shot(p, `mod__bulk-off-${k}-${w}`, `Alice ${w}×${h} light, bulk_billing OFF — ${u}: tabs ${JSON.stringify(pr.tabs)}; notice ${JSON.stringify(pr.notice)}; h1 ${JSON.stringify(pr.h1)}`);
      }
    }
    await p.setViewportSize({ width: 1440, height: 900 });
  } finally {
    if (switchedOff) {
      lab.label = "modules|bulk_billing restore";
      await p.setViewportSize({ width: 1440, height: 900 });
      await features();
      const on = await toggle("bulk_billing", true);
      log({ step: "bulk_billing ON again (UI switch) — restore", ...on, api: await mods() });
      await shot(p, "mod__features-restored", `Alice 1440 light — Features after restore: "${on.label}" ${on.before} → ${on.after}`);
      await p.goto(`${L.BASE}/settings/fees?tab=fee-runs`, { waitUntil: "load" });
      await settle(p);
      log({ step: "after restore → /settings/fees?tab=fee-runs @1440", ...(await p.evaluate(PROBE, L.TABLIST)) });
    }
    await ctx.close();
    await browser.close();
    out.finalApi = await mods();
    out.console = consoleSink;
    out.tiles = tiles;
    fs.writeFileSync(path.join(L.RAW, "modules.json"), JSON.stringify(out));
    console.log("final", JSON.stringify(out.finalApi), "console", consoleSink.length, "tiles", tiles.length);
  }
})();
