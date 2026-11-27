// 670A exit matrix — shared helpers for Stage M1 (m1-*.cjs) and Stage M2 (m2-*.cjs).
// Re-exports the 661B helpers ($EV/661B/walk/scripts/common.cjs, required, never edited): chromium,
// APP/API/IDP/ORG, USERS, INIT (stores localStorage `kazi-theme` + the __hex/__rgb canvas probes),
// newCtx (viewport, DPR 1, `sidebar_state` cookie, theme init script), idpToken, api, list, lum,
// ratio, hex3. Adds: Bob, /mock-login UI login for every persona (669B pattern), settle(), the
// per-page MEASURE probe, a console watcher that also counts minified React hydration errors, a
// generic target runner (runGroup), a contact-sheet builder (one row per target × persona, one
// column per variant) and append-by-group writers for ../measurements.json and ../console.json.
//
// Record schema (measurements.json = single-line JSON array; one record per page load):
//   { group, surface, key, persona, variant, w, h, storedTheme (what the init script stored, or
//     null), expectDark, url (requested), finalUrl (path+search, org prefix kept), status (HTTP of
//     the document), htmlDark, themeOk (htmlDark === expectDark), mainIdCount (main#main-content),
//     mainCount (<main>), h1InMain (h1 + role=heading level 1 inside #main-content), h1Texts,
//     h1Page (all h1 in the document), overflowPx (documentElement scrollWidth − clientWidth),
//     sidebarState (data-state of the app sidebar or null), notFound, errorBoundary, forbidden
//     (first permission/denied sentence in main or null), head (first 160 chars of main text),
//     act (result of the target's interaction, if any), cErr, cWarn, cHyd (console errors incl.
//     pageerrors / warnings / hydration messages attributed to this load; cErrExpected404 = 1 when the
//     one document-404 console line of an expected 404 was subtracted; cErrKnown = console errors
//     explained by a target's `known` failed-fetch regexes, e.g. the mock-mode /sign-up prefetch), shell (whether the
//     main/h1 rule applies), expect404, ok (verdict), why (failed checks), file (raw tile path),
//     at (ISO time) }
// console.json = single-line JSON array of { group, label, type, text } (label = "group|key|persona|variant";
//   type = error | warning | pageerror | (hydration text) | http-<status> for a failed response, text = "<resourceType> <url>").
const path = require("path");
const fs = require("fs");
const C = require("../../661B/walk/scripts/common.cjs");
C.USERS.bob = { userId: "user_e2e_bob", orgId: "org_e2e_test", orgSlug: C.ORG, orgRole: "org:admin" };

