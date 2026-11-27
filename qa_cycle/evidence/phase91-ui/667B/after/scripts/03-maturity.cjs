// 667B OBS-after maturity-alert probe (NEW). ONE write, run AFTER the unedited capture and the
// layout probe so neither sees it: Alice places a section 86(4) CLIENT_INSTRUCTION investment
// (the PlaceInvestmentDialog call: POST /api/trust-accounts/{primary}/investments + Idempotency-Key)
// for Ndlovu Family Trust on its own investment account, R 1 000, deposit today, maturity 18 days
// out, so it is ACTIVE and inside the 30-day window → the `maturity-alert` row. CLIENT_INSTRUCTION
// keeps ?investmentBasis=FIRM_DISCRETION empty (checked after). Idempotent: an investment whose
// notes carry MARK is reused, never placed twice.
// Then, Alice × 1024/1440 × light/dark on /investments: the maturity row's tint measured two ways —
// (a) the CSS layers composited (as the shared probe does) and (b) real screenshot pixels sampled in
// an empty padding area of a normal cell and of the sticky Actions cell (the same row), plus the
// same samples on an untinted row. Crops of the register card per variant.
// Usage (from this dir): PHASE=after node 03-maturity.cjs → ../maturity.json + ../crop-maturity-*.png
const path = require("path");
const fs = require("fs");
const L = require("../../before/scripts/lib.cjs");

const MARK = "667B OBS-after maturity-alert probe";
const S = L.readSeed();
const ACC = S.ids.accountId;
const NDLOVU = { investmentAccountId: "28691bf6-677b-4fc4-a50e-c3277dd08316", clientId: "5eb6515c-b132-4378-a999-44c3277b2230", matterId: "943f98cf-13a5-4e01-b994-9c863e781f43" };

function sampleTargets() {
  const t = document.querySelector('[data-testid="investments-table"]');
  const hdr = [...t.querySelectorAll("thead th")].map((th) => th.textContent.trim());
  const iA = hdr.indexOf("Actions");
  const cv = document.createElement("canvas"); cv.width = cv.height = 1;
  const cx = cv.getContext("2d", { willReadFrequently: true });
  const px = (layers) => { cx.fillStyle = "#fff"; cx.fillRect(0, 0, 1, 1); for (const c of layers) { cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); } const d = cx.getImageData(0, 0, 1, 1).data; return [d[0], d[1], d[2]]; };
  const bgLayers = (el) => { const ls = []; for (let e = el; e; e = e.parentElement) { const b = getComputedStyle(e).backgroundColor; if (b && b !== "rgba(0, 0, 0, 0)" && b !== "transparent") { ls.unshift(b); if (px([b]).join() === px(["#000", b]).join()) break; } } return ls; };
  const hex = (a) => "#" + a.map((v) => v.toString(16).padStart(2, "0")).join("");
  return [...t.querySelectorAll("tbody tr")].map((tr) => {
    const tds = [...tr.querySelectorAll("td")];
    const c0 = tds[0].getBoundingClientRect();
    const a = iA >= 0 ? tds[iA] : null; const ar = a && a.getBoundingClientRect();
    return {
      testid: tr.dataset.testid, text: tds[0].innerText.replace(/\s+/g, " ").trim(),
      trBg: getComputedStyle(tr).backgroundColor, cssRow: hex(px(bgLayers(tds[0]))), trLayers: bgLayers(tr),
      cssSticky: a && hex(px(bgLayers(a))), stickyBg: a && getComputedStyle(a).backgroundColor, stickyPosition: a && getComputedStyle(a).position,
      // empty padding points, document coordinates (2 px inside the top-left of the first cell; 3 px inside the bottom-right of the sticky cell)
      ptRow: [Math.round(c0.left + 2), Math.round(c0.top + scrollY + 2)],
      ptSticky: ar && [Math.round(ar.right - 3), Math.round(ar.bottom + scrollY - 3)],
      ptStickyTopLeft: ar && [Math.round(ar.left + 3), Math.round(ar.top + scrollY + 2)],
    };
  });
}

