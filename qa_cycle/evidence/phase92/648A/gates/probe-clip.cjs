// 648A OBS (g) stacked-dialog probe (read-only: both dialogs are cancelled, nothing is submitted). Alice, viewport
// W x 720 (the E2E viewport height) for W in 1280 / 1440 / 1920: Investments › Place Investment › s86(4) › "Add
// investment account". Measures the stacked dialog's scroll container (DialogContent's inner div, which carries the
// className) and whether "Create Account" is in the viewport before / after scrolling it into view. Also records every
// HTTP response >= 400 seen while visiting the client-ledger page (to identify the console 404 from the OBS run).
const W = "/Users/rakheendama/Projects/2026/worktree-epic-648A";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const OUT = `${W}/qa_cycle/evidence/phase92/648A/gates/obs`;
const ORG = "http://localhost:3001/org/e2e-test-org";
const R1 = JSON.parse(fs.readFileSync(`${OUT}/run1/obs-result.json`, "utf8"));
function reach() {
  const outer = document.querySelector("[data-testid='create-trust-account-dialog']");
  const d = outer.firstElementChild; // the div carrying max-h-[90vh] overflow-y-auto
  const b = [...d.querySelectorAll("button")].find((x) => x.innerText.trim() === "Create Account");
  const vh = document.documentElement.clientHeight, vw = document.documentElement.clientWidth;
  const cs = getComputedStyle(d), dr = d.getBoundingClientRect(), b0 = b.getBoundingClientRect();
  const r = { vw, vh, container: { className: d.className.slice(0, 140), overflowY: cs.overflowY, maxHeight: cs.maxHeight, top: Math.round(dr.top), bottom: Math.round(dr.bottom), left: Math.round(dr.left), right: Math.round(dr.right), clientHeight: d.clientHeight, scrollHeight: d.scrollHeight, scrollable: d.scrollHeight > d.clientHeight + 1, insideViewport: dr.top >= -0.5 && dr.bottom <= vh + 0.5 && dr.left >= -0.5 && dr.right <= vw + 0.5 },
    buttonBefore: { top: Math.round(b0.top), bottom: Math.round(b0.bottom), inViewport: b0.top >= 0 && b0.bottom <= vh, insideContainerBox: b0.top >= dr.top && b0.bottom <= dr.bottom } };
  d.scrollTop = d.scrollHeight;
  const b1 = b.getBoundingClientRect();
  r.buttonAfterScroll = { scrollTop: d.scrollTop, top: Math.round(b1.top), bottom: Math.round(b1.bottom), inViewport: b1.top >= 0 && b1.bottom <= vh, insideContainerBox: b1.top >= dr.top - 0.5 && b1.bottom <= dr.bottom + 0.5 };
  r.horizontal = { scrollWidth: d.scrollWidth, clientWidth: d.clientWidth, overflow: d.scrollWidth > d.clientWidth + 1 };
  return r;
}
(async () => {
  const browser = await chromium.launch({ headless: true });
  const res = { at: new Date().toISOString(), widths: [], consoleErrors: [], pageErrors: [], badResponses: [] };
  for (const width of [1280, 1440, 1920]) {
    const ctx = await browser.newContext({ viewport: { width, height: 720 }, timezoneId: "Africa/Johannesburg" });
    const p = await ctx.newPage();
    p.on("console", (m) => { if (m.type() === "error") res.consoleErrors.push({ width, url: p.url(), text: m.text() }); });
    p.on("pageerror", (e) => res.pageErrors.push({ width, text: String(e) }));
    p.on("response", (r) => { if (r.status() >= 400) res.badResponses.push({ width, page: p.url(), url: r.url(), status: r.status() }); });
    await p.goto("http://localhost:3001/mock-login", { waitUntil: "load" });
    await p.locator("#user-select").selectOption("user_e2e_alice");
    await p.getByRole("button", { name: "Sign In", exact: true }).click();
    await p.waitForURL(/\/org\//, { timeout: 60_000 });
    await p.getByRole("heading").first().waitFor({ timeout: 30_000 });
    await p.waitForLoadState("networkidle").catch(() => {});
    await p.goto(`${ORG}/trust-accounting/investments`, { waitUntil: "load" });
    await p.getByTestId("investments-page").waitFor({ timeout: 60_000 });
    await p.getByRole("button", { name: "Place Investment" }).first().click();
    const dlg = p.getByTestId("place-investment-dialog");
    await dlg.waitFor({ timeout: 15_000 });
    await dlg.getByRole("radio", { name: "Section 86(4) — invested on a client's instruction" }).check();
    await dlg.getByRole("button", { name: "Add investment account" }).click();
    const add = p.getByRole("dialog", { name: "Add Investment Account" });
    await add.waitFor({ timeout: 15_000 });
    await p.waitForTimeout(800);
    await p.screenshot({ path: `${OUT}/g-stacked-add-dialog-${width}x720-top.png` });
    const m = { width, reach: await p.evaluate(reach) };
    await p.waitForTimeout(300);
    await p.screenshot({ path: `${OUT}/g-stacked-add-dialog-${width}x720-scrolled.png` });
    m.createAccountVisibleToPlaywright = await add.getByRole("button", { name: "Create Account" }).isVisible();
    m.ok = m.reach.container.insideViewport && m.reach.buttonAfterScroll.inViewport && m.reach.buttonAfterScroll.insideContainerBox && !m.reach.horizontal.overflow;
    await add.getByRole("button", { name: "Cancel" }).click();
    await add.waitFor({ state: "hidden", timeout: 10_000 });
    await dlg.getByRole("button", { name: "Cancel" }).click();
    await dlg.waitFor({ state: "hidden", timeout: 10_000 });
    if (width === 1440) {
      await p.goto(`${ORG}/trust-accounting/client-ledgers/${R1.ids.client}`, { waitUntil: "load" });
      await p.waitForTimeout(3000);
      m.clientLedgerPageText = (await p.locator("main").first().innerText().catch(() => "")).replace(/\s+/g, " ").slice(0, 300);
      await p.goto(`${ORG}/trust-accounting/client-ledgers`, { waitUntil: "load" });
      await p.waitForTimeout(3000);
      m.clientLedgersListPageText = (await p.locator("main").first().innerText().catch(() => "")).replace(/\s+/g, " ").slice(0, 300);
      await p.screenshot({ path: `${OUT}/g-client-ledgers-list-page-1440.png`, fullPage: true });
    }
    res.widths.push(m);
    await ctx.close();
  }
  res.verdict = res.widths.filter((w) => w.width !== 1920).every((w) => w.ok) ? "PASS" : "FAIL";
  fs.writeFileSync(`${OUT}/probe-clip.json`, JSON.stringify(res, null, 2));
  console.log(JSON.stringify({ verdict: res.verdict, widths: res.widths.map((w) => ({ width: w.width, ok: w.ok, reach: w.reach, pw: w.createAccountVisibleToPlaywright, ledger: w.clientLedgerPageText, list: w.clientLedgersListPageText })), badResponses: res.badResponses, consoleErrors: res.consoleErrors, pageErrors: res.pageErrors }, null, 2));
  await browser.close();
})().catch((e) => { console.error("PROBE FAILED", e); process.exit(1); });
