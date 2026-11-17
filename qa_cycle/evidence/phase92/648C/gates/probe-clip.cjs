// 648C OBS (f) clip probe (read-only; 647C lesson F-1, 647B/647D bounding-rect method). Signs in as Alice, opens
// Settings › Fees › Trust accounting at 1280 / 1440 (1920 for reference) and measures:
//  1. the Trust Accounts card: every account row and every descendant (name, badges, bank line, the new section label
//     and basis line) must sit inside the card and the viewport; no text element truncated (scrollWidth > clientWidth);
//     no horizontal overflow on the card / row chain;
//  2. the Add Account dialog with type Investment and the basis select OPEN: the dialog inside the viewport, the
//     listbox and each option inside the viewport, no option truncated (wrapping is fine, clipping is not); then a basis
//     picked: the trigger's value text not truncated. The dialog is cancelled — nothing is submitted.
const W = "/Users/rakheendama/Projects/2026/worktree-epic-648C";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const OUT = `${W}/qa_cycle/evidence/phase92/648C/gates/obs`;
const SETTINGS = "http://localhost:3001/org/e2e-test-org/settings/fees?tab=trust-accounting";

function measureIn({ rootSel, opts }) {
  // runs in the page
  const vw = document.documentElement.clientWidth;
  const root = typeof rootSel === "string" ? document.querySelector(rootSel) : rootSel;
  if (!root) return { missing: true };
  const rr = root.getBoundingClientRect();
  const boxL = opts.boxIsViewport ? 0 : rr.left, boxR = opts.boxIsViewport ? vw : rr.right;
  const out = { vw, root: { left: Math.round(rr.left), right: Math.round(rr.right), clientWidth: root.clientWidth, scrollWidth: root.scrollWidth },
    rootInsideViewport: rr.left >= -0.5 && rr.right <= vw + 0.5, offenders: [], truncated: [] };
  for (const el of root.querySelectorAll(opts.only || "*")) {
    const r = el.getBoundingClientRect();
    if (r.width === 0 || r.height === 0) continue;
    const cs = getComputedStyle(el);
    if (cs.visibility === "hidden" || cs.position === "fixed" && el !== root) continue;
    // screen-reader-only text (e.g. the dialog close button's "Close" span: 1 px, clip) is not visible — probe run 2 false positive
    if (el.classList.contains("sr-only") || (r.width <= 1 && r.height <= 1)) continue;
    if (el.closest("[aria-hidden='true']") && !el.closest("[role='dialog']")) continue;
    const label = `${el.tagName.toLowerCase()}${el.getAttribute("data-testid") ? `[${el.getAttribute("data-testid")}]` : ""} "${(el.innerText || "").replace(/\s+/g, " ").trim().slice(0, 60)}"`;
    if (r.left < boxL - 0.5 || r.right > boxR + 0.5 || r.right > vw + 0.5) out.offenders.push({ el: label, left: Math.round(r.left), right: Math.round(r.right) });
    const hasText = [...el.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim());
    if (hasText && el.scrollWidth > el.clientWidth + 1 && cs.overflowX !== "visible") out.truncated.push({ el: label, scrollWidth: el.scrollWidth, clientWidth: el.clientWidth, overflowX: cs.overflowX, textOverflow: cs.textOverflow });
    if (hasText && (cs.textOverflow === "ellipsis") && el.scrollWidth > el.clientWidth + 1) out.truncated.push({ el: label, ellipsis: true });
  }
  out.noHorizontalOverflow = root.scrollWidth <= root.clientWidth + 1;
  out.ok = out.rootInsideViewport && out.offenders.length === 0 && out.truncated.length === 0 && out.noHorizontalOverflow;
  return out;
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const res = { at: new Date().toISOString(), widths: [], consoleErrors: [], pageErrors: [] };
  for (const width of [1280, 1440, 1920]) {
    const ctx = await browser.newContext({ viewport: { width, height: 1100 }, timezoneId: "Africa/Johannesburg" });
    const p = await ctx.newPage();
    p.on("console", (m) => { if (m.type() === "error") res.consoleErrors.push({ width, text: m.text() }); });
    p.on("pageerror", (e) => res.pageErrors.push({ width, text: String(e) }));
    await p.goto("http://localhost:3001/mock-login", { waitUntil: "load" });
    await p.locator("#user-select").selectOption("user_e2e_alice");
    await p.getByRole("button", { name: "Sign In", exact: true }).click();
    await p.waitForURL(/\/org\//, { timeout: 60_000 });
    // settle after login: leaving the dashboard at once aborts the mock-auth userinfo fetch (spurious console error, probe run 2)
    await p.getByRole("heading").first().waitFor({ timeout: 30_000 });
    await p.waitForLoadState("networkidle").catch(() => {});
    await p.goto(SETTINGS, { waitUntil: "load" });
    await p.getByTestId("trust-accounts-section").waitFor({ timeout: 60_000 });
    await p.waitForTimeout(800);
    const m = { width };
    // 1. the card
    m.card = await p.evaluate(measureIn, { rootSel: "[data-testid='trust-accounts-section']", opts: { boxIsViewport: false } });
    m.rows = await p.evaluate(() => [...document.querySelectorAll("[data-testid='trust-accounts-section'] div.divide-y > div")].map((row) => {
      const r = row.getBoundingClientRect(); const s = row.querySelector("[data-testid='trust-account-section']"); const sr = s.getBoundingClientRect();
      return { text: row.innerText.replace(/\s+/g, " ").trim(), left: Math.round(r.left), right: Math.round(r.right), scrollWidth: row.scrollWidth, clientWidth: row.clientWidth,
        sectionLeft: Math.round(sr.left), sectionRight: Math.round(sr.right), sectionInsideRow: sr.left >= r.left - 0.5 && sr.right <= r.right + 0.5,
        sectionLines: [...s.querySelectorAll("p")].map((x) => ({ text: x.innerText, height: Math.round(x.getBoundingClientRect().height), scrollWidth: x.scrollWidth, clientWidth: x.clientWidth })) };
    }));
    await p.screenshot({ path: `${OUT}/f-probe-settings-${width}.png`, fullPage: true });
    await p.getByTestId("trust-accounts-section").screenshot({ path: `${OUT}/f-probe-trust-accounts-${width}-crop.png` });
    // 2. the dialog, Investment, basis select open
    await p.getByTestId("add-trust-account-button").click();
    await p.getByRole("dialog").waitFor({ timeout: 15_000 });
    await p.getByTestId("trust-account-type-select").click();
    await p.getByRole("option", { name: "Investment", exact: true }).click();
    await p.getByTestId("trust-account-basis-select").click();
    await p.getByRole("listbox").waitFor({ timeout: 10_000 });
    await p.waitForTimeout(500);
    m.dialogOpenSelect = await p.evaluate(measureIn, { rootSel: "[role='dialog']", opts: { boxIsViewport: false } });
    m.listbox = await p.evaluate(measureIn, { rootSel: "[role='listbox']", opts: { boxIsViewport: true } });
    m.options = await p.evaluate(() => [...document.querySelectorAll("[role='option']")].map((o) => {
      const r = o.getBoundingClientRect(); const vw = document.documentElement.clientWidth; const span = o.querySelector("span:last-child") || o;
      return { text: o.innerText.replace(/\s+/g, " ").trim(), left: Math.round(r.left), right: Math.round(r.right), height: Math.round(r.height), insideViewport: r.left >= -0.5 && r.right <= vw + 0.5,
        scrollWidth: o.scrollWidth, clientWidth: o.clientWidth, truncated: o.scrollWidth > o.clientWidth + 1 || span.scrollWidth > span.clientWidth + 1 };
    }));
    await p.screenshot({ path: `${OUT}/f-probe-dialog-basis-open-${width}.png` });
    await p.getByRole("option").filter({ hasText: "Section 86(4)" }).click();
    await p.waitForTimeout(400);
    m.trigger = await p.evaluate(() => {
      const t = document.querySelector("[data-testid='trust-account-basis-select']"); const v = t.querySelector("[data-slot='select-value']") || t.querySelector("span");
      const r = t.getBoundingClientRect(); const d = document.querySelector("[role='dialog']").getBoundingClientRect();
      return { text: t.innerText.replace(/\s+/g, " ").trim(), left: Math.round(r.left), right: Math.round(r.right), insideDialog: r.left >= d.left - 0.5 && r.right <= d.right + 0.5,
        valueScrollWidth: v.scrollWidth, valueClientWidth: v.clientWidth, valueTruncated: v.scrollWidth > v.clientWidth + 1 };
    });
    m.dialogPicked = await p.evaluate(measureIn, { rootSel: "[role='dialog']", opts: { boxIsViewport: false } });
    await p.screenshot({ path: `${OUT}/f-probe-dialog-basis-picked-${width}.png` });
    await p.getByRole("dialog").screenshot({ path: `${OUT}/f-probe-dialog-basis-picked-${width}-crop.png` });
    await p.getByRole("dialog").getByRole("button", { name: "Cancel" }).click();
    m.ok = m.card.ok && m.rows.every((r) => r.sectionInsideRow && r.scrollWidth <= r.clientWidth + 1 && r.sectionLines.every((l) => l.scrollWidth <= l.clientWidth + 1))
      && m.dialogOpenSelect.ok && m.listbox.ok && m.options.every((o) => o.insideViewport && !o.truncated) && m.trigger.insideDialog && !m.trigger.valueTruncated && m.dialogPicked.ok;
    res.widths.push(m);
    await ctx.close();
  }
  res.verdict = res.widths.filter((w) => w.width !== 1920).every((w) => w.ok) ? "PASS" : "FAIL";
  fs.writeFileSync(`${OUT}/probe-clip.json`, JSON.stringify(res, null, 2));
  console.log(JSON.stringify({ verdict: res.verdict, widths: res.widths.map((w) => ({ width: w.width, ok: w.ok, card: w.card.ok, dialogOpen: w.dialogOpenSelect.ok, listbox: w.listbox.ok, trigger: w.trigger, options: w.options.map((o) => `${o.left}-${o.right} h${o.height} trunc=${o.truncated}`), cardOffenders: w.card.offenders.length, cardTrunc: w.card.truncated.length, dlgOff: w.dialogOpenSelect.offenders.length, dlgTrunc: w.dialogOpenSelect.truncated.length })), consoleErrors: res.consoleErrors.length, pageErrors: res.pageErrors.length }, null, 2));
  await browser.close();
})().catch((e) => { console.error("PROBE FAILED", e); process.exit(1); });
