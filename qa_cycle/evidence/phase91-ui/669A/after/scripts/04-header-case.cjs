// 669A OBS-after: header casing of kit DataTable headers (read-only). For each visible <th>: its label
// and the computed text-transform of the element that holds the label text (the kit sort <button> when
// sortable). Disbursement list + matter tab (669A) vs /reports/profitability (kit local-sort consumer
// merged earlier — shows whether the casing is a kit-level, pre-existing behaviour). Also crops the
// disbursement-list header row for the defect.
// Usage: PHASE=after node 04-header-case.cjs → ../header-case.json
const path = require("path");
const fs = require("fs");
const L = require("../../before/scripts/lib.cjs");
const ID = L.readSeed().ids;
const B = L.BASE;
(async () => {
  const browser = await L.chromium.launch();
  const st = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, st, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const page = await ctx.newPage();
  const out = { at: new Date().toISOString() };
  for (const [k, u, tid] of [["disbursements", `${B}/legal/disbursements`, "disbursement-list"], ["matter-tab", `${B}/matters/${ID.disbursementActiveMatterMatterId}?tab=disbursements`, "disbursement-list"], ["profitability", `${B}/reports/profitability`, null]]) {
    await page.goto(u, { waitUntil: "load" });
    await page.waitForLoadState("networkidle").catch(() => {});
    if (tid) await page.getByTestId(tid).first().waitFor({ timeout: 15000 }).catch(() => {});
    await page.waitForTimeout(1200);
    out[k] = await page.evaluate(() => [...document.querySelectorAll("#main-content table")].filter((t) => t.getBoundingClientRect().width > 0).map((t) => [...t.querySelectorAll("thead th")].filter((h) => getComputedStyle(h).display !== "none").map((h) => {
      const leaf = [...h.querySelectorAll("button,a,span")].find((e) => [...e.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim())) || h;
      return `${h.textContent.trim() || "(blank)"} → ${leaf.tagName.toLowerCase()} text-transform:${getComputedStyle(leaf).textTransform} (th:${getComputedStyle(h).textTransform})`;
    })));
    if (k === "disbursements") await page.locator('[data-testid="disbursement-list"] thead').screenshot({ path: path.join(L.OUT, "crop-defect-disbursement-header-case-1440-light.png") });
  }
  await browser.close();
  fs.writeFileSync(path.join(L.OUT, "header-case.json"), JSON.stringify(out));
  console.log(JSON.stringify(out, null, 1));
})().catch((e) => { console.error("FAIL", e); process.exit(1); });
