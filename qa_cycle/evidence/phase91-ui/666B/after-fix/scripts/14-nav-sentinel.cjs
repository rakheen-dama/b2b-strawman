// 666B OBS-AFTER step 14 (new): prove "no reload" for Record Payment with a window sentinel. 11-interact
// saw one same-URL `framenavigated` event during the INV-0009 save; this sets window.__obsSentinel before
// Confirm and checks it survives (a document load would drop it), plus the hero / totals after.
// Write: one more payment on INV-0009 (now PARTIALLY_PAID): R 100,00 EFT ref "666B-OBS-AFTER-2".
// Usage: PHASE=after node 14-nav-sentinel.cjs → $RAW/sentinel.json
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const P = require("./probes.cjs");
const P2 = require("./probes2.cjs");

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ids = JSON.parse(fs.readFileSync(path.join(L.RAW, "setup-02b.json"), "utf8")).notes;
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  const sink = []; const lab = { label: "sentinel" }; L.watch(p, sink, lab);
  const navs = []; p.on("framenavigated", (fr) => { if (fr === p.mainFrame()) navs.push(fr.url().replace(L.C.APP, "")); });
  const loads = []; p.on("load", () => loads.push(new Date().toISOString()));
  await p.goto(`${L.BASE}/invoices/${ids["sent-overdue"]}`, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(800);
  const hero = async () => { await p.evaluate(P.HELPERS, ""); await p.evaluate(P.HELPERS2); const h = await p.evaluate(P2.HERO); return { badge: h.status.map((s) => s.text), tiles: h.tiles.map((x) => `${x.label}: ${x.value}${x.hint ? ` (${x.hint})` : ""}`), agree: h.agree }; };
  const before = await hero();
  await p.evaluate(() => { window.__obsSentinel = 42; });
  const n0 = navs.length, l0 = loads.length;
  await p.locator("#main-content").getByRole("button", { name: "Record Payment" }).click();
  const form = p.getByTestId("record-payment-form"); await form.waitFor();
  await form.getByLabel("Amount", { exact: true }).fill("100");
  await form.getByPlaceholder(/CHK-12345/).fill("666B-OBS-AFTER-2");
  await form.getByRole("button", { name: "Confirm Payment" }).click();
  await form.waitFor({ state: "detached", timeout: 20000 }); await p.waitForTimeout(1500);
  const sentinel = await p.evaluate(() => window.__obsSentinel ?? null);
  const after = await hero();
  const out = { at: new Date().toISOString(), id: ids["sent-overdue"], before, after, sentinelAfterSave: sentinel, framenavigatedDuringSave: navs.slice(n0), loadEventsDuringSave: loads.length - l0, console: sink };
  fs.writeFileSync(path.join(L.RAW, "sentinel.json"), JSON.stringify(out));
  console.log(JSON.stringify(out));
  await browser.close();
})();
