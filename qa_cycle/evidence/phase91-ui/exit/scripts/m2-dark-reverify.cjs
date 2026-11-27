// 670A 670.6 targeted re-verification + defect crops (Alice, 1440×900, stored dark). For each carried
// item / DD row: open the page (read-only; a menu is opened with the keyboard and closed with Escape),
// scroll the element into view, measure its text/icon colour against its composited background on a
// 1×1 canvas (S7 gotcha 14; same math as m2-dark-audit.cjs), and cut a padded crop. All crops go on one
// sheet ../darkmode-crops.png (captions carry the measured values); numbers → ../darkwalk.json → reverify.
// Usage: node m2-dark-reverify.cjs
const fs = require("fs");
const path = require("path");
const L = require("./lib.cjs");
const ids = L.readIds();
const m2 = ids.m2;
const B = L.BASE;
const RAWD = path.join(L.RAW, "reverify");
fs.mkdirSync(RAWD, { recursive: true });

// Composite fg/bg + border of the first visible match of `sel` (optionally whose text matches `re`).
const PROBE = ([sel, re, attr]) => {
  const cv = document.createElement("canvas"); cv.width = cv.height = 1; const cx = cv.getContext("2d", { willReadFrequently: true });
  const rgba = (c) => { cx.clearRect(0, 0, 1, 1); cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); const d = cx.getImageData(0, 0, 1, 1).data; return [d[0], d[1], d[2], d[3] / 255]; };
  const blend = (t, u) => [t[0] * t[3] + u[0] * (1 - t[3]), t[1] * t[3] + u[1] * (1 - t[3]), t[2] * t[3] + u[2] * (1 - t[3]), 1];
  const lum = (c) => { const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(c[0]) + 0.7152 * f(c[1]) + 0.0722 * f(c[2]); };
  const ratio = (a, b) => { const x = lum(a), y = lum(b); return +((Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05)).toFixed(2); };
  const hex = (c) => "#" + c.slice(0, 3).map((v) => Math.round(v).toString(16).padStart(2, "0")).join("");
  const bgOf = (el) => { if (!el || el.nodeType !== 1) return rgba(getComputedStyle(document.body).backgroundColor); const o = rgba(getComputedStyle(el).backgroundColor); return o[3] >= 0.999 ? o : o[3] > 0 ? blend(o, bgOf(el.parentElement)) : bgOf(el.parentElement); };
  const rx = re ? new RegExp(re) : null;
  const el = [...document.querySelectorAll(sel)].find((e) => { const r = e.getBoundingClientRect(); return r.width > 1 && r.height > 1 && (!rx || rx.test((e.getAttribute(attr || "") || e.textContent || "").trim())); });
  if (!el) return null;
  el.scrollIntoView({ block: "center" });
  const cs = getComputedStyle(el); const own = bgOf(el); const under = bgOf(el.parentElement);
  const fg = blend(rgba(cs.color), own); const bc = blend(rgba(cs.borderTopColor), own);
  const r = el.getBoundingClientRect();
  return { text: (el.getAttribute("aria-label") || el.textContent || "").trim().replace(/\s+/g, " ").slice(0, 40), fg: hex(fg), bg: hex(own), textRatio: ratio(fg, own), fillVsSurround: ratio(own, under), surround: hex(under), border: hex(bc), borderWidth: cs.borderTopWidth, borderVsSurround: ratio(bc, under), cls: String(el.className).slice(0, 220), box: [r.x, r.y, r.width, r.height].map(Math.round) };
};

const CASES = [
  { id: "DD-1a", label: "K-21 / K-31: header notification badge", url: `${B}/dashboard`, sel: 'header span[class*="button-accent"]' },
  { id: "DD-1b", label: "K-21: accent button 'Send fee note' (APPROVED INV-0009)", url: `${B}/invoices/${m2.invoices.APPROVED.id}`, sel: "#main-content button", re: "^Send fee note$" },
  { id: "DD-1c", label: "F-666A-9: fee-run wizard active step badge", url: `${B}/invoices/billing-runs/new`, sel: "#main-content span, #main-content div", re: "^1$" },
  { id: "DD-2", label: "K-21 + F-670A-3: row menu (adverse parties), first item focused via ArrowDown", url: `${B}/legal/adverse-parties`, menu: true },
  { id: "DD-3a", label: "K-57 / F-668A-A-2: My Work agenda label", url: `${B}/my-work`, sel: "#main-content p, #main-content span, #main-content div", re: "^Time Today$" },
  { id: "DD-3b", label: "K-57: matter Overview setup steps", url: `${B}/matters/${ids.matterId}?tab=overview`, sel: "#main-content .line-through", re: "^Client assigned$" },
  { id: "DD-3c", label: "K-57: audit trail total / entity ids", url: `${B}/settings/firm?tab=audit-trail`, sel: "#main-content span", re: "^\\d+ total$" },
  { id: "DD-4", label: "K-58: matter Team avatars", url: `${B}/matters/${ids.matterId}?tab=team`, sel: "#main-content div.rounded-full, #main-content span.rounded-full", re: "^[A-Z]{2}$" },
  { id: "DD-5a", label: "F-665C-14: Data protection processing-register Delete icon", url: `${B}/settings/firm?tab=data-protection`, sel: "#main-content button", re: "^Delete ", attr: "aria-label" },
  { id: "DD-5b", label: "F-665C-14: Compliance retention Delete icon", url: `${B}/settings/firm?tab=compliance`, sel: "#main-content button", re: "^Delete", attr: "aria-label" },
  { id: "DD-5c", label: "D-55 workaround (red, for comparison): Tax rates Deactivate", url: `${B}/settings/fees?tab=tax`, sel: "#main-content button", re: "^Deactivate ", attr: "aria-label" },
  { id: "DD-6", label: "K-13: Card border (My Work)", url: `${B}/my-work`, sel: '#main-content [data-slot="card"]' },
  { id: "DD-7", label: "F-670A-4: Rates currency selector inside the card", url: `${B}/settings/fees?tab=tariffs`, sel: '#main-content button[role="combobox"]' },
  { id: "DD-7b", label: "F-670A-4 sibling (Phase 90 same 1.15): audit-trail event-type trigger", url: `${B}/settings/firm?tab=audit-trail`, sel: "#audit-filter-event-type" },
  { id: "P90-D-5", label: "Phase 90 D-5: Xero card button ('Connect to Xero' only when not configured)", url: `${B}/settings/connections?tab=xero`, sel: "#main-content button, #main-content a", re: "Xero" },
  { id: "P90-D-1", label: "Phase 90 D-1: My Work time-today figure", url: `${B}/my-work`, sel: '[data-testid="time-progress-today"]' },
];

