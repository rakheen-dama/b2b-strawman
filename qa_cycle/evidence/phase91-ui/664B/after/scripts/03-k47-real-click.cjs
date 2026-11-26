// 664B after (new, additive): K-47 real-input check of the Save View dialog. Run from this dir after
// the before scripts: `PHASE=after node 03-k47-real-click.cjs`. The seed (00-seed.cjs) pressed Next/Save
// with dispatchEvent and 01-capture.cjs only trial-clicks Next, so this script drives REAL mouse input:
// page.mouse.wheel over a point of the step body that is not a <select>/<input>, and page.mouse.click
// at the centre of Next / Close after confirming with elementFromPoint that the button is the topmost
// element there. Steps 1 → 2 → 3 by real clicks; Save is NEVER clicked (closed with the real Close X).
// /matters and /clients × 1024×768, 1024×900, 1440×900 × light/dark (Alice).
// Output: ../k47-real-click.json, ../sheet-k47-real-click.png (raw in /tmp/664B-after-raw/k47rc/).
const path = require("path");
const fs = require("fs");
const L = require("../../before/scripts/lib.cjs");

const RAW = path.join(L.RAW, "k47rc");
fs.mkdirSync(RAW, { recursive: true });
const R = { phase: "after", capturedAt: null, runs: {} };
const sink = [];
const lab = { label: "" };
const tiles = [];

const GEOM = () => {
  const d = [...document.querySelectorAll('[role="dialog"]')].pop();
  if (!d) return { open: false };
  const vh = innerHeight;
  const box = (e) => { if (!e) return null; const b = e.getBoundingClientRect(); return { top: Math.round(b.top), bottom: Math.round(b.bottom), inViewport: b.top >= 0 && b.bottom <= vh }; };
  const btn = (re) => [...d.querySelectorAll("button")].find((b) => re.test((b.getAttribute("aria-label") || b.innerText || "").trim()));
  const body = [...d.querySelectorAll("div")].find((e) => /overflow-y-auto/.test(e.className) && /flex-1/.test(e.className));
  return { open: true, title: (d.querySelector('[data-slot="dialog-title"], h2') || {}).textContent, step: (d.querySelector('[data-slot="dialog-description"], p') || {}).textContent, dialog: box(d), titleBox: box(d.querySelector('[data-slot="dialog-title"], h2')), next: box(btn(/^next$/i)), save: box(btn(/^save( view)?$/i)), close: box(btn(/^close$/i)), body: body ? { scrollTop: Math.round(body.scrollTop), scrollH: body.scrollHeight, clientH: body.clientHeight } : null };
};
// A point inside the step body whose topmost element is not a form control (so the wheel scrolls the body).
const WHEEL_POINT = () => {
  const d = [...document.querySelectorAll('[role="dialog"]')].pop();
  const body = [...d.querySelectorAll("div")].find((e) => /overflow-y-auto/.test(e.className) && /flex-1/.test(e.className));
  if (!body) return null;
  const b = body.getBoundingClientRect();
  for (let y = b.top + 10; y < b.bottom - 5; y += 12)
    for (const x of [b.left + 6, b.left + b.width / 2, b.right - 8]) {
      const e = document.elementFromPoint(x, y);
      if (e && body.contains(e) && !e.closest("select,input,textarea,[role=listbox],[role=combobox]")) return { x: Math.round(x), y: Math.round(y), hit: e.tagName.toLowerCase() };
    }
  return null;
};
// Centre of a dialog button + whether that button (or its child) is the topmost element there.
const HIT = (re) => {
  const d = [...document.querySelectorAll('[role="dialog"]')].pop();
  const b = [...d.querySelectorAll("button")].find((x) => new RegExp(re, "i").test((x.getAttribute("aria-label") || x.innerText || "").trim()));
  if (!b) return null;
  const r = b.getBoundingClientRect();
  const x = Math.round(r.left + r.width / 2), y = Math.round(r.top + r.height / 2);
  const top = document.elementFromPoint(x, y);
  return { x, y, topmostIsButton: !!top && (top === b || b.contains(top)), inViewport: r.top >= 0 && r.bottom <= innerHeight };
};

async function realClick(p, re) {
  const h = await p.evaluate(HIT, re);
  if (!h || !h.topmostIsButton || !h.inViewport) return { clicked: false, hit: h };
  await p.mouse.click(h.x, h.y);
  await p.waitForTimeout(500);
  return { clicked: true, hit: h };
}

