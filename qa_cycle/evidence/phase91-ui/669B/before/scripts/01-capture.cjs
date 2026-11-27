// 669B step 1: capture. Reads the ids from before/seed.json (00-seed.cjs, run once). Re-run UNEDITED
// with PHASE=after for the OBS-after (frontend rebuilt from the slice head; do NOT re-seed).
// Read-only on the data: tabs clicked, a search typed (client state only), the member detail Sheet
// and the billing detail Sheet opened and Escaped. Nothing saved, nothing approved or rejected.
// Personas through /mock-login: alice (owner + platform-admin), bob (admin; AI reviews 1440 light),
// carol (member; no AI_REVIEW; not a platform admin → 404 on /platform-admin/*).
// Variants 1024×768 / 1440×900 × light / dark for /org/ pages; platform-admin LIGHT ONLY (outside
// /org/, always light). Theme via localStorage `kazi-theme`, rail expanded, DPR 1, reduced motion,
// Africa/Johannesburg.
// Raw PNGs → /tmp/669B-$PHASE-raw/ (never committed); JSON → $RAW/capture.json (02-assemble.cjs).
// Usage: PHASE=before|after node 01-capture.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const { probe } = require("./probe.cjs");

const S = L.readSeed();
const B = L.BASE;
const V4 = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const L1 = ["1440-light"];
const PL = ["1024-light", "1440-light"];
const TWO = ["1440-light", "1024-dark"];
const tab = (name) => async (page) => { await page.locator("#main-content, body").first().getByRole("tab", { name, exact: true }).first().click(); await page.waitForTimeout(600); };
const NOMATCH = "zzz-669B-no-match";
// key, url, {persona: variants}, act (after settle, before the probe)
const TARGETS = [
  ["team", `${B}/team`, { alice: V4, carol: V4 }],
  ["team-invitations", `${B}/team`, { alice: TWO, carol: TWO }, tab("Pending Invitations")],
  ["ai-reviews", `${B}/ai/reviews`, { alice: V4, bob: L1, carol: V4 }],
  ["ai-reviews-history", `${B}/ai/reviews`, { alice: V4, bob: L1 }, tab("History")],
  ["pa-access", `${L.PA}/access-requests`, { alice: PL, carol: L1 }],
  ["pa-access-All", `${L.PA}/access-requests`, { alice: PL }, tab("All")],
  ["pa-access-Approved", `${L.PA}/access-requests`, { alice: PL }, tab("Approved")],
  ["pa-access-Rejected", `${L.PA}/access-requests`, { alice: PL }, tab("Rejected")],
  ["pa-billing", `${L.PA}/billing`, { alice: PL, carol: L1 }],
  ["pa-billing-nomatch", `${L.PA}/billing`, { alice: PL }, async (page) => { await page.getByPlaceholder("Search by org name...").fill(NOMATCH); await page.waitForTimeout(600); }],
  ["pa-billing-sheet", `${L.PA}/billing`, { alice: PL }, async (page) => { await page.getByRole("button", { name: "Open", exact: true }).first().click(); await page.getByRole("dialog").first().waitFor({ timeout: 10000 }); await page.waitForTimeout(800); }],
  ["pa-demo", `${L.PA}/demo`, { alice: PL, carol: L1 }],
];

