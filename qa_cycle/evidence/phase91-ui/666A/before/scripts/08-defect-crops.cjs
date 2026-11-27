// 666A OBS step 8: element screenshots (crops) of the kinks / defects seen in 01 / 01b / 03b,
// read-only (page loads only), as Alice, light. Adapted from 665F/before/scripts/08-defect-crops.cjs
// (not edited). Each crop also records the measurement behind it.
//   defect__list-table-1024            K-72: fee-note numbers break at the hyphen ("INV-" / "0005"),
//                                      dates wrap; row heights; rows above the 768 fold
//   defect__tiles-overdue-1440         K-73: "Overdue" R 0,00 painted red (text-red-600)
//   defect__debtors-table-1024         K-71: Debtor book table wider than its scroller at 1024
//   defect__run-detail-header-1440     K-65: fee-run detail h1 empty (only the status pill)
//   defect__runs-list-row-1440         K-65: the Fee runs list row's Name cell — an empty link
//   defect__run-items-1440             K-34: "View fee note" links teal-600 (contrast)
// Writes $RAW/crops.json + tiles; 05-assemble copies them as crop-*.png.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const P = require("./probes.cjs");

(async () => {
  const tiles = [];
  const out = { at: new Date().toISOString(), crops: [] };
  const t = await L.idpToken("alice");
  const aliceId = (await L.apiGet(t, "/api/me/capabilities")).json.memberId;
  const run = L.list((await L.apiGet(t, "/api/billing-runs?size=100")).json).find((r) => r.periodFrom === L.RUN.periodFrom && r.periodTo === L.RUN.periodTo && r.createdBy === aliceId);
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const crop = async (p, key, loc, caption, measure) => {
    const f = path.join(L.RAW, `${key}.png`);
    if (!(await loc.count())) { out.crops.push({ key, missing: true }); return; }
    await loc.first().scrollIntoViewIfNeeded();
    await loc.first().screenshot({ path: f });
    tiles.push([key, f, caption]);
    out.crops.push({ key, caption, measure });
  };
  const open = async (w, h) => { const ctx = await L.newCtx(browser, S, { width: w, height: h, theme: "light", extra: L.EXTRA }); const p = await ctx.newPage(); return { ctx, p }; };
  const go = async (p, url) => { await p.goto(`${L.BASE}${url}`, { waitUntil: "load" }); await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(700); await p.evaluate(P.HELPERS, ""); await p.evaluate(P.HELPERS2); await p.evaluate(`window.__fee = (${P.FEE.toString()})`); };

  let { ctx, p } = await open(1024, 768);
  await go(p, "/invoices");
  let m = await p.evaluate(() => window.__fee());
  const k72 = { rowHeights: m.tables[0].rowHeights, rowsAboveFold: m.tables[0].rowsAboveFold, numberLinks: m.tables[0].links.filter((l) => /^(INV-|Draft)/.test(l.text)).map((l) => `${l.text}:${l.lines} lines`), headLines: m.tables[0].headLines, tileGrid: m.tileGrid };
  await crop(p, "defect__list-table-1024", p.locator("#main-content table").first(), `Alice 1024×768 light — Fee notes table: numbers break at the hyphen (${k72.numberLinks.filter((x) => /2 lines/.test(x)).length} of ${k72.numberLinks.length} number links on 2 lines), dates and headers wrap; rows ${Math.min(...k72.rowHeights)}–${Math.max(...k72.rowHeights)} px, ${k72.rowsAboveFold} rows above the 768 fold (K-72)`, k72);
  await go(p, "/invoices/collections");
  m = await p.evaluate(() => window.__fee());
  const k71 = { table: m.tables[0] && { tableWidth: m.tables[0].tableWidth, scroller: m.tables[0].scroller, cut: m.tables[0].cols.filter((c) => c.cutPx).map((c) => `${c.h}=${c.cutPx}${c.hidden ? "(hidden)" : ""}`), headLines: m.tables[0].headLines, firstCellLines: m.tables[0].firstCellLines, rowHeights: m.tables[0].rowHeights }, oldestCells: await p.evaluate(() => [...document.querySelectorAll("#main-content tbody tr td:nth-child(3)")].map((td) => { const r = document.createRange(); r.selectNodeContents(td); return `${td.innerText.trim()}:${new Set([...r.getClientRects()].map((x) => Math.round(x.top))).size} lines`; })) };
  await crop(p, "defect__debtors-table-1024", p.locator("#main-content table").first().locator("xpath=.."), `Alice 1024×768 light — Debtor book: table ${k71.table.tableWidth} px in a ${k71.table.scroller.clientWidth} px scroller; cut ${k71.table.cut.join(", ")}; Oldest "${(k71.oldestCells[0] || "").split(":")[0]}" on ${(k71.oldestCells[0] || "").split(":")[1]} (K-71)`, k71);
  await ctx.close();

  ({ ctx, p } = await open(1440, 900));
  await go(p, "/invoices");
  m = await p.evaluate(() => window.__fee());
  const k73 = { tiles: m.tiles, tileGrid: m.tileGrid };
  const od = m.tiles.find((x) => x.testid === "fee-notes-overdue");
  await crop(p, "defect__tiles-overdue-1440", p.locator('#main-content [data-testid="fee-notes-overdue"]').locator("xpath=ancestor::div[contains(@class,'grid')][1]"), `Alice 1440×900 light — the 4 tiles: "Overdue" ${od.value} painted ${od.valueColor} (${od.valueCls.match(/text-red-\d+/)}) though nothing is overdue (K-73); grid ${m.tileGrid.cols} cols × ${m.tileGrid.width} px`, k73);
  await go(p, `/invoices/billing-runs/${run.id}`);
  m = await p.evaluate(() => window.__fee());
  const k65 = { h1Text: m.h1Text, h1Empty: m.h1Empty, pill: m.pill, title: await p.title(), breadcrumb: await p.evaluate(() => [...document.querySelectorAll('nav[aria-label="breadcrumb"] li, nav[aria-label="Breadcrumb"] li')].map((x) => x.textContent.trim()).filter(Boolean)) };
  await crop(p, "defect__run-detail-header-1440", p.locator("#main-content h1").locator("xpath=ancestor::div[contains(@class,'sm:flex-row')][1]"), `Alice 1440×900 light — fee-run detail header: h1 text "${k65.h1Text}" (empty; the run has name null — the wizard has no Name field), only the status pill; breadcrumb ${JSON.stringify(k65.breadcrumb)} (K-65)`, k65);
  const k34 = { links: m.tables[0] ? m.tables[0].links : [], stats: m.stats, statGrid: m.statGrid };
  await crop(p, "defect__run-items-1440", p.locator("#main-content table").first().locator("xpath=.."), `Alice 1440×900 light — fee-run Items table: "View fee note" links ${k34.links.map((l) => `${l.color} ${l.contrast}:1`).filter((v, i, a) => a.indexOf(v) === i).join(", ")} (teal-600, K-34)`, k34);
  await go(p, "/invoices/billing-runs");
  const row = await p.evaluate(() => { const a = document.querySelector("#main-content tbody tr td a"); const r = a.getBoundingClientRect(); return { text: a.textContent, width: Math.round(r.width), height: Math.round(r.height), href: a.getAttribute("href").replace(/[0-9a-f-]{36}/, "{id}") }; });
  await crop(p, "defect__runs-list-row-1440", p.locator("#main-content table").first(), `Alice 1440×900 light — Fee runs list: the run's Name cell is an empty link (text ${JSON.stringify(row.text)}, ${row.width}×${row.height} px) — the row has no visible way into the detail page once the run is no longer active (K-65)`, { nameLink: row });
  await ctx.close();
  await browser.close();
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "crops.json"), JSON.stringify(out));
  console.log("crops", tiles.length, JSON.stringify(out.crops.map((c) => ({ key: c.key, missing: c.missing, measure: c.measure }))));
})();
