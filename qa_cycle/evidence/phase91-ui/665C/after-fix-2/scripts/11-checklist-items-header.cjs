// 665C OBS after-fix-2 step 11 (new, read-only): re-observes F-665C-15 after b87d6d742
// (`headerClassName: "whitespace-normal"` on the Document Required column). As Alice, on
// checklists/{id} for PLATFORM "Generic Client Onboarding" and ORG_CUSTOM "OBS 665C Checklist" (ids by
// name, GET /api/checklist-templates), at 1024×768 / 1440×900 × light / dark:
//   - the items-table scroller clientWidth / scrollWidth;
//   - docHOverflowPx;
//   - every <th>: class / white-space, line count, text rects inside the <th> and inside the scroller
//     box (no clipped header text), th scrollWidth <= clientWidth;
//   - every "Required" badge and every Document Required cell inside the box and untruncated;
//   - the h1 count in #main-content;
//   - console errors / warnings / hydration messages (sink from common.cjs `watch`).
// Writes ../checks.json, sheet-checklist-{pack,custom}-alice.png (1024/1440 × light/dark full page)
// and crop-checklist-pack-header-1024-light-3x.png. No writes to the app; no SQL.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const VIEWPORTS = [[1024, 768], [1440, 900]];
const VARIANTS = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const consoleSink = [];
const lab = { label: "" };
const tiles = {};
const out = { at: new Date().toISOString(), checklist: {} };

