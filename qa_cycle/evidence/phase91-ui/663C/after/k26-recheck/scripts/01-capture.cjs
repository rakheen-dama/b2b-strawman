// 663C K-26 re-check after c4d1f7675 (NEW; derived from ../../scripts/03-capture.cjs k26() and ../../scripts/05-capture-extra.cjs
// k26(), which stay unedited). Same helpers and context options: 661B common.cjs (required unedited; Alice through /mock-login,
// Carol through the mock-auth-token cookie), 1024×768, theme via localStorage `kazi-theme`, rail via cookie `sidebar_state`,
// reduced motion, timezone Africa/Johannesburg, header strip clip 1024×96.
// Changes vs the after scripts: (1) ids are re-discovered (stack rebuilt): INV-0005 by a read-only GET /api/invoices (as
// 01-discover-ids.cjs), the template by GET /api/templates (longest name, as 01-discover-ids.cjs), Carol's matter, Alice's client
// and a checklist template from the UI lists (first link); (2) CRUMB_MEASURE records per crumb the label element's width,
// scrollWidth/clientWidth, natural text width (Range) and title, plus nav height; (3) two extra pages: Alice client detail (no
// client edit route exists) and the checklist template editor route (/edit redirects to the 4-crumb detail for a non-ORG_CUSTOM
// template; recorded as such); (4) raw tiles go to .tmp/663C-k26-raw/; (5) crumbs are not filtered by width > 0, and the verdict
// also fails a crumb row that overflows the nav or draws under the next header control.
// Read-only: navigation only. No form input, no submits, no SQL, no REST writes.
const path = require("path");
const fs = require("fs");
const C = require("../../../../661B/walk/scripts/common.cjs");

