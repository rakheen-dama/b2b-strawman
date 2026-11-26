// 665C OBS-after step 7 (after-only, no before equivalent): frame / dark / table checks per Alice
// surface, and the K-15 tag chips with real rows.
//   A. Every Alice surface from 01-capture (8 tabs, the wizard, 9 sub-pages) × 1024/1440 × light/dark,
//      read-only: SettingsSection / card counts; nested frames (a card or settings-section inside
//      another, or a bordered rounded box ≥ 200×80 inside a section = double-frame candidates);
//      h2 texts; every table's scroller visible/scroll width, visible headers, and whether the last
//      header (Actions) is reachable (scroller scrolled to its end: header right ≤ scroller right and
//      ≤ viewport); dark-only: visible boxes ≥ 40×40 whose own background is near-white; all themes:
//      text below 4.5:1 / 3:1 against its composited background.
//   B. Tags with rows (the fresh stack has 0 tags, so 01-capture saw the EmptyState): through the UI as
//      Alice, "Add Tag" → Create Tag "OBS 665C Dark" #1E293B and "OBS 665C Light" #FDE68A; per variant
//      the chip text colour, background and contrast, the SEL-COMPAT tag keys (cell exact, Add Tag,
//      Edit/Delete sr-only names), the table scroller; tiles; then both deleted through the UI
//      (Delete → AlertDialog "Delete Tag" → Delete). Tags end as [] (as before this script).
// Writes ../after-checks.json (single line), sheet-tags-rows-k15-alice.png; raw tiles in $RAW.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const out = { at: new Date().toISOString(), surfaces: {}, tags: {} };
const consoleSink = [];
const lab = { label: "" };
const tiles = [];
const VIEWPORTS = [[1024, 768], [1440, 900]];