const PROBE = () => {
  const main = document.querySelector("main#main-content");
  const row0 = main.querySelector('[data-testid="checklist-item-row"]');
  if (!row0) return { error: "no checklist-item-row" };
  const t = row0.closest("table");
  let sc = t.parentElement;
  while (sc && sc !== main && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
  const sr = sc.getBoundingClientRect();
  const inBox = (r) => r.left >= sr.left - 0.5 && r.right <= sr.right + 0.5 && r.right <= innerWidth + 0.5;
  const textRects = (el) => { const rg = document.createRange(); rg.selectNodeContents(el); return [...rg.getClientRects()].filter((r) => r.width > 0); };
  const ths = [...t.querySelectorAll("thead th")].map((h) => {
    const hr = h.getBoundingClientRect();
    const rs = textRects(h);
    const tops = new Set(rs.map((r) => Math.round(r.top)));
    return {
      text: (h.innerText || "").trim().replace(/\s+/g, " "),
      cls: h.className,
      ws: getComputedStyle(h).whiteSpace,
      va: getComputedStyle(h).verticalAlign,
      w: Math.round(hr.width), h: Math.round(hr.height), left: Math.round(hr.left), right: Math.round(hr.right),
      lines: tops.size,
      textTop: rs.length ? Math.round(Math.min(...rs.map((r) => r.top))) : null,
      textBottom: rs.length ? Math.round(Math.max(...rs.map((r) => r.bottom))) : null,
      textRight: rs.length ? Math.round(Math.max(...rs.map((r) => r.right))) : null,
      textInTh: rs.every((r) => r.left >= hr.left - 0.5 && r.right <= hr.right + 0.5),
      textInBox: rs.every((r) => inBox(r)),
      thInBox: inBox(hr),
      noOverflow: h.scrollWidth <= h.clientWidth,
    };
  });
  const idx = Object.fromEntries(ths.map((h, i) => [h.text.toLowerCase(), i]));
  const rows = [...t.querySelectorAll('[data-testid="checklist-item-row"]')].map((r) => {
    const td = [...r.children];
    const rq = td[idx["required"]], dr = td[idx["document required"]];
    const badge = rq && rq.querySelector('[data-slot="badge"]');
    const drEl = dr && (dr.querySelector('[data-slot="badge"]') || dr.querySelector("span") || dr);
    return {
      name: (td[idx["name"]].innerText || "").trim().slice(0, 40),
      required: badge ? { text: badge.textContent.trim(), inBox: inBox(badge.getBoundingClientRect()), untruncated: badge.scrollWidth <= badge.clientWidth } : { text: (rq.innerText || "").trim(), inBox: inBox(rq.getBoundingClientRect()), untruncated: true },
      docRequired: { text: (drEl.innerText || drEl.textContent || "").trim().slice(0, 40), inBox: inBox(drEl.getBoundingClientRect()), untruncated: drEl.scrollWidth <= drEl.clientWidth },
    };
  });
  const tr = t.querySelector("thead tr").getBoundingClientRect();
  return {
    h1: [...main.querySelectorAll("h1")].map((h) => (h.innerText || "").trim()),
    scroller: { clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, left: Math.round(sr.left), right: Math.round(sr.right), fits: sc.scrollWidth <= sc.clientWidth },
    theadRow: { top: Math.round(tr.top), height: Math.round(tr.height) },
    headers: ths,
    rows,
    summary: {
      rows: rows.length,
      requiredBadges: rows.filter((r) => r.required.text === "Required").length,
      requiredAllVisible: rows.every((r) => r.required.inBox && r.required.untruncated),
      docRequiredAllVisible: rows.every((r) => r.docRequired.inBox && r.docRequired.untruncated),
      headersAllVisible: ths.every((h) => h.thInBox && h.textInTh && h.textInBox && h.noOverflow),
      drHeaderHasClass: ths.some((h) => h.text.toLowerCase() === "document required" && /\bwhitespace-normal\b/.test(h.cls)),
    },
    hOverflow: Math.max(0, document.scrollingElement.scrollWidth - document.scrollingElement.clientWidth),
  };
};

async function open(browser, S, w, h, theme, dpr = 1) {
  const ctx = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: dpr, storageState: S, ...L.EXTRA });
  await ctx.addCookies([{ name: "sidebar_state", value: "true", domain: "localhost", path: "/" }]);
  await ctx.addInitScript(L.INIT, theme);
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  return { ctx, p };
}
async function go(p, url) {
  await p.goto(`${L.BASE}${url}`, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(400);
  await p.mouse.move(0, 0);
  await p.waitForTimeout(200);
}

(async () => {
  const a = await L.idpToken("alice");
  const cl = L.list((await L.apiGet(a, "/api/checklist-templates")).json);
  const byName = (n) => cl.find((x) => x.name === n) || null;
  const CL = { pack: byName("Generic Client Onboarding"), custom: byName("OBS 665C Checklist") };
  out.checklistIds = Object.fromEntries(Object.entries(CL).map(([k, v]) => [k, v && { id: v.id, name: v.name, source: v.source }]));
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  for (const [w, h] of VIEWPORTS)
    for (const theme of ["light", "dark"]) {
      const v = `${w}-${theme}`;
      const { ctx, p } = await open(browser, S, w, h, theme);
      for (const k of ["pack", "custom"]) {
        lab.label = `11|checklists-id-${k}|${v}`;
        await go(p, `/settings/matters/checklists/${CL[k].id}`);
        const f = path.join(L.RAW, `checklists-id-${k}-${v}.png`);
        await p.screenshot({ path: f, fullPage: true });
        out.checklist[`${k}|${v}`] = await p.evaluate(PROBE);
        const r = out.checklist[`${k}|${v}`];
        tiles[`${k}-${v}`] = [`${w}×${h} ${theme} — scroller ${r.scroller.clientWidth}/${r.scroller.scrollWidth}, docHOverflow ${r.hOverflow}`, f];
      }
      await ctx.close();
    }

  // 3× crop of the PLATFORM table header (plus the first row) at 1024 light.
  {
    const { ctx, p } = await open(browser, S, 1024, 768, "light", 3);
    lab.label = "11|crop-pack-header-1024-light";
    await go(p, `/settings/matters/checklists/${CL.pack.id}`);
    const c = await p.evaluate(() => {
      const t = document.querySelector('[data-testid="checklist-item-row"]').closest("table");
      let s = t.parentElement; while (s && !/(auto|scroll)/.test(getComputedStyle(s).overflowX)) s = s.parentElement;
      const sr = s.getBoundingClientRect(), hr = t.querySelector("thead").getBoundingClientRect(), r1 = t.querySelector("tbody tr").getBoundingClientRect();
      return { x: sr.left, w: sr.width, y: hr.top + scrollY, h: r1.bottom - hr.top };
    });
    await p.screenshot({ path: path.join(L.OUT, "crop-checklist-pack-header-1024-light-3x.png"), fullPage: true, clip: { x: Math.max(0, c.x - 16), y: Math.max(0, c.y - 12), width: Math.min(1024 - Math.max(0, c.x - 16), c.w + 32), height: c.h + 24 } });
    await ctx.close();
  }

  const COLS = "cols 1024 light | 1024 dark | 1440 light | 1440 dark, full page";
  const vt = (k) => VARIANTS.map((v) => tiles[`${k}-${v}`] || [`${k}-${v} (not shot)`, null]);
  await L.sheet(browser, path.join(L.OUT, "sheet-checklist-pack-alice.png"), `665C after-fix-2 (b87d6d742) — checklists/{id} PLATFORM 'Generic Client Onboarding', Alice: ${COLS}. F-665C-15: Document Required header wraps`, vt("pack"), 4, 520);
  await L.sheet(browser, path.join(L.OUT, "sheet-checklist-custom-alice.png"), `665C after-fix-2 (b87d6d742) — checklists/{id} ORG_CUSTOM 'OBS 665C Checklist', Alice: ${COLS}. F-665C-15`, vt("custom"), 4, 520);
  await browser.close();

  out.console = { n: consoleSink.length, errors: consoleSink.filter((m) => m.type === "error" || m.type === "pageerror").length, warnings: consoleSink.filter((m) => m.type === "warning").length, hydration: consoleSink.filter((m) => /hydrat/i.test(m.text)).length, messages: consoleSink };
  fs.writeFileSync(path.join(L.OUT, "checks.json"), JSON.stringify(out, null, 1) + "\n");
  for (const [k, r] of Object.entries(out.checklist))
    console.log(k, JSON.stringify({ sc: `${r.scroller.clientWidth}/${r.scroller.scrollWidth}`, ov: r.hOverflow, h1: r.h1, ...r.summary, th: r.headers.map((h) => `${h.text}:${h.w}w:${h.lines}L:${h.ws}:${h.va}:top${h.textTop}${h.thInBox && h.textInTh && h.textInBox && h.noOverflow ? "" : ":CLIP"}`) }));
  console.log("ids", JSON.stringify(out.checklistIds));
  console.log("console", JSON.stringify({ n: out.console.n, errors: out.console.errors, warnings: out.console.warnings, hydration: out.console.hydration }));
})().catch((e) => { console.error(e); process.exit(1); });
