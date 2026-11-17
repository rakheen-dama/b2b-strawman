// 647C gates round 2 — F-1 re-probe (read-only), run 2: line count over text nodes only. Adapted from gates/probe-clip.cjs.
// Signs in as Alice, opens Other trust creditors at 1280/1440/1920 x 1100 and measures, against the
// table's overflow-x-auto wrapper: table width vs wrapper clientWidth; the right edge of the Running balance
// header and of every fund-ledger-balance span/cell vs the wrapper's VISIBLE right edge (wrapper rect ∩ every
// clipping ancestor ∩ viewport); whether Amount / Running balance text is on one line; whether the Type badges
// are readable (not clipped, inside the wrapper, no mid-word breaks). Takes section + column crops. No state change.
const W = "/Users/rakheendama/Projects/2026/worktree-epic-647C";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const OUT = `${W}/qa_cycle/evidence/phase92/647C/gates-r2/obs`;
(async () => {
  const browser = await chromium.launch({ headless: true });
  const res = { at: new Date().toISOString(), widths: [], consoleErrors: [], pageErrors: [] };
  for (const width of [1280, 1440, 1920]) {
    const ctx = await browser.newContext({ viewport: { width, height: 1100 }, timezoneId: "Africa/Johannesburg" });
    const p = await ctx.newPage();
    p.on("console", (m) => { if (m.type() === "error") res.consoleErrors.push({ width, url: p.url(), text: m.text() }); });
    p.on("pageerror", (e) => res.pageErrors.push({ width, url: p.url(), text: String(e) }));
    await p.goto("http://localhost:3001/mock-login", { waitUntil: "load" });
    await p.locator("#user-select").selectOption("user_e2e_alice");
    await p.getByRole("button", { name: "Sign In", exact: true }).click();
    await p.waitForURL(/\/org\//, { timeout: 60_000 });
    await p.goto("http://localhost:3001/org/e2e-test-org/trust-accounting/other-creditors", { waitUntil: "load" });
    await p.getByTestId("fund-ledger-row").first().waitFor({ timeout: 60_000 });
    await p.waitForTimeout(800);
    const m = await p.evaluate(() => {
      const R = (r) => ({ left: Math.round(r.left * 10) / 10, right: Math.round(r.right * 10) / 10, top: Math.round(r.top), height: Math.round(r.height) });
      const lines = (el) => {
        // text nodes only (run 1 counted the aria-hidden arrow svg in Amount as a second "line")
        const tw = document.createTreeWalker(el, NodeFilter.SHOW_TEXT); const tops = new Set();
        for (let n = tw.nextNode(); n; n = tw.nextNode()) { if (!n.textContent.trim()) continue;
          const rg = document.createRange(); rg.selectNodeContents(n);
          for (const r of rg.getClientRects()) if (r.width > 0) tops.add(Math.round(r.top)); }
        return tops.size;
      };
      const tEl = document.querySelector("[data-testid='fund-ledger-table']");
      const t = tEl.tagName === "TABLE" ? tEl : tEl.querySelector("table");
      let wrapper = t.parentElement;
      while (wrapper && !getComputedStyle(wrapper).overflowX.match(/auto|scroll|hidden/)) wrapper = wrapper.parentElement;
      const wr = wrapper.getBoundingClientRect();
      // visible horizontal extent: wrapper's content box ∩ clipping ancestors ∩ viewport
      const cs = getComputedStyle(wrapper);
      let visLeft = wr.left + wrapper.clientLeft, visRight = wr.left + wrapper.clientLeft + wrapper.clientWidth;
      const clippers = [];
      for (let el = wrapper.parentElement; el && el !== document.documentElement; el = el.parentElement) {
        const ox = getComputedStyle(el).overflowX;
        if (ox !== "visible") { const r = el.getBoundingClientRect(); clippers.push({ tag: el.tagName, cls: String(el.className).slice(0, 80), overflowX: ox, right: Math.round(r.right) });
          visLeft = Math.max(visLeft, r.left + el.clientLeft); visRight = Math.min(visRight, r.left + el.clientLeft + el.clientWidth); }
      }
      visRight = Math.min(visRight, document.documentElement.clientWidth);
      const ths = [...t.querySelectorAll("thead th")];
      const idx = (label) => ths.findIndex((th) => th.innerText.trim().toLowerCase() === label.toLowerCase());
      const iAmt = idx("Amount"), iType = idx("Type"), iBal = idx("Running balance"), iCharge = idx("Bank charge");
      const header = ths.map((th) => ({ text: th.innerText.trim(), ...R(th.getBoundingClientRect()), lines: lines(th), scrollW: th.scrollWidth, clientW: th.clientWidth }));
      const balHeader = header[iBal];
      const rows = [...t.querySelectorAll("[data-testid='fund-ledger-row']")].map((tr) => {
        const tds = tr.querySelectorAll("td");
        const bal = tr.querySelector("[data-testid='fund-ledger-balance']");
        const balTd = bal.closest("td");
        const amtTd = tds[iAmt];
        const badges = [...tds[iType].querySelectorAll("[data-slot='badge'], span[class*='rounded']")].filter((b) => b.innerText.trim());
        const badgeInfo = badges.map((b) => { const r = b.getBoundingClientRect(); const s = getComputedStyle(b);
          return { text: b.innerText.trim(), ...R(r), lines: lines(b), scrollW: b.scrollWidth, clientW: b.clientWidth, scrollH: b.scrollHeight, clientH: b.clientHeight,
            whiteSpace: s.whiteSpace, overflow: s.overflow, textOverflow: s.textOverflow, overflowWrap: s.overflowWrap, wordBreak: s.wordBreak,
            insideVisible: r.left >= visLeft - 0.5 && r.right <= visRight + 0.5,
            // a mid-word break would show as a line whose last char is a letter and the next line starts with a letter of the same word
            words: b.innerText.trim().split(/\s+/).length } });
        const chargeTd = tds[iCharge];
        return {
          balanceText: bal.innerText.trim(), balanceSpan: R(bal.getBoundingClientRect()), balanceTd: R(balTd.getBoundingClientRect()), balanceLines: lines(bal),
          balTdScrollW: balTd.scrollWidth, balTdClientW: balTd.clientWidth,
          amountText: amtTd.innerText.trim(), amount: R(amtTd.getBoundingClientRect()), amountLines: lines(amtTd), amtTdScrollW: amtTd.scrollWidth, amtTdClientW: amtTd.clientWidth,
          chargeText: chargeTd.innerText.trim(), chargeLines: lines(chargeTd), charge: R(chargeTd.getBoundingClientRect()),
          badges: badgeInfo,
        };
      });
      const tr = t.getBoundingClientRect();
      return {
        tableWidth: Math.round(tr.width * 10) / 10, tableOffsetWidth: t.offsetWidth, tableRight: Math.round(tr.right * 10) / 10,
        wrapper: { cls: String(wrapper.className), overflowX: cs.overflowX, clientWidth: wrapper.clientWidth, scrollWidth: wrapper.scrollWidth, ...R(wr) },
        visibleLeft: Math.round(visLeft * 10) / 10, visibleRight: Math.round(visRight * 10) / 10, clippers,
        docScrollWidth: document.documentElement.scrollWidth, docClientWidth: document.documentElement.clientWidth,
        header, balHeader, rows,
      };
    });
    m.width = width;
    const maxBalRight = Math.max(...m.rows.map((r) => Math.max(r.balanceSpan.right, r.balanceTd.right)));
    m.checks = {
      tableFitsWrapper: m.tableOffsetWidth <= m.wrapper.clientWidth && m.wrapper.scrollWidth <= m.wrapper.clientWidth,
      balanceHeaderInsideVisible: m.balHeader.right <= m.visibleRight + 0.5 && m.balHeader.text.toUpperCase() === "RUNNING BALANCE",
      everyBalanceInsideVisible: m.rows.every((r) => r.balanceSpan.right <= m.visibleRight + 0.5 && r.balanceTd.right <= m.visibleRight + 0.5),
      balanceOneLine: m.rows.every((r) => r.balanceLines === 1 && r.balTdScrollW <= r.balTdClientW),
      amountOneLine: m.rows.every((r) => r.amountLines === 1 && r.amtTdScrollW <= r.amtTdClientW),
      badgesReadable: m.rows.every((r) => r.badges.length > 0 && r.badges.every((b) => b.insideVisible && b.scrollW <= b.clientW && b.scrollH <= b.clientH && b.textOverflow !== "ellipsis")),
      noPageHorizontalOverflow: m.docScrollWidth <= m.docClientWidth,
    };
    m.maxBalanceRight = maxBalRight;
    m.allPass = Object.values(m.checks).every(Boolean);
    await p.getByTestId("fund-ledger-section").scrollIntoViewIfNeeded();
    await p.screenshot({ path: `${OUT}/22-probe-fund-ledger-${width}.png`, fullPage: false });
    await p.getByTestId("fund-ledger-section").screenshot({ path: `${OUT}/22-probe-fund-ledger-${width}-crop.png` });
    // right-hand column crop: Amount .. Running balance, header to last row, clipped to the wrapper's visible box
    const tb = await p.getByTestId("fund-ledger-table").boundingBox();
    const amtLeft = m.header[m.header.findIndex((h) => h.text.toUpperCase() === "AMOUNT")].left;
    await p.screenshot({ path: `${OUT}/22-probe-fund-ledger-${width}-rightcols.png`, fullPage: true,
      clip: { x: Math.max(0, amtLeft - 8), y: tb.y + (await p.evaluate(() => window.scrollY)), width: Math.min(m.visibleRight + 24, width) - Math.max(0, amtLeft - 8), height: tb.height } });
    res.widths.push(m);
    await ctx.close();
  }
  res.verdict = res.widths.every((w) => w.allPass) ? "F-1 FIXED (all checks true at all widths)" : "F-1 STILL OPEN (see checks)";
  fs.writeFileSync(`${OUT}/probe-clip.json`, JSON.stringify(res, null, 2));
  for (const w of res.widths) console.log(w.width, "table", w.tableOffsetWidth, "wrapper", w.wrapper.clientWidth, "scrollW", w.wrapper.scrollWidth, "visR", w.visibleRight, "hdrR", w.balHeader.right, "maxBalR", w.maxBalanceRight, JSON.stringify(w.checks));
  console.log(res.verdict, "console", res.consoleErrors.length, "page", res.pageErrors.length);
  await browser.close();
})().catch((e) => { console.error("PROBE FAILED", e); process.exit(1); });
