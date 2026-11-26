// 664A OBS-after step 3 (NEW, not in before/): the 664A acceptance probes on the running e2e stack
// (:3001, rebuilt from worktree-epic-664A @ 14d5b9b84). Measures what 01-capture.cjs's copied SHELL
// probe cannot see any more (it reads the strip's parentElement as the scroller; after 664A the strip
// sits in a Radix ScrollArea, so the real scroller is [data-slot=scroll-area-viewport]).
// Interacts (clicks family links / tabs, presses keys) — navigation only, no form input, no submits.
// K-02 family bar 390 (both personas, click-through), landmark count 390/1024/1440, K-03 rail tokens +
// sticky in dark, strip underline/overflow/fades/manual activation, K-12 tokens + contrast, K-20 labels
// for every matter/client sub-tab + bar pixel diff vs before, K-100 rail widths/truncation + Connections
// card overflow at 1024. Raw tiles to /tmp/664A-after-verify-raw/; verify.json + sheets to ../.
const path = require("path");
const fs = require("fs");
const C = require("../../../661B/walk/scripts/common.cjs");

const OUT = path.resolve(__dirname, "..");
const RAW = "/tmp/664A-after-verify-raw";
const BEFORE_RAW = "/tmp/664A-before-raw";
const AFTER_RAW = "/tmp/664A-after-raw";
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${C.APP}/org/${C.ORG}`;
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };
const MATTER = "9a7042bc-c0cc-4010-b37e-442a3eb07d5e";
const CLIENT = "68c6b51e-a8f9-4b76-9e05-6bf992a79e53";
const FAMS = [["firm", "Firm"], ["matters", "Matters"], ["documents", "Documents"], ["fees", "Fees & Trust"], ["connections", "Connections"]];
const V = { k02: {}, landmarks: {}, k03: {}, strip: {}, activation: {}, k12: {}, k20: {}, k100: {}, tokens: {} };
const sink = [];
const lab = { label: "" };
const tiles = {};
let pagesOpened = 0;

async function open(browser, state, w, h, theme) {
  const ctx = await C.newCtx(browser, state, { width: w, height: h, theme, extra: EXTRA });
  const p = await ctx.newPage();
  C.watch(p, sink, lab);
  return { ctx, p };
}
async function go(p, url, label) {
  lab.label = label;
  await p.goto(url, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(400);
  pagesOpened++;
}
const shot = async (p, key, caption, clip) => {
  const f = path.join(RAW, `${key}.png`);
  await p.screenshot({ path: f, ...(clip ? { clip } : {}) });
  tiles[key] = [caption, f];
};

// Token values resolved in the page (a probe element per custom property).
const TOKENS = () => {
  const out = {};
  for (const t of ["background", "foreground", "muted", "muted-foreground", "border", "primary", "sidebar-primary", "ring"]) {
    const d = document.createElement("div");
    d.style.backgroundColor = `var(--${t})`;
    document.body.appendChild(d);
    out[t] = window.__hex(getComputedStyle(d).backgroundColor);
    d.remove();
  }
  return out;
};

// A horizontal tab/link row in a ScrollArea: kind "tabs" (family tab strip) | "bar" (family bar).
const ROW = (kind) => {
  const vis = (e) => { const b = e.getBoundingClientRect(); return b.width > 0 && b.height > 0; };
  const r = (e) => { const b = e.getBoundingClientRect(); return { left: +b.left.toFixed(1), top: +b.top.toFixed(1), right: +b.right.toFixed(1), bottom: +b.bottom.toFixed(1), width: +b.width.toFixed(1), height: +b.height.toFixed(1) }; };
  let root;
  if (kind === "tabs") {
    const tl = document.querySelector('#main-content [role="tablist"][aria-label$="settings"]');
    root = tl && tl.closest('[data-slot="scroll-area"]') && tl.closest('[data-slot="scroll-area"]').parentElement;
  } else root = [...document.querySelectorAll('nav[aria-label="Settings navigation"]')].find((n) => n.querySelector('[data-slot="scroll-area"]') && vis(n));
  if (!root) return null;
  const vp = root.querySelector('[data-slot="scroll-area-viewport"]');
  const vpr = r(vp);
  const items = kind === "tabs" ? [...root.querySelectorAll('[role="tab"]')] : [...root.querySelectorAll("a[href]")];
  const fade = (edge) => { const f = root.querySelector(`[data-slot$="-fade-${edge}"]`); if (!f) return null; const cs = getComputedStyle(f); return { dataVisible: f.getAttribute("data-visible"), opacity: +cs.opacity, box: r(f), bgImage: cs.backgroundImage.slice(0, 120) }; };
  const sb = root.querySelector('[data-slot="scroll-area-scrollbar"][data-orientation="horizontal"]');
  const tl = root.querySelector('[role="tablist"]');
  const rcs = getComputedStyle(root);
  const out = {
    label: kind === "tabs" ? tl.getAttribute("aria-label") : root.getAttribute("aria-label"),
    rootBox: r(root),
    rootBorderBottom: `${rcs.borderBottomWidth} ${rcs.borderBottomStyle} ${window.__hex(rcs.borderBottomColor)}`,
    viewport: { box: vpr, scrollWidth: vp.scrollWidth, clientWidth: vp.clientWidth, scrollLeft: vp.scrollLeft, overflows: vp.scrollWidth > vp.clientWidth, overflowX: getComputedStyle(vp).overflowX, overflowY: getComputedStyle(vp).overflowY },
    scrollbar: sb ? { box: r(sb), state: sb.getAttribute("data-state") } : null,
    fades: { start: fade("start"), end: fade("end") },
    tablist: tl ? { label: tl.getAttribute("aria-label"), role: tl.getAttribute("role"), after: getComputedStyle(tl, "::after").content, borderBottom: getComputedStyle(tl).borderBottomWidth, bg: window.__hex(getComputedStyle(tl).backgroundColor) } : null,
    items: items.map((e) => {
      const cs = getComputedStyle(e);
      const a = getComputedStyle(e, "::after");
      const b = e.getBoundingClientRect();
      const h = parseFloat(a.height) || 0;
      const bottom = b.bottom - (parseFloat(a.bottom) || 0);
      return {
        text: e.textContent.trim(),
        role: e.getAttribute("role"),
        href: e.getAttribute("href"),
        ariaSelected: e.getAttribute("aria-selected"),
        ariaCurrent: e.getAttribute("aria-current"),
        tabIndex: e.tabIndex,
        color: window.__hex(cs.color),
        box: r(e),
        fullyInViewport: b.left >= vpr.left - 0.5 && b.right <= vpr.right + 0.5,
        after: { content: a.content, opacity: +a.opacity, height: a.height, bottom: a.bottom, left: a.left, right: a.right, bg: window.__hex(a.backgroundColor), radius: a.borderRadius },
        underlineBox: { top: +(bottom - h).toFixed(1), bottom: +bottom.toFixed(1) },
        underlineInsideViewport: bottom <= vpr.bottom + 0.5 && bottom - h >= vpr.top - 0.5,
      };
    }),
  };
  out.visibleUnderlines = out.items.filter((i) => i.after.opacity > 0).map((i) => i.text);
  out.underlineGapToBorderPx = out.items.length ? +(out.rootBox.bottom - 1 - Math.max(...out.items.map((i) => i.underlineBox.bottom))).toFixed(1) : null;
  return out;
};

async function landmarks(p) {
  const snap = await p.locator("body").ariaSnapshot();
  return {
    byRole: await p.getByRole("navigation", { name: "Settings navigation", exact: true }).count(),
    ariaSnapshot: (snap.match(/navigation "Settings navigation"/g) || []).length,
    dom: await p.locator('nav[aria-label="Settings navigation"]').count(),
    domVisible: await p.locator('nav[aria-label="Settings navigation"]:visible').count(),
    // the one visible landmark: the rail (has a nested ul) or the family bar (has a ScrollArea)
    visibleKind: await p.evaluate(() => [...document.querySelectorAll('nav[aria-label="Settings navigation"]')].filter((n) => n.getBoundingClientRect().width > 0).map((n) => (n.querySelector('[data-slot="scroll-area"]') ? "family-bar" : "rail"))),
  };
}

// ---------- K-02: family bar at 390, both personas ----------
async function k02(browser, S) {
  for (const who of ["alice", "carol"]) {
    const { ctx, p } = await open(browser, S[who], 390, 844, "light");
    const R = (V.k02[who] = { steps: [] });
    await go(p, `${BASE}/settings/firm`, `k02|${who}|firm`);
    R.initial = await p.evaluate(ROW, "bar");
    R.landmarks390 = await landmarks(p);
    await shot(p, `k02__${who}-firm`, `${who} 390 /settings/firm — bar scrollLeft ${R.initial.viewport.scrollLeft}, ${R.initial.viewport.scrollWidth}/${R.initial.viewport.clientWidth}`, { x: 0, y: 0, width: 390, height: 300 });
    for (const [fam, label] of [...FAMS.slice(1), FAMS[0]]) {
      lab.label = `k02|${who}|click ${label}`;
      const link = p.getByRole("navigation", { name: "Settings navigation", exact: true }).getByRole("link", { name: label, exact: true });
      await link.click();
      await p.waitForURL(new RegExp(`/settings/${fam}(\\?|$)`), { timeout: 30000 });
      await p.waitForLoadState("networkidle").catch(() => {});
      await p.waitForTimeout(500);
      pagesOpened++;
      const row = await p.evaluate(ROW, "bar");
      const act = row.items.find((i) => i.ariaCurrent === "page");
      const strip = await p.evaluate(ROW, "tabs");
      R.steps.push({ clicked: label, url: p.url().replace(C.APP, ""), activeLink: act && act.text, activeFullyInViewport: act && act.fullyInViewport, activeUnderline: act && act.after, activeClearOfFades: act && (!(row.fades.end.opacity > 0) || act.box.right <= row.fades.end.box.left + 0.5) && (!(row.fades.start.opacity > 0) || act.box.left >= row.fades.start.box.right - 0.5), scrollLeft: row.viewport.scrollLeft, fades: { start: row.fades.start.dataVisible, end: row.fades.end.dataVisible }, visibleUnderlines: row.visibleUnderlines, stripLabel: strip && strip.label, h1: await p.evaluate(() => [...document.querySelectorAll("#main-content h1")].map((h) => h.textContent.trim())) });
      await shot(p, `k02__${who}-click-${fam}`, `${who} 390 clicked "${label}" → ${p.url().replace(`${BASE}`, "")}; current "${act && act.text}" scrollLeft ${row.viewport.scrollLeft}, fades ${row.fades.start.dataVisible}/${row.fades.end.dataVisible}`, { x: 0, y: 0, width: 390, height: 300 });
    }
    R.order = R.initial.items.map((i) => i.text);
    R.hrefs = R.initial.items.map((i) => i.href);
    // Deep link at 390: the active family is scrolled into view on load.
    await go(p, `${BASE}/settings/connections`, `k02|${who}|deeplink connections`);
    R.deepLinkConnections = await p.evaluate(ROW, "bar");
    await ctx.close();
  }
  // Dark 390 (Alice): two stacked rows, bar + strip, light-only artefacts.
  const { ctx, p } = await open(browser, S.alice, 390, 844, "dark");
  await go(p, `${BASE}/settings/firm`, "k02|alice|firm dark");
  V.k02.aliceDark = { bar: await p.evaluate(ROW, "bar"), strip: await p.evaluate(ROW, "tabs"), tokens: await p.evaluate(TOKENS) };
  await shot(p, "k02__alice-firm-dark", "alice 390 dark /settings/firm — family bar + tab strip", { x: 0, y: 0, width: 390, height: 300 });
  await go(p, `${BASE}/settings/connections`, "k02|alice|connections dark");
  await shot(p, "k02__alice-connections-dark", "alice 390 dark /settings/connections (deep link) — bar scrolled to Connections", { x: 0, y: 0, width: 390, height: 300 });
  await ctx.close();
}

// ---------- landmark count at 390 / 1024 / 1440 (both personas, both themes for 1440) ----------
async function landmarkCounts(browser, S) {
  for (const who of ["alice", "carol"])
    for (const [w, h] of [[390, 844], [1024, 768], [1440, 900]]) {
      const { ctx, p } = await open(browser, S[who], w, h, "light");
      await go(p, `${BASE}/settings/firm`, `landmarks|${who}|${w}`);
      V.landmarks[`${who}-${w}`] = await landmarks(p);
      await ctx.close();
    }
}

// ---------- K-03: rail active tokens (light/dark) + sticky in dark ----------
async function k03(browser, S) {
  for (const theme of ["light", "dark"])
    for (const [w, h] of [[1024, 768], [1440, 900]]) {
      const { ctx, p } = await open(browser, S.alice, w, h, theme);
      await go(p, `${BASE}/settings/firm?tab=general`, `k03|${w}|${theme}`);
      const probe = () => {
        const rail = document.querySelector('aside nav[aria-label="Settings navigation"]');
        const hd = document.querySelector("header");
        const a = rail.querySelector('a[aria-current="true"]');
        const cs = getComputedStyle(a);
        const be = getComputedStyle(a, "::before");
        const rb = rail.getBoundingClientRect();
        const hb = hd.getBoundingClientRect();
        return { scrollY: Math.round(scrollY), railTop: +rb.top.toFixed(1), railBottom: +rb.bottom.toFixed(1), railHeight: +rb.height.toFixed(1), innerHeight, headerTop: +hb.top.toFixed(1), headerBottom: +hb.bottom.toFixed(1), railCssTop: getComputedStyle(rail).top, railCssHeight: getComputedStyle(rail).height, active: { text: a.textContent.trim(), dataActive: a.getAttribute("data-active"), bg: window.__hex(cs.backgroundColor), color: window.__hex(cs.color), borderLeft: cs.borderLeftWidth, radius: cs.borderRadius, before: { content: be.content, bg: window.__hex(be.backgroundColor), width: be.width, top: be.top, bottom: be.bottom, left: be.left, radius: be.borderRadius } }, familyCurrent: [...rail.querySelectorAll('a[aria-current="page"]')].map((x) => ({ text: x.textContent.trim(), color: window.__hex(getComputedStyle(x).color), bg: window.__hex(getComputedStyle(x).backgroundColor) })) };
      };
      const at0 = await p.evaluate(probe);
      await p.evaluate(() => window.scrollTo(0, 600));
      await p.waitForTimeout(400);
      const at600 = await p.evaluate(probe);
      const tokens = await p.evaluate(TOKENS);
      V.k03[`${w}-${theme}`] = { at0, at600, tokens, railTopGteHeaderBottom: at600.railTop >= at600.headerBottom, railBottomInViewport: at600.railBottom <= at600.innerHeight, activeBgIsMuted: at600.active.bg === tokens.muted, activeBarIsPrimary: at600.active.before.bg === tokens.primary };
      if (w === 1440) await shot(p, `k03__1440-${theme}`, `alice 1440 ${theme} firm › General scrollY 600 — rail top ${at600.railTop} / header bottom ${at600.headerBottom}; active bg ${at600.active.bg} (muted ${tokens.muted}), bar ${at600.active.before.bg} (primary ${tokens.primary})`, { x: 240, y: 0, width: 460, height: 400 });
      await ctx.close();
    }
}

// ---------- tab strip: underline / overflow / fades at 390, 1024, 1440; manual activation ----------
async function strip(browser, S) {
  for (const who of ["alice", "carol"])
    for (const theme of ["light", "dark"])
      for (const [w, h] of [[390, 844], [1024, 768], [1440, 900]]) {
        if (who === "carol" && (theme === "dark" || w === 390)) continue;
        const { ctx, p } = await open(browser, S[who], w, h, theme);
        for (const [fam] of FAMS) {
          await go(p, `${BASE}/settings/${fam}`, `strip|${who}|${fam}|${w}|${theme}`);
          const row = await p.evaluate(ROW, "tabs");
          V.strip[`${who}-${fam}-${w}-${theme}`] = row;
          if (who === "alice" && w === 1024 && ["firm", "matters", "fees"].includes(fam)) {
            const loc = p.locator(`#main-content [role="tablist"][aria-label$="settings"]`).first().locator("xpath=ancestor::*[@data-slot='scroll-area']/..");
            const f = path.join(RAW, `strip__${fam}-${theme}-start.png`);
            await loc.screenshot({ path: f });
            tiles[`strip__${fam}-${theme}-start`] = [`alice 1024 ${theme} ${fam} strip at start — ${row.viewport.scrollWidth}/${row.viewport.clientWidth}, fades ${row.fades.start.dataVisible}/${row.fades.end.dataVisible}, scrollbar ${row.scrollbar ? "mounted" : "none"}, underlines ${row.visibleUnderlines.length}`, f];
            await p.evaluate(() => { const vp = document.querySelector('#main-content [role="tablist"][aria-label$="settings"]').closest('[data-slot="scroll-area-viewport"]'); vp.scrollLeft = vp.scrollWidth; });
            await p.waitForTimeout(300);
            const end = await p.evaluate(ROW, "tabs");
            V.strip[`${who}-${fam}-${w}-${theme}`].scrolledToEnd = { scrollLeft: end.viewport.scrollLeft, fades: { start: end.fades.start.dataVisible, end: end.fades.end.dataVisible, startOpacity: end.fades.start.opacity, endOpacity: end.fades.end.opacity } };
            const f2 = path.join(RAW, `strip__${fam}-${theme}-end.png`);
            await loc.screenshot({ path: f2 });
            tiles[`strip__${fam}-${theme}-end`] = [`… scrolled to end (scrollLeft ${end.viewport.scrollLeft}) — fades ${end.fades.start.dataVisible}/${end.fades.end.dataVisible}`, f2];
          }
        }
        await ctx.close();
      }
  // Manual activation (Alice 1024 light Firm and 390 light Matters).
  for (const [w, h, fam, clickName, arrowFrom] of [[1024, 768, "firm", "Roles", "Roles"], [390, 844, "matters", "Naming", "Naming"]]) {
    const { ctx, p } = await open(browser, S.alice, w, h, "light");
    await go(p, `${BASE}/settings/${fam}`, `activate|${fam}|${w}`);
    const A = (V.activation[`${fam}-${w}`] = {});
    const state = () => p.evaluate(() => { const ae = document.activeElement; const panel = document.querySelector('#main-content [role="tabpanel"]'); return { url: location.pathname + location.search, h1: [...document.querySelectorAll("#main-content h1")].map((x) => x.textContent.trim()), selected: [...document.querySelectorAll('#main-content [role="tablist"][aria-label$="settings"] [role="tab"][aria-selected="true"]')].map((x) => x.textContent.trim()), active: ae ? `${ae.tagName.toLowerCase()}${ae.getAttribute("role") ? `[role=${ae.getAttribute("role")}]` : ""} "${(ae.textContent || ae.getAttribute("aria-label") || "").trim().slice(0, 40)}"` : null, focusInPanel: !!(panel && ae && panel.contains(ae)), panelLabelledBy: panel && panel.getAttribute("aria-labelledby") }; });
    A.initial = await state();
    const tab = p.getByRole("tablist", { name: `${FAMS.find((f) => f[0] === fam)[1]} settings`, exact: true }).getByRole("tab", { name: clickName, exact: true });
    await tab.click();
    await p.waitForURL(/\?tab=/, { timeout: 30000 });
    await p.waitForLoadState("networkidle").catch(() => {});
    await p.waitForTimeout(800);
    A.afterClick = await state();
    // Arrow keys: focus the selected tab, ArrowRight moves focus only (manual), Enter activates.
    await tab.focus();
    await p.keyboard.press("ArrowRight");
    await p.waitForTimeout(400);
    A.afterArrowRight = await state();
    await p.keyboard.press("ArrowLeft");
    await p.waitForTimeout(300);
    A.afterArrowLeft = await state();
    await p.keyboard.press("ArrowRight");
    await p.waitForTimeout(300); // Radix RovingFocusGroup moves focus in a setTimeout
    await p.keyboard.press("Enter");
    await p.waitForFunction((u) => location.pathname + location.search !== u, A.afterClick.url, { timeout: 30000 }).catch(() => {});
    await p.waitForLoadState("networkidle").catch(() => {});
    await p.waitForTimeout(800);
    A.afterEnter = await state();
    await p.getByRole("tablist", { name: `${FAMS.find((f) => f[0] === fam)[1]} settings`, exact: true }).getByRole("tab", { selected: true }).focus();
    await p.keyboard.press("ArrowRight");
    await p.waitForTimeout(300);
    await p.keyboard.press("Space");
    await p.waitForFunction((u) => location.pathname + location.search !== u, A.afterEnter.url, { timeout: 30000 }).catch(() => {});
    await p.waitForLoadState("networkidle").catch(() => {});
    await p.waitForTimeout(800);
    A.afterSpace = await state();
    pagesOpened += 3;
    await ctx.close();
  }
}

