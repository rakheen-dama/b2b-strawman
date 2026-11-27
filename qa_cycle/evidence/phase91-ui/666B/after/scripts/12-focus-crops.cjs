// 666B OBS-AFTER step 12 (new, read-only): focus-ring evidence + measured crops.
//   Focus: INV-0010 (PARTIALLY_PAID) at 1440 light + dark — Tab from the h1 to each hero action and the
//   Payment History receipt link; per stop the box-shadow / outline while focused vs after blur (a real
//   ring = they differ), and a crop of the focused control's section.
//   Crops (light unless noted): INV-0010 line table + Payment History at 1024, the draft line table
//   scrolled fully right at 1024 (the hidden Actions column), Collection activity + Payment History
//   empty states on INV-0001 at 1024.
// Writes $RAW/focus-crops.json + PNGs.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const DRAFT_ID = "de6ac48f-bcfe-4c5e-b122-a8d012f152ec";
const out = { at: new Date().toISOString(), focus: {}, crops: [] };
const consoleSink = [];
const lab = { label: "" };
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(800); };
const STYLE = () => { const e = document.activeElement; const cs = getComputedStyle(e); return { name: (e.getAttribute("aria-label") || e.innerText || "").trim().slice(0, 40), outline: `${cs.outlineStyle} ${cs.outlineWidth}`, boxShadow: cs.boxShadow }; };

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ids = JSON.parse(fs.readFileSync(path.join(L.RAW, "setup-02b.json"), "utf8")).notes;
  const seeded = L.list((await L.apiGet(await L.idpToken("alice"), "/api/invoices")).json).find((i) => i.invoiceNumber === "INV-0001");
  for (const theme of ["light", "dark"]) {
    const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme, extra: L.EXTRA });
    const p = await ctx.newPage(); L.watch(p, consoleSink, lab);
    lab.label = `focus|${theme}`;
    await p.goto(`${L.BASE}/invoices/${ids.partial}`, { waitUntil: "load" }); await settle(p);
    await p.locator("#main-content h1").click({ position: { x: 2, y: 2 } });
    const stops = [];
    for (let i = 0; i < 4; i++) {
      await p.keyboard.press("Tab"); await p.waitForTimeout(150);
      const f = await p.evaluate(STYLE);
      const key = `focus__${theme}-${i}`;
      const file = path.join(L.RAW, `${key}.png`);
      const region = await p.evaluate(() => { const e = document.activeElement; const r = (e.closest('[data-slot="detail-hero"]') || e.closest("div.space-y-3") || e.parentElement).getBoundingClientRect(); return { x: Math.max(0, r.left - 8), y: Math.max(0, r.top - 8), width: r.width + 16, height: Math.min(r.height + 16, 400) }; });
      await p.screenshot({ path: file, clip: region });
      out.crops.push({ key, caption: `INV-0010 ${theme} 1440 — Tab stop ${i}: "${f.name}" focused (outline ${f.outline})`, box: region });
      const blurred = await p.evaluate(() => { const e = document.activeElement; e.blur(); const cs = getComputedStyle(e); const r = { outline: `${cs.outlineStyle} ${cs.outlineWidth}`, boxShadow: cs.boxShadow }; e.focus({ focusVisible: true }); return r; });
      stops.push({ ...f, blurred, ringDiffers: f.boxShadow !== blurred.boxShadow || f.outline !== blurred.outline });
    }
    out.focus[theme] = stops;
    await ctx.close();
  }
  const ctx = await L.newCtx(browser, S, { width: 1024, height: 768, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage(); L.watch(p, consoleSink, lab);
  const crop = async (key, loc, caption) => { const f = path.join(L.RAW, `${key}.png`); const b = await loc.boundingBox(); await loc.screenshot({ path: f }); out.crops.push({ key, caption, box: b && Object.fromEntries(Object.entries(b).map(([k, v]) => [k, Math.round(v)])) }); };
  const section = (txt) => p.locator("#main-content").locator("h2,h3", { hasText: txt }).first().locator("xpath=..");
  lab.label = "crops|partial-1024";
  await p.goto(`${L.BASE}/invoices/${ids.partial}`, { waitUntil: "load" }); await settle(p);
  await crop("crop__partial-lines-1024", p.locator("#main-content h2", { hasText: "Line Items" }).locator("xpath=ancestor::div[contains(@class,'space-y-4')][1]"), "INV-0010 line items at 1024 light: Description 2 lines, TAX cell 4 lines (was 3 before)");
  await crop("crop__payment-history-1024", section("Payment History"), "INV-0010 Payment History at 1024 light on DataTable: Provider / Method hidden, carried under the reference");
  lab.label = "crops|draft-1024";
  await p.goto(`${L.BASE}/invoices/${DRAFT_ID}`, { waitUntil: "load" }); await settle(p);
  const lt = p.locator("#main-content h2", { hasText: "Line Items" }).locator("xpath=ancestor::div[contains(@class,'space-y-4')][1]");
  await crop("crop__defect-draft-lines-1024", lt, "seeded Draft line items at 1024 light, scrollLeft 0: table 767 px in a 710 px scroller — ACTIONS header cut ('AC'), Edit / Delete off-canvas");
  const sl = await p.evaluate(() => { const t = [...document.querySelectorAll("#main-content table")][0]; let e = t.parentElement; while (e && getComputedStyle(e).overflowX !== "auto") e = e.parentElement; e.scrollLeft = 9999; return { scrollLeft: e.scrollLeft, clientWidth: e.clientWidth, scrollWidth: e.scrollWidth }; });
  await p.waitForTimeout(200);
  await crop("crop__defect-draft-lines-1024-scrolled", lt, `the same table scrolled fully right (scrollLeft ${sl.scrollLeft}): Edit / Delete reachable only by horizontal scroll`);
  out.draftScroll = sl;
  lab.label = "crops|sent-1024";
  await p.goto(`${L.BASE}/invoices/${seeded.id}`, { waitUntil: "load" }); await settle(p);
  await crop("crop__payment-history-empty-1024", section("Payment History"), "INV-0001 Payment History empty state (EmptyState inline) at 1024 light");
  await crop("crop__collection-activity-empty-1024", section("Collection activity"), "INV-0001 Collection activity empty state (inline) at 1024 light");
  await ctx.close();
  await browser.close();
  out.console = consoleSink;
  out.tiles = out.crops.map((c) => [c.key, path.join(L.RAW, `${c.key}.png`), c.caption]);
  fs.writeFileSync(path.join(L.RAW, "focus-crops.json"), JSON.stringify(out));
  console.log(JSON.stringify(out.focus), JSON.stringify(out.draftScroll), "crops", out.crops.length, "console", consoleSink.length, JSON.stringify(consoleSink.slice(0, 3)));
})();