const OUT = path.resolve(__dirname, "..");
const RAW = path.join(C.W, ".tmp/663C-k26-raw");
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${C.APP}/org/${C.ORG}`;
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };
const M = { pages: {}, captures: {} };
const consoleSink = [];
const tiles = [];
const lab = { label: "" };

async function page(browser, state, opts, label) {
  const ctx = await C.newCtx(browser, state, { ...opts, extra: { ...EXTRA, ...(opts.extra || {}) } });
  const p = await ctx.newPage();
  lab.label = label;
  C.watch(p, consoleSink, lab);
  return { ctx, p };
}
async function settle(p) {
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(400);
}

const CRUMB_MEASURE = () => {
  const nav = document.querySelector('nav[aria-label="Breadcrumb"]');
  if (!nav) return { error: "no breadcrumb nav" };
  const r = (e) => e.getBoundingClientRect();
  const org = nav.querySelector(":scope > a");
  const lh = parseFloat(getComputedStyle(org).lineHeight) || 20;
  const header = nav.closest("header");
  // Crumbs = nav children that are displayed (not `hidden`). Zero-width crumbs are KEPT (a crumb shrunk to 0 px is a finding);
  // the after scripts filtered width > 0, which would relabel roles when the org or the last crumb collapses to nothing.
  const kids = [...nav.children].filter((e) => getComputedStyle(e).display !== "none");
  const textW = (el) => {
    const rg = document.createRange();
    rg.selectNodeContents(el);
    return +rg.getBoundingClientRect().width.toFixed(1);
  };
  const one = (e, i) => {
    const label = i === 0 ? e : [...e.children].filter((c) => c.matches("a,span")).pop() || e;
    const b = r(e);
    const lb = r(label);
    const nat = textW(label);
    return {
      text: label.textContent.trim().slice(0, 60),
      role: i === 0 ? "org" : i === kids.length - 1 ? "last" : "middle",
      w: Math.round(b.width), // wrapper (chevron + label), the element 03-capture.cjs measured
      labelW: +lb.width.toFixed(1),
      naturalW: nat,
      scrollW: label.scrollWidth,
      clientW: label.clientWidth,
      truncated: label.scrollWidth > label.clientWidth,
      over160: nat > 160,
      title: label.getAttribute("title"),
      h: Math.round(b.height),
      left: Math.round(b.left),
      right: Math.round(b.right),
    };
  };
  const crumbs = kids.map(one);
  const last = crumbs[crumbs.length - 1];
  const nb = r(nav);
  // First header control to the right of the nav (search button etc.): crumbs drawn past its left edge sit under it.
  const ctrls = header ? [...header.querySelectorAll("button, a, input")].filter((e) => !nav.contains(e) && r(e).width > 0 && r(e).left >= nb.left) : [];
  const neighbourLeft = ctrls.length ? Math.round(Math.min(...ctrls.map((e) => r(e).left))) : null;
  const maxRight = Math.max(...crumbs.map((c) => c.right));
  return {
    viewport: [window.innerWidth, window.innerHeight],
    navH: Math.round(nb.height),
    navW: Math.round(nb.width),
    navRight: Math.round(nb.right),
    headerH: header ? Math.round(r(header).height) : null,
    lineHeight: lh,
    wraps: kids.some((e) => r(e).height > lh * 1.5),
    crumbs,
    neighbourLeft,
    crumbsOverflowNav: maxRight > Math.round(nb.right) + 1,
    crumbsUnderControl: neighbourLeft != null && maxRight > neighbourLeft,
    lastVisible: last.labelW >= 1 && last.right <= window.innerWidth && last.right <= Math.round(nb.right) + 1,
  };
};
function verdict(m) {
  const fails = [];
  if (m.error) return { pass: false, fails: [m.error] };
  if (m.navH !== 20 || m.wraps) fails.push(`nav ${m.navH}px`);
  const org = m.crumbs[0];
  if (org.w > 160) fails.push(`org ${org.w}px > 160`);
  for (const c of m.crumbs.filter((c) => c.role === "middle")) if (c.truncated && !c.over160) fails.push(`middle "${c.text}" truncated (natural ${c.naturalW}px ≤ 160)`);
  if (!m.lastVisible) fails.push(`last crumb "${m.crumbs[m.crumbs.length - 1].text}" not visible (label ${m.crumbs[m.crumbs.length - 1].labelW}px)`);
  if (m.crumbsOverflowNav || m.crumbsUnderControl) fails.push(`crumbs overflow the nav${m.crumbsUnderControl ? " and draw under the header control at x=" + m.neighbourLeft : ""}`);
  return { pass: fails.length === 0, fails };
}
async function k26(browser, S, who, pk, url) {
  for (const collapsed of [false, true])
    for (const theme of ["light", "dark"]) {
      const rail = collapsed ? "collapsed" : "expanded";
      const key = `${who}-${pk}-${rail}-${theme}`;
      const { ctx, p } = await page(browser, S[who], { width: 1024, height: 768, theme, collapsed }, `K26r|${key}`);
      const resp = await p.goto(url, { waitUntil: "load" });
      await settle(p);
      const m = await p.evaluate(CRUMB_MEASURE);
      m.status = resp && resp.status();
      m.url = p.url().replace(C.APP, "");
      m.htmlTheme = await p.evaluate(() => document.documentElement.className.match(/\bdark\b/) ? "dark" : "light");
      m.verdict = verdict(m);
      M.captures[key] = m;
      const f = path.join(RAW, `k26r__${key}.png`);
      await p.screenshot({ path: f, clip: { x: 0, y: 0, width: 1024, height: 96 } });
      const crumbs = m.crumbs ? m.crumbs.slice(1).map((c) => `${c.text.slice(0, 18)} ${c.w}${c.truncated ? "…" : ""}`).join(" / ") : "?";
      tiles.push([key, f, `${who} ${pk} · rail ${rail} · ${theme} — nav ${m.navH}px · org ${m.crumbs ? m.crumbs[0].w : "?"} · ${crumbs} — ${m.verdict.pass ? "PASS" : "FAIL: " + m.verdict.fails.join("; ")}`]);
      console.log(key, m.navH, m.verdict.pass ? "PASS" : "FAIL " + m.verdict.fails.join("; "));
      await ctx.close();
    }
}
async function firstLink(browser, state, rel, sel, label) {
  const { ctx, p } = await page(browser, state, { width: 1440, height: 900 }, label);
  await p.goto(`${BASE}${rel}`, { waitUntil: "load" });
  await settle(p);
  const hrefs = await p.locator(sel).evaluateAll((as) => as.map((a) => a.getAttribute("href")));
  await ctx.close();
  return hrefs;
}

(async () => {
  const browser = await C.chromium.launch();
  const S = { alice: await C.loginState(browser, "alice"), carol: await C.loginState(browser, "carol") };
  const step = async (name, f) => {
    try { await f(); console.log("OK", name); } catch (e) { console.log("FAIL", name, e.message.split("\n")[0]); (M.failures = M.failures || []).push({ step: name, error: e.message.split("\n")[0].slice(0, 200) }); }
  };
  // Discovery (read-only GETs with Alice's token; UI lists).
  const t = await C.idpToken("alice");
  const fee = C.list(await C.api(t, "/api/invoices?size=100")).find((i) => i.invoiceNumber === "INV-0005");
  const tpl = C.list(await C.api(t, "/api/templates")).sort((a, b) => (b.name || "").length - (a.name || "").length)[0];
  M.pages.feeNote = fee && { id: fee.id, number: fee.invoiceNumber, status: fee.status };
  M.pages.template = tpl && { id: tpl.id, name: tpl.name };
  const uuid = /\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:$|[?#])/;
  const carolMatter = (await firstLink(browser, S.carol, "/matters", '#main-content a[href*="/matters/"]', "disc|carol|matters")).find((h) => uuid.test(h));
  const aliceClient = (await firstLink(browser, S.alice, "/clients", '#main-content a[href*="/clients/"]', "disc|alice|clients")).find((h) => uuid.test(h));
  const checklist = (await firstLink(browser, S.alice, "/settings/matters?tab=checklists", '#main-content a[href*="/settings/matters/checklists/"]', "disc|alice|checklists")).filter((h) => uuid.test(h));
  Object.assign(M.pages, { carolMatter, aliceClient, checklists: checklist.length, checklist: checklist[0] || null });
  console.log("discovered", JSON.stringify(M.pages));

  await step("fee-note", () => k26(browser, S, "alice", "fee-note", `${BASE}/invoices/${fee.id}`));
  await step("alice trust", () => k26(browser, S, "alice", "trust-transactions", `${BASE}/trust-accounting/transactions`));
  await step("carol trust", () => k26(browser, S, "carol", "trust-transactions", `${BASE}/trust-accounting/transactions`));
  await step("carol matter", () => k26(browser, S, "carol", "matter", `${C.APP}${carolMatter}`));
  await step("template edit", () => k26(browser, S, "alice", "template-edit", `${BASE}/settings/documents/templates/${tpl.id}/edit`));
  await step("checklist edit", () => k26(browser, S, "alice", "checklist-edit", `${C.APP}${checklist[0]}/edit`));
  await step("client detail", () => k26(browser, S, "alice", "client-detail", `${C.APP}${aliceClient}`));

  // Sheets (≤ 3): 3-crumb pages; long chains + client detail.
  const tile = (k) => { const x = tiles.find((t) => t[0] === k); return x ? [x[2], x[1]] : [k, null]; };
  const rows = (list) => list.flatMap(([who, pk]) => ["light", "dark"].flatMap((th) => ["expanded", "collapsed"].map((r) => tile(`${who}-${pk}-${r}-${th}`))));
  const sheets = path.join(OUT);
  await C.sheet(browser, path.join(sheets, "sheet-k26r-3crumb-1024.png"), "K-26 re-check @ c4d1f7675 — 1024×768 header strip (top 96 px): cols rail expanded / collapsed; rows page × theme. Caption: wrapper width per crumb, … = truncated", rows([["alice", "fee-note"], ["alice", "trust-transactions"], ["carol", "trust-transactions"], ["carol", "matter"]]), 2);
  await C.sheet(browser, path.join(sheets, "sheet-k26r-long-chain-1024.png"), "K-26 re-check @ c4d1f7675 — 1024×768: template editor (5 crumbs), checklist editor route, client detail (Alice); cols rail expanded / collapsed; rows page × theme", rows([["alice", "template-edit"], ["alice", "checklist-edit"], ["alice", "client-detail"]]), 2);
  await browser.close();

  M.console = { errors: consoleSink.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: consoleSink.filter((c) => c.type === "warning").length, hydration: consoleSink.filter((c) => /hydrat/i.test(c.text)).length, messages: consoleSink };
  const caps = Object.values(M.captures);
  M.summary = { captures: caps.length, pass: caps.filter((m) => m.verdict.pass).length, fail: caps.filter((m) => !m.verdict.pass).length };
  fs.writeFileSync(path.join(RAW, "tiles.json"), JSON.stringify(tiles.map(([k, f, c]) => [k, C.rel(f), c])));
  fs.writeFileSync(path.join(OUT, "measurements.json"), JSON.stringify({ capturedAt: new Date().toISOString(), stack: "e2e :3001, fresh e2e-down/up from worktree-epic-663C @ ab5ea53cd (frontend = c4d1f7675)", ...M }) + "\n");
  console.log("summary", JSON.stringify(M.summary), "console", JSON.stringify({ e: M.console.errors, w: M.console.warnings, h: M.console.hydration }), "failures", (M.failures || []).length);
})();
