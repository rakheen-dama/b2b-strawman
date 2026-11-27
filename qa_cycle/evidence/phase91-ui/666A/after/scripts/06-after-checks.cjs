// 666A OBS-after step 6 (NEW, after-only): the kit-specific checks the copied before scripts can't
// read (probes-after.cjs AFTER()), as Alice, read-only except the CSV download (client-side) and the
// unbilled filter Apply (router.replace, no write).
//   A  AFTER() on the 7 routes × 1024/1440 × light/dark + element crops of the measured parts
//   B  CSV: /invoices "Export CSV" (1440 light) → ../fee-notes.csv, and under ?status=DRAFT →
//      ../fee-notes-status-DRAFT.csv; parsed and compared row by row with the rendered table
//   C  Unbilled filters: type From/To, pick a currency, Apply → the resulting URL (×2) + table/total
//   D  KBD at 1024 light per route: Tab from the top of the page through #main-content; every step's
//      focus-visible indicator, whether it sits in a table scroller, the scroller's scrollLeft and
//      whether the focused element is inside the scroller's visible box; crops of table steps
// Writes ../after-checks.json, ../csv-check.json, ../kbd.json (single line) + $RAW tiles + sheets
// ../sheet-after-crops.png, ../sheet-kbd-1024.png.
// Usage: PHASE=after node 06-after-checks.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const P = require("./probes.cjs");
const A = require("./probes-after.cjs");

const U = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;
const consoleSink = [];
const lab = { label: "" };
const crops = [];
const kbdTiles = [];
const settle = async (p, ms = 700) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(ms); };
const load = async (p) => { await p.evaluate(P.HELPERS, ""); await p.evaluate(P.HELPERS2); await p.evaluate(`window.__after = (${A.AFTER.toString()})`); return p.evaluate(() => window.__after()); };
const crop = async (p, key, sel, caption) => {
  const loc = p.locator(sel).first();
  if (!(await loc.count())) { crops.push([`${key} (missing: ${sel})`, null]); return; }
  await loc.scrollIntoViewIfNeeded().catch(() => {});
  const f = path.join(L.RAW, `${key}.png`);
  await loc.screenshot({ path: f });
  crops.push([caption, f]);
};
// Which crops per route (key suffix, selector, variants).
const CROPS = {
  list: [["header", '#main-content [data-slot="page-header"]', ["1440-light", "1440-dark"]], ["subnav", '#main-content a[aria-current="page"] >> xpath=..', ["1440-light", "1440-dark"]], ["tiles", '#main-content [data-slot="stat-card"] >> xpath=../..', ["1024-light", "1024-dark", "1440-light"]], ["table", '#main-content [data-slot="table-container"]', ["1024-light", "1024-dark", "1440-light"]]],
  "billing-runs": [["header", '#main-content [data-slot="page-header"]', ["1440-light"]], ["table", '#main-content [data-slot="table-container"]', ["1024-light", "1440-light", "1024-dark"]]],
  "billing-run-detail": [["header", '#main-content [data-slot="page-header"]', ["1024-light", "1440-light"]], ["cards", '#main-content [data-slot="stat-card"] >> xpath=../..', ["1024-light", "1024-dark", "1440-light"]], ["items", '#main-content [data-slot="table-container"]', ["1024-light", "1440-light", "1440-dark"]]],
  "billing-runs-new": [["steps", '#main-content nav[aria-label="Wizard steps"]', ["1024-light", "1024-dark", "1440-light"]]],
  collections: [["debtors", '#main-content [data-slot="table-container"]', ["1024-light", "1024-dark", "1440-light"]], ["reminders-empty", '#main-content [data-slot="empty-state"]', ["1440-light", "1440-dark"]]],
  unbilled: [["table", '#main-content [data-slot="data-table"]', ["1024-light", "1440-light", "1440-dark"]]],
  "unbilled-empty": [["empty", '#main-content [data-slot="empty-state"]', ["1440-light"]]],
};

function parseCsv(text) {
  const rows = []; let row = [], f = "", q = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (q) { if (ch === '"' && text[i + 1] === '"') { f += '"'; i++; } else if (ch === '"') q = false; else f += ch; }
    else if (ch === '"') q = true; else if (ch === ",") { row.push(f); f = ""; } else if (ch === "\r") {} else if (ch === "\n") { row.push(f); rows.push(row); row = []; f = ""; } else f += ch;
  }
  if (f || row.length) { row.push(f); rows.push(row); }
  return rows;
}

