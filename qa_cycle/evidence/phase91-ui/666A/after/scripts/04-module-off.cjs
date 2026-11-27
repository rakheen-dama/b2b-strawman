// 666A OBS step 4: the Fee runs module-off branch (ModuleDisabledFallback), through the UI as Alice.
// The bulk_billing module is toggleable only in Settings › Firm › Features (switch "Toggle Bulk Billing
// Runs", components/settings/features-settings-form.tsx — an instant optimistic switch, no Save). So:
//   1 Features: switch OFF (shot before / after; GET /api/settings/modules confirms enabled=false)
//   2 capture /invoices/billing-runs, /new, /[runId] at 1024/1440 × light/dark + /invoices (the Fee
//     notes | Fee runs links, which still point at Fee runs) at 1440 light + the app rail Fees group
//   3 Features: switch ON again (in a finally block, so it is restored even if a capture fails);
//     GET confirms enabled=true.
// Records the times of both toggles. Writes $RAW/module-off.json + tiles.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const P = require("./probes.cjs");

const out = { at: new Date().toISOString(), toggles: [], surfaces: {} };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };
const U = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;
const settle = async (p, ms = 700) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(ms); };
const probe = async (p) => { await p.evaluate(P.HELPERS, ""); await p.evaluate(P.HELPERS2); await p.evaluate(`window.__fee = (${P.FEE.toString()})`); return p.evaluate(P.COMPACT); };
const shot = async (p, key, caption, opt = {}) => { const f = path.join(L.RAW, `${key}.png`); await p.screenshot({ path: f, ...opt }); tiles.push([key, f, caption]); };
const modState = async (t) => ((await L.apiGet(t, "/api/settings/modules")).json.modules || []).find((m) => m.id === "bulk_billing").enabled;

async function toggle(browser, S, t, want) {
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  lab.label = `module|features toggle ${want}`;
  await p.goto(`${L.BASE}/settings/firm?tab=features`, { waitUntil: "load" });
  await settle(p);
  const sw = p.getByRole("switch", { name: "Toggle Bulk Billing Runs" });
  const before = await sw.getAttribute("aria-checked");
  await shot(p, `m__features-before-${want ? "on" : "off"}`, `Alice 1440 light — Settings › Firm › Features before switching Bulk Billing Runs ${want ? "ON" : "OFF"} (aria-checked ${before})`);
  if (before !== String(want)) { await sw.click(); await settle(p, 1500); }
  const after = await sw.getAttribute("aria-checked");
  const api = await modState(t);
  out.toggles.push({ want, at: new Date().toISOString(), before, after, api });
  await shot(p, `m__features-after-${want ? "on" : "off"}`, `Alice 1440 light — Features after switching Bulk Billing Runs ${want ? "ON" : "OFF"} (aria-checked ${after}; API enabled=${api})`);
  await ctx.close();
  if (api !== want) throw new Error(`bulk_billing expected ${want}, API says ${api}`);
}

(async () => {
  const t = await L.idpToken("alice");
  const aliceId = (await L.apiGet(t, "/api/me/capabilities")).json.memberId;
  const run = L.list((await L.apiGet(t, "/api/billing-runs?size=100")).json).find((r) => r.periodFrom === L.RUN.periodFrom && r.periodTo === L.RUN.periodTo && r.createdBy === aliceId);
  out.initial = await modState(t);
  if (!out.initial) throw new Error("bulk_billing is already OFF — not touching it");
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  try {
    await toggle(browser, S, t, false);
    const SURF = [["billing-runs", "/invoices/billing-runs"], ["billing-runs-new", "/invoices/billing-runs/new"], ["billing-run-detail", `/invoices/billing-runs/${run.id}`]];
    for (const [w, h] of [[1024, 768], [1440, 900]])
      for (const th of ["light", "dark"]) {
        const ctx = await L.newCtx(browser, S, { width: w, height: h, theme: th, extra: L.EXTRA });
        const p = await ctx.newPage();
        L.watch(p, consoleSink, lab);
        for (const [sk, url] of SURF) {
          const v = `${w}-${th}`;
          lab.label = `module-off|${sk}|${v}`;
          const resp = await p.goto(`${L.BASE}${url}`, { waitUntil: "load" });
          await settle(p);
          const m = await probe(p);
          const card = await p.evaluate(() => { const h2 = [...document.querySelectorAll("#main-content h2")].map((x) => x.textContent.trim()); const a = [...document.querySelectorAll("#main-content a")].map((x) => `${x.textContent.trim()} → ${x.getAttribute("href")}`); const box = document.querySelector('#main-content [data-slot="card"]'); return { h2, links: a, cardWidth: box ? Math.round(box.getBoundingClientRect().width) : null }; });
          (out.surfaces[sk] = out.surfaces[sk] || {})[v] = { status: resp && resp.status(), url: p.url().replace(L.BASE, "").replace(U, "{id}"), title: await p.title(), h1: m.h1, h1Count: m.h1Count, mainElCount: m.mainElCount, hOverflow: m.hOverflow, card, text: m.bodyTextHead, lowContrast: m.lowContrast.slice(0, 5) };
          await shot(p, `mo__${sk}-${v}`, `Alice ${w}×${h} ${th} — bulk_billing OFF: ${url.replace(U, "{id}")} → h1 ${JSON.stringify(m.h1)}, h2 ${JSON.stringify(card.h2)}`, { fullPage: true });
        }
        if (w === 1440 && th === "light") {
          lab.label = "module-off|list|1440-light";
          await p.goto(`${L.BASE}/invoices`, { waitUntil: "load" });
          await settle(p);
          const m = await probe(p);
          out.surfaces.list = { tabLinks: m.fee.tabLinks, h1: m.h1 };
          out.rail = await p.evaluate(() => [...document.querySelectorAll('[data-sidebar="sidebar"] a, nav a')].map((a) => `${a.textContent.trim()} → ${a.getAttribute("href")}`).filter((x) => /invoices/.test(x)));
          await shot(p, "mo__list-1440-light", `Alice 1440 light — bulk_billing OFF: /invoices still shows the "Fee runs" link (${JSON.stringify(m.fee.tabLinks.map((x) => x.text))})`);
        }
        await ctx.close();
      }
  } finally {
    await toggle(browser, S, t, true);
    await browser.close();
  }
  out.final = await modState(t);
  out.console = consoleSink;
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "module-off.json"), JSON.stringify(out));
  console.log(JSON.stringify({ initial: out.initial, toggles: out.toggles, final: out.final, surfaces: Object.fromEntries(Object.entries(out.surfaces).map(([k, v]) => [k, v["1440-light"] ? { st: v["1440-light"].status, h1: v["1440-light"].h1, card: v["1440-light"].card } : v])), rail: out.rail, console: consoleSink.length }));
})();
