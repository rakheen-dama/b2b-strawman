// 665B OBS-after step 6 (NEW, after-only; the before run had no equivalent): the gate checks the
// before scripts do not measure. Alice, read-only (URL changes, row expand, nothing saved).
//  A. Page horizontal overflow (documentElement.scrollWidth vs clientWidth) at 1024×768, 1280×800
//     and 1440×900, light + dark, on the three tabs, plus every table scroller's visible/scroll
//     width; on the audit tab also with the first row expanded (the builder dropped the
//     `contain-inline-size` wrapper, so a wide details row must not widen the page).
//  B. K-109 at 390×844 light + dark: Processing Register header — "Export Register" / "Add Activity"
//     rects vs the section's action column and the viewport; page overflow; a 390 crop.
//  C. K-32: computed colour of the first audit entity link and "View DSAR Requests →" (light +
//     dark, 1440), composited over every ancestor background on a canvas, and the WCAG ratio; the
//     before colour is reproduced on the same canvas with an injected `text-teal-600` span (same
//     parent, same font) because the before run did not record the ratio.
//  D. Audit expand semantics: `[data-testid^=audit-row-]`.first() element, its "Expand details"
//     button, the details container, collapse on Next.
//  E. Dark/light box scan per tab at 1440: visible boxes ≥ 40×40 inside #main-content whose own
//     background is white or slate-950, and text whose composited contrast is < 4.5:1 (top 8).
// Output: $RAW/after-checks.json + tiles (crop-k109-390-*.png copied into ../ by 05b).
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const R = { capturedAt: new Date().toISOString(), overflow: {}, k109: {}, k32: {}, expand: {}, boxScan: {} };
const consoleSink = [];
const tiles = [];
const lab = { label: "" };
const TAB = (t) => `${L.BASE}/settings/firm?tab=${t}`;
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(500); };
const shot = async (target, key, caption, opt = {}) => {
  const f = path.join(L.RAW, `${key}.png`);
  await target.screenshot({ path: f, ...opt });
  tiles.push([key, f, caption]);
};