const PROBE = (theme) => {
  const main = document.querySelector("main#main-content");
  const vis = (e) => { const b = e.getBoundingClientRect(); const cs = getComputedStyle(e); return b.width > 0 && b.height > 0 && cs.visibility !== "hidden" && cs.display !== "none" && cs.opacity !== "0"; };
  const txt = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
  const cv = document.createElement("canvas"); cv.width = cv.height = 1;
  const cx = cv.getContext("2d", { willReadFrequently: true });
  const rgba = (c) => { cx.clearRect(0, 0, 1, 1); cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); const d = cx.getImageData(0, 0, 1, 1).data; return [d[0], d[1], d[2], d[3] / 255]; };
  const over = (top, bot) => [0, 1, 2].map((i) => Math.round(top[i] * top[3] + bot[i] * (1 - top[3])));
  const lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
  const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return +((x + 0.05) / (y + 0.05)).toFixed(2); };
  const hex = (a) => "#" + a.slice(0, 3).map((v) => v.toString(16).padStart(2, "0")).join("");
  const bgOf = (e) => {
    const chain = [];
    for (let n = e; n; n = n.parentElement) { const c = rgba(getComputedStyle(n).backgroundColor); if (c[3] > 0) chain.push(c); if (c[3] >= 1) break; }
    let base = rgba(getComputedStyle(document.documentElement).backgroundColor);
    if (base[3] < 1) base = over(base, [255, 255, 255]).concat(1);
    let acc = base.slice(0, 3);
    for (let i = chain.length - 1; i >= 0; i--) acc = over(chain[i], acc);
    return acc;
  };
  if (!main) return { error: "no main" };
  const FR = '[data-slot="card"],[data-slot="settings-section"]';
  const frames = [...main.querySelectorAll(FR)].filter(vis);
  const head = (c) => { const h = c.querySelector("h1,h2,h3,[data-slot=card-title]"); return h ? txt(h).slice(0, 50) : txt(c).slice(0, 30); };
  const nested = frames.filter((f) => f.parentElement && f.parentElement.closest(FR)).map((f) => ({ inner: `${f.dataset.slot}:${head(f)}`, outer: `${f.parentElement.closest(FR).dataset.slot}:${head(f.parentElement.closest(FR))}`, testid: f.dataset.testid }));
  const borderedInSection = [];
  for (const s of main.querySelectorAll('[data-slot="settings-section"]')) {
    for (const e of s.querySelectorAll("div,section,fieldset,ul,form")) {
      if (!vis(e) || e.matches(FR)) continue;
      const cs = getComputedStyle(e); const b = e.getBoundingClientRect();
      const all4 = ["Top", "Right", "Bottom", "Left"].every((k) => parseFloat(cs[`border${k}Width`]) > 0 && cs[`border${k}Style`] !== "none");
      if (all4 && parseFloat(cs.borderTopLeftRadius) > 0 && b.width >= 200 && b.height >= 80) borderedInSection.push({ section: head(s), cls: (e.className || "").toString().slice(0, 90), w: Math.round(b.width), h: Math.round(b.height), testid: e.dataset.testid, text: txt(e).slice(0, 50) });
    }
  }
  const tables = [...main.querySelectorAll("table")].filter(vis).map((t) => {
    let sc = t.parentElement; while (sc && sc !== main && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
    if (sc === main) sc = null;
    const ths = [...t.querySelectorAll("thead th")];
    const visTh = ths.filter(vis);
    const last = visTh[visTh.length - 1];
    let reach = null;
    if (sc && last) {
      const keep = sc.scrollLeft; sc.scrollLeft = sc.scrollWidth;
      const lr = last.getBoundingClientRect(), sr = sc.getBoundingClientRect();
      const lastBtns = [...t.querySelectorAll("tbody tr")].slice(0, 3).map((r) => { const cells = [...r.children].filter(vis); const c = cells[cells.length - 1]; const b = c && c.querySelector("button,a"); return b ? Math.round(b.getBoundingClientRect().right) : null; });
      reach = { lastHeader: txt(last), lastHeaderRight: Math.round(lr.right), scrollerRight: Math.round(sr.right), viewport: innerWidth, reachable: lr.right <= sr.right + 1 && lr.right <= innerWidth + 1, lastColButtonRights: lastBtns };
      sc.scrollLeft = keep;
    }
    return { dataSlot: t.getAttribute("data-slot"), headersAll: ths.map(txt), headersVisible: visTh.map(txt), scroller: sc ? { cls: (sc.className || "").toString().slice(0, 60), position: getComputedStyle(sc).position, clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth } : null, reach };
  });
  const whiteBoxes = theme === "dark" ? [...main.querySelectorAll("*")].filter((e) => { if (!vis(e)) return false; const b = e.getBoundingClientRect(); if (b.width < 40 || b.height < 40) return false; const c = rgba(getComputedStyle(e).backgroundColor); return c[3] > 0.5 && lum(c) > 0.6; }).map((e) => ({ tag: e.tagName.toLowerCase(), cls: (e.className || "").toString().slice(0, 80), bg: getComputedStyle(e).backgroundColor, w: Math.round(e.getBoundingClientRect().width), h: Math.round(e.getBoundingClientRect().height), text: txt(e).slice(0, 40) })) : null;
  const low = [];
  const walker = document.createTreeWalker(main, NodeFilter.SHOW_TEXT);
  const seen = new Set();
  for (let n = walker.nextNode(); n; n = walker.nextNode()) {
    const e = n.parentElement; if (!e || seen.has(e) || !n.textContent.trim()) continue; seen.add(e);
    if (!vis(e) || e.closest(".sr-only") || e.closest("[disabled],[aria-disabled=true]") || e.closest("option")) continue;
    const r = e.getBoundingClientRect(); if (r.width < 2 || r.height < 2) continue;
    const cs = getComputedStyle(e);
    const bg = bgOf(e); const fgA = rgba(cs.color); const fg = over(fgA, bg);
    const k = ratio(fg, bg);
    const large = parseFloat(cs.fontSize) >= 24 || (parseFloat(cs.fontSize) >= 18.66 && +cs.fontWeight >= 700);
    if (k < 4.5) low.push({ text: n.textContent.trim().slice(0, 40), ratio: k, large, fg: hex(fg), bg: hex(bg), cls: (e.className || "").toString().slice(0, 60) });
  }
  return {
    h1: [...main.querySelectorAll("h1")].map(txt),
    h2: [...main.querySelectorAll("h2")].filter(vis).map(txt),
    settingsSections: main.querySelectorAll('[data-slot="settings-section"]').length,
    cards: main.querySelectorAll('[data-slot="card"]').length,
    dataTables: main.querySelectorAll('[data-slot="data-table"]').length,
    emptyStates: [...main.querySelectorAll('[data-slot="empty-state"]')].filter(vis).map((e) => txt(e).slice(0, 80)),
    nestedFrames: nested,
    borderedInSection,
    tables,
    whiteBoxesDark: whiteBoxes,
    lowContrast: { below45: low.length, below3: low.filter((x) => x.ratio < 3).length, belowLargeOk: low.filter((x) => !(x.large && x.ratio >= 3)).length, examples: low.slice(0, 12) },
    hOverflow: Math.max(0, document.scrollingElement.scrollWidth - document.scrollingElement.clientWidth),
  };
};

