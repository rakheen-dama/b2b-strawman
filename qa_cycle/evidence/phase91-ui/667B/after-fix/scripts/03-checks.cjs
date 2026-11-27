// 667B OBS after-fix point checks (NEW, read-only page loads; no clicks that write). Alice, 1024×768
// light + dark, plus Carol 1024 light for the monthly list:
//  - open statements: the Period cell's line boxes (each line's text — the range must break between
//    its two dates, never inside one), Discard scrolled into view (Playwright) → its box vs the
//    scroller, an elementFromPoint hit-test at its centre and a Playwright trial click (actionability
//    only, nothing is clicked) — the layout probe's hit is null when the button is below the fold;
//  - monthly list (31 Oct): `monthly-list-creditors` is the <table> itself, its <tfoot> text, the
//    after-lock table, and the "a change of R 500,00" sentence.
// Usage (from this dir): PHASE=after-fix node 03-checks.cjs → ../checks.json
const path = require("path");
const fs = require("fs");
const L = require("../../before/scripts/lib.cjs");

const S = L.readSeed();
const R = `${L.TA}/reconciliation`, ML = `${R}/lists/${S.ids.monthlyListId}`;

function openStatements() {
  const t = document.querySelector('[data-testid="open-statements-table"]');
  let sc = t.parentElement; while (sc && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
  const s = sc.getBoundingClientRect();
  const ths = [...t.querySelectorAll("thead th")].map((th) => th.textContent.trim());
  const td = t.querySelectorAll("tbody tr")[0].querySelectorAll("td")[ths.indexOf("Period")];
  // line boxes: group the text's character rects by top
  const byTop = new Map();
  const w = document.createTreeWalker(td, NodeFilter.SHOW_TEXT); let n;
  while ((n = w.nextNode())) for (let i = 0; i < n.length; i++) { const rg = document.createRange(); rg.setStart(n, i); rg.setEnd(n, i + 1); const r = rg.getBoundingClientRect(); if (!r.width) continue; const k = Math.round(r.top); byTop.set(k, (byTop.get(k) || "") + n.data[i]); }
  const b = t.querySelector('[data-testid^="discard-statement-"]');
  const br = b.getBoundingClientRect();
  const hitEl = document.elementFromPoint(br.left + br.width / 2, br.top + br.height / 2);
  return {
    scroller: { clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, l: Math.round(s.left), r: Math.round(s.right) },
    periodWhiteSpace: getComputedStyle(td).whiteSpace, periodLines: [...byTop.entries()].sort((x, y) => x[0] - y[0]).map(([, v]) => v.replace(/\s+/g, " ").trim()),
    discard: { text: b.innerText.trim(), l: Math.round(br.left), r: Math.round(br.right), rightGapPx: Math.round(s.right - br.right), fullyInView: br.left >= s.left && br.right <= s.right, hit: !!hitEl && b.contains(hitEl) },
  };
}
function monthlyList() {
  const c = document.querySelector('[data-testid="monthly-list-creditors"]');
  const al = document.querySelector('[data-testid="monthly-list-after-lock"]');
  const alT = al && (al.matches("table") ? al : al.querySelector("table"));
  const main = document.querySelector("#main-content");
  return {
    creditorsTag: c && c.tagName, creditorsTfoot: c && c.querySelector("tfoot") && c.querySelector("tfoot").innerText.replace(/\s+/g, " ").trim(), creditorsBodyRows: c && c.querySelectorAll("tbody tr").length,
    afterLockTag: al && al.tagName, afterLockRows: alT && [...alT.querySelectorAll("tbody tr")].map((r) => r.innerText.replace(/\s+/g, " ").trim()),
    changeSentence: (main.innerText.match(/[^.\n]*a change of R[^.\n]*/) || [null])[0],
  };
}

(async () => {
  const browser = await L.chromium.launch();
  const res = { at: new Date().toISOString(), runs: {} };
  for (const [who, theme] of [["alice", "light"], ["alice", "dark"], ["carol", "light"]]) {
    const st = await L.loginState(browser, who);
    const ctx = await L.newCtx(browser, st, { width: 1024, height: 768, theme, extra: L.EXTRA });
    const page = await ctx.newPage();
    const r = {};
    if (who === "alice") {
      await page.goto(R, { waitUntil: "load" }); await page.waitForLoadState("networkidle").catch(() => {}); await page.waitForTimeout(600);
      // Playwright scrolls the button into view (the app scrolls an inner container with smooth
      // scrolling, so an in-page scrollIntoView + synchronous hit-test reads the pre-scroll box) and
      // runs its actionability check (visible, stable, receives pointer events) without clicking.
      const discard = page.getByTestId(/^discard-statement-/).first();
      await discard.scrollIntoViewIfNeeded(); await page.waitForTimeout(800);
      r.openStatements = await page.evaluate(openStatements);
      try { await discard.click({ trial: true, timeout: 5000 }); r.openStatements.discardTrialClick = "ok"; } catch (e) { r.openStatements.discardTrialClick = "FAIL " + e.message.split("\n")[0]; }
    }
    await page.goto(ML, { waitUntil: "load" }); await page.waitForLoadState("networkidle").catch(() => {}); await page.waitForTimeout(600);
    r.monthlyList = await page.evaluate(monthlyList);
    res.runs[`${who}/1024-${theme}`] = r;
    await ctx.close();
  }
  await browser.close();
  fs.writeFileSync(path.join(L.OUT, "checks.json"), JSON.stringify(res));
  console.log(JSON.stringify(res, null, 1));
})().catch((e) => { console.error("CHECKS FAIL", e); process.exit(1); });
