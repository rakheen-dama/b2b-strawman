// Supplement to obs-after-fix.cjs: (a) ring pixels of the worst sub-pixel case (matter Fees, ArrowRight#3,
// 0.8px under the end fade) at DPR 2, last 3 device px of the right ring strip; (b) does the
// MockAuthContextProvider "failed to fetch userinfo" error recur when the dashboard is allowed to settle?
const path = require("path");
const { chromium } = require(require.resolve("@playwright/test", { paths: [path.resolve(__dirname, "../../../../../frontend")] }));
const BASE = "http://localhost:3001";
const URL_M = `${BASE}/org/e2e-test-org/matters/e1f5964b-b227-46f2-84a0-463b35aaa834`;
(async () => {
  const b = await chromium.launch();
  const ctx = await b.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 });
  const p = await ctx.newPage();
  const errs = [];
  p.on("console", (m) => (m.type() === "error" || m.type() === "warning") && errs.push({ type: m.type(), url: p.url().replace(BASE, ""), text: m.text().slice(0, 120) }));
  p.on("pageerror", (e) => errs.push({ type: "pageerror", text: String(e).slice(0, 120) }));
  await p.goto(`${BASE}/mock-login`);
  await p.selectOption("#user-select", "user_e2e_alice");
  await p.getByRole("button", { name: "Sign In" }).click();
  await p.waitForURL(/dashboard/, { timeout: 60000 });
  await p.waitForLoadState("networkidle");
  await p.waitForTimeout(3000);
  const dashErrs = errs.length;
  await p.goto(URL_M, { waitUntil: "load" });
  await p.waitForTimeout(2500);
  await p.locator('[data-testid="grouped-tab-bar"] [role="tab"]').first().focus();
  await p.keyboard.press("Shift+Tab");
  await p.keyboard.press("Tab");
  for (let i = 0; i < 3; i++) { await p.keyboard.press("ArrowRight"); await p.waitForTimeout(350); }
  const r = await p.evaluate(() => { const a = document.activeElement, b = a.getBoundingClientRect(); return { id: a.dataset.testid, left: b.left, top: b.top, w: b.width, h: b.height }; });
  const buf = await p.screenshot({ clip: { x: r.left, y: r.top, width: r.w, height: r.h } });
  const px = await p.evaluate(async (b64) => {
    const img = new Image(); img.src = `data:image/png;base64,${b64}`; await img.decode();
    const c = document.createElement("canvas"); c.width = img.width; c.height = img.height; const x = c.getContext("2d"); x.drawImage(img, 0, 0);
    const y = Math.floor(img.height / 2);
    return { w: img.width, left: [0, 1, 2, 3].map((i) => [...x.getImageData(i, y, 1, 1).data.slice(0, 3)]), right: [4, 3, 2, 1].map((i) => [...x.getImageData(img.width - i, y, 1, 1).data.slice(0, 3)]) };
  }, buf.toString("base64"));
  console.log(JSON.stringify({ focused: r, ringPixelsDevicePx: px, dashboardErrorsAfterSettle: dashErrs, allConsole: errs }));
  await b.close();
})();
