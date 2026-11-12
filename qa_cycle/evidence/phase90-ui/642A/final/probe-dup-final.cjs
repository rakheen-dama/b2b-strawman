// 642A final: characterise transient duplicate [data-testid=clients-table] during a full page load.
// Per width: N full loads of /clients; a 10 ms interval sampler plus a MutationObserver record the max
// count, the overlap duration and, at the first moment with >= 2 tables, where each copy lives
// (visible? inside a [hidden] div? which ancestor ids).
// Usage: node probe-dup-final.cjs [N]   → final/probe-dup.json
const path = require("path");
const fs = require("fs");
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/worktree-epic-642A/frontend/node_modules/@playwright/test"
);
const BASE = "http://localhost:3001";
const LIST = "/org/e2e-test-org/clients";
const N = Number(process.argv[2] || 10);
const FRESH = process.argv[3] === "fresh"; // fresh context + dark theme per load
const OUTF = FRESH ? "probe-dup-fresh.json" : "probe-dup.json";

function initSampler() {
  if (!location.pathname.endsWith("/clients")) return;
  const t0 = performance.now();
  const S = (window.__dup = { max: 0, firstMultiAt: null, lastMultiAt: null, detail: null, samples: 0 });
  const describe = () =>
    [...document.querySelectorAll('[data-testid="clients-table"]')].map((t) => {
      const hiddenAnc = t.closest("[hidden]");
      const ids = [];
      for (let n = t.parentElement; n && ids.length < 6; n = n.parentElement) if (n.id) ids.push(n.id);
      return {
        visible: t.getClientRects().length > 0,
        insideHiddenDiv: !!hiddenAnc,
        hiddenDivId: hiddenAnc ? hiddenAnc.id : null,
        ancestorIds: ids,
        inMainContent: !!t.closest("#main-content"),
        rows: t.querySelectorAll('[data-testid="client-row"]').length,
      };
    });
  const check = () => {
    const n = document.querySelectorAll('[data-testid="clients-table"]').length;
    const t = Math.round(performance.now() - t0);
    if (n > S.max) S.max = n;
    if (n >= 2) {
      if (S.firstMultiAt === null) { S.firstMultiAt = t; S.detail = describe(); }
      S.lastMultiAt = t;
    }
  };
  const id = setInterval(() => { S.samples++; check(); if (performance.now() - t0 > 5000) clearInterval(id); }, 10);
  const mo = new MutationObserver(check);
  mo.observe(document.documentElement, { childList: true, subtree: true });
  setTimeout(() => mo.disconnect(), 5000);
}

(async () => {
  const browser = await chromium.launch();
  const out = { startedAt: new Date().toISOString(), N, widths: {} };
  try {
    for (const [w, h] of [[390, 844], [768, 1024], [1440, 900]]) {
      const login = async () => {
        const context = await browser.newContext({ viewport: { width: w, height: h } });
        if (FRESH) await context.addInitScript(() => { try { localStorage.setItem("kazi-theme", "dark"); } catch (e) {} });
        await context.addInitScript(initSampler);
        const page = await context.newPage();
        await page.goto(`${BASE}/mock-login`);
        await page.locator("#user-select").selectOption("user_e2e_alice");
        await page.getByRole("button", { name: "Sign In" }).click();
        await page.waitForURL("**/dashboard", { timeout: 90_000 });
        return { context, page };
      };
      let { context, page } = await login();
      const runs = [];
      for (let i = 0; i < N; i++) {
        if (FRESH && i > 0) { await context.close(); ({ context, page } = await login()); }
        await page.goto(`${BASE}${LIST}`, { waitUntil: "commit" });
        await page.waitForTimeout(5300);
        runs.push(await page.evaluate(() => window.__dup));
      }
      out.widths[`${w}x${h}`] = {
        loadsWithDuplicate: runs.filter((r) => r && r.max >= 2).length,
        loads: runs.length,
        runs,
      };
      await context.close();
      fs.writeFileSync(path.join(__dirname, OUTF), JSON.stringify(out, null, 2));
    }
  } finally {
    out.finishedAt = new Date().toISOString();
    fs.writeFileSync(path.join(__dirname, OUTF), JSON.stringify(out, null, 2));
    await browser.close();
  }
})().catch((e) => { console.error(e); process.exit(1); });