const CHIPS = () => {
  const cv = document.createElement("canvas"); cv.width = cv.height = 1;
  const cx = cv.getContext("2d", { willReadFrequently: true });
  const rgb = (c) => { cx.clearRect(0, 0, 1, 1); cx.fillStyle = "#000"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); const d = cx.getImageData(0, 0, 1, 1).data; return [d[0], d[1], d[2]]; };
  const lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
  const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return +((x + 0.05) / (y + 0.05)).toFixed(2); };
  const hex = (a) => "#" + a.map((v) => v.toString(16).padStart(2, "0")).join("");
  return [...document.querySelectorAll('#main-content table [data-slot="badge"]')].map((b) => { const cs = getComputedStyle(b); const fg = rgb(cs.color), bg = rgb(cs.backgroundColor); return { text: b.textContent.trim(), styleColor: b.style.color, fg: hex(fg), bg: hex(bg), ratio: ratio(fg, bg) }; });
};

const TAGS = [["OBS 665C Dark", "#1E293B"], ["OBS 665C Light", "#FDE68A"]];

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const T = await L.targets();
  const SURF = L.TABS.map((t) => [t, `/settings/matters?tab=${t}`]);
  SURF.push(["take-on-batch", `/settings/matters?tab=take-on&batch=${T.batch.id}`]);
  for (const [k, u] of [["templates-id", `/settings/matters/templates/${T.matterTemplate.id}`], ["templates-new", "/settings/matters/templates/new"], ["checklists-new", "/settings/matters/checklists/new"], ["checklists-id-custom", `/settings/matters/checklists/${T.checklistCustom.id}`], ["checklists-id-pack", `/settings/matters/checklists/${T.checklistPack.id}`], ["checklists-edit-custom", `/settings/matters/checklists/${T.checklistCustom.id}/edit`], ["request-templates-new", "/settings/matters/request-templates/new"], ["request-templates-id-custom", `/settings/matters/request-templates/${T.requestTemplate.id}`], ["request-templates-id-pack", `/settings/matters/request-templates/${T.requestTemplatePack.id}`]]) SURF.push([k, u]);

  // A.
  for (const [w, h] of VIEWPORTS)
    for (const theme of ["light", "dark"]) {
      const ctx = await L.newCtx(browser, S, { width: w, height: h, theme, extra: L.EXTRA });
      const p = await ctx.newPage();
      L.watch(p, consoleSink, lab);
      for (const [sk, url] of SURF) {
        lab.label = `07|${sk}|${w}-${theme}`;
        await p.goto(`${L.BASE}${url}`, { waitUntil: "load" });
        await p.waitForLoadState("networkidle").catch(() => {});
        await p.waitForTimeout(400);
        (out.surfaces[sk] = out.surfaces[sk] || {})[`${w}-${theme}`] = await p.evaluate(PROBE, theme);
      }
      await ctx.close();
    }

  // B. tags with rows
  const a = await L.idpToken("alice");
  out.tags.before = L.list((await L.apiGet(a, "/api/tags")).json).map((t) => t.name);
  {
    const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
    const p = await ctx.newPage();
    L.watch(p, consoleSink, lab);
    lab.label = "07|tags-create";
    await p.goto(`${L.BASE}/settings/matters?tab=tags`, { waitUntil: "load" });
    await p.waitForLoadState("networkidle").catch(() => {});
    out.tags.emptyStateButtons = await p.getByRole("button", { name: /^Add tag$/i }).evaluateAll((els) => els.map((e) => e.textContent.trim()));
    for (const [name, color] of TAGS) {
      await p.getByRole("button", { name: "Add Tag", exact: true }).first().click();
      const dlg = p.getByRole("dialog", { name: "Create Tag" });
      await dlg.waitFor();
      await dlg.getByLabel("Name", { exact: true }).fill(name);
      await dlg.getByLabel("Color (optional)").fill(color);
      await dlg.getByRole("button", { name: "Create", exact: true }).click();
      await dlg.waitFor({ state: "hidden", timeout: 15000 });
      await p.getByRole("cell", { name, exact: true }).waitFor({ timeout: 15000 });
    }
    await ctx.close();
  }
  out.tags.created = L.list((await L.apiGet(a, "/api/tags")).json).map((t) => ({ name: t.name, color: t.color }));
  out.tags.variants = {};
  for (const [w, h] of VIEWPORTS)
    for (const theme of ["light", "dark"]) {
      const v = `${w}-${theme}`;
      const ctx = await L.newCtx(browser, S, { width: w, height: h, theme, extra: L.EXTRA });
      const p = await ctx.newPage();
      L.watch(p, consoleSink, lab);
      lab.label = `07|tags-rows|${v}`;
      await p.goto(`${L.BASE}/settings/matters?tab=tags`, { waitUntil: "load" });
      await p.waitForLoadState("networkidle").catch(() => {});
      await p.waitForTimeout(400);
      const c = async (loc) => loc.count();
      const sel = {
        "tags.spec:23 heading h1 /^Tags/": await c(p.getByRole("heading", { level: 1, name: /^Tags/ })),
        "tags.spec:36/43 button 'Add Tag'": await c(p.getByRole("button", { name: "Add Tag" })),
        "cell exact 'OBS 665C Dark'": await c(p.getByRole("cell", { name: "OBS 665C Dark", exact: true })),
        "cell exact 'OBS 665C Light'": await c(p.getByRole("cell", { name: "OBS 665C Light", exact: true })),
        "button 'Edit OBS 665C Dark'": await c(p.getByRole("button", { name: "Edit OBS 665C Dark" })),
        "button 'Delete OBS 665C Dark'": await c(p.getByRole("button", { name: "Delete OBS 665C Dark" })),
        "placeholder 'Search tags...'": await c(p.getByPlaceholder("Search tags...")),
      };
      const probe = await p.evaluate(PROBE, theme);
      out.tags.variants[v] = { selCompat: sel, chips: await p.evaluate(CHIPS), tables: probe.tables, hOverflow: probe.hOverflow, lowContrast: probe.lowContrast, whiteBoxesDark: probe.whiteBoxesDark, nestedFrames: probe.nestedFrames };
      const f = path.join(L.RAW, `tags-rows-${v}.png`);
      await p.screenshot({ path: f, fullPage: true });
      tiles.push([`Alice tags with 2 rows ${w}×${h} ${theme} — chips ${out.tags.variants[v].chips.map((x) => `${x.text} ${x.fg} on ${x.bg} ${x.ratio}:1`).join("; ")}`, f]);
      await ctx.close();
    }
  {
    const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
    const p = await ctx.newPage();
    L.watch(p, consoleSink, lab);
    lab.label = "07|tags-delete";
    await p.goto(`${L.BASE}/settings/matters?tab=tags`, { waitUntil: "load" });
    await p.waitForLoadState("networkidle").catch(() => {});
    for (const [name] of TAGS) {
      await p.getByRole("button", { name: `Delete ${name}`, exact: true }).click();
      const del = p.getByRole("alertdialog", { name: "Delete Tag" });
      await del.waitFor();
      await del.getByRole("button", { name: "Delete", exact: true }).click();
      await del.waitFor({ state: "hidden", timeout: 15000 });
      await p.getByRole("cell", { name, exact: true }).waitFor({ state: "detached", timeout: 15000 });
    }
    await ctx.close();
  }
  out.tags.after = L.list((await L.apiGet(a, "/api/tags")).json).map((t) => t.name);
  await L.sheet(browser, path.join(L.OUT, "sheet-tags-rows-k15-alice.png"), "665C after — Settings › Matters ?tab=tags with 2 UI-created tags (dark #1E293B, light #FDE68A; K-15 chip text by luminance), Alice: cols 1024 light | 1024 dark | 1440 light | 1440 dark, full page. Both tags deleted afterwards.", tiles, 4, 520);
  await browser.close();
  out.console = { messages: consoleSink, errors: consoleSink.filter((m) => m.type === "error" || m.type === "pageerror").length, hydration: consoleSink.filter((m) => /hydrat/i.test(m.text)).length };
  fs.writeFileSync(path.join(L.OUT, "after-checks.json"), JSON.stringify(out) + "\n");
  for (const [sk, vv] of Object.entries(out.surfaces)) console.log(sk, JSON.stringify(Object.fromEntries(Object.entries(vv).map(([v, m]) => [v, { h1: m.h1.length, h2: m.h2.length, ss: m.settingsSections, nested: m.nestedFrames.length, bis: m.borderedInSection.length, white: m.whiteBoxesDark && m.whiteBoxesDark.length, low: `${m.lowContrast.below45}/${m.lowContrast.below3}`, ov: m.hOverflow, t: m.tables.map((t) => `${t.scroller && t.scroller.clientWidth}/${t.scroller && t.scroller.scrollWidth}:${t.reach && t.reach.reachable}`) }]))));
  console.log("tags", JSON.stringify({ before: out.tags.before, created: out.tags.created, after: out.tags.after, empty: out.tags.emptyStateButtons, chips: Object.fromEntries(Object.entries(out.tags.variants).map(([v, x]) => [v, x.chips])), sel: out.tags.variants["1024-light"].selCompat }));
  console.log("console", JSON.stringify({ n: consoleSink.length, errors: out.console.errors, hydration: out.console.hydration }));
})();
