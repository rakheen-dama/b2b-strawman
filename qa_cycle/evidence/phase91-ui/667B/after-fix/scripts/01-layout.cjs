// 667B OBS after-fix layout probe: a COPY of after/scripts/01-layout.cjs (that file is not edited).
// Changed only: the plan (adds 1280 light/dark, 1366, recon page 1 at every width, investments at
// 1280/1366, mlist at 1440), the viewport heights (1280×800, 1366×768, 1024×768, 1440×900) and this header.
// Original header: Read-only: page loads
// and a horizontal scroll of the table scroller only. Measures what the shared probe does not:
//  - reconciliation page 2: the "List locked" link box vs its scroller (fully in view?), hit-test at
//    its centre, Period line count, row heights, the Completed column (display / width) per viewport
//    width (1024 / 1280 / 1366 / 1440) and whether the completed date is visible anywhere;
//  - open statements: the Discard button vs the scroller edge;
//  - investments: the sticky Actions cell vs the Balance amount text (gap px) and the Status chip
//    (covered px) at scroll 0 and at max scroll, hit-test at the chip centre, the sticky cell's
//    composited background vs the row's;
//  - monthly list tables at 1024.
// Usage (from this dir): PHASE=after-fix node 01-layout.cjs  → ../layout.json
const path = require("path");
const fs = require("fs");
const L = require("../../before/scripts/lib.cjs");

const S = L.readSeed();
const ID = S.ids;
const R = `${L.TA}/reconciliation`, INV = `${L.TA}/investments`, ML = `${R}/lists/${ID.monthlyListId}`;