const EXIT = path.resolve(__dirname, "..");
const RAW = process.env.EXIT_RAW || "/tmp/670A-exit-raw";
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${C.APP}/org/${C.ORG}`;
const PA = `${C.APP}/platform-admin`;
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };
const LABELS = { alice: "Alice (Owner)", bob: "Bob (Admin)", carol: "Carol (Member)" };

// Variants: id → viewport + stored theme + expected <html class="dark">.
const VAR = {
  "1024-light": { w: 1024, h: 768, store: "light", expectDark: false },
  "1024-dark": { w: 1024, h: 768, store: "dark", expectDark: true },
  "1440-light": { w: 1440, h: 900, store: "light", expectDark: false },
  "1440-dark": { w: 1440, h: 900, store: "dark", expectDark: true },
  "390-light": { w: 390, h: 844, store: "light", expectDark: false },
  "390-dark": { w: 390, h: 844, store: "dark", expectDark: true },
};
const V4 = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const PL = ["1024-light", "1440-light"];

// Every persona through the /mock-login UI: user select + "Sign In" (669B lib.cjs loginState).
async function loginState(browser, who = "alice") {
  if (who === "anon") return undefined;
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const p = await ctx.newPage();
  await p.goto(`${C.APP}/mock-login`, { waitUntil: "load" });
  await p.locator("#user-select").selectOption({ label: LABELS[who] });
  await p.getByRole("button", { name: "Sign In", exact: true }).click();
  await p.waitForURL(/\/(org\/.*\/dashboard|platform-admin)/, { timeout: 60000 });
  await p.waitForTimeout(4000); // let the mock-auth userinfo fetch finish (S7 gotcha 14)
  const state = await ctx.storageState();
  await ctx.close();
  return state;
}

// Context for a variant id (or an explicit spec { w, h, store, expectDark, colorScheme }).
async function ctxFor(browser, state, v, { collapsed = false } = {}) {
  const s = typeof v === "string" ? VAR[v] : v;
  const extra = { ...EXTRA, ...(s.colorScheme ? { colorScheme: s.colorScheme } : {}) };
  return C.newCtx(browser, state, { width: s.w, height: s.h, theme: s.store, collapsed, extra });
}

async function settle(page) {
  await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
  await page.locator("#main-content h1, h1").first().waitFor({ timeout: 15000 }).catch(() => {});
  await page.getByText(/^Loading/).first().waitFor({ state: "hidden", timeout: 10000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
  await page.waitForTimeout(1500);
}

// Console watcher (661B watch() + minified React hydration codes #418/#423/#425).
const HYD = /hydrat|Minified React error #(418|423|425)/i;
function watch(page, sink, labelRef, group) {
  page.on("console", (m) => {
    const t = m.text();
    if (m.type() === "error" || m.type() === "warning" || HYD.test(t)) sink.push({ group, label: labelRef.label, type: m.type(), text: t.slice(0, 300) });
  });
  page.on("pageerror", (e) => sink.push({ group, label: labelRef.label, type: "pageerror", text: String(e).slice(0, 300) }));
  // Failed sub-resources / documents (≥ 400): the URL behind a "Failed to load resource" console line.
  page.on("response", (r) => { if (r.status() >= 400) sink.push({ group, label: labelRef.label, type: `http-${r.status()}`, text: `${r.request().resourceType()} ${r.url().slice(0, 240)}` }); });
}
const counts = (sink, label) => {
  const m = sink.filter((c) => c.label === label);
  return { cErr: m.filter((c) => c.type === "error" || c.type === "pageerror").length, cWarn: m.filter((c) => c.type === "warning").length, cHyd: m.filter((c) => HYD.test(c.text)).length };
};

// In-page probe: pure DOM reads.
const MEASURE = () => {
  const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
  const de = document.documentElement;
  const main = document.querySelector("main#main-content");
  const h1s = main ? [...main.querySelectorAll("h1,[role=heading][aria-level='1']")] : [];
  let stored = null;
  try { stored = localStorage.getItem("kazi-theme"); } catch (e) {}
  const sb = document.querySelector('[data-slot="sidebar"][data-state]') || document.querySelector("[data-state][data-collapsible]");
  const scope = main || document.body;
  const text = norm(scope.innerText);
  const fm = text.match(/[^.]*\b(permission|not authori[sz]ed|access denied|don't have access|do not have access|only (admins|owners))\b[^.]*\.?/i);
  return {
    finalUrl: location.pathname + location.search,
    title: document.title,
    storedThemeRead: stored,
    htmlDark: de.classList.contains("dark"),
    mainIdCount: document.querySelectorAll("main#main-content").length,
    mainCount: document.querySelectorAll("main").length,
    h1InMain: h1s.length,
    h1Texts: h1s.map((h) => norm(h.textContent).slice(0, 80)),
    h1Page: document.querySelectorAll("h1").length,
    overflowPx: de.scrollWidth - de.clientWidth,
    sidebarState: sb ? sb.getAttribute("data-state") : null,
    notFound: /could not be found|page not found|\b404\b/i.test(norm(document.body.innerText).slice(0, 600)),
    errorBoundary: /Something went wrong/i.test(document.body.innerText),
    forbidden: fm ? norm(fm[0]).slice(0, 200) : null,
    head: text.slice(0, 160),
  };
};

// Target: { key, surface, url, sheet, personas: { alice: [variantIds], carol: [...] }, act?, collapsed?,
//           shell (default true: main#main-content = 1 and h1 = 1 apply), expect404 (persona list) }
async function runGroup(group, targets, { personas = ["alice", "carol"], meta = {} } = {}) {
  const browser = await C.chromium.launch();
  const sink = [];
  const recs = [];
  const gdir = path.join(RAW, group);
  fs.mkdirSync(gdir, { recursive: true });
  for (const who of personas) {
    const state = await loginState(browser, who);
    const combos = [];
    for (const t of targets) for (const v of t.personas[who] || []) combos.push(`${v}|${t.collapsed ? 1 : 0}`);
    for (const combo of [...new Set(combos)]) {
      const [v, col] = combo.split("|");
      const spec = typeof v === "string" && VAR[v] ? VAR[v] : meta.variants[v];
      const ctx = await ctxFor(browser, state, spec, { collapsed: col === "1" });
      const page = await ctx.newPage();
      const lbl = { label: "" };
      watch(page, sink, lbl, group);
      for (const t of targets) {
        if (!(t.personas[who] || []).includes(v) || (t.collapsed ? "1" : "0") !== col) continue;
        lbl.label = `${group}|${t.key}|${who}|${v}`;
        const url = typeof t.url === "function" ? t.url(who) : t.url;
        let resp = null;
        let navErr = null;
        try { resp = await page.goto(url, { waitUntil: "load", timeout: 45000 }); } catch (e) { navErr = e.message.split("\n")[0]; }
        await settle(page);
        let act = null;
        if (t.act) { try { act = (await t.act(page, { who, v })) || "ok"; } catch (e) { act = { error: e.message.split("\n")[0] }; } await page.waitForTimeout(300); }
        const m = await page.evaluate(MEASURE);
        const file = path.join(gdir, `${t.key}--${who}--${v}.png`);
        await page.screenshot({ path: file, fullPage: false });
        await page.waitForTimeout(250);
        const c = counts(sink, lbl.label);
        // An expected 404 document logs one "Failed to load resource … 404" console error: not a defect.
        if ((t.expect404 || []).includes(who) && sink.some((x) => x.label === lbl.label && x.type === "http-404" && x.text.startsWith("document "))) { c.cErrExpected404 = 1; c.cErr = Math.max(0, c.cErr - 1); }
        // Known, pre-existing failed fetches (target.known = [regex on the http text]): each one's
        // "Failed to load resource" console error is counted in cErrKnown instead of cErr.
        const kn = sink.filter((x) => x.label === lbl.label && /^http-/.test(x.type) && (t.known || []).some((re) => re.test(x.text))).length;
        if (kn) { c.cErrKnown = kn; c.cErr = Math.max(0, c.cErr - kn); }
        const shell = t.shell !== false;
        const e404 = (t.expect404 || []).includes(who);
        const status = resp ? resp.status() : null;
        const r = { group, surface: t.surface, key: t.key, persona: who, variant: v, w: spec.w, h: spec.h, storedTheme: spec.store || null, expectDark: !!spec.expectDark, url, status, navErr, ...m, themeOk: m.htmlDark === !!spec.expectDark, act, ...c, shell, expect404: e404, file, at: new Date().toISOString() };
        const why = [];
        if (e404) { if (!m.notFound && status !== 404) why.push("expected 404"); }
        else {
          if (navErr) why.push("nav error");
          if (status && status >= 400) why.push(`status ${status}`);
          if (shell && r.mainIdCount !== 1) why.push(`main#main-content=${r.mainIdCount}`);
          if (shell && r.h1InMain !== 1) why.push(`h1=${r.h1InMain}`);
          if (r.notFound) why.push("not found");
          if (r.errorBoundary) why.push("error boundary");
        }
        if (!r.themeOk) why.push(`theme html.dark=${r.htmlDark} expected ${!!spec.expectDark}`);
        if (r.overflowPx > 0) why.push(`overflow ${r.overflowPx}px`);
        if (c.cErr) why.push(`console errors ${c.cErr}`);
        if (c.cHyd) why.push(`hydration ${c.cHyd}`);
        r.ok = why.length === 0;
        r.why = why;
        recs.push(r);
        console.log(r.ok ? "OK " : "BAD", lbl.label, `st=${status} main=${r.mainIdCount} h1=${r.h1InMain} ovf=${r.overflowPx} dark=${r.htmlDark} e/w/h=${c.cErr}/${c.cWarn}/${c.cHyd}`, why.join("; "), r.finalUrl);
      }
      await ctx.close();
    }
  }
  fs.writeFileSync(path.join(RAW, `${group}.json`), JSON.stringify({ recs, sink }));
  await buildSheets(browser, group, targets, recs);
  await browser.close();
  appendGroup(path.join(EXIT, "measurements.json"), group, recs.map((r) => ({ ...r, file: C.rel(r.file) })));
  appendGroup(path.join(EXIT, "console.json"), group, sink);
  const bad = recs.filter((r) => !r.ok);
  console.log(`GROUP ${group}: ${recs.length} loads, ${bad.length} not ok, console ${sink.length} (err ${sink.filter((c) => c.type === "error" || c.type === "pageerror").length})`);
  return { recs, sink };
}