(async () => {
  const out = { at: new Date().toISOString(), routes: {}, unbilledApply: [], console: null };
  const t = await L.idpToken("alice");
  const aliceId = (await L.apiGet(t, "/api/me/capabilities")).json.memberId;
  const run = L.list((await L.apiGet(t, "/api/billing-runs?size=100")).json).find((r) => r.periodFrom === L.RUN.periodFrom && r.periodTo === L.RUN.periodTo && r.createdBy === aliceId);
  const e = L.EMPTY_PERIOD;
  const SURF = [["list", "/invoices"], ["billing-runs", "/invoices/billing-runs"], ["billing-runs-new", "/invoices/billing-runs/new"], ["billing-run-detail", `/invoices/billing-runs/${run.id}`], ["collections", "/invoices/collections"], ["unbilled", "/invoices/unbilled"], ["unbilled-empty", `/invoices/unbilled?periodFrom=${e.periodFrom}&periodTo=${e.periodTo}&currency=ZAR`]];
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  // A
  for (const [w, h] of [[1024, 768], [1440, 900]])
    for (const th of ["light", "dark"]) {
      const ctx = await L.newCtx(browser, S, { width: w, height: h, theme: th, extra: L.EXTRA });
      const p = await ctx.newPage();
      L.watch(p, consoleSink, lab);
      for (const [sk, url] of SURF) {
        const v = `${w}-${th}`;
        lab.label = `06|${sk}|${v}`;
        const resp = await p.goto(`${L.BASE}${url}`, { waitUntil: "load" });
        await settle(p);
        const m = await load(p);
        m.status = resp && resp.status();
        m.mainCount = await p.locator("main#main-content").count();
        m.mainElCount = await p.locator("main").count();
        m.hOverflow = await p.evaluate(() => document.scrollingElement.scrollWidth - document.scrollingElement.clientWidth);
        (out.routes[sk] = out.routes[sk] || {})[v] = m;
        for (const [ck, sel, vs] of CROPS[sk] || []) if (vs.includes(v)) await crop(p, `c__${sk}-${ck}-${v}`, sel, `Alice ${w}×${h} ${th} — ${url.replace(U, "{id}")} · ${ck}`);
      }
      await ctx.close();
    }
  // B CSV
  const csv = { files: [] };
  {
    const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: { ...L.EXTRA, acceptDownloads: true } });
    const p = await ctx.newPage();
    L.watch(p, consoleSink, lab);
    for (const [q, name] of [["", "fee-notes.csv"], ["?status=DRAFT", "fee-notes-status-DRAFT.csv"]]) {
      lab.label = `06|csv ${q || "all"}|1440-light`;
      await p.goto(`${L.BASE}/invoices${q}`, { waitUntil: "load" });
      await settle(p);
      const btn = p.locator("#main-content").getByRole("button", { name: /Export CSV/i });
      const btnInfo = { count: await btn.count(), text: (await btn.first().innerText().catch(() => "")).trim() };
      const [dl] = await Promise.all([p.waitForEvent("download", { timeout: 15000 }), btn.first().click()]);
      const dest = path.join(L.OUT, name);
      await dl.saveAs(dest);
      const raw = fs.readFileSync(dest, "utf8");
      const rows = parseCsv(raw.replace(/^﻿/, ""));
      const header = rows[0];
      const body = rows.slice(1).filter((r) => r.length > 1 || r[0]);
      const ui = await p.evaluate(() => [...document.querySelectorAll("#main-content tbody tr")].map((tr) => [...tr.children].map((c) => (c.innerText || "").replace(/\s+/g, " ").trim())));
      const uiHead = await p.evaluate(() => [...document.querySelectorAll("#main-content thead th")].map((c) => (c.textContent || "").trim()));
      const api = L.list((await L.apiGet(t, `/api/invoices${q}`)).json);
      const idx = (n) => header.indexOf(n);
      const mism = [];
      body.forEach((r, i) => {
        const u = ui[i] || [];
        const a = api[i] || {};
        if (r[idx("Fee note")] !== u[0]) mism.push({ i, field: "Fee note", csv: r[idx("Fee note")], ui: u[0] });
        if (r[idx("Client")] !== u[1]) mism.push({ i, field: "Client", csv: r[idx("Client")], ui: u[1] });
        if (!(u[2] || "").startsWith(r[idx("Status")])) mism.push({ i, field: "Status", csv: r[idx("Status")], ui: u[2] });
        if (Number(r[idx("Total")]) !== Number(a.total)) mism.push({ i, field: "Total vs API", csv: r[idx("Total")], api: a.total });
        if (r[idx("Currency")] !== a.currency) mism.push({ i, field: "Currency vs API", csv: r[idx("Currency")], api: a.currency });
        if ((r[idx("Issue Date")] || null) !== (a.issueDate || null)) mism.push({ i, field: "Issue Date vs API", csv: r[idx("Issue Date")], api: a.issueDate });
        if ((r[idx("Due Date")] || null) !== (a.dueDate || null)) mism.push({ i, field: "Due Date vs API", csv: r[idx("Due Date")], api: a.dueDate });
      });
      csv.files.push({ query: q || "(none)", file: name, suggestedFilename: dl.suggestedFilename(), bytes: Buffer.byteLength(raw), bom: raw.charCodeAt(0) === 0xfeff, crlf: /\r\n/.test(raw), header, rows: body.length, uiRows: ui.length, apiRows: api.length, uiHeaders: uiHead, button: btnInfo, mismatches: mism.slice(0, 20), mismatchCount: mism.length, sample: body.slice(0, 4) });
    }
    // C unbilled Apply
    for (const [from, to, cur] of [["2026-11-01", "2026-11-27", "USD"], ["2026-11-01", "2026-11-27", "ZAR"]]) {
      lab.label = `06|unbilled apply ${cur}|1440-light`;
      await p.goto(`${L.BASE}/invoices/unbilled`, { waitUntil: "load" });
      await settle(p);
      await p.locator("#unbilled-period-from").fill(from);
      await p.locator("#unbilled-period-to").fill(to);
      await p.getByTestId("unbilled-currency").click();
      await p.getByRole("option", { name: cur, exact: true }).click();
      await p.getByRole("button", { name: "Apply", exact: true }).click();
      await p.waitForURL((u) => u.search.includes(`currency=${cur}`), { timeout: 15000 }).catch(() => {});
      await settle(p);
      await p.evaluate(P.HELPERS, ""); await p.evaluate(P.HELPERS2); await p.evaluate(`window.__after = (${A.AFTER.toString()})`);
      const m = await p.evaluate(() => window.__after());
      out.unbilledApply.push({ typed: { from, to, cur }, url: p.url().replace(L.BASE, ""), help: m.unbilledForm && m.unbilledForm.help ? m.unbilledForm.help.text : null, table: m.tables.map((x) => ({ testid: x.testid, rows: x.cellTexts })), grandTotal: m.grandTotal, empties: m.empties.map((x) => `${x.variant}:${x.text.slice(0, 80)}`) });
      const f = path.join(L.RAW, `c__unbilled-apply-${cur}-1440-light.png`);
      await p.screenshot({ path: f, fullPage: true });
      crops.push([`Alice 1440 light — Unbilled work after typing ${from} → ${to}, ${cur}, Apply → ${p.url().replace(L.BASE, "")}`, f]);
    }
    await ctx.close();
  }
  // D KBD
  const kbd = {};
  {
    const ctx = await L.newCtx(browser, S, { width: 1024, height: 768, theme: "light", extra: L.EXTRA });
    const p = await ctx.newPage();
    L.watch(p, consoleSink, lab);
    for (const [sk, url] of SURF.filter(([k]) => k !== "unbilled-empty")) {
      lab.label = `06|kbd ${sk}|1024-light`;
      await p.goto(`${L.BASE}${url}`, { waitUntil: "load" });
      await settle(p);
      await p.evaluate(() => { window.scrollTo(0, 0); if (document.activeElement) document.activeElement.blur(); });
      const steps = []; let entered = false; let presses = 0; let shotTable = 0;
      for (let i = 0; i < 160; i++) {
        await p.keyboard.press("Tab"); presses++;
        await p.waitForTimeout(60);
        const s = await p.evaluate(() => {
          const el = document.activeElement;
          if (!el || el === document.body) return { body: true };
          const main = document.querySelector("main#main-content");
          const cs = getComputedStyle(el);
          const sc = el.closest('[data-slot="table-container"]');
          const r = el.getBoundingClientRect();
          const sr = sc ? sc.getBoundingClientRect() : null;
          const name = (el.getAttribute("aria-label") || el.innerText || el.getAttribute("title") || "").replace(/\s+/g, " ").trim().slice(0, 50);
          return { inMain: !!(main && main.contains(el)), tag: el.tagName.toLowerCase(), role: el.getAttribute("role"), name, href: el.getAttribute("href") ? el.getAttribute("href").replace(/[0-9a-f-]{36}/g, "{id}") : null, focusVisible: el.matches(":focus-visible"), outline: `${cs.outlineStyle} ${cs.outlineWidth} ${cs.outlineColor}`, boxShadow: cs.boxShadow === "none" ? "none" : cs.boxShadow.slice(0, 80), indicator: !(cs.outlineStyle === "none" || parseFloat(cs.outlineWidth) === 0) || cs.boxShadow !== "none", isScroller: el.dataset && el.dataset.slot === "table-container", inScroller: !!sc, scroller: sc ? { clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, scrollLeft: Math.round(sc.scrollLeft), tabindex: sc.getAttribute("tabindex") } : null, inScrollerView: sc ? r.left >= sr.left - 1 && r.right <= sr.left + sc.clientWidth + 1 : null, rect: { x: Math.round(r.left), y: Math.round(r.top), w: Math.round(r.width), h: Math.round(r.height) }, inViewport: r.top >= 0 && r.bottom <= innerHeight };
        });
        if (s.inMain) { entered = true; steps.push(s); if (s.inScroller && shotTable < 3) { shotTable++; const f = path.join(L.RAW, `k__${sk}-${steps.length}.png`); await p.screenshot({ path: f, clip: { x: Math.max(0, s.rect.x - 120), y: Math.max(0, s.rect.y - 50), width: Math.min(520, 1024 - Math.max(0, s.rect.x - 120)), height: 140 } }); kbdTiles.push([`${sk} step ${steps.length}: ${s.tag} "${s.name}" focusVisible=${s.focusVisible} outline=${s.outline} scrollLeft=${s.scroller.scrollLeft}/${s.scroller.scrollWidth - s.scroller.clientWidth} inScrollerView=${s.inScrollerView}`, f]); } }
        else if (entered) break;
        if (steps.length >= 60) break;
      }
      const sc = await p.evaluate(() => [...document.querySelectorAll('#main-content [data-slot="table-container"]')].map((c) => ({ clientWidth: c.clientWidth, scrollWidth: c.scrollWidth, overflows: c.scrollWidth > c.clientWidth, tabindex: c.getAttribute("tabindex") })));
      kbd[sk] = { presses, stepsInMain: steps.length, scrollers: sc, scrollerEverFocused: steps.some((x) => x.isScroller), stepsWithoutIndicator: steps.filter((x) => !x.indicator).map((x) => `${x.tag} ${x.name}`), stepsNotFocusVisible: steps.filter((x) => !x.focusVisible).map((x) => `${x.tag} ${x.name}`), stepsInScroller: steps.filter((x) => x.inScroller).length, inScrollerHiddenAfterFocus: steps.filter((x) => x.inScroller && !x.inScrollerView).map((x) => `${x.tag} ${x.name}`), order: steps.map((x) => `${x.tag}${x.role ? "[" + x.role + "]" : ""} ${x.name}${x.inScroller ? " (table)" : ""}`), steps };
      const f = path.join(L.RAW, `k__${sk}-last.png`);
      await p.screenshot({ path: f });
      kbdTiles.push([`${sk}: ${steps.length} Tab stops in #main-content; scroller focusable: ${sc.map((x) => x.tabindex).join("/") || "no table"}; overflowing: ${sc.filter((x) => x.overflows).map((x) => `${x.clientWidth}/${x.scrollWidth}`).join(", ") || "none"} — viewport at the last step`, f]);
    }
    await ctx.close();
  }
  await browser.close();
  out.console = consoleSink;
  fs.writeFileSync(path.join(L.OUT, "after-checks.json"), JSON.stringify(out) + "\n");
  fs.writeFileSync(path.join(L.OUT, "csv-check.json"), JSON.stringify({ at: new Date().toISOString(), ...csv }) + "\n");
  fs.writeFileSync(path.join(L.OUT, "kbd.json"), JSON.stringify({ at: new Date().toISOString(), viewport: "1024x768 light", routes: kbd }) + "\n");
  const b2 = await L.chromium.launch();
  await L.sheet(b2, path.join(L.OUT, "sheet-after-crops.png"), `666A after — measured parts per route (element crops; Alice; caption = viewport/theme/route/part)`, crops, 3, 520);
  await L.sheet(b2, path.join(L.OUT, "sheet-kbd-1024.png"), `666A after — KBD at 1024×768 light: Tab steps inside table scrollers (first 3 per route) + the viewport at the last Tab stop in #main-content`, kbdTiles, 3, 520);
  await b2.close();
  console.log("crops", crops.length, "kbd tiles", kbdTiles.length, "console", consoleSink.length, JSON.stringify(consoleSink.slice(0, 4)));
  console.log("csv", JSON.stringify(csv.files.map((f) => ({ q: f.query, rows: f.rows, ui: f.uiRows, api: f.apiRows, header: f.header, mism: f.mismatchCount, bom: f.bom, name: f.suggestedFilename }))));
  console.log("apply", JSON.stringify(out.unbilledApply.map((x) => ({ url: x.url, gt: x.grandTotal && x.grandTotal.text, empties: x.empties }))));
  console.log("kbd", JSON.stringify(Object.fromEntries(Object.entries(kbd).map(([k, v]) => [k, { n: v.stepsInMain, scrollers: v.scrollers, focused: v.scrollerEverFocused, noInd: v.stepsWithoutIndicator.length, notFV: v.stepsNotFocusVisible.length, hidden: v.inScrollerHiddenAfterFocus }]))));
})();