function measure() {
  const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
  const main = document.querySelector("#main-content");
  const cv = document.createElement("canvas"); cv.width = cv.height = 1;
  const cx = cv.getContext("2d", { willReadFrequently: true });
  const px = (layers) => { cx.fillStyle = "#fff"; cx.fillRect(0, 0, 1, 1); for (const c of layers) { cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); } const d = cx.getImageData(0, 0, 1, 1).data; return [d[0], d[1], d[2]]; };
  const bgLayers = (el) => { const ls = []; for (let e = el; e; e = e.parentElement) { const b = getComputedStyle(e).backgroundColor; if (b && b !== "rgba(0, 0, 0, 0)" && b !== "transparent") { ls.unshift(b); if (px([b]).join() === px(["#000", b]).join()) break; } } return ls; };
  const hex = (a) => "#" + a.map((v) => v.toString(16).padStart(2, "0")).join("");
  const bg = (el) => hex(px(bgLayers(el)));
  const scrollerOf = (t) => { let sc = t.parentElement; while (sc && sc !== main && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement; return sc; };
  const textRects = (el) => { const rs = []; const w = document.createTreeWalker(el, NodeFilter.SHOW_TEXT); let n; while ((n = w.nextNode())) { if (!n.textContent.trim()) continue; const rg = document.createRange(); rg.selectNodeContents(n); for (const r of rg.getClientRects()) if (r.width > 0) rs.push(r); } return rs; };
  const lines = (el) => new Set(textRects(el).map((r) => Math.round(r.top))).size;
  const box = (r) => r && { l: Math.round(r.left), r: Math.round(r.right), t: Math.round(r.top), w: Math.round(r.width), h: Math.round(r.height) };
  const hitInside = (r, container) => { const e = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2); return e ? container.contains(e) : null; };
  const out = { url: location.pathname + location.search, vw: innerWidth };
  const tbl = (id) => main.querySelector(`[data-testid="${id}"]`);
  // ---- reconciliation history
  const rt = tbl("reconciliations-table");
  if (rt) {
    const sc = scrollerOf(rt); const s = sc.getBoundingClientRect();
    const ths = [...rt.querySelectorAll("thead th")];
    const comp = ths.find((th) => /Completed/i.test(th.textContent));
    out.recon = {
      scroller: { l: Math.round(s.left), r: Math.round(s.right), clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth },
      completedHeader: comp && { display: getComputedStyle(comp).display, w: Math.round(comp.getBoundingClientRect().width) },
      headers: ths.map((th) => `${norm(th.innerText) || "(hidden)"}:${Math.round(th.getBoundingClientRect().width)}`),
      rows: [...rt.querySelectorAll("tbody tr")].map((tr) => {
        const tds = [...tr.querySelectorAll("td")];
        const link = tr.querySelector('a[data-testid^="list-locked-"]');
        const lr = link && link.getBoundingClientRect();
        const compTd = comp ? tds[ths.indexOf(comp)] : null;
        return {
          period: norm(tds[0].innerText), h: Math.round(tr.getBoundingClientRect().height), periodLines: lines(tds[0]), periodW: Math.round(tds[0].getBoundingClientRect().width),
          completedCell: compTd && { text: norm(compTd.textContent), display: getComputedStyle(compTd).display, visibleRects: textRects(compTd).length },
          completedDateVisibleElsewhere: compTd && norm(compTd.textContent) !== "---" ? [...tr.querySelectorAll("td")].filter((td) => td !== compTd && norm(td.innerText).includes(norm(compTd.textContent))).length + (tr.getAttribute("title") || "").includes(norm(compTd.textContent)) : null,
          listLocked: link && { box: box(lr), leftInside: lr.left >= s.left - 0.5, rightInside: lr.right <= s.right + 0.5, rightGapPx: Math.round(s.right - lr.right), hit: hitInside(lr, link) },
        };
      }),
    };
  }
  // ---- open statements
  const os = tbl("open-statements-table");
  if (os) {
    const sc = scrollerOf(os); const s = sc.getBoundingClientRect();
    out.openStatements = { clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, headers: [...os.querySelectorAll("thead th")].map((th) => `${norm(th.textContent)}:${Math.round(th.getBoundingClientRect().width)}`),
      rows: [...os.querySelectorAll("tbody tr")].map((tr) => { const b = tr.querySelector('[data-testid^="discard-statement-"]'); const br = b && b.getBoundingClientRect(); return { cells: [...tr.querySelectorAll("td")].map((td) => norm(td.innerText)), h: Math.round(tr.getBoundingClientRect().height), discard: b && { box: box(br), outOfViewPx: Math.max(0, Math.round(br.right - s.right)), fullyInView: br.right <= s.right + 0.5, hit: hitInside(br, b) } }; }) };
  }
  // ---- investments
  const it = tbl("investments-table");
  if (it) {
    const sc = scrollerOf(it); const s = sc.getBoundingClientRect();
    const ths = [...it.querySelectorAll("thead th")].map((th) => norm(th.textContent));
    const iB = ths.indexOf("Balance"), iS = ths.indexOf("Status"), iA = ths.indexOf("Actions");
    const thA = iA >= 0 ? it.querySelectorAll("thead th")[iA] : null;
    out.investments = { scrollLeft: sc.scrollLeft, clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, headers: ths, actionsHeader: thA && { position: getComputedStyle(thA).position, l: Math.round(thA.getBoundingClientRect().left - s.left), bg: bg(thA) },
      rows: [...it.querySelectorAll("tbody tr")].map((tr) => {
        const tds = [...tr.querySelectorAll("td")];
        const a = iA >= 0 ? tds[iA] : null; const ar = a && a.getBoundingClientRect();
        const balR = textRects(tds[iB]); const balRight = balR.length ? Math.max(...balR.map((r) => r.right)) : null;
        const chip = tds[iS].querySelector('[data-slot="badge"]') || tds[iS]; const cr = chip.getBoundingClientRect();
        const covered = ar ? Math.max(0, Math.min(cr.right, ar.right) - Math.max(cr.left, ar.left)) : 0;
        const btns = a ? [...a.querySelectorAll("button")].map((b) => { const r = b.getBoundingClientRect(); return { text: norm(b.innerText), l: Math.round(r.left - s.left), r: Math.round(r.right - s.left), inView: r.left >= s.left - 0.5 && r.right <= s.right + 0.5 }; }) : [];
        return {
          testid: tr.dataset.testid, h: Math.round(tr.getBoundingClientRect().height), rowBg: bg(tr), cells: tds.map((td) => norm(td.innerText).slice(0, 50)),
          balanceText: norm(tds[iB].innerText), balanceTextRight: balRight && Math.round(balRight - s.left),
          sticky: a && { position: getComputedStyle(a).position, l: Math.round(ar.left - s.left), r: Math.round(ar.right - s.left), bg: bg(a), lastChildBg: getComputedStyle(a).backgroundColor },
          gapBalanceToSticky: ar && balRight != null ? Math.round(ar.left - balRight) : null,
          status: { text: norm(chip.innerText), l: Math.round(cr.left - s.left), r: Math.round(cr.right - s.left), w: Math.round(cr.width), coveredPx: Math.round(covered), hitOnChip: hitInside(cr, chip) },
          principalLines: lines(tds[ths.indexOf("Principal")]), rateLines: lines(tds[ths.indexOf("Rate")]),
          buttons: btns,
        };
      }) };
  }
  // ---- monthly list tables
  for (const id of ["monthly-list-creditors", "monthly-list-after-lock"]) {
    const w = tbl(id); if (!w) continue;
    const t = w.matches("table") ? w : w.querySelector("table"); if (!t) continue;
    const sc = scrollerOf(t);
    (out.mlist = out.mlist || {})[id] = { tableW: Math.round(t.getBoundingClientRect().width), clientWidth: sc && sc !== main ? sc.clientWidth : null, scrollWidth: sc && sc !== main ? sc.scrollWidth : null, rowH: [...t.querySelectorAll("tbody tr")].map((r) => Math.round(r.getBoundingClientRect().height)) };
  }
  out.docHOverflowPx = document.documentElement.scrollWidth - document.documentElement.clientWidth;
  return out;
}