// Replace this group's records in a single-line JSON array file (so a group can be re-run).
function appendGroup(file, group, items) {
  const cur = fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, "utf8")) : [];
  const next = cur.filter((x) => x.group !== group).concat(items);
  fs.writeFileSync(file, JSON.stringify(next) + "\n");
}

// Contact sheet per target.sheet: rows = (target, persona) in order, columns = the variants used in
// the sheet (V4 order first). Tiles scaled: 1024/390 → 512 px... (w/2), 1440 → 720 px.
const ORDER = ["390-light", "390-dark", "1024-light", "1024-dark", "1440-light", "1440-dark"];
async function buildSheets(browser, group, targets, recs) {
  const sheets = {};
  for (const t of targets) (sheets[t.sheet || t.key] = sheets[t.sheet || t.key] || []).push(t);
  for (const [name, ts] of Object.entries(sheets)) {
    const rs = recs.filter((r) => ts.some((t) => t.key === r.key));
    if (!rs.length) continue;
    const cols = [...new Set(rs.map((r) => r.variant))].sort((a, b) => (ORDER.indexOf(a) + 1 || 99) - (ORDER.indexOf(b) + 1 || 99));
    const rows = [];
    for (const t of ts) for (const who of Object.keys(t.personas)) if ((t.personas[who] || []).length) rows.push([t, who]);
    const cells = [];
    for (const [t, who] of rows)
      for (const v of cols) {
        const r = rs.find((x) => x.key === t.key && x.persona === who && x.variant === v);
        if (!r) { cells.push([`${t.surface} · ${who} · ${v}: not in plan`, null]); continue; }
        const cap = `${r.ok ? "OK" : "CHECK"} · ${t.surface} · ${who} · ${v}${t.collapsed ? " · collapsed" : ""} · ${r.finalUrl.replace(`/org/${C.ORG}`, "")} · st ${r.status} · main ${r.mainIdCount} · h1 ${r.h1InMain} "${(r.h1Texts[0] || "").slice(0, 40)}" · ovf ${r.overflowPx} · dark ${r.htmlDark} · console ${r.cErr}/${r.cWarn}/${r.cHyd}${r.why.length ? " · " + r.why.join("; ") : ""}`;
        cells.push([cap, r.file, r.w >= 1440 ? 720 : Math.round(r.w / 2)]);
      }
    const out = path.join(EXIT, group.split("-")[0] === "settings" ? "settings" : group, `${name}.png`);
    fs.mkdirSync(path.dirname(out), { recursive: true });
    await sheet(browser, out, `670A exit matrix · ${group} · ${name} · rows = target × persona, cols = ${cols.join(" / ")} (viewport tiles; caption: st = HTTP, main = main#main-content count, h1 = level-1 headings in main, ovf = page h-overflow px, console = errors/warnings/hydration)`, cells, cols.length);
    console.log("SHEET", C.rel(out));
  }
}

