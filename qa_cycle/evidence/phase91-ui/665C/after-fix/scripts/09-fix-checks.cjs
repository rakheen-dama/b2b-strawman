// 665C OBS after-fix step 9 (new, after-fix only): re-observes the three fixes of 3b0ac2574 in a real
// browser (Alice, 1024×768 / 1440×900 × light / dark):
//   F-665C-10  Templates and Tags (with 2 UI-made rows): computed `color` of the row Delete / Edit /
//              Duplicate icon buttons vs the `--destructive` token (a probe span with
//              `color: var(--destructive)` inside #main-content), at rest and on hover (bg too).
//   F-665C-12  checklists/{id} for PLATFORM "Generic Client Onboarding" and ORG_CUSTOM "OBS 665C
//              Checklist" (ids by name, GET): items-table scroller client/scroll width; Name and
//              Description td white-space + rendered line count; every "Required" badge and every
//              Document Required cell inside the scroller box and untruncated; docHOverflowPx.
//   F-665C-11  Naming: each placeholder <code> chip's composited background vs its box's, and the
//              chip text vs chip background.
//   K-15       the two tags' chip text vs fill.
// Tags "OBS 665C Dark" #1E293B / "OBS 665C Light" #FDE68A are created through Add Tag and deleted
// through the row Delete → AlertDialog in a finally block (tags end as before). No SQL; GETs only.
// Writes ../fix-checks.json (single line), sheet-{templates,tags-rows,sub-checklists-detail,naming}-alice.png,
// crop-fixed-{templates,tags}-actions-dark-3x.png, crop-fixed-checklist-items-{1440,1024}-light.png.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const VIEWPORTS = [[1024, 768], [1440, 900]];
const VARIANTS = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const TAGS = [["OBS 665C Dark", "#1E293B"], ["OBS 665C Light", "#FDE68A"]];
const consoleSink = [];
const lab = { label: "" };
const tiles = {};
const out = { at: new Date().toISOString(), icons: {}, hover: {}, checklist: {}, naming: {}, chips: {}, docHOverflowPx: {} };

// Shared in-page colour maths (composited background, WCAG ratio).
const HELPERS = () => {
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
  window.__fx = { rgba, over, ratio, hex, bgOf };
};

const ICONS = () => {
  const { rgba, hex } = window.__fx;
  const main = document.querySelector("main#main-content");
  const probe = document.createElement("span"); probe.style.color = "var(--destructive)"; probe.textContent = "x"; main.appendChild(probe);
  const token = getComputedStyle(probe).color; probe.remove();
  const btns = [...main.querySelectorAll("button")].filter((b) => /^(Delete|Edit|Duplicate) /.test((b.querySelector(".sr-only") || {}).textContent || ""));
  const rows = btns.slice(0, 6).map((b) => { const cs = getComputedStyle(b); const svg = b.querySelector("svg"); return { name: b.querySelector(".sr-only").textContent.trim(), color: cs.color, hex: hex(rgba(cs.color)), svgStroke: svg ? getComputedStyle(svg).stroke : null, bg: cs.backgroundColor, eqToken: cs.color === token }; });
  return { destructiveToken: token, tokenHex: hex(rgba(token)), dark: document.documentElement.classList.contains("dark"), buttons: rows, hOverflow: Math.max(0, document.scrollingElement.scrollWidth - document.scrollingElement.clientWidth) };
};

const ONE = (name) => {
  const { rgba, hex } = window.__fx;
  const main = document.querySelector("main#main-content");
  const probe = document.createElement("span"); probe.style.color = "var(--destructive)"; main.appendChild(probe);
  const token = getComputedStyle(probe).color; probe.remove();
  const b = [...main.querySelectorAll("button")].find((x) => ((x.querySelector(".sr-only") || {}).textContent || "").trim() === name);
  if (!b) return { error: `no button ${name}` };
  const cs = getComputedStyle(b);
  return { name, hovered: b.matches(":hover"), color: cs.color, hex: hex(rgba(cs.color)), bg: cs.backgroundColor, bgHex: hex(rgba(cs.backgroundColor)), bgAlpha: rgba(cs.backgroundColor)[3], eqToken: cs.color === token, token };
};