(async () => {
  const t = await L.idpToken("alice");
  const res = { at: new Date().toISOString(), mark: MARK, write: null, variants: {} };
  const all = async () => L.list((await L.apiGet(t, `/api/trust-accounts/${ACC}/investments?page=0&size=100&sort=depositDate,desc`)).json);
  let inv = (await all()).find((i) => (i.notes || "") === MARK);
  const today = new Date(Date.now() + 2 * 3600e3).toISOString().slice(0, 10); // SA date
  const maturity = new Date(Date.parse(today) + 18 * 86400e3).toISOString().slice(0, 10);
  if (!inv) {
    const body = { ...NDLOVU, institution: "Standard Bank", accountNumber: "62000000001", principal: 1000, interestRate: 0.075, depositDate: today, maturityDate: maturity, notes: MARK, investmentBasis: "CLIENT_INSTRUCTION" };
    inv = await L.apiWrite(t, "POST", `/api/trust-accounts/${ACC}/investments`, body);
    res.write = { request: { method: "POST", path: `/api/trust-accounts/${ACC}/investments`, body }, created: true };
  } else res.write = { created: false, reused: true };
  res.investment = { id: inv.id, client: inv.clientName, basis: inv.investmentBasis, status: inv.status, awaitingApproval: inv.awaitingApproval, principal: inv.principal, depositDate: inv.depositDate, maturityDate: inv.maturityDate, depositTransactionId: inv.depositTransactionId };
  res.maturingEndpoint = (await L.apiGet(t, `/api/trust-accounts/${ACC}/investments/maturing`)).json;
  res.investmentsNow = (await all()).map((i) => `${i.clientName} ${i.investmentBasis} ${i.status} ${i.balance} maturity=${i.maturityDate}`);
  res.firmDiscretionNow = L.list((await L.apiGet(t, `/api/trust-accounts/${ACC}/investments?page=0&size=100&investmentBasis=FIRM_DISCRETION`)).json).length;

  const browser = await L.chromium.launch();
  const st = await L.loginState(browser, "alice");
  const decoder = await (await browser.newContext()).newPage();
  const pixel = async (buf, x, y) => decoder.evaluate(async ([b64, x, y]) => { const img = new Image(); img.src = "data:image/png;base64," + b64; await img.decode(); const c = document.createElement("canvas"); c.width = img.width; c.height = img.height; const g = c.getContext("2d"); g.drawImage(img, 0, 0); const d = g.getImageData(x, y, 1, 1).data; return "#" + [d[0], d[1], d[2]].map((v) => v.toString(16).padStart(2, "0")).join(""); }, [buf.toString("base64"), x, y]);
  for (const v of ["1024-light", "1024-dark", "1440-light", "1440-dark"]) {
    const [w, theme] = v.split("-");
    const ctx = await L.newCtx(browser, st, { width: +w, height: w === "1024" ? 768 : 900, theme, extra: L.EXTRA });
    const page = await ctx.newPage();
    await page.goto(`${L.TA}/investments`, { waitUntil: "load" });
    await page.waitForLoadState("networkidle").catch(() => {});
    await page.waitForTimeout(600);
    const card = page.locator('[data-slot="card"]:has([data-testid="investments-table"])').first();
    await card.scrollIntoViewIfNeeded();
    const rows = await page.evaluate(sampleTargets);
    const shot = await page.screenshot({ fullPage: true }); // document coordinates (pt* carry scrollY)
    for (const r of rows) {
      r.pxRow = await pixel(shot, ...r.ptRow);
      if (r.ptSticky) { r.pxSticky = await pixel(shot, ...r.ptSticky); r.pxStickyTopLeft = await pixel(shot, ...r.ptStickyTopLeft); }
    }
    const f = path.join(L.OUT, `crop-maturity-${v}.png`);
    if (v.startsWith("1024")) await card.screenshot({ path: f });
    res.variants[v] = { maturityAlertCount: await page.getByTestId("maturity-alert").count(), investmentRowCount: await page.locator('[data-testid^="investment-row-"]').count(), rows, crop: v.startsWith("1024") ? path.basename(f) : null };
    if (v === "1440-light") {
      await page.goto(`${L.TA}/investments?investmentBasis=FIRM_DISCRETION`, { waitUntil: "load" });
      await page.waitForLoadState("networkidle").catch(() => {});
      res.firmDiscretionEmptyStillRendered = await page.getByTestId("empty-investments").count();
    }
    await ctx.close();
  }
  await browser.close();
  fs.writeFileSync(path.join(L.OUT, "maturity.json"), JSON.stringify(res));
  console.log(JSON.stringify(res).slice(0, 6000));
})().catch((e) => { console.error("MATURITY FAIL", e); process.exit(1); });
