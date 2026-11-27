// 665F OBS after-fix (PR #1875 head cec81dad5; fix commit f3c298e43..cec81dad5). NEW file; lib.cjs is a
// verbatim copy of ../../after/scripts/lib.cjs (that original is not edited). Read-only: GET + render only.
// Alice (/mock-login UI) on Connections ?tab=xero and ?tab=mcp × 1024x768 / 1440x900 × light / dark,
// full-page shots to $RAW (/tmp, not committed) and one contact sheet per tab to ../sheet-*.png.
// Measures:
//   xero  h1 text + font-size (in #main-content, count); "Xero Integration" h2 (#xero-heading) computed
//         font-size / weight / line-height / colour / family vs a SettingsSection h2 on the same page;
//         h1 > h2; [id="xero-heading"] count = 1; section[aria-labelledby] resolves to that h2;
//         the sibling integration cards' icon tiles (box, aria-hidden, gap to the label).
//   mcp   h1 "MCP"; heading with accessible name exactly "Claude / MCP Connector" (getByRole exact +
//         aria snapshot); its icon tile (aria-hidden, 40x40, gap) vs the xero-tab tiles.
//   1024  document.scrollingElement scrollWidth/clientWidth, visible elements past the tab panel's right edge.
//   console errors / warnings / hydration over every page.
// Writes ../verify.json (single line). Usage: PHASE=after-fix node 01-verify.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const TABS = ["xero", "mcp"];
const VARIANTS = [[1024, 768, "light"], [1024, 768, "dark"], [1440, 900, "light"], [1440, 900, "dark"]];
const T = (tab) => `${L.BASE}/settings/connections?tab=${tab}`;
const consoleSink = [];
const lab = { label: "" };
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(800); };

