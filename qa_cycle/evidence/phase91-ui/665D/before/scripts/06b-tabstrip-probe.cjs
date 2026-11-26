// 665D OBS step 6b (one-off, read-only): the Fees tab strip's scroll container at 1024×768 light, Alice, ?tab=tax.
// Walks from the tablist up to #main-content printing overflow-x / clientWidth / scrollWidth.
// Usage: PHASE=before node 06b-tabstrip-probe.cjs "$PWD"   (prints one JSON line; README quotes it)
const L = require(process.argv[2] + "/lib.cjs");
(async () => {
  const b = await L.chromium.launch(); const S = await L.loginState(b, "alice");
  const ctx = await L.newCtx(b, S, { width: 1024, height: 768, theme: "light", extra: L.EXTRA }); const p = await ctx.newPage();
  await p.goto(`${L.BASE}/settings/fees?tab=tax`, { waitUntil: "load" }); await p.waitForLoadState("networkidle");
  console.log(JSON.stringify(await p.evaluate(() => { const s = document.querySelector('#main-content [role="tablist"]'); const out = []; for (let e = s; e && e.id !== "main-content"; e = e.parentElement) { const cs = getComputedStyle(e); out.push(`${e.tagName.toLowerCase()}[${e.getAttribute("data-slot")||e.getAttribute("role")||""}] ox=${cs.overflowX} cw=${e.clientWidth} sw=${e.scrollWidth} mask=${cs.maskImage||cs.webkitMaskImage}`); } return out; })));
  await b.close();
})();
