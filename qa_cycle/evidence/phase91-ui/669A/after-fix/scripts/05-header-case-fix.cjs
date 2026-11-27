// 669A OBS after-fix (@d61e5dd4e): disbursement-table header casing after `[&_button]:uppercase` on the
// sortable headers (OBS-after D-1). Read-only. Matter discovered via GET /api/legal/disbursements as
// Alice (the first matter with a disbursement); no seeding. For /legal/disbursements and that matter's
// Disbursements tab at 1024x768 + 1440x900 x light + dark: every visible <th> (text-transform,
// innerText, letter-spacing of the label element), sort-control slot geometry vs the th / next th,
// table + document overflow, then a "Date" header click (aria-sort before/after, text still uppercase).
// Usage: PHASE=after-fix node 05-header-case-fix.cjs → ../measurements.json + ../sheet-disbursement-headers.png
const path = require("path");
const fs = require("fs");
const L = require("../../before/scripts/lib.cjs");
const B = L.BASE;
const TID = '[data-testid="disbursement-list"]';

const probe = () => {
  const root = document.querySelector('[data-testid="disbursement-list"]');
  const table = root && (root.tagName === "TABLE" ? root : root.querySelector("table"));
  if (!table) return { error: "no table" };
  const ths = [...table.querySelectorAll("thead th")].filter((h) => getComputedStyle(h).display !== "none" && h.getBoundingClientRect().width > 0);
  const heads = ths.map((h, i) => {
    const ctl = h.querySelector("button,a");
    const leaf = ctl || [...h.querySelectorAll("span,div")].find((e) => [...e.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim())) || h;
    const hb = h.getBoundingClientRect();
    const nb = ths[i + 1] && ths[i + 1].getBoundingClientRect();
    const cb = ctl && ctl.getBoundingClientRect();
    const icon = ctl && ctl.querySelector("svg");
    const ib = icon && icon.getBoundingClientRect();
    const txt = h.innerText.trim();
    return {
      text: txt,
      upper: txt === txt.toUpperCase(),
      sortable: !!ctl,
      ctlTag: ctl ? ctl.tagName.toLowerCase() : null,
      tt: getComputedStyle(leaf).textTransform,
      thTT: getComputedStyle(h).textTransform,
      ls: getComputedStyle(leaf).letterSpacing,
      thLS: getComputedStyle(h).letterSpacing,
      ariaSort: h.getAttribute("aria-sort"),
      th: [Math.round(hb.left * 10) / 10, Math.round(hb.right * 10) / 10],
      ctlR: cb ? Math.round(cb.right * 10) / 10 : null,
      iconR: ib ? Math.round(ib.right * 10) / 10 : null,
      inSlot: cb ? cb.right <= hb.right + 0.5 && (!ib || ib.right <= hb.right + 0.5) : null,
      noOverlapNext: nb ? (cb ? Math.max(cb.right, ib ? ib.right : 0) : hb.right) <= nb.left + 0.5 : null,
    };
  });
  const sc = table.parentElement;
  return {
    heads,
    table: { sw: table.scrollWidth, cw: table.clientWidth, wrapSW: sc.scrollWidth, wrapCW: sc.clientWidth },
    doc: { sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth },
  };
};

(async () => {
  const t = await L.idpToken("alice");
  const r = await L.apiGet(t, "/api/legal/disbursements?page=0&size=50");
  const ds = L.list(r.json);
  if (!ds.length) throw new Error("no disbursements on the stack");
  const matterId = ds[0].matterId;
  const browser = await L.chromium.launch();
  const st = await L.loginState(browser, "alice");
  const out = { at: new Date().toISOString(), sha: "d61e5dd4e", disbursementCount: ds.length, matterId, runs: [] };
  const tiles = [];
  for (const [surface, u] of [["list", `${B}/legal/disbursements`], ["matter-tab", `${B}/matters/${matterId}?tab=disbursements`]]) {
    for (const [w, h] of [[1024, 768], [1440, 900]]) {
      for (const theme of ["light", "dark"]) {
        const ctx = await L.newCtx(browser, st, { width: w, height: h, theme, extra: L.EXTRA });
        const page = await ctx.newPage();
        await page.goto(u, { waitUntil: "load" });
        await page.waitForLoadState("networkidle").catch(() => {});
        await page.locator(TID).first().waitFor({ timeout: 20000 });
        await page.waitForTimeout(1000);
        const before = await page.evaluate(probe);
        const thead = page.locator(`${TID} thead`).first();
        await thead.scrollIntoViewIfNeeded();
        const f = path.join(L.RAW, `${surface}-${w}-${theme}.png`);
        const bb = await thead.boundingBox();
        await page.screenshot({ path: f, clip: { x: Math.max(0, bb.x - 8), y: Math.max(0, bb.y - 8), width: Math.min(w - Math.max(0, bb.x - 8), bb.width + 16), height: bb.height + 16 } });
        tiles.push([`${surface} ${w}x${h} ${theme}`, f]);
        // Click the "Date" sort header.
        const dateCtl = page.locator(`${TID} thead th`).filter({ hasText: /^date$/i }).locator("button,a").first();
        const ariaBefore = await page.locator(`${TID} thead th`).filter({ hasText: /^date$/i }).first().getAttribute("aria-sort");
        await dateCtl.click();
        await page.waitForLoadState("networkidle").catch(() => {});
        await page.waitForTimeout(1200);
        const after = await page.evaluate(probe);
        const dAfter = after.heads ? after.heads.find((x) => /^date$/i.test(x.text)) : null;
        const ariaAll = after.heads ? after.heads.filter((x) => x.ariaSort).map((x) => `${x.text}=${x.ariaSort}`) : [];
        out.runs.push({
          surface, w, h, theme, url: page.url().replace(L.APP, ""),
          heads: before.heads, table: before.table, doc: before.doc,
          click: { ariaBefore, ariaAfter: dAfter && dAfter.ariaSort, ariaAll, dateTextAfter: dAfter && dAfter.text, allUpperAfter: after.heads ? after.heads.every((x) => x.upper) : false },
        });
        await ctx.close();
      }
    }
  }
  await L.sheet(browser, path.join(L.OUT, "sheet-disbursement-headers.png"), "669A after-fix @d61e5dd4e — disbursement table headers (list + matter tab × 1024/1440 × light/dark)", tiles, 2, 760);
  await browser.close();
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(out));
  for (const x of out.runs) console.log(x.surface, x.w, x.theme, JSON.stringify(x.heads.map((y) => `${y.text}|${y.tt}|${y.ls}/${y.thLS}|slot:${y.inSlot}|next:${y.noOverlapNext}`)), JSON.stringify(x.table), JSON.stringify(x.doc), JSON.stringify(x.click));
})().catch((e) => { console.error("FAIL", e); process.exit(1); });