// ---------- K-12 forbidden notice tokens + contrast ----------
async function k12(browser, S) {
  for (const theme of ["light", "dark"]) {
    const { ctx, p } = await open(browser, S.carol, 1440, 900, theme);
    for (const tab of ["audit-trail", "roles"]) {
      await go(p, `${BASE}/settings/firm?tab=${tab}`, `k12|${tab}|${theme}`);
      const n = await p.evaluate(() => { const e = [...document.querySelectorAll('#main-content p[role="status"]')].find((x) => /have access to/.test(x.textContent)); if (!e) return null; const cs = getComputedStyle(e); return { text: e.textContent.trim(), role: e.getAttribute("role"), color: window.__hex(cs.color), bg: window.__hex(cs.backgroundColor), border: `${cs.borderTopWidth} ${cs.borderTopStyle} ${window.__hex(cs.borderTopColor)}`, borderHex: window.__hex(cs.borderTopColor), radius: cs.borderRadius, fg3: window.__rgb(cs.color, cs.backgroundColor), bg3: window.__rgb(cs.backgroundColor), className: e.className }; });
      const tokens = await p.evaluate(TOKENS);
      if (n) { n.contrast = C.ratio(n.fg3, n.bg3); n.matches = { color: n.color === tokens["muted-foreground"], bg: n.bg === tokens.muted, border: n.borderHex === tokens.border }; delete n.fg3; delete n.bg3; }
      V.k12[`${tab}-${theme}`] = { notice: n, tokens };
    }
    await ctx.close();
  }
}