// In-page: composite helpers (canvas, starting from opaque white, then every ancestor bg bottom-up).
const HELP = () => {
  window.__px = (layers) => {
    const cv = document.createElement("canvas");
    cv.width = cv.height = 1;
    const x = cv.getContext("2d");
    x.fillStyle = "#fff";
    x.fillRect(0, 0, 1, 1);
    for (const c of layers) { x.fillStyle = "rgba(0,0,0,0)"; x.fillStyle = c; x.fillRect(0, 0, 1, 1); }
    const d = x.getImageData(0, 0, 1, 1).data;
    return [d[0], d[1], d[2]];
  };
  window.__alpha = (c) => {
    const cv = document.createElement("canvas");
    cv.width = cv.height = 1;
    const x = cv.getContext("2d");
    x.fillStyle = "rgba(0,0,0,0)"; x.fillStyle = c; x.fillRect(0, 0, 1, 1);
    return x.getImageData(0, 0, 1, 1).data[3];
  };
  window.__bgLayers = (e) => {
    const out = [];
    for (let n = e; n; n = n.parentElement) { const b = getComputedStyle(n).backgroundColor; if (window.__alpha(b) > 0) out.push(b); }
    return out.reverse();
  };
  window.__lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
  window.__ratio = (a, b) => { const [l1, l2] = [window.__lum(a), window.__lum(b)].sort((x, y) => y - x); return +((l1 + 0.05) / (l2 + 0.05)).toFixed(2); };
  window.__hx = (a) => "#" + a.map((v) => v.toString(16).padStart(2, "0")).join("");
  window.__probe = (e) => {
    const layers = window.__bgLayers(e);
    const bg = window.__px(layers);
    const fg = window.__px([...layers, getComputedStyle(e).color]);
    return { color: getComputedStyle(e).color, layers, bg: window.__hx(bg), fg: window.__hx(fg), ratio: window.__ratio(fg, bg), cls: (e.className || "").toString() };
  };
};
const MEASURE = () => {
  const se = document.documentElement;
  const sc = (t) => { for (let e = t.parentElement; e && e !== document.body; e = e.parentElement) { const cs = getComputedStyle(e); if (/(auto|scroll)/.test(cs.overflowX)) return e; } return null; };
  return {
    scrollWidth: se.scrollWidth, clientWidth: se.clientWidth, pageHOverflow: se.scrollWidth - se.clientWidth,
    tables: [...document.querySelectorAll("#main-content table")].map((t) => { const s = sc(t); return { headers: [...t.querySelectorAll("thead th")].map((h) => h.innerText.trim()).join("|"), table: Math.round(t.getBoundingClientRect().width), visible: s && s.clientWidth, scroll: s && s.scrollWidth }; }),
  };
};
const BOXSCAN = () => {
  const main = document.querySelector("#main-content");
  const hx = (c) => window.__hx(window.__px([c]));
  const boxes = [];
  for (const e of main.querySelectorAll("*")) {
    const b = e.getBoundingClientRect();
    if (b.width < 40 || b.height < 40) continue;
    const bg = getComputedStyle(e).backgroundColor;
    if (window.__alpha(bg) < 250) continue;
    const h = hx(bg);
    if (h === "#ffffff" || h === "#020618" || h === "#020617") boxes.push({ tag: e.tagName.toLowerCase(), slot: e.dataset.slot, testid: e.dataset.testid, bg: h, w: Math.round(b.width), hgt: Math.round(b.height), cls: (e.className || "").toString().slice(0, 100), text: (e.innerText || "").replace(/\s+/g, " ").trim().slice(0, 50) });
  }
  const low = [];
  for (const e of main.querySelectorAll("*")) {
    if (![...e.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim())) continue;
    const b = e.getBoundingClientRect(); const cs = getComputedStyle(e);
    if (!b.width || !b.height || cs.visibility === "hidden" || +cs.opacity === 0 || e.closest("[aria-hidden=true],.sr-only,[disabled]")) continue;
    const pr = window.__probe(e);
    if (pr.ratio < 4.5) low.push({ text: e.innerText.replace(/\s+/g, " ").trim().slice(0, 40), tag: e.tagName.toLowerCase(), ratio: pr.ratio, fg: pr.fg, bg: pr.bg, fontPx: cs.fontSize, weight: cs.fontWeight });
  }
  low.sort((a, b) => a.ratio - b.ratio);
  const uniq = []; const seen = new Set();
  for (const l of low) { const k = `${l.text}|${l.ratio}`; if (!seen.has(k)) { seen.add(k); uniq.push(l); } }
  return { whiteOrSlate950Boxes: boxes, lowContrastCount: uniq.length, lowContrastWorst: uniq.slice(0, 8) };
};

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctxFor = async (w, h, theme) => { const ctx = await L.newCtx(browser, S, { width: w, height: h, theme, extra: L.EXTRA }); const p = await ctx.newPage(); L.watch(p, consoleSink, lab); return { ctx, p }; };

  // A. overflow
  for (const [w, h] of [[1024, 768], [1280, 800], [1440, 900]])
    for (const theme of ["light", "dark"]) {
      const { ctx, p } = await ctxFor(w, h, theme);
      for (const tab of L.TABS) {
        lab.label = `overflow|${tab}|${w}-${theme}`;
        await p.goto(TAB(tab), { waitUntil: "load" });
        await settle(p);
        R.overflow[`${tab} ${w}-${theme}`] = await p.evaluate(MEASURE);
        if (tab === "audit-trail") {
          await p.locator('[data-testid^="audit-row-toggle-"]').first().click();
          await p.waitForTimeout(400);
          R.overflow[`${tab} ${w}-${theme} (row 1 expanded)`] = await p.evaluate(MEASURE);
          if (theme === "light" && w !== 1440) await shot(p, `ovf__audit-expanded-${w}`, `Alice ${w} light — audit trail, first row expanded: page h-overflow ${R.overflow[`${tab} ${w}-${theme} (row 1 expanded)`].pageHOverflow}px`, { fullPage: false });
        }
      }
      await ctx.close();
    }

  // B. K-109 at 390
  for (const theme of ["light", "dark"]) {
    const { ctx, p } = await ctxFor(390, 844, theme);
    lab.label = `k109|${theme}`;
    await p.goto(TAB("data-protection"), { waitUntil: "load" });
    await settle(p);
    const sec = p.locator('[data-slot="settings-section"]').filter({ has: p.getByRole("heading", { level: 2, name: "Processing Register", exact: true }) });
    await sec.scrollIntoViewIfNeeded();
    const m = await sec.evaluate((s) => {
      const r = (e) => { if (!e) return null; const b = e.getBoundingClientRect(); return { left: Math.round(b.left), right: Math.round(b.right), top: Math.round(b.top), bottom: Math.round(b.bottom), width: Math.round(b.width) }; };
      const btn = (n) => [...s.querySelectorAll("button")].find((b) => b.innerText.trim() === n);
      const action = s.querySelector('[data-slot="card-action"]');
      const header = s.querySelector('[data-slot="card-header"]');
      const exp = btn("Export Register"), add = btn("Add Activity");
      const ar = r(action), er = r(exp), dr = r(add);
      return {
        section: r(s), header: r(header), actionColumn: ar, title: r(s.querySelector("h2")), description: r(s.querySelector('[data-slot="card-description"]')),
        exportRegister: er, addActivity: dr,
        wrapped: er && dr ? er.top !== dr.top : null,
        buttonsInsideActionColumn: ar && er && dr ? er.left >= ar.left && er.right <= ar.right && dr.left >= ar.left && dr.right <= ar.right : null,
        viewport: innerWidth, pageScrollWidth: document.documentElement.scrollWidth, pageClientWidth: document.documentElement.clientWidth,
      };
    });
    m.pageHOverflow = m.pageScrollWidth - m.pageClientWidth;
    m.addActivityRightLe390 = m.addActivity ? m.addActivity.right <= 390 : null;
    R.k109[theme] = m;
    const box = await sec.boundingBox();
    const top = Math.max(0, box.y - 8);
    await shot(p, `k109__390-${theme}`, `K-109 at 390×844 ${theme}: Processing Register header — Add Activity right ${m.addActivity && m.addActivity.right} (≤ 390: ${m.addActivityRightLe390}), wrapped ${m.wrapped}, inside action column ${m.buttonsInsideActionColumn}; page h-overflow ${m.pageHOverflow}px`, { clip: { x: 0, y: top, width: 390, height: Math.min(300, 844 - top) } });
    await ctx.close();
  }

  // C. K-32 (+ before colour reproduced) and E. box scan
  for (const theme of ["light", "dark"]) {
    const { ctx, p } = await ctxFor(1440, 900, theme);
    lab.label = `k32|audit|${theme}`;
    await p.goto(TAB("audit-trail"), { waitUntil: "load" });
    await settle(p);
    await p.evaluate(HELP);
    R.k32[`audit entity link ${theme}`] = await p.evaluate(() => {
      const a = document.querySelector('[data-testid="entity-cell-link"]');
      const after = window.__probe(a);
      const s = document.createElement("span"); s.className = "text-xs whitespace-nowrap text-teal-600 dark:text-teal-400"; s.textContent = "x"; a.parentElement.appendChild(s);
      const before = window.__probe(s); s.remove();
      return { text: a.innerText.trim(), after, beforeReproduced: before };
    });
    R.boxScan[`audit-trail ${theme}`] = await p.evaluate(BOXSCAN);
    lab.label = `k32|dp|${theme}`;
    await p.goto(TAB("data-protection"), { waitUntil: "load" });
    await settle(p);
    await p.evaluate(HELP);
    R.k32[`View DSAR Requests ${theme}`] = await p.evaluate(() => {
      const a = [...document.querySelectorAll("#main-content a")].find((x) => /View DSAR Requests/.test(x.innerText));
      const after = window.__probe(a);
      const s = document.createElement("span"); s.className = "text-sm font-medium text-teal-600 dark:text-teal-400"; s.textContent = "x"; a.parentElement.appendChild(s);
      const before = window.__probe(s); s.remove();
      return { text: a.innerText.trim(), after, beforeReproduced: before };
    });
    R.boxScan[`data-protection ${theme}`] = await p.evaluate(BOXSCAN);
    if (theme === "dark") {
      const sec = p.locator('[data-slot="settings-section"]').filter({ has: p.getByRole("heading", { level: 2, name: "Data Subject Access Requests", exact: true }) });
      await sec.scrollIntoViewIfNeeded();
      await shot(sec, "dark__dsar-section", `Alice 1440 dark — Data Subject Access Requests section (before: bg-white / dark:bg-slate-950 box)`);
    }
    lab.label = `scan|dr|${theme}`;
    await p.goto(TAB("data-requests"), { waitUntil: "load" });
    await settle(p);
    await p.evaluate(HELP);
    R.boxScan[`data-requests ${theme}`] = await p.evaluate(BOXSCAN);
    await ctx.close();
  }

  // D. expand semantics
  {
    const { ctx, p } = await ctxFor(1440, 900, "light");
    lab.label = "expand";
    await p.goto(TAB("audit-trail"), { waitUntil: "load" });
    await settle(p);
    const first = p.locator('[data-testid^="audit-row-"]').first();
    R.expand.firstElement = await first.evaluate((e) => ({ tag: e.tagName.toLowerCase(), testid: e.dataset.testid }));
    R.expand.expandButtonsInFirst = await first.getByRole("button", { name: "Expand details" }).count();
    await first.getByRole("button", { name: "Expand details" }).click();
    await p.waitForTimeout(400);
    const d = p.locator('[data-testid^="audit-row-details-"]');
    R.expand.afterClick = await d.evaluateAll((els) => els.map((e) => ({ tag: e.tagName.toLowerCase(), testid: e.dataset.testid, inTr: !!e.closest("tr"), trTestid: e.closest("tr") && e.closest("tr").dataset.testid || null, text: e.innerText.replace(/\s+/g, " ").trim().slice(0, 200) })));
    R.expand.detailsIdMatchesRow = R.expand.afterClick.length === 1 && R.expand.afterClick[0].testid === R.expand.firstElement.testid.replace("audit-row-", "audit-row-details-");
    R.expand.toggleLabelAfter = await p.locator('[data-testid^="audit-row-toggle-"]').first().getAttribute("aria-label");
    R.expand.firstElementAfterExpand = await p.locator('[data-testid^="audit-row-"]').first().evaluate((e) => ({ tag: e.tagName.toLowerCase(), testid: e.dataset.testid }));
    await Promise.all([p.waitForURL(/page=1/), p.getByRole("button", { name: "Next", exact: true }).click()]);
    await settle(p);
    R.expand.afterNext = { url: p.url().replace(L.BASE, ""), detailsRows: await d.count(), expandedToggles: await p.locator('[data-testid^="audit-row-toggle-"][aria-expanded="true"]').count() };
    await Promise.all([p.waitForURL((u) => !/[?&]page=/.test(u.search)), p.getByRole("button", { name: "Previous", exact: true }).click()]);
    await settle(p);
    R.expand.backOnPage1 = { url: p.url().replace(L.BASE, ""), detailsRows: await d.count() };
    await ctx.close();
  }

  await browser.close();
  R.console = consoleSink;
  R.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "after-checks.json"), JSON.stringify(R));
  for (const k of L.RAW ? Object.keys(R.overflow) : []) console.log("ovf", k, JSON.stringify(R.overflow[k]));
  console.log("k109", JSON.stringify(R.k109));
  console.log("k32", JSON.stringify(R.k32));
  console.log("expand", JSON.stringify(R.expand));
  for (const [k, v] of Object.entries(R.boxScan)) console.log("scan", k, JSON.stringify({ boxes: v.whiteOrSlate950Boxes, low: v.lowContrastCount, worst: v.lowContrastWorst }));
  console.log("console", consoleSink.length, JSON.stringify(consoleSink).slice(0, 1200));
})();