(async () => {
  const browser = await L.chromium.launch();
  const state = await L.loginState(browser, "alice");
  const ctx = await L.ctxFor(browser, state, "1440-dark");
  const page = await ctx.newPage();
  const out = [];
  const cells = [];
  for (const c of CASES) {
    const rec = { id: c.id, label: c.label, url: c.url.replace(L.APP, "") };
    try {
      await page.goto(c.url, { waitUntil: "load", timeout: 45000 });
      await L.settle(page);
      rec.dark = await page.evaluate(() => document.documentElement.classList.contains("dark"));
      if (c.menu) {
        await page.locator('#main-content button[aria-haspopup="menu"]').first().click();
        await page.waitForTimeout(600);
        await page.keyboard.press("ArrowDown");
        await page.waitForTimeout(400);
        rec.items = [];
        for (const re of ["^Link to Matter$", "^Delete$"]) rec.items.push(await page.evaluate(PROBE, ['[role="menuitem"]', re, null]));
        rec.m = rec.items[0];
      } else {
        rec.m = await page.evaluate(PROBE, [c.sel, c.re || null, c.attr || null]);
        await page.waitForTimeout(400);
        if (rec.m) rec.m.box = await page.evaluate(PROBE, [c.sel, c.re || null, c.attr || null]).then((x) => x && x.box);
      }
      const bx = rec.m ? rec.m.box : null;
      const file = path.join(RAWD, `${c.id}.png`);
      if (bx) {
        const pad = c.menu ? 160 : 90;
        const x = Math.max(0, bx[0] - pad), y = Math.max(0, bx[1] - pad);
        const w = Math.min(1440 - x, bx[2] + 2 * pad + (c.menu ? 160 : 0)), h = Math.min(900 - y, bx[3] + 2 * pad);
        await page.screenshot({ path: file, clip: { x, y, width: Math.max(40, w), height: Math.max(40, h) } });
      }
      if (c.menu) await page.keyboard.press("Escape");
      rec.file = bx ? L.rel(file) : null;
      const mm = rec.m;
      const cap = `${c.id} · ${c.label} · ${rec.url.replace(`/org/${L.ORG}`, "")} · dark ${rec.dark} · ` + (c.menu ? rec.items.map((i) => (i ? `"${i.text}" ${i.fg} on ${i.bg} ${i.textRatio}:1` : "item n/a")).join(" | ") : mm ? `"${mm.text}" text ${mm.fg} on ${mm.bg} ${mm.textRatio}:1 · fill vs surround ${mm.fillVsSurround}:1 · border ${mm.border} (${mm.borderWidth}) vs surround ${mm.surround} ${mm.borderVsSurround}:1` : "element NOT FOUND");
      cells.push([cap, bx ? file : null, 560]);
    } catch (e) { rec.error = String(e).slice(0, 200); cells.push([`${c.id} · ${c.label} · ERROR ${rec.error}`, null, 560]); }
    out.push(rec);
    console.log(c.id, JSON.stringify(rec.items || rec.m || rec.error).slice(0, 330));
  }
  await ctx.close();
  await L.sheet(browser, path.join(L.EXIT, "darkmode-crops.png"), "670A 670.6 dark walk — measured re-verification crops (Alice, 1440×900, stored dark; composited canvas ratios)", cells, 3);
  await browser.close();
  const f = path.join(L.EXIT, "darkwalk.json");
  const j = JSON.parse(fs.readFileSync(f, "utf8"));
  j.reverify = { capturedAt: new Date().toISOString(), cases: out };
  fs.writeFileSync(f, JSON.stringify(j) + "\n");
  console.log("DONE", out.length);
})().catch((e) => { console.error("REVERIFY FAIL", e); process.exit(1); });