const MEASURE = () => {
  const v = (e) => { const b = e.getBoundingClientRect(); const cs = getComputedStyle(e); return b.width > 0 && b.height > 0 && cs.visibility !== "hidden" && cs.display !== "none"; };
  const t = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
  const main = document.querySelector("main#main-content");
  const h1s = [...main.querySelectorAll("h1")];
  const st = (e) => { const cs = getComputedStyle(e); return { text: t(e), fontSize: cs.fontSize, fontWeight: cs.fontWeight, lineHeight: cs.lineHeight, color: window.__hex(cs.color), fontFamily: cs.fontFamily.split(",")[0].trim(), cls: e.className }; };
  const panel = main.querySelector('[role="tabpanel"]');
  const pr = panel.getBoundingClientRect();
  const past = [...panel.querySelectorAll("*")].filter((e) => v(e) && e.getBoundingClientRect().right > pr.right + 1);
  const se = document.scrollingElement;
  // icon tiles: an aria-hidden span directly inside a heading's flex span
  const tiles = [...main.querySelectorAll("h2, h3")].map((h) => {
    const tile = h.querySelector(":scope > span > span[aria-hidden]");
    if (!tile) return null;
    const tr = tile.getBoundingClientRect();
    const r = document.createRange();
    const textNode = [...tile.parentElement.childNodes].find((n) => n.nodeType === 3 && n.textContent.trim());
    let labelLeft = null;
    if (textNode) { r.selectNodeContents(textNode); labelLeft = r.getBoundingClientRect().left; }
    const svg = tile.querySelector("svg");
    return { heading: t(h), tag: h.tagName.toLowerCase(), ariaHidden: tile.getAttribute("aria-hidden"), w: +tr.width.toFixed(2), h: +tr.height.toFixed(2), radius: getComputedStyle(tile).borderRadius, bg: window.__hex(getComputedStyle(tile).backgroundColor), icon: svg ? `${Math.round(svg.getBoundingClientRect().width)}x${Math.round(svg.getBoundingClientRect().height)}` : null, gap: labelLeft == null ? null : +(labelLeft - tr.right).toFixed(2), flexGap: getComputedStyle(tile.parentElement).columnGap };
  }).filter(Boolean);
  const out = { url: location.pathname + location.search, h1Count: h1s.length, h1: h1s.map(st), scrollWidth: se.scrollWidth, clientWidth: se.clientWidth, panelRight: Math.round(pr.right), pastPanelEdge: past.length, pastSample: past.slice(0, 3).map((e) => `${e.tagName.toLowerCase()}:${t(e).slice(0, 30)}`), tiles };
  const xh = document.getElementById("xero-heading");
  if (xh) {
    const sec = document.querySelector('section[aria-labelledby="xero-heading"]');
    const ref = [...main.querySelectorAll('[data-slot="card-header"] > h2')][0];
    out.xero = { h2: { tag: xh.tagName.toLowerCase(), ...st(xh) }, idCount: document.querySelectorAll('[id="xero-heading"]').length, sectionCount: document.querySelectorAll('section[aria-labelledby="xero-heading"]').length, labelledbyResolves: !!sec && document.getElementById(sec.getAttribute("aria-labelledby")) === xh, settingsSectionH2Ref: ref ? st(ref) : null };
  }
  return out;
};

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const res = { capturedAt: new Date().toISOString(), runs: {} };
  const tiles = { xero: [], mcp: [] };
  let pages = 0;
  for (const tab of TABS)
    for (const [w, h, theme] of VARIANTS) {
      const vk = `${w}-${theme}`;
      const ctx = await L.newCtx(browser, S, { width: w, height: h, theme, extra: L.EXTRA });
      const p = await ctx.newPage();
      pages++;
      lab.label = `${tab}|${vk}`;
      L.watch(p, consoleSink, lab);
      await p.goto(T(tab), { waitUntil: "load" });
      await settle(p);
      const m = await p.evaluate(MEASURE);
      m.htmlClass = await p.evaluate(() => document.documentElement.className);
      if (tab === "mcp") {
        const hd = p.getByRole("heading", { name: "Claude / MCP Connector", exact: true });
        m.mcpHeading = { exactCount: await hd.count(), level: await hd.first().evaluate((e) => e.tagName.toLowerCase()).catch(() => null), ariaSnapshot: await hd.first().ariaSnapshot().catch((e) => `ERR ${e.message}`) };
        m.mcpH1Role = await p.locator("#main-content").getByRole("heading", { level: 1, name: "MCP", exact: true }).count();
      } else {
        m.xeroHeadingRole = await p.getByRole("heading", { name: "Xero Integration", exact: true }).count();
      }
      const f = path.join(L.RAW, `${tab}-${vk}.png`);
      await p.screenshot({ path: f, fullPage: true });
      tiles[tab].push([`Alice ${w}x${h} ${theme} — ?tab=${tab}`, f]);
      res.runs[`${tab}|${vk}`] = m;
      console.log(tab, vk, JSON.stringify({ h1: m.h1.map((x) => `${x.text}@${x.fontSize}`), sw: `${m.scrollWidth}/${m.clientWidth}`, past: m.pastPanelEdge, xero: m.xero && { fs: m.xero.h2.fontSize, fw: m.xero.h2.fontWeight, lh: m.xero.h2.lineHeight, c: m.xero.h2.color, ff: m.xero.h2.fontFamily }, tiles: m.tiles.map((x) => `${x.heading}:${x.w}x${x.h}/gap${x.gap}/${x.ariaHidden}`), mcp: m.mcpHeading, html: m.htmlClass }));
      await ctx.close();
    }
  await L.sheet(browser, path.join(L.OUT, "sheet-xero-alice.png"), "665F after-fix @cec81dad5 — Connections › Integrations (?tab=xero), Alice: 1024 light / dark, 1440 light / dark (full page)", tiles.xero, 2, 720);
  await L.sheet(browser, path.join(L.OUT, "sheet-mcp-alice.png"), "665F after-fix @cec81dad5 — Connections › MCP (?tab=mcp), Alice: 1024 light / dark, 1440 light / dark (full page)", tiles.mcp, 2, 720);
  await browser.close();

  // ---------- verdicts ----------
  const R = res.runs;
  const px = (s) => parseFloat(s);
  const xr = VARIANTS.map(([w, , th]) => R[`xero|${w}-${th}`]);
  const mr = VARIANTS.map(([w, , th]) => R[`mcp|${w}-${th}`]);
  const xItem = xr.every((m) => m.h1Count === 1 && m.h1[0].text === "Integrations" && m.xero && px(m.h1[0].fontSize) > px(m.xero.h2.fontSize) && m.xero.h2.fontSize === "18px" && m.xero.h2.fontWeight === "600" && m.xero.h2.lineHeight === "18px" && m.xero.idCount === 1 && m.xero.sectionCount === 1 && m.xero.labelledbyResolves && m.xeroHeadingRole === 1);
  // Extra check (not in the brief's bar): computed parity with a real SettingsSection h2 on the same page.
  const PROPS = ["fontSize", "fontWeight", "lineHeight", "color", "fontFamily"];
  const parity = xr.map((m) => Object.fromEntries(PROPS.map((k) => [k, { xero: m.xero.h2[k], settingsSection: m.xero.settingsSectionH2Ref && m.xero.settingsSectionH2Ref[k], equal: !!m.xero.settingsSectionH2Ref && m.xero.h2[k] === m.xero.settingsSectionH2Ref[k] }])));
  const parityDiffs = [...new Set(parity.flatMap((pp) => PROPS.filter((k) => !pp[k].equal)))];
  const sib = xr.flatMap((m) => m.tiles);
  const mt = mr.map((m) => m.tiles.find((x) => x.heading === "Claude / MCP Connector"));
  // box only (size, gap, radius, aria-hidden); the icon glyph is recorded but not keyed (Xero Connection's tile is a letter "X", no svg).
  const tileKey = (x) => x && `${x.w}x${x.h}|gap${x.gap}|${x.radius}|${x.ariaHidden}`;
  const sibKeys = [...new Set(sib.map(tileKey))];
  const mItem = mr.every((m, i) => m.h1Count === 1 && m.h1[0].text === "MCP" && m.mcpH1Role === 1 && m.mcpHeading.exactCount === 1 && mt[i] && mt[i].ariaHidden === "true" && mt[i].w === 40 && mt[i].h === 40) && sibKeys.length === 1 && mt.every((x) => tileKey(x) === sibKeys[0]);
  const ov = [...xr.slice(0, 2), ...mr.slice(0, 2)];
  const ovItem = ov.every((m) => m.scrollWidth === 1024 && m.clientWidth === 1024 && m.pastPanelEdge === 0);
  const errs = consoleSink.filter((c) => /error/.test(c.type));
  const hyd = consoleSink.filter((c) => /hydrat/i.test(c.text));
  const conItem = errs.length === 0 && hyd.length === 0;
  const pick = (m) => ({ h1: `${m.h1[0].text} ${m.h1[0].fontSize}/${m.h1[0].fontWeight} ${m.h1[0].fontFamily}`, theme: m.htmlClass });
  const items = [
    { id: "xero-h2", verdict: xItem ? "PASS" : "FAIL", detail: Object.fromEntries(VARIANTS.map(([w, , th], i) => [`${w}-${th}`, { ...pick(xr[i]), h1Count: xr[i].h1Count, h2: xr[i].xero && { fontSize: xr[i].xero.h2.fontSize, fontWeight: xr[i].xero.h2.fontWeight, lineHeight: xr[i].xero.h2.lineHeight, color: xr[i].xero.h2.color, fontFamily: xr[i].xero.h2.fontFamily, cls: xr[i].xero.h2.cls }, settingsSectionH2Ref: xr[i].xero && xr[i].xero.settingsSectionH2Ref, idCount: xr[i].xero && xr[i].xero.idCount, sectionCount: xr[i].xero && xr[i].xero.sectionCount, labelledbyResolves: xr[i].xero && xr[i].xero.labelledbyResolves, headingRoleExact: xr[i].xeroHeadingRole }])) },
    { id: "xero-h2-parity-vs-SettingsSection-h2 (extra)", verdict: parityDiffs.length === 0 ? "PASS" : `FAIL (LOW: ${parityDiffs.join(",")} differ)`, detail: { ref: "first [data-slot=card-header] > h2 on the page (Accounting)", byVariant: Object.fromEntries(VARIANTS.map(([w, , th], i) => [`${w}-${th}`, parity[i]])), refCls: xr[0].xero.settingsSectionH2Ref && xr[0].xero.settingsSectionH2Ref.cls, xeroCls: xr[0].xero.h2.cls } },
    { id: "mcp-title", verdict: mItem ? "PASS" : "FAIL", detail: { mcp: Object.fromEntries(VARIANTS.map(([w, , th], i) => [`${w}-${th}`, { ...pick(mr[i]), h1Count: mr[i].h1Count, headingExactCount: mr[i].mcpHeading.exactCount, level: mr[i].mcpHeading.level, ariaSnapshot: mr[i].mcpHeading.ariaSnapshot, tile: mt[i] }])), siblingTilesOnXero: Object.fromEntries(VARIANTS.map(([w, , th], i) => [`${w}-${th}`, xr[i].tiles])), distinctSiblingTileKeys: sibKeys, mcpTileKeys: [...new Set(mt.map(tileKey))] } },
    { id: "overflow-1024", verdict: ovItem ? "PASS" : "FAIL", detail: Object.fromEntries([["xero", xr], ["mcp", mr]].flatMap(([t, rr]) => rr.slice(0, 2).map((m, i) => [`${t}|1024-${i ? "dark" : "light"}`, { scrollWidth: m.scrollWidth, clientWidth: m.clientWidth, panelRight: m.panelRight, pastPanelEdge: m.pastPanelEdge, pastSample: m.pastSample }]))) },
    { id: "console", verdict: conItem ? "PASS" : "FAIL", detail: { pagesOpened: pages, messages: consoleSink.length, errors: errs.length, hydration: hyd.length, all: consoleSink } },
  ];
  const verify = { capturedAt: res.capturedAt, phase: L.PHASE, head: "cec81dad5", items };
  fs.writeFileSync(path.join(L.OUT, "verify.json"), JSON.stringify(verify) + "\n");
  for (const it of items) console.log(it.id.padEnd(46), it.verdict);
  console.log("sibKeys", JSON.stringify(sibKeys), "mcpKeys", JSON.stringify([...new Set(mt.map(tileKey))]));
  console.log("console", JSON.stringify(consoleSink).slice(0, 1500));
})().catch((e) => { console.error(e); process.exit(1); });