// Contact sheet (661B sheet() adapted: per-tile width, placeholder for "not in plan").
async function sheet(browser, outFile, title, cells, cols) {
  const html = cells
    .map(([cap, f, tw]) =>
      f && fs.existsSync(f)
        ? `<figure><figcaption style="max-width:${tw}px">${cap}</figcaption><img style="width:${tw}px" src="data:image/png;base64,${fs.readFileSync(f).toString("base64")}"></figure>`
        : `<figure><figcaption style="max-width:300px">${cap}</figcaption><div class="miss">—</div></figure>`,
    )
    .join("");
  const doc = `<html><body style="margin:0;background:#888;font:12px sans-serif"><h2 style="margin:8px;font-size:15px;max-width:2400px">${title}</h2>
  <div style="display:grid;grid-template-columns:repeat(${cols},max-content);gap:8px;padding:8px;align-items:start">${html}</div>
  <style>figure{margin:0;background:#fff}figcaption{padding:4px 6px;font-weight:600;overflow-wrap:anywhere}img{display:block;border-top:1px solid #444}.miss{padding:40px;color:#666}</style></body></html>`;
  const p = await browser.newPage({ viewport: { width: 800, height: 600 } });
  await p.setContent(doc, { waitUntil: "load" });
  await p.screenshot({ path: outFile, fullPage: true });
  await p.close();
}

const readIds = () => { const f = path.join(EXIT, "ids.json"); return fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, "utf8")) : {}; };

module.exports = { ...C, C, EXIT, RAW, BASE, PA, EXTRA, LABELS, VAR, V4, PL, ORDER, loginState, ctxFor, settle, watch, counts, MEASURE, runGroup, appendGroup, buildSheets, sheet, readIds, HYD };