// ---------- K-20: every matter / client sub-tab label ----------
async function k20(browser, S) {
  const groups = {
    matter: [["details", "Details", ["details:Details", "fields:Custom fields", "team:Team"]], ["work", "Work", ["tasks:Tasks", "documents:Documents", "correspondence:Correspondence", "generated:Generated documents"]], ["finance", "Fees", ["time:Time recording", "disbursements:Disbursements", "budget:Fee estimate", "rates:Tariffs", "financials:Financials", "statements:Statements of account", "trust:Trust"]], ["client", "Client", ["clients:Clients", "requests:Information requests", "comments:Comments", "adverse-parties:Adverse parties"]], ["activity", "Activity", ["activity:Activity", "audit:Audit trail"]]],
    client: [["details", "Details", ["details:Details", "fields:Custom fields", "tags:Tags"]], ["work", "Matters", ["matters:Matters", "documents:Documents", "generated:Generated documents"]], ["finance", "Fees", ["invoices:Fee notes", "rates:Tariffs", "financials:Financials", "trust:Trust"]], ["compliance", "FICA & compliance", ["onboarding:FICA onboarding", "requests:Information requests"]]],
  };
  const { ctx, p } = await open(browser, S.alice, 1440, 900, "light");
  for (const [k, url] of [["matter", `${BASE}/matters/${MATTER}`], ["client", `${BASE}/clients/${CLIENT}`]]) {
    V.k20[k] = [];
    for (const [gid, glabel, subs] of groups[k])
      for (const s of subs) {
        const [id, slabel] = s.split(":");
        await go(p, `${url}?tab=${id}`, `k20|${k}|${id}`);
        await p.locator('[data-testid="grouped-tab-bar"]').first().waitFor({ timeout: 20000 });
        const got = await p.evaluate((gid) => { const t = document.querySelector(`[data-testid="tab-group-${gid}"]`); return t ? { text: t.textContent.replace(/\s+/g, " ").trim(), selected: t.getAttribute("aria-selected") } : null; }, gid);
        const expected = slabel === glabel ? glabel : `${glabel} · ${slabel}`;
        V.k20[k].push({ tab: id, group: glabel, sub: slabel, got, expected, beforeWouldBe: `${glabel} · ${slabel}`, pass: !!got && got.text === expected && got.selected === "true" });
        if ((k === "matter" && (id === "details" || id === "activity" || id === "time")) || (k === "client" && (id === "details" || id === "matters"))) {
          const loc = p.locator('[data-slot="grouped-tab-bar-sticky"]').first();
          const f = path.join(RAW, `k20__${k}-${id}.png`);
          await loc.screenshot({ path: f });
          tiles[`k20__${k}-${id}`] = [`${k} ?tab=${id} — trigger "${got && got.text}" (before: "${glabel} · ${slabel}")`, f];
        }
      }
  }
  await ctx.close();
  // Pixel compare of the default-state bar element shots (01-capture, both phases).
  const D = C.differ(browser);
  for (const k of ["matter", "client"]) await D.cmp(`gtb-${k}-bar`, { a: path.join(AFTER_RAW, `gtb__${k}-bar.png`), b: path.join(BEFORE_RAW, `gtb__${k}-bar.png`), keepVisual: true });
  for (const k of ["matter", "client"]) await D.cmp(`gtb-${k}-viewport`, { a: path.join(AFTER_RAW, `gtb__${k}-viewport.png`), b: path.join(BEFORE_RAW, `gtb__${k}-viewport.png`) });
  V.k20.pixelDiff = D.diffs;
  V.k20.visuals = D.visuals.map(([n, v]) => { const f = path.join(RAW, `diff__${n}.png`); fs.writeFileSync(f, Buffer.from(v.diff.split(",")[1], "base64")); tiles[`diff__${n}`] = [`${n}: after vs before diff (red = differs)`, f]; return n; });
}