(async () => {
  const browser = await L.chromium.launch();
  const res = { at: new Date().toISOString(), note: "x positions are relative to the table scroller's left edge unless stated; gapBalanceToSticky = sticky Actions left − Balance amount text right (> 0 = clear); status.coveredPx = horizontal overlap of the Status chip with the sticky Actions cell", runs: {} };
  const plan = {
    alice: [["1024-light", ["recon", "recon-p2", "inv", "mlist"]], ["1024-dark", ["recon", "recon-p2", "inv", "mlist"]], ["1280-light", ["recon", "recon-p2", "inv"]], ["1280-dark", ["recon", "recon-p2", "inv"]], ["1366-light", ["recon", "recon-p2", "inv"]], ["1440-light", ["recon", "recon-p2", "inv", "mlist"]], ["1440-dark", ["recon", "recon-p2", "inv"]]],
    carol: [["1024-light", ["recon", "recon-p2", "inv"]], ["1024-dark", ["inv"]], ["1280-light", ["recon-p2"]]],
  };
  const URL_ = { recon: R, "recon-p2": `${R}?page=1`, inv: INV, mlist: ML };
  for (const [who, vs] of Object.entries(plan)) {
    const st = await L.loginState(browser, who);
    for (const [v, keys] of vs) {
      const [w, theme] = v.split("-");
      const ctx = await L.newCtx(browser, st, { width: +w, height: ({ 1024: 768, 1280: 800, 1366: 768 })[w] || 900, theme, extra: L.EXTRA });
      const page = await ctx.newPage();
      for (const k of keys) {
        await page.goto(URL_[k], { waitUntil: "load" });
        await page.waitForLoadState("networkidle").catch(() => {});
        await page.waitForTimeout(600);
        const m = await page.evaluate(measure);
        if (k === "inv" && m.investments && m.investments.scrollWidth > m.investments.clientWidth) {
          await page.evaluate(() => { const t = document.querySelector('[data-testid="investments-table"]'); let sc = t.parentElement; while (sc && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement; sc.scrollLeft = sc.scrollWidth; });
          await page.waitForTimeout(200);
          m.investmentsAtMaxScroll = (await page.evaluate(measure)).investments;
          if (who === "alice" && v === "1024-light") await page.locator('[data-slot="card"]:has([data-testid="investments-table"])').first().screenshot({ path: path.join(L.RAW, "layout-inv-maxscroll-1024-light.png") });
        }
        (res.runs[`${who}/${v}`] = res.runs[`${who}/${v}`] || {})[k] = m;
      }
      await ctx.close();
    }
  }
  await browser.close();
  fs.writeFileSync(path.join(L.OUT, "layout.json"), JSON.stringify(res));
  console.log("ok", path.join(L.OUT, "layout.json"));
})().catch((e) => { console.error("LAYOUT FAIL", e); process.exit(1); });
