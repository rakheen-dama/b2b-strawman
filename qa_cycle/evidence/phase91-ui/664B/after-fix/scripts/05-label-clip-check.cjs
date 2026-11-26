// 664B after-fix (new, additive): disambiguates 04-header-overlap's `clipped` flag after D-38.
// 04 marks a label clipped when the sort control's scrollWidth > clientWidth + 1. D-38 makes the
// control `relative` and puts the icon at `left-full`, so the icon now overflows the control by
// design and counts in its scrollWidth. This probe re-measures every sortable th on the same 7 pages:
// the control's scrollWidth with the icon present and with it hidden (display:none, restored at once),
// the icon's overflow past the control, the label text's right edge vs the control's right edge, and
// the overflow-x of the control and th (anything but `visible` could actually cut the icon or label).
// Read-only (navigation only). Run: `PHASE=after-fix node 05-label-clip-check.cjs`.
// Output: ../label-clip-check.json.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const VIEW = "664B K-11 view";
const OUT = { phase: L.PHASE, capturedAt: null, pages: {} };
const sink = [];
const lab = { label: "" };

const PROBE = () => {
  const vis = (e) => { const b = e.getBoundingClientRect(); return b.width > 0 && b.height > 0; };
  return [...document.querySelectorAll("#main-content table")].filter(vis).map((t) =>
    [...t.querySelectorAll("thead th")].filter(vis).map((h) => {
      const ctl = h.querySelector("a,button");
      const svg = ctl && ctl.querySelector("svg");
      if (!svg) return null;
      const c = ctl.getBoundingClientRect();
      const s = svg.getBoundingClientRect();
      let textRight = -Infinity;
      const w = document.createTreeWalker(ctl, NodeFilter.SHOW_TEXT);
      for (let n = w.nextNode(); n; n = w.nextNode()) {
        if (!n.textContent.trim()) continue;
        const r = document.createRange();
        r.selectNodeContents(n);
        for (const q of r.getClientRects()) if (q.width > 0) textRight = Math.max(textRight, q.right);
      }
      const withIcon = [ctl.scrollWidth, ctl.clientWidth];
      const prev = svg.style.display;
      svg.style.display = "none";
      const withoutIcon = [ctl.scrollWidth, ctl.clientWidth];
      svg.style.display = prev;
      return {
        label: h.innerText.trim().replace(/\n/g, " / ").slice(0, 28),
        iconPosition: getComputedStyle(svg).position,
        ctlPosition: getComputedStyle(ctl).position,
        ctlOverflowX: getComputedStyle(ctl).overflowX,
        thOverflowX: getComputedStyle(h).overflowX,
        scrollMinusClientWithIcon: withIcon[0] - withIcon[1],
        scrollMinusClientWithoutIcon: withoutIcon[0] - withoutIcon[1],
        iconOverflowPastCtl: Math.round(s.right - c.right),
        textRightMinusCtlRight: +(textRight - c.right).toFixed(1),
        labelClipped: withoutIcon[0] > withoutIcon[1] + 1 || textRight > c.right + 0.5,
      };
    }).filter(Boolean)
  );
};

async function page(browser, S, key, url, { w = 1024, h = 768, theme = "light", who = "alice", view = null } = {}) {
  const ctx = await L.newCtx(browser, S[who], { width: w, height: h, theme, extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, sink, lab);
  lab.label = key;
  await p.goto(`${L.BASE}${url}`, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  if (view) {
    await p.locator('[data-testid="saved-view-selector"]').getByRole("tab", { name: view, exact: true }).first().click();
    await p.waitForURL(/view=/, { timeout: 15000 }).catch(() => {});
    await p.waitForLoadState("networkidle").catch(() => {});
  }
  await p.mouse.move(1, 1);
  await p.waitForTimeout(900);
  OUT.pages[key] = { url: p.url().replace(L.BASE, ""), viewport: `${w}x${h}`, theme, tables: await p.evaluate(PROBE) };
  await ctx.close();
}

(async () => {
  const browser = await L.chromium.launch();
  const S = { alice: await L.loginState(browser, "alice"), carol: await L.loginState(browser, "carol") };
  await page(browser, S, "matters-alice-1024-light", "/matters");
  await page(browser, S, "matters-alice-1440-dark", "/matters", { w: 1440, h: 900, theme: "dark" });
  await page(browser, S, "matters-carol-1024-light", "/matters", { who: "carol" });
  await page(browser, S, "clients-default-1024-light", "/clients");
  await page(browser, S, "clients-default-1440-light", "/clients", { w: 1440, h: 900 });
  await page(browser, S, "clients-k11view-1024-light", "/clients", { view: VIEW });
  await page(browser, S, "profitability-1024-light", "/reports/profitability");
  await browser.close();
  OUT.capturedAt = new Date().toISOString();
  const all = Object.entries(OUT.pages).flatMap(([k, r]) => r.tables.flat().map((x) => ({ k, ...x })));
  const kit = all.filter((x) => x.iconPosition === "absolute");
  const count = (f) => all.filter(f).map((x) => `${x.k}: ${x.label}`);
  OUT.summary = {
    sortControls: all.length,
    kitAbsoluteIcons: kit.length,
    labelClipped: count((x) => x.labelClipped),
    nonVisibleOverflow: count((x) => x.ctlOverflowX !== "visible" || x.thOverflowX !== "visible"),
    kitScrollMinusClientWithIcon: [...new Set(kit.map((x) => x.scrollMinusClientWithIcon))],
    kitScrollMinusClientWithoutIcon: [...new Set(kit.map((x) => x.scrollMinusClientWithoutIcon))],
    kitIconOverflowPastCtl: [...new Set(kit.map((x) => x.iconOverflowPastCtl))],
    console: { errors: sink.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: sink.filter((c) => c.type === "warning").length, hydration: sink.filter((c) => /hydrat/i.test(c.text)).length, messages: sink },
  };
  fs.writeFileSync(path.join(L.OUT, "label-clip-check.json"), JSON.stringify(OUT) + "\n");
  console.log(JSON.stringify(OUT.summary));
})();