(async () => {
  const browser = await L.chromium.launch();
  const S = { alice: await L.loginState(browser, "alice") };
  for (const route of ["/matters", "/clients"])
    for (const [w, h] of [[1024, 768], [1024, 900], [1440, 900]])
      for (const theme of ["light", "dark"]) {
        const key = `${route.slice(1)}-${w}x${h}-${theme}`;
        const ctx = await L.newCtx(browser, S.alice, { width: w, height: h, theme, extra: L.EXTRA });
        const p = await ctx.newPage();
        L.watch(p, sink, lab);
        lab.label = key;
        await p.goto(`${L.BASE}${route}`, { waitUntil: "load" });
        await p.waitForLoadState("networkidle").catch(() => {});
        const tabsBefore = await p.locator('[data-testid="saved-view-selector"] [role="tab"]').count();
        await p.locator("#main-content").getByRole("button", { name: "Save View" }).first().click();
        await p.getByRole("dialog").waitFor();
        await p.waitForTimeout(700);
        const run = { step1: await p.evaluate(GEOM) };
        // Real wheel over the body (not over a <select multiple>).
        const pt = await p.evaluate(WHEEL_POINT);
        run.wheelPoint = pt;
        if (pt) {
          await p.mouse.move(pt.x, pt.y);
          await p.mouse.wheel(0, 400);
          await p.waitForTimeout(500);
        }
        run.step1AfterWheel = await p.evaluate(GEOM);
        run.bodyScrolledPx = run.step1AfterWheel.body && run.step1.body ? run.step1AfterWheel.body.scrollTop - run.step1.body.scrollTop : null;
        const snap = async (tag, cap) => {
          if (theme !== "light" || w !== 1024 || h !== 768) return;
          const f = path.join(RAW, `${key}-${tag}.png`);
          await p.screenshot({ path: f });
          tiles.push([`${route} ${w}×${h} ${theme} — ${cap}`, f]);
        };
        await snap("s1", `step 1 after a real 400px wheel over the body (body scrollTop ${run.step1AfterWheel.body && run.step1AfterWheel.body.scrollTop}); title/Close/Next in viewport: ${run.step1AfterWheel.titleBox.inViewport}/${run.step1AfterWheel.close && run.step1AfterWheel.close.inViewport}/${run.step1AfterWheel.next && run.step1AfterWheel.next.inViewport}`);
        run.next1 = await realClick(p, "^next$");
        run.step2 = await p.evaluate(GEOM);
        await snap("s2", `after a real mouse click on Next: "${run.step2.title}" (${run.step2.step})`);
        run.next2 = await realClick(p, "^next$");
        run.step3 = await p.evaluate(GEOM);
        await snap("s3", `after a second real click on Next: "${run.step3.title}" (${run.step3.step}); Save in viewport ${run.step3.save && run.step3.save.inViewport} (not clicked)`);
        run.close = await realClick(p, "^close$");
        await p.waitForTimeout(400);
        run.closedAfterRealClick = (await p.getByRole("dialog").count()) === 0;
        run.tabsUnchanged = (await p.locator('[data-testid="saved-view-selector"] [role="tab"]').count()) === tabsBefore;
        run.pass = !!(run.step1.dialog.inViewport && run.step1.titleBox.inViewport && run.step1.next.inViewport && run.step1.close.inViewport && run.bodyScrolledPx > 0 && run.step1AfterWheel.next.inViewport && run.step1AfterWheel.titleBox.inViewport && run.next1.clicked && /Select Columns/.test(run.step2.title) && run.step2.dialog.inViewport && run.next2.clicked && /Name Your View/.test(run.step3.title) && run.step3.save && run.step3.save.inViewport && run.close.clicked && run.closedAfterRealClick && run.tabsUnchanged);
        R.runs[key] = run;
        console.log(key, run.pass ? "PASS" : "FAIL", `d ${run.step1.dialog.top}/${run.step1.dialog.bottom} body ${run.step1.body && run.step1.body.scrollH}/${run.step1.body && run.step1.body.clientH} wheel +${run.bodyScrolledPx} → ${run.step2.title} → ${run.step3.title} closed ${run.closedAfterRealClick}`);
        await ctx.close();
      }
  await browser.close();
  R.capturedAt = new Date().toISOString();
  R.summary = { pass: Object.values(R.runs).filter((r) => r.pass).length, of: Object.keys(R.runs).length, console: { errors: sink.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: sink.filter((c) => c.type === "warning").length, hydration: sink.filter((c) => /hydrat/i.test(c.text)).length, messages: sink } };
  fs.writeFileSync(path.join(L.OUT, "k47-real-click.json"), JSON.stringify(R) + "\n");
  const b2 = await L.chromium.launch();
  await L.sheet(b2, path.join(L.OUT, "sheet-k47-real-click.png"), "664B after — K-47 real mouse input, Alice 1024×768 light: rows /matters, /clients; cols step 1 after a real wheel / step 2 after a real click on Next / step 3 after a second real click (Save not clicked)", tiles, 3, 600);
  await b2.close();
  console.log(JSON.stringify({ pass: R.summary.pass, of: R.summary.of, console: { e: R.summary.console.errors, w: R.summary.console.warnings, h: R.summary.console.hydration } }));
})();