const cons = [];
const out = { phase: L.PHASE, at: new Date().toISOString(), seedIds: S.ids, caps: {}, pages: {}, interactions: {}, acts: {} };
const rel = (page) => { const u = new URL(page.url()); return u.pathname.replace(`/org/${L.ORG}`, "") + u.search; };
async function settle(page) {
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.locator("h1").first().waitFor({ timeout: 15000 }).catch(() => {});
  await page.getByText(/^Loading/).first().waitFor({ state: "hidden", timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(700);
}

async function capture(browser, who, state) {
  const t = await L.idpToken(who);
  const cap = (await L.apiGet(t, "/api/me/capabilities")).json;
  out.caps[who] = cap && { role: cap.role, isAdmin: cap.isAdmin, isOwner: cap.isOwner, aiReview: (cap.capabilities || []).includes("AI_REVIEW") };
  const variants = [...new Set(TARGETS.flatMap(([, , p]) => p[who] || []))];
  for (const v of variants) {
    const [w, theme] = v.split("-");
    const ctx = await L.newCtx(browser, state, { width: +w, height: w === "1024" ? 768 : 900, theme, extra: L.EXTRA });
    const page = await ctx.newPage();
    const lbl = { label: "" };
    L.watch(page, cons, lbl);
    for (const [key, url, per, act] of TARGETS) {
      if (!(per[who] || []).includes(v)) continue;
      lbl.label = `${who} ${key} ${v}`;
      const resp = await page.goto(url, { waitUntil: "load" });
      await settle(page);
      if (act) { try { await act(page); out.acts[`${key}/${who}/${v}`] = "ok"; } catch (e) { out.acts[`${key}/${who}/${v}`] = e.message.split("\n")[0]; } }
      const m = await page.evaluate(probe);
      m.httpStatus = resp ? resp.status() : null;
      m.finalUrl = rel(page);
      (out.pages[key] = out.pages[key] || {})[`${who}/${v}`] = m;
      await page.screenshot({ path: path.join(L.RAW, `${key}--${who}--${v}.png`), fullPage: true });
    }
    await ctx.close();
  }
}

// Team: the member row → MemberDetailPanel, by click and by keyboard (Tab from the Members tab), and
// the row hover colours (Alice 1440 light + 1024 dark, Carol 1440 light + 1024 dark).
async function interact(browser, state, who, v) {
  const [w, theme] = v.split("-");
  const ctx = await L.newCtx(browser, state, { width: +w, height: w === "1024" ? 768 : 900, theme, extra: L.EXTRA });
  const page = await ctx.newPage();
  const lbl = { label: `${who} interactions ${v}` };
  L.watch(page, cons, lbl);
  const I = (out.interactions[`${who}/${v}`] = {});
  const go = async (u) => { await page.goto(u, { waitUntil: "load" }); await settle(page); };
  const safe = async (k, fn) => { try { I[k] = await fn(); } catch (e) { I[k] = { ...(I[k] || {}), error: e.message.split("\n")[0] }; } };
  const shot = async (name) => { const f = path.join(L.RAW, `int-${name}--${who}--${v}.png`); await page.screenshot({ path: f, fullPage: false }); return f; };
  const dialogs = () => page.evaluate(() => [...document.querySelectorAll('[role="dialog"],[role="alertdialog"]')].filter((d) => d.getBoundingClientRect().width > 0).map((d) => ({ role: d.getAttribute("role"), text: d.innerText.replace(/\s+/g, " ").trim().slice(0, 200), w: Math.round(d.getBoundingClientRect().width) })));
  const ROW = '[data-testid="member-row-bob@e2e-test.local"]';

  await safe("rowClick", async () => {
    await go(`${B}/team`);
    const r = { start: rel(page) };
    const row = page.locator(ROW);
    r.rowAttrs = await row.evaluate((e) => ({ tag: e.tagName.toLowerCase(), role: e.getAttribute("role"), tabindex: e.getAttribute("tabindex"), cursor: getComputedStyle(e).cursor, cls: e.className }));
    await row.locator("td").nth(1).click();
    await page.waitForTimeout(900);
    r.dialogs = await dialogs();
    r.opened = r.dialogs.length > 0;
    r.url = rel(page);
    r.file = await shot("member-click");
    if (r.opened) { await page.keyboard.press("Escape"); await page.waitForTimeout(600); r.closedAfterEscape = (await dialogs()).length === 0; }
    return r;
  });
  // Hover on Bob's row (the pointer rests on the Email cell): row background + cell text contrast.
  // Before: the admin row's class string ends "dark:hover:bg-slate-900/30cursor-pointer" (no space),
  // so its dark hover class is lost and the light hover:bg-slate-50 applies in dark mode.
  await safe("rowHover", async () => {
    await go(`${B}/team`);
    const row = page.locator(ROW);
    await row.locator("td").nth(1).hover();
    await page.waitForTimeout(500);
    const r = await row.evaluate((tr) => {
      const cv = document.createElement("canvas"); cv.width = cv.height = 1; const cx = cv.getContext("2d", { willReadFrequently: true });
      const px = (ls) => { cx.fillStyle = "#fff"; cx.fillRect(0, 0, 1, 1); for (const c of ls) { cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); } const d = cx.getImageData(0, 0, 1, 1).data; return [d[0], d[1], d[2]]; };
      const layers = (el) => { const ls = []; for (let e = el; e; e = e.parentElement) { const b = getComputedStyle(e).backgroundColor; if (b && b !== "rgba(0, 0, 0, 0)" && b !== "transparent") { ls.unshift(b); if (px([b]).join() === px(["#000", b]).join()) break; } } return ls; };
      const lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
      const ratio = (a, b) => { const [l1, l2] = [lum(a), lum(b)].sort((x, y) => y - x); return +((l1 + 0.05) / (l2 + 0.05)).toFixed(2); };
      const hex = (a) => "#" + a.map((v) => v.toString(16).padStart(2, "0")).join("");
      const col = (el) => { const ls = layers(el); const bg = px(ls); const fg = px([...ls, getComputedStyle(el).color]); return `${hex(fg)} on ${hex(bg)} ${ratio(fg, bg)}`; };
      const leaves = [...tr.querySelectorAll("td *, td")].filter((e) => [...e.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim()));
      return { rowBg: getComputedStyle(tr).backgroundColor, rowCls: tr.className, cells: leaves.map((e) => `${e.textContent.trim().slice(0, 30)}: ${col(e)}`) };
    });
    const f = path.join(L.RAW, `int-member-hover--${who}--${v}.png`);
    await page.locator("#main-content table").first().screenshot({ path: f });
    r.file = f;
    await page.mouse.move(0, 0);
    return r;
  });
  await safe("rowKeyboard", async () => {
    await go(`${B}/team`);
    const r = { steps: [] };
    await page.getByRole("tab", { name: "Members", exact: true }).focus();
    for (let i = 0; i < 25; i++) {
      await page.keyboard.press("Tab");
      const a = await page.evaluate(() => { const e = document.activeElement; const row = e && e.closest('[data-testid^="member-row-"]'); return { tag: e.tagName.toLowerCase(), role: e.getAttribute("role"), testid: e.dataset.testid || null, text: (e.innerText || e.getAttribute("aria-label") || "").replace(/\s+/g, " ").trim().slice(0, 50), inRow: row ? row.dataset.testid : null, focusVisibleOutline: getComputedStyle(e).outlineStyle + " " + getComputedStyle(e).outlineWidth, boxShadow: getComputedStyle(e).boxShadow.slice(0, 60) }; });
      r.steps.push(a);
      if (a.inRow) break;
    }
    const last = r.steps[r.steps.length - 1];
    r.reachedRow = last && last.inRow;
    r.tabsToRow = r.reachedRow ? r.steps.length : null;
    if (r.reachedRow) {
      r.focusFile = await shot("member-focus");
      await page.keyboard.press("Enter");
      await page.waitForTimeout(900);
      r.dialogsAfterEnter = await dialogs();
      r.openedByEnter = r.dialogsAfterEnter.length > 0;
      r.file = await shot("member-enter");
      if (r.openedByEnter) { await page.keyboard.press("Escape"); await page.waitForTimeout(600); r.focusAfterEscape = await page.evaluate(() => { const e = document.activeElement; return `${e.tagName.toLowerCase()} ${e.dataset.testid || ""} ${(e.closest('[data-testid^="member-row-"]') || { dataset: {} }).dataset.testid || ""}`.trim(); }); }
    }
    return r;
  });
  await ctx.close();
}

(async () => {
  const browser = await L.chromium.launch();
  const states = {};
  for (const who of ["alice", "bob", "carol"]) {
    states[who] = await L.loginState(browser, who);
    await capture(browser, who, states[who]);
    console.log("captured pages", who);
  }
  await interact(browser, states.alice, "alice", "1440-light");
  await interact(browser, states.alice, "alice", "1024-dark");
  await interact(browser, states.carol, "carol", "1440-light");
  await interact(browser, states.carol, "carol", "1024-dark");
  console.log("interactions done");
  const t = await L.idpToken("alice");
  out.api = { accessRequests: L.list((await L.apiGet(t, "/api/platform-admin/access-requests")).json).map((r) => `${r.organizationName}: ${r.status}`), aiGates: ((await L.apiGet(t, "/api/ai/gates?size=50")).json || {}).page, members: L.list((await L.apiGet(t, "/api/members")).json).length };
  out.console = cons;
  await browser.close();
  fs.writeFileSync(path.join(L.RAW, "capture.json"), JSON.stringify(out));
  console.log("pages", Object.values(out.pages).reduce((n, x) => n + Object.keys(x).length, 0), "console", cons.length, "acts", JSON.stringify(out.acts));
  console.log("interactions", JSON.stringify(out.interactions).slice(0, 3000));
})().catch((e) => { console.error("CAPTURE FAIL", e); process.exit(1); });