const CHECKLIST = () => {
  const main = document.querySelector("main#main-content");
  const row0 = main.querySelector('[data-testid="checklist-item-row"]');
  if (!row0) return { error: "no checklist-item-row" };
  const t = row0.closest("table");
  let sc = t.parentElement; while (sc && sc !== main && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
  if (sc === main) sc = null;
  const sr = sc.getBoundingClientRect();
  const inBox = (r) => r.left >= sr.left - 0.5 && r.right <= sr.right + 0.5 && r.right <= innerWidth + 0.5;
  const ths = [...t.querySelectorAll("thead th")].map((h) => ({ text: (h.innerText || "").trim(), right: Math.round(h.getBoundingClientRect().right), inBox: inBox(h.getBoundingClientRect()) }));
  const idx = Object.fromEntries(ths.map((h, i) => [h.text.toLowerCase(), i]));
  const lines = (td) => { const rg = document.createRange(); rg.selectNodeContents(td); const tops = new Set([...rg.getClientRects()].filter((r) => r.width > 0).map((r) => Math.round(r.top))); return tops.size; };
  const rows = [...t.querySelectorAll('[data-testid="checklist-item-row"]')].map((r) => {
    const td = [...r.children];
    const nm = td[idx["name"]], ds = td[idx["description"]], rq = td[idx["required"]], dr = td[idx["document required"]];
    const badge = rq && rq.querySelector('[data-slot="badge"]');
    const drEl = dr && (dr.querySelector('[data-slot="badge"]') || dr.querySelector("span") || dr);
    return {
      name: (nm.innerText || "").trim().slice(0, 40),
      nameWS: getComputedStyle(nm).whiteSpace, nameLines: lines(nm), nameW: Math.round(nm.getBoundingClientRect().width),
      descWS: getComputedStyle(ds).whiteSpace, descLines: lines(ds), descLen: (ds.innerText || "").trim().length,
      required: badge ? { text: badge.textContent.trim(), inBox: inBox(badge.getBoundingClientRect()), untruncated: badge.scrollWidth <= badge.clientWidth, right: Math.round(badge.getBoundingClientRect().right) } : { text: (rq.innerText || "").trim(), inBox: inBox(rq.getBoundingClientRect()) },
      docRequired: { text: (drEl.innerText || drEl.textContent || "").trim().slice(0, 40), inBox: inBox(drEl.getBoundingClientRect()), untruncated: drEl.scrollWidth <= drEl.clientWidth, right: Math.round(drEl.getBoundingClientRect().right) },
    };
  });
  return {
    h1: [...main.querySelectorAll("h1")].map((h) => (h.innerText || "").trim()),
    scroller: { clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, left: Math.round(sr.left), right: Math.round(sr.right), noClip: sc.scrollWidth <= sc.clientWidth },
    headers: ths, rows,
    summary: {
      rows: rows.length,
      nameAllNormal: rows.every((r) => r.nameWS === "normal"), descAllNormal: rows.every((r) => r.descWS === "normal"),
      maxNameLines: Math.max(...rows.map((r) => r.nameLines)), maxDescLines: Math.max(...rows.map((r) => r.descLines)),
      requiredBadges: rows.filter((r) => r.required.text === "Required").length,
      requiredAllVisible: rows.every((r) => r.required.inBox && r.required.untruncated !== false),
      docRequiredAllVisible: rows.every((r) => r.docRequired.inBox && r.docRequired.untruncated),
      allHeadersInBox: ths.every((h) => h.inBox),
    },
    hOverflow: Math.max(0, document.scrollingElement.scrollWidth - document.scrollingElement.clientWidth),
  };
};

const NAMING = () => {
  const { rgba, over, ratio, hex, bgOf } = window.__fx;
  const main = document.querySelector("main#main-content");
  const codes = [...main.querySelectorAll("code")];
  return {
    hOverflow: Math.max(0, document.scrollingElement.scrollWidth - document.scrollingElement.clientWidth),
    chips: codes.map((c) => {
      const box = c.closest("div.bg-muted") || c.closest("div");
      const chipBg = bgOf(c), boxBg = bgOf(box);
      const fg = over(rgba(getComputedStyle(c).color), chipBg);
      return { text: c.textContent.trim(), chipCls: c.className, chipBgRaw: getComputedStyle(c).backgroundColor, chipBg: hex(chipBg), boxCls: (box.className || "").toString().slice(0, 60), boxBgRaw: getComputedStyle(box).backgroundColor, boxBg: hex(boxBg), chipVsBox: ratio(chipBg, boxBg), distinct: hex(chipBg) !== hex(boxBg), textVsChip: ratio(fg, chipBg), fg: hex(fg) };
    }),
  };
};

const CHIPS = () => {
  const { rgba, ratio, hex, bgOf } = window.__fx;
  return [...document.querySelectorAll('#main-content table [data-slot="badge"]')].map((b) => { const cs = getComputedStyle(b); const bg = bgOf(b); const fg = rgba(cs.color).slice(0, 3); return { text: b.textContent.trim(), styleColor: b.style.color, fg: hex(fg), bg: hex(bg), ratio: ratio(fg, bg) }; });
};

async function open(browser, S, w, h, theme, dpr = 1) {
  const ctx = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: dpr, storageState: S, ...L.EXTRA });
  await ctx.addCookies([{ name: "sidebar_state", value: "true", domain: "localhost", path: "/" }]);
  await ctx.addInitScript(L.INIT, theme);
  await ctx.addInitScript(HELPERS);
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  return { ctx, p };
}
async function go(p, url) {
  await p.goto(`${L.BASE}${url}`, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(400);
}
async function shot(p, key, caption) {
  const f = path.join(L.RAW, `${key}.png`);
  await p.mouse.move(0, 0);
  await p.waitForTimeout(200);
  await p.screenshot({ path: f, fullPage: true });
  tiles[key] = [caption, f];
}
async function hoverMeasure(p, name) {
  const b = p.getByRole("button", { name, exact: true });
  await b.scrollIntoViewIfNeeded();
  await b.hover();
  await p.waitForTimeout(400);
  const r = await p.evaluate(ONE, name);
  await p.mouse.move(0, 0);
  await p.waitForTimeout(200);
  return r;
}

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const a = await L.idpToken("alice");
  const cl = L.list((await L.apiGet(a, "/api/checklist-templates")).json);
  const byName = (n) => cl.find((x) => x.name === n) || null;
  const CL = { pack: byName("Generic Client Onboarding"), custom: byName("OBS 665C Checklist") };
  out.checklistIds = Object.fromEntries(Object.entries(CL).map(([k, v]) => [k, v && { id: v.id, name: v.name, source: v.source }]));
  out.tagsBefore = L.list((await L.apiGet(a, "/api/tags")).json).map((t) => t.name);
  let created = false;
  try {
    // Create the two tags through the UI.
    {
      const { ctx, p } = await open(browser, S, 1440, 900, "light");
      lab.label = "09|tags-create";
      await go(p, "/settings/matters?tab=tags");
      for (const [name, color] of TAGS) {
        await p.getByRole("button", { name: "Add Tag", exact: true }).first().click();
        const dlg = p.getByRole("dialog", { name: "Create Tag" });
        await dlg.waitFor();
        await dlg.getByLabel("Name", { exact: true }).fill(name);
        await dlg.getByLabel("Color (optional)").fill(color);
        await dlg.getByRole("button", { name: "Create", exact: true }).click();
        created = true;
        await dlg.waitFor({ state: "hidden", timeout: 15000 });
        await p.getByRole("cell", { name, exact: true }).waitFor({ timeout: 15000 });
      }
      await ctx.close();
    }
    out.tagsCreated = L.list((await L.apiGet(a, "/api/tags")).json).map((t) => ({ name: t.name, color: t.color }));

    for (const [w, h] of VIEWPORTS)
      for (const theme of ["light", "dark"]) {
        const v = `${w}-${theme}`;
        const { ctx, p } = await open(browser, S, w, h, theme);
        // Templates
        lab.label = `09|templates|${v}`;
        await go(p, "/settings/matters?tab=templates");
        await shot(p, `templates-${v}`, `Templates ${w}×${h} ${theme} full page`);
        out.icons[`templates|${v}`] = await p.evaluate(ICONS);
        const tDel = out.icons[`templates|${v}`].buttons.find((b) => b.name.startsWith("Delete"));
        const tEdit = out.icons[`templates|${v}`].buttons.find((b) => b.name.startsWith("Edit"));
        out.hover[`templates|${v}`] = { del: tDel && (await hoverMeasure(p, tDel.name)), edit: tEdit && (await hoverMeasure(p, tEdit.name)) };
        // Tags with rows
        lab.label = `09|tags|${v}`;
        await go(p, "/settings/matters?tab=tags");
        await shot(p, `tags-${v}`, `Tags (2 UI-made rows) ${w}×${h} ${theme} full page`);
        out.icons[`tags|${v}`] = await p.evaluate(ICONS);
        out.chips[v] = await p.evaluate(CHIPS);
        out.hover[`tags|${v}`] = { del: await hoverMeasure(p, "Delete OBS 665C Dark"), edit: await hoverMeasure(p, "Edit OBS 665C Dark") };
        // Checklist detail
        for (const k of ["custom", "pack"]) {
          lab.label = `09|checklists-id-${k}|${v}`;
          await go(p, `/settings/matters/checklists/${CL[k].id}`);
          await shot(p, `checklists-id-${k}-${v}`, `checklists/{id} ${k === "pack" ? "PLATFORM 'Generic Client Onboarding'" : "ORG_CUSTOM 'OBS 665C Checklist'"} ${w}×${h} ${theme} full page`);
          out.checklist[`${k}|${v}`] = await p.evaluate(CHECKLIST);
          if (k === "pack" && theme === "light") {
            const sec = p.locator('[data-testid="checklist-item-row"]').first().locator("xpath=ancestor::table[1]");
            const box = await sec.boundingBox();
            const sc = await p.evaluate(() => { const t = document.querySelector('[data-testid="checklist-item-row"]').closest("table"); let s = t.parentElement; while (s && !/(auto|scroll)/.test(getComputedStyle(s).overflowX)) s = s.parentElement; const r = s.getBoundingClientRect(); return { x: r.left, w: r.width }; });
            await p.screenshot({ path: path.join(L.OUT, `crop-fixed-checklist-items-${w}-light.png`), fullPage: true, clip: { x: Math.max(0, sc.x - 24), y: box.y + (await p.evaluate(() => scrollY)) - 16, width: Math.min(w - Math.max(0, sc.x - 24), sc.w + 48), height: box.height + 32 } });
          }
        }
        // Naming
        lab.label = `09|naming|${v}`;
        await go(p, "/settings/matters?tab=naming");
        await shot(p, `naming-${v}`, `Naming ${w}×${h} ${theme} full page`);
        out.naming[v] = await p.evaluate(NAMING);
        out.docHOverflowPx[v] = { templates: out.icons[`templates|${v}`].hOverflow, tags: out.icons[`tags|${v}`].hOverflow, checklistsIdCustom: out.checklist[`custom|${v}`].hOverflow, checklistsIdPack: out.checklist[`pack|${v}`].hOverflow, naming: out.naming[v].hOverflow };
        await ctx.close();
      }

    // 3× crops of the row actions in dark (1440).
    {
      const { ctx, p } = await open(browser, S, 1440, 900, "dark", 3);
      for (const [k, url] of [["templates", "/settings/matters?tab=templates"], ["tags", "/settings/matters?tab=tags"]]) {
        lab.label = `09|crop-${k}`;
        await go(p, url);
        await p.mouse.move(0, 0);
        const row = p.locator("#main-content tbody tr").first();
        const box = await row.boundingBox();
        await p.screenshot({ path: path.join(L.OUT, `crop-fixed-${k}-actions-dark-3x.png`), clip: { x: box.x + box.width - 160, y: box.y, width: 160, height: box.height } });
      }
      await ctx.close();
    }
  } finally {
    // Delete whichever of the two tags exists, through the UI.
    const now = L.list((await L.apiGet(a, "/api/tags")).json).map((t) => t.name);
    const left = TAGS.map(([n]) => n).filter((n) => now.includes(n));
    if (created || left.length) {
      const { ctx, p } = await open(browser, S, 1440, 900, "light");
      lab.label = "09|tags-delete";
      await go(p, "/settings/matters?tab=tags");
      for (const name of left) {
        await p.getByRole("button", { name: `Delete ${name}`, exact: true }).click();
        const del = p.getByRole("alertdialog", { name: "Delete Tag" });
        await del.waitFor();
        await del.getByRole("button", { name: "Delete", exact: true }).click();
        await del.waitFor({ state: "hidden", timeout: 15000 });
        await p.getByRole("cell", { name, exact: true }).waitFor({ state: "detached", timeout: 15000 });
      }
      await ctx.close();
    }
    out.tagsAfter = L.list((await L.apiGet(a, "/api/tags")).json).map((t) => t.name);
  }

  const vt = (k) => VARIANTS.map((v) => tiles[`${k}-${v}`] || [`${k}-${v} (not shot)`, null]);
  const COLS = "cols 1024 light | 1024 dark | 1440 light | 1440 dark, full page";
  await L.sheet(browser, path.join(L.OUT, "sheet-templates-alice.png"), `665C after-fix (3b0ac2574) — Settings › Matters ?tab=templates, Alice: ${COLS}. F-665C-10: row Delete icon`, vt("templates"), 4, 520);
  await L.sheet(browser, path.join(L.OUT, "sheet-tags-rows-alice.png"), `665C after-fix (3b0ac2574) — ?tab=tags with 2 UI-made tags (#1E293B, #FDE68A), Alice: ${COLS}. F-665C-10 Delete icon, K-15 chips. Both tags deleted afterwards`, vt("tags"), 4, 520);
  await L.sheet(browser, path.join(L.OUT, "sheet-sub-checklists-detail-alice.png"), `665C after-fix (3b0ac2574) — checklists/{id}: row 1 ORG_CUSTOM 'OBS 665C Checklist', row 2 PLATFORM 'Generic Client Onboarding', Alice: ${COLS}. F-665C-12`, [...vt("checklists-id-custom"), ...vt("checklists-id-pack")], 4, 520);
  await L.sheet(browser, path.join(L.OUT, "sheet-naming-alice.png"), `665C after-fix (3b0ac2574) — ?tab=naming, Alice: ${COLS}. F-665C-11: placeholder chips`, vt("naming"), 4, 520);
  await browser.close();
  out.console = { n: consoleSink.length, errors: consoleSink.filter((m) => m.type === "error" || m.type === "pageerror").length, hydration: consoleSink.filter((m) => /hydrat/i.test(m.text)).length, messages: consoleSink };
  fs.writeFileSync(path.join(L.OUT, "fix-checks.json"), JSON.stringify(out) + "\n");

  for (const [k, v] of Object.entries(out.icons)) console.log("icons", k, v.tokenHex, JSON.stringify(v.buttons.map((b) => `${b.name.split(" ")[0]}:${b.hex}${b.eqToken ? "=tok" : ""}`)));
  for (const [k, v] of Object.entries(out.hover)) console.log("hover", k, JSON.stringify({ del: v.del && `${v.del.hovered}:${v.del.hex}${v.del.eqToken ? "=tok" : ""} bg ${v.del.bgHex}@${v.del.bgAlpha}`, edit: v.edit && `${v.edit.hovered}:${v.edit.hex}${v.edit.eqToken ? "=tok" : ""} bg ${v.edit.bgHex}@${v.edit.bgAlpha}` }));
  for (const [k, v] of Object.entries(out.checklist)) console.log("checklist", k, JSON.stringify({ h1: v.h1, sc: `${v.scroller.clientWidth}/${v.scroller.scrollWidth}`, ...v.summary, ov: v.hOverflow }));
  for (const [k, v] of Object.entries(out.naming)) console.log("naming", k, JSON.stringify(v.chips.map((c) => `${c.text} ${c.chipBg} on ${c.boxBg} ${c.chipVsBox}:1 text ${c.textVsChip}:1`)));
  for (const [k, v] of Object.entries(out.chips)) console.log("chips", k, JSON.stringify(v.map((c) => `${c.text} ${c.fg} on ${c.bg} ${c.ratio}:1`)));
  console.log("overflow", JSON.stringify(out.docHOverflowPx));
  console.log("tags", JSON.stringify({ before: out.tagsBefore, created: out.tagsCreated, after: out.tagsAfter, ids: out.checklistIds }));
  console.log("console", JSON.stringify({ n: out.console.n, errors: out.console.errors, hydration: out.console.hydration }));
})().catch((e) => { console.error(e); process.exit(1); });
