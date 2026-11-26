// 665C OBS after-fix step 9c (new, read-only, diagnostic): on checklists/{id} PLATFORM "Generic Client
// Onboarding" at 1024×768 light, the items-table scroller is still 446/487 after the fix. This records
// each <th>'s width / white-space, then sets `white-space: normal` on the <th>s IN THE PAGE ONLY (an
// in-memory style, nothing saved) and re-measures, to test the hypothesis that the nowrap header
// "DOCUMENT REQUIRED" (TableHead `whitespace-nowrap`) sets the table's min width. Prints only.
const L = require("./lib.cjs");
(async () => {
  const a = await L.idpToken("alice");
  const cl = L.list((await L.apiGet(a, "/api/checklist-templates")).json).find((x) => x.name === "Generic Client Onboarding");
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1024, height: 768, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  await p.goto(`${L.BASE}/settings/matters/checklists/${cl.id}`, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(400);
  const r = await p.evaluate(() => {
    const t = document.querySelector('[data-testid="checklist-item-row"]').closest("table");
    let sc = t.parentElement; while (sc && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
    const ths = [...t.querySelectorAll("thead th")];
    const m = () => ({ sc: `${sc.clientWidth}/${sc.scrollWidth}`, th: ths.map((h) => `${h.innerText.trim()}:${Math.round(h.getBoundingClientRect().width)}:${getComputedStyle(h).whiteSpace}`) });
    const before = m();
    const last = ths[ths.length - 1]; last.style.whiteSpace = "normal";
    const lastOnly = m();
    ths.forEach((h) => (h.style.whiteSpace = "normal"));
    const all = m();
    return { before, lastHeaderNormal: lastOnly, allHeadersNormal: all, thClass: last.className };
  });
  console.log(JSON.stringify(r));
  await ctx.close();
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