// ---------- K-100: rail widths / truncation at 1024 + 1440; Connections overflow at 1024 ----------
async function k100(browser, S) {
  for (const [w, h] of [[1024, 768], [1280, 800], [1440, 900]]) {
    const { ctx, p } = await open(browser, S.alice, w, h, "light");
    for (const [fam] of FAMS) {
      await go(p, `${BASE}/settings/${fam}`, `k100|rail|${fam}|${w}`);
      V.k100[`rail-${fam}-${w}`] = await p.evaluate(() => { const rail = document.querySelector('aside nav[aria-label="Settings navigation"]'); const aside = rail.closest("aside"); return { asideWidth: aside.getBoundingClientRect().width, truncatedOrWrapped: [...rail.querySelectorAll("a")].map((a) => { const cs = getComputedStyle(a); const lh = parseFloat(cs.lineHeight) || 20; const tops = new Set(); const tw = document.createTreeWalker(a, NodeFilter.SHOW_TEXT); for (let n = tw.nextNode(); n; n = tw.nextNode()) { if (!n.textContent.trim()) continue; const range = document.createRange(); range.selectNodeContents(n); for (const x of range.getClientRects()) if (x.width > 0) tops.add(Math.round(x.top)); } const lines = tops.size; return { text: a.textContent.trim(), scrollW: a.scrollWidth, clientW: a.clientWidth, height: Math.round(a.getBoundingClientRect().height), lines, overflowed: a.scrollWidth > a.clientWidth, textOverflow: cs.textOverflow, lh }; }).filter((x) => x.overflowed || x.lines > 1) }; });
    }
    if (w === 1024)
      for (const tab of ["xero", "ai"]) {
        await go(p, `${BASE}/settings/connections?tab=${tab}`, `k100|connections?tab=${tab}|1024`);
        V.k100[`connections-${tab}-1024`] = await p.evaluate(() => { const col = document.querySelector("#main-content [role=tabpanel]"); const cr = col.getBoundingClientRect(); const over = [...col.querySelectorAll("*")].filter((e) => { const b = e.getBoundingClientRect(); return b.width > 0 && b.right > cr.right + 1; }); const inner = [...col.querySelectorAll("*")].filter((e) => e.scrollWidth > e.clientWidth + 1 && e.clientWidth > 0 && /(hidden|clip|visible)/.test(getComputedStyle(e).overflowX)); return { panel: { left: Math.round(cr.left), right: Math.round(cr.right), width: Math.round(cr.width) }, docScrollWidth: document.scrollingElement.scrollWidth, innerWidth, pastPanelRight: over.slice(0, 8).map((e) => `${e.tagName.toLowerCase()}.${String(e.className).slice(0, 50)} right=${Math.round(e.getBoundingClientRect().right)} "${e.textContent.trim().slice(0, 30)}"`), pastPanelRightCount: over.length, innerOverflow: inner.slice(0, 8).map((e) => `${e.tagName.toLowerCase()}.${String(e.className).slice(0, 50)} ${e.scrollWidth}>${e.clientWidth} "${e.textContent.trim().slice(0, 30)}"`), innerOverflowCount: inner.length }; });
        await shot(p, `k100__connections-${tab}-1024`, `alice 1024 light connections?tab=${tab} — past-panel elements ${V.k100[`connections-${tab}-1024`].pastPanelRightCount}, inner overflow ${V.k100[`connections-${tab}-1024`].innerOverflowCount}, doc scrollWidth ${V.k100[`connections-${tab}-1024`].docScrollWidth}`);
      }
    if (w === 1024) {
      await go(p, `${BASE}/settings/matters?tab=request-templates`, "k100|rail matters 1024");
      await shot(p, "k100__rail-matters-1024", "alice 1024 light matters?tab=request-templates — rail 192px", { x: 240, y: 0, width: 460, height: 768 });
      await go(p, `${BASE}/settings/fees?tab=trust-accounting`, "k100|rail fees 1024");
      await shot(p, "k100__rail-fees-1024", "alice 1024 light fees?tab=trust-accounting — rail 192px", { x: 240, y: 0, width: 460, height: 768 });
    }
    await ctx.close();
  }
}

(async () => {
  const browser = await C.chromium.launch();
  const S = { alice: await C.loginState(browser, "alice"), carol: await C.loginState(browser, "carol") };
  const fails = [];
  for (const [n, f] of [["k02", k02], ["landmarks", landmarkCounts], ["k03", k03], ["strip", strip], ["k12", k12], ["k20", k20], ["k100", k100]]) {
    try { await f(browser, S); console.log("OK", n); } catch (e) { console.log("FAIL", n, e.message.split("\n")[0]); fails.push({ step: n, error: e.message.split("\n")[0].slice(0, 300) }); }
  }
  V.failures = fails;
  V.console = { pagesOpened, errors: sink.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: sink.filter((c) => c.type === "warning").length, hydration: sink.filter((c) => /hydrat/i.test(c.text)).length, messages: sink };
  fs.writeFileSync(path.join(RAW, "tiles.json"), JSON.stringify(tiles));
  fs.writeFileSync(path.join(OUT, "verify.json"), JSON.stringify({ capturedAt: new Date().toISOString(), ...V }) + "\n");
  console.log("tiles", Object.keys(tiles).length, "pages", pagesOpened, "console", sink.length, "failures", fails.length);
  await browser.close();
})();
