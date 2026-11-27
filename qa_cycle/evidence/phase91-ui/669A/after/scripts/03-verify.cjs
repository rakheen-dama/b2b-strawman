// 669A OBS-after supplementary verification (NOT a replay of before/ — those ran unedited). Read-only:
// no writes at all (no conflict submit; menus opened and Escaped; sorts are client state).
// Covers what the before scripts did not measure: (1) disbursement tables — firm list and matter tab at
// 1024/1440 × light/dark: frame clientWidth vs table scrollWidth, table min-content width (headroom),
// clipped cells, header labels, plus a viewport sweep 1024→1440 (step 16) for overflow at the 58rem /
// 66rem column thresholds; (2) behaviour — row click → detail on the list and the matter tab, "…" menu
// does not navigate, sort toggles + aria-sort; (3) element-level reads for K-39 / K-79 / K-80 (c) /
// K-85 / empty states / the "Data Requests" h1 name and its count pill.
// Usage: PHASE=after node 03-verify.cjs   → ../verify.json (single line)
const path = require("path");
const fs = require("fs");
const L = require("../../before/scripts/lib.cjs");
const S = L.readSeed();
const ID = S.ids;
const B = L.BASE;
const out = { at: new Date().toISOString(), phase: L.PHASE, tables: {}, sweep: {}, behaviour: {}, reads: {}, console: [] };
const rel = (page) => { const u = new URL(page.url()); return u.pathname.replace(`/org/${L.ORG}`, "") + u.search; };
async function settle(page, tid) {
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.locator("#main-content h1").first().waitFor({ timeout: 15000 }).catch(() => {});
  if (tid) await page.getByTestId(tid).first().waitFor({ timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(700);
}
// in-page: disbursement table geometry
function tableGeom() {
  const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
  return [...document.querySelectorAll('[data-testid="disbursement-list"]')].map((w) => {
    const t = w.querySelector("table");
    if (!t) return { wrapperClientWidth: w.clientWidth, table: null, text: norm(w.innerText).slice(0, 120) };
    let sc = t.parentElement;
    while (sc && sc !== w && !/(auto|scroll|hidden)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
    const before = t.getBoundingClientRect().width;
    const prev = t.style.width;
    t.style.width = "1px";
    const minContent = Math.round(t.getBoundingClientRect().width);
    t.style.width = prev;
    const clipped = [...t.querySelectorAll("th,td")].filter((c) => getComputedStyle(c).display !== "none" && c.scrollWidth > c.clientWidth + 1).map((c) => `${c.tagName}:${norm(c.innerText).slice(0, 30)} ${c.scrollWidth}/${c.clientWidth}`);
    return {
      wrapperClientWidth: w.clientWidth, wrapperScrollWidth: w.scrollWidth,
      kitScroller: sc ? { tag: sc.tagName.toLowerCase(), clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, overflowPx: sc.scrollWidth - sc.clientWidth } : null,
      tableWidth: Math.round(before), tableScrollWidth: t.scrollWidth, minContent, headroomPx: (sc ? sc.clientWidth : w.clientWidth) - minContent,
      headers: [...t.querySelectorAll("thead th")].filter((h) => getComputedStyle(h).display !== "none").map((h) => `${norm(h.textContent) || "(blank)"}:${Math.round(h.getBoundingClientRect().width)}${h.getAttribute("aria-sort") ? " [" + h.getAttribute("aria-sort") + "]" : ""}`),
      hiddenHeaders: [...t.querySelectorAll("thead th")].filter((h) => getComputedStyle(h).display === "none").map((h) => norm(h.textContent)),
      headerStyle: (() => { const h = t.querySelector("thead th"); const s = h && getComputedStyle(h); return s ? `${s.fontSize} ${s.textTransform} ls ${s.letterSpacing}` : null; })(),
      rows: [...t.querySelectorAll("tbody tr")].map((r) => ({ testid: r.dataset.testid || null, h: Math.round(r.getBoundingClientRect().height), cursor: getComputedStyle(r).cursor, cells: [...r.querySelectorAll("td")].filter((c) => getComputedStyle(c).display !== "none").map((c) => norm(c.innerText).slice(0, 50)), dateFont: (() => { const c = r.querySelector("td"); return c ? getComputedStyle(c.querySelector("span,div") || c).fontFamily.split(",")[0] : null; })() })),
      clipped,
    };
  });
}
function emptyStruct(text) {
  const main = document.querySelector("#main-content");
  const es = [...main.querySelectorAll('[data-slot="empty-state"]')].filter((e) => e.innerText.includes(text));
  return es.map((e) => { const r = e.getBoundingClientRect(); const textEl = [...e.querySelectorAll("*")].find((x) => x.children.length === 0 && x.textContent.trim() === text); return { variant: e.dataset.variant, h: Math.round(r.height), w: Math.round(r.width), textTag: textEl ? textEl.tagName.toLowerCase() : null, headingsInside: e.querySelectorAll("h1,h2,h3,h4,h5,h6,[role=heading]").length, parentTestid: (e.closest("[data-testid]") || { dataset: {} }).dataset.testid || null, parentH: Math.round(e.parentElement.getBoundingClientRect().height) }; });
}
const W = { "1024-light": [1024, 768, "light"], "1024-dark": [1024, 768, "dark"], "1440-light": [1440, 900, "light"], "1440-dark": [1440, 900, "dark"] };

(async () => {
  const browser = await L.chromium.launch();
  const st = { alice: await L.loginState(browser, "alice"), carol: await L.loginState(browser, "carol") };
  const ctxFor = async (who, v) => { const [w, h, theme] = W[v]; const ctx = await L.newCtx(browser, st[who], { width: w, height: h, theme, extra: L.EXTRA }); const page = await ctx.newPage(); const lbl = { label: `${who} verify ${v}` }; L.watch(page, out.console, lbl); return { ctx, page, lbl }; };

  // (1) table geometry at the four variants
  for (const who of ["alice", "carol"]) for (const v of Object.keys(W)) {
    const { ctx, page } = await ctxFor(who, v);
    const targets = [["firm-list", `${B}/legal/disbursements`, "disbursements-list-page"]];
    if (who === "alice") targets.push(["matter-tab", `${B}/matters/${ID.disbursementActiveMatterMatterId}?tab=disbursements`, "project-disbursements-tab"], ["matter-tab-empty", `${B}/matters/${ID.statementsEmptyMatterId}?tab=disbursements`, "project-disbursements-tab"]);
    for (const [k, u, tid] of targets) {
      await page.goto(u, { waitUntil: "load" });
      await settle(page, tid);
      await page.getByText(/^Loading/).first().waitFor({ state: "hidden", timeout: 15000 }).catch(() => {});
      await page.waitForTimeout(500);
      const g = await page.evaluate(tableGeom);
      const extra = await page.evaluate(() => ({ docHOverflowPx: document.documentElement.scrollWidth - document.documentElement.clientWidth, headingsWithDisbursements: [...document.querySelectorAll("#main-content h1,#main-content h2,#main-content h3,#main-content h4,#main-content [role=heading]")].filter((h) => /disbursements/i.test(h.innerText)).map((h) => `${h.tagName.toLowerCase()}: ${h.innerText.trim()}`) }));
      const empties = k === "matter-tab-empty" ? await page.evaluate(emptyStruct, "No disbursements found.") : undefined;
      (out.tables[k] = out.tables[k] || {})[`${who}/${v}`] = { geom: g, ...extra, empties };
    }
    await ctx.close();
  }
  // viewport sweep (Alice light): overflow / headroom per width, firm list + matter tab
  {
    const { ctx, page } = await ctxFor("alice", "1440-light");
    for (const [k, u, tid] of [["firm-list", `${B}/legal/disbursements`, "disbursements-list-page"], ["matter-tab", `${B}/matters/${ID.disbursementActiveMatterMatterId}?tab=disbursements`, "project-disbursements-tab"]]) {
      await page.goto(u, { waitUntil: "load" });
      await settle(page, tid);
      out.sweep[k] = [];
      for (let w = 1024; w <= 1440; w += 16) {
        await page.setViewportSize({ width: w, height: 900 });
        await page.waitForTimeout(250);
        const g = (await page.evaluate(tableGeom))[0];
        out.sweep[k].push(g && g.kitScroller ? `${w}: frame ${g.kitScroller.clientWidth} min ${g.minContent} head ${g.headroomPx} ov ${g.kitScroller.overflowPx} wrapOv ${g.wrapperScrollWidth - g.wrapperClientWidth} clip ${g.clipped.length} cols ${g.headers.length}` : `${w}: no table`);
      }
      await page.setViewportSize({ width: 1440, height: 900 });
    }
    await ctx.close();
  }

  // (2) behaviour (Alice 1440 light, and Carol 1440 light for row click)
  for (const who of ["alice", "carol"]) {
    const { ctx, page } = await ctxFor(who, "1440-light");
    const R = (out.behaviour[who] = {});
    const list = async () => { await page.goto(`${B}/legal/disbursements`, { waitUntil: "load" }); await settle(page, "disbursements-list-page"); };
    await list();
    // row click on the Date cell
    const row = page.locator('[data-testid^="disbursement-row-"]').first();
    const rid = (await row.getAttribute("data-testid")).replace("disbursement-row-", "");
    await row.locator("td").first().click();
    await page.waitForURL(/\/legal\/disbursements\/[0-9a-f-]{36}/, { timeout: 15000 }).catch(() => {});
    await settle(page, "disbursement-detail");
    R.rowClick = { rowId: rid, url: rel(page), ok: rel(page) === `/legal/disbursements/${rid}`, detailTestid: await page.getByTestId("disbursement-detail").count() };
    // row click on the Description cell (prose column)
    await list();
    const row2 = page.locator('[data-testid^="disbursement-row-"]').nth(1);
    const rid2 = (await row2.getAttribute("data-testid")).replace("disbursement-row-", "");
    await row2.locator("td:visible").nth(2).click();
    await page.waitForURL(/\/legal\/disbursements\/[0-9a-f-]{36}/, { timeout: 15000 }).catch(() => {});
    R.rowClickDescription = { rowId: rid2, url: rel(page), ok: rel(page) === `/legal/disbursements/${rid2}` };
    // "…" menu: does not navigate
    await list();
    const trig = page.locator('[data-testid^="disbursement-actions-"]').first();
    R.menu = { triggers: await page.locator('[data-testid^="disbursement-actions-"]').count() };
    if (R.menu.triggers) {
      const from = rel(page);
      await trig.click();
      await page.waitForTimeout(1500);
      R.menu.urlAfterClick = rel(page);
      R.menu.navigated = rel(page) !== from;
      R.menu.menuOpen = await page.getByRole("menu").count();
      R.menu.items = await page.getByRole("menuitem").allInnerTexts();
      await page.keyboard.press("Escape");
      await page.waitForTimeout(800);
      R.menu.afterEscape = { url: rel(page), menuOpen: await page.getByRole("menu").count(), focusOnTrigger: await page.evaluate(() => (document.activeElement && document.activeElement.dataset.testid) || (document.activeElement && document.activeElement.tagName)) };
    }
    // sort toggles (Alice only)
    if (who === "alice") {
      await list();
      const state = async () => page.evaluate(() => {
        const t = document.querySelector('[data-testid="disbursement-list"] table');
        const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
        return {
          ariaSort: [...t.querySelectorAll("thead th")].filter((h) => h.hasAttribute("aria-sort")).map((h) => `${norm(h.textContent)}${getComputedStyle(h).display === "none" ? "(hidden)" : ""}=${h.getAttribute("aria-sort")}`),
          order: [...t.querySelectorAll("tbody tr")].map((r) => { const c = [...r.querySelectorAll("td")].filter((x) => getComputedStyle(x).display !== "none"); const txt = (i) => norm(c[i] && c[i].innerText); return `${(r.dataset.testid || "").slice(18, 26)} | ${txt(2).slice(0, 28)} | ${c.map((x) => norm(x.innerText)).find((s) => /^R\s/.test(s)) || ""} | ${c.map((x) => norm(x.innerText)).find((s) => /^(Approved|Pending|Draft|Rejected|Pending approval|Written off)/i.test(s)) || ""}`; }),
        };
      });
      R.sort = { initial: await state() };
      const heads = [["Date", () => page.locator('[data-testid="disbursement-list"] thead th button', { hasText: /^Date$/ }).first()], ["Supplier", () => page.getByTestId("disbursement-description-supplier-sort")], ["Amount", () => page.locator('[data-testid="disbursement-list"] thead th button', { hasText: /^Amount/ }).first()], ["Approval", () => page.locator('[data-testid="disbursement-list"] thead th button', { hasText: /^Approval$/ }).first()]];
      for (const [name, loc] of heads) {
        R.sort[name] = [];
        for (let i = 0; i < 2; i++) {
          const from = rel(page);
          const l = loc();
          if (!(await l.count())) { R.sort[name].push({ missing: true }); break; }
          await l.click();
          await page.waitForTimeout(500);
          const s = await state();
          s.url = rel(page);
          s.urlChanged = s.url !== from;
          if (name === "Supplier") s.supplierButton = await page.getByTestId("disbursement-description-supplier-sort").evaluate((b) => ({ label: b.getAttribute("aria-label"), pressed: b.getAttribute("aria-pressed"), thAriaSort: b.closest("th").getAttribute("aria-sort") }));
          R.sort[name].push(s);
        }
      }
      // matter tab row click
      await page.goto(`${B}/matters/${ID.disbursementActiveMatterMatterId}?tab=disbursements`, { waitUntil: "load" });
      await settle(page, "project-disbursements-tab");
      await page.locator('[data-testid="disbursement-list"] [data-testid^="disbursement-row-"]').first().waitFor({ timeout: 15000 }).catch(() => {});
      const mrow = page.locator('[data-testid="disbursement-list"] [data-testid^="disbursement-row-"]').first();
      const mid = (await mrow.getAttribute("data-testid")).replace("disbursement-row-", "");
      const mtrig = page.locator('[data-testid="disbursement-list"] [data-testid^="disbursement-actions-"]').first();
      R.matterTabMenu = { triggers: await mtrig.count() };
      if (R.matterTabMenu.triggers) { const from = rel(page); await mtrig.click(); await page.waitForTimeout(1500); R.matterTabMenu.navigated = rel(page) !== from; R.matterTabMenu.menuOpen = await page.getByRole("menu").count(); await page.keyboard.press("Escape"); await page.waitForTimeout(600); }
      await mrow.locator("td").first().click();
      await page.waitForURL(/\/legal\/disbursements\/[0-9a-f-]{36}/, { timeout: 15000 }).catch(() => {});
      R.matterTabRowClick = { rowId: mid, url: rel(page), ok: rel(page) === `/legal/disbursements/${mid}` };
    }
    await ctx.close();
  }

  // (3) element reads
  for (const v of ["1024-light", "1440-light", "1440-dark", "1024-dark"]) {
    const { ctx, page } = await ctxFor("alice", v);
    const Rd = (out.reads[v] = {});
    const colourFn = () => {
      window.__c = (el) => {
        const cv = document.createElement("canvas"); cv.width = cv.height = 1; const cx = cv.getContext("2d", { willReadFrequently: true });
        const px = (ls) => { cx.fillStyle = "#fff"; cx.fillRect(0, 0, 1, 1); for (const c of ls) { cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); } const d = cx.getImageData(0, 0, 1, 1).data; return [d[0], d[1], d[2]]; };
        const ls = []; for (let e = el; e; e = e.parentElement) { const b = getComputedStyle(e).backgroundColor; if (b && b !== "rgba(0, 0, 0, 0)" && b !== "transparent") { ls.unshift(b); if (px([b]).join() === px(["#000", b]).join()) break; } }
        const lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
        const bg = px(ls), fg = px([...ls, getComputedStyle(el).color]); const [a, b] = [lum(fg), lum(bg)].sort((x, y) => y - x);
        const hex = (q) => "#" + q.map((n) => n.toString(16).padStart(2, "0")).join("");
        return `${hex(fg)} on ${hex(bg)} ${((a + 0.05) / (b + 0.05)).toFixed(2)}`;
      };
    };
    const go = async (u, tid) => { await page.goto(u, { waitUntil: "load" }); await settle(page, tid); await page.evaluate(colourFn); };
    // information request: separator, e-mail, progress, link
    await go(`${B}/information-requests/${ID.informationRequestNullReminder}`);
    Rd.infoRequest = await page.evaluate(() => {
      const main = document.querySelector("#main-content");
      const leaf = (re) => [...main.querySelectorAll("*")].filter((e) => [...e.childNodes].some((n) => n.nodeType === 3 && re.test(n.textContent.trim())));
      const one = (re) => leaf(re).map((e) => `${e.tagName.toLowerCase()} "${e.textContent.trim().slice(0, 30)}" ${window.__c(e)} ${getComputedStyle(e).fontFamily.split(",")[0]}`);
      return { separator: one(/^\|$/), email: one(/@acme\.example\.com$/), progress: one(/accepted$/), waiting: one(/^Waiting for client$/), clientLink: [...main.querySelectorAll("a")].filter((a) => a.textContent.trim() === "Acme Corp").map((a) => window.__c(a)), reminder: one(/^(Firm default|Off|Every)/), h1: [...main.querySelectorAll("h1")].map((h) => `${h.textContent} ${getComputedStyle(h).fontSize}/${getComputedStyle(h).fontWeight}`) };
    });
    // disbursement detail (trust + office): K-79 incurred, K-80 (c) mono per Row, wrap of the VAT value
    for (const [k, id] of [["detailTrust", ID.disbursementTrust], ["detailOffice", ID.disbursementActiveMatter]]) {
      await go(`${B}/legal/disbursements/${id}`, "disbursement-detail");
      Rd[k] = await page.evaluate(() => {
        const main = document.querySelector("#main-content");
        const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
        const rows = [...main.querySelectorAll("div.flex.items-start.justify-between")].filter((d) => d.children.length === 2 && d.firstElementChild.tagName === "SPAN").map((d) => {
          const [l, v] = d.children; const vs = getComputedStyle(v); const lh = parseFloat(vs.lineHeight) || parseFloat(vs.fontSize) * 1.5;
          const monoParts = [...v.querySelectorAll("*")].filter((x) => /mono/i.test(getComputedStyle(x).fontFamily) && x.children.length === 0).map((x) => norm(x.textContent));
          return `${norm(l.textContent)} = "${norm(v.textContent).slice(0, 50)}" font ${vs.fontFamily.split(",")[0]}${monoParts.length ? " monoParts[" + monoParts.join("; ") + "]" : ""} lines ${Math.round(v.getBoundingClientRect().height / lh)} (h ${Math.round(v.getBoundingClientRect().height)})`;
        });
        const inc = [...main.querySelectorAll("*")].filter((e) => [...e.childNodes].some((n) => n.nodeType === 3 && /Incurred/.test(n.textContent))).map((e) => `${norm(e.textContent)} (${getComputedStyle(e).fontFamily.split(",")[0]})`);
        const trust = main.querySelector('[data-testid="trust-tx-link"]');
        return { rows, incurred: inc, trustLink: trust ? `${norm(trust.textContent)} ${window.__c(trust)} ${getComputedStyle(trust).fontFamily.split(",")[0]}` : null, h1: [...main.querySelectorAll("h1")].map((h) => `${h.textContent} ${getComputedStyle(h).fontSize}/${getComputedStyle(h).fontWeight}`) };
      });
    }
    // disbursement list date cells
    await go(`${B}/legal/disbursements`, "disbursements-list-page");
    Rd.listDates = await page.evaluate(() => [...document.querySelectorAll('[data-testid^="disbursement-row-"]')].map((r) => { const td = r.querySelector("td"); const leafs = [...td.querySelectorAll("*")].filter((x) => x.children.length === 0); const el = leafs[0] || td; return `${td.innerText.trim()} (${getComputedStyle(el).fontFamily.split(",")[0]})`; }));
    // tariffs: effective-from, "(n)" counts, empty structure
    await go(`${B}/legal/tariffs`);
    await page.getByTestId(`schedule-row-${ID.tariffScheduleId}`).click();
    await page.getByTestId("tariff-item-search").waitFor({ timeout: 15000 }).catch(() => {});
    await page.waitForTimeout(800);
    Rd.tariffs = await page.evaluate(() => {
      const main = document.querySelector("#main-content");
      const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
      return { effective: [...main.querySelectorAll("*")].filter((e) => [...e.childNodes].some((n) => n.nodeType === 3 && /Effective/.test(n.textContent))).map((e) => norm(e.textContent)), counts: [...main.querySelectorAll('[data-testid^="section-header-"] *')].filter((e) => e.children.length === 0 && /^\(\d+\)$/.test(e.textContent.trim())).slice(0, 3).map((e) => `${e.textContent.trim()} ${window.__c(e)}`), emptyBadges: [...main.querySelectorAll('[data-slot="badge"]')].filter((b) => !norm(b.innerText)).length };
    });
    await page.getByTestId("tariff-item-search").fill("zzz-669A-no-match");
    await page.waitForTimeout(1000);
    Rd.tariffsEmpty = await page.evaluate(emptyStruct, "No tariff items found.");
    // adverse parties empty
    await go(`${B}/legal/adverse-parties`);
    await page.getByPlaceholder("Search parties...").fill("zzz-669A-no-match");
    await page.waitForTimeout(1200);
    Rd.adverseEmpty = await page.evaluate(emptyStruct, "No adverse parties found.");
    Rd.adverseEmptyTable = await page.getByTestId("adverse-party-table").count();
    // disbursement filtered empty
    await go(`${B}/legal/disbursements`, "disbursements-list-page");
    await page.locator("#main-content").getByLabel("From date").fill("2030-01-01");
    await page.waitForTimeout(1200);
    Rd.disbursementsEmpty = await page.evaluate(emptyStruct, "No disbursements found.");
    Rd.disbursementsEmptyHeadings = await page.evaluate(() => [...document.querySelectorAll("#main-content h1,#main-content h2,#main-content h3,#main-content h4,#main-content [role=heading]")].filter((h) => /disbursements/i.test(h.innerText)).map((h) => `${h.tagName.toLowerCase()}: ${h.innerText.trim()}`));
    // data requests: h1 name exact + the count pill outside it
    await go(`${B}/compliance/requests`);
    Rd.dataRequestsH1 = await page.evaluate(() => { const h = document.querySelector("#main-content h1"); const pill = [...document.querySelectorAll("#main-content span")].find((s) => s.textContent.trim() === "1" && s.children.length === 0); return { h1Text: h.textContent, h1InnerHTMLHasSpan: !!h.querySelector("span"), pillInsideH1: pill ? h.contains(pill) : null, pillClass: pill ? pill.className : null, pillColour: pill ? window.__c(pill) : null }; });
    // statements + checklist empties
    await go(`${B}/matters/${ID.statementsEmptyMatterId}?tab=statements`, "project-statements-tab");
    await page.getByTestId("statements-empty").waitFor({ timeout: 15000 }).catch(() => {});
    Rd.statementsEmpty = await page.evaluate(() => [...document.querySelectorAll('[data-testid="statements-empty"]')].map((e) => { const es = e.querySelector('[data-slot="empty-state"]'); return { h: Math.round(e.getBoundingClientRect().height), variant: es && es.dataset.variant, text: e.innerText.trim(), colour: window.__c([...e.querySelectorAll("*")].find((x) => x.children.length === 0 && x.textContent.trim()) || e) }; }));
    await go(`${B}/clients/${ID.checklistEmptyClientId}?tab=onboarding`);
    await page.getByText("No checklists yet.").first().waitFor({ timeout: 15000 }).catch(() => {});
    Rd.checklistEmpty = await page.evaluate(emptyStruct, "No checklists yet.");
    // conflict form root width
    await go(`${B}/conflict-check`);
    Rd.conflictForm = await page.evaluate(() => { const f = document.querySelector('[data-testid="conflict-check-form"]'); return f ? { w: Math.round(f.getBoundingClientRect().width), maxWidth: getComputedStyle(f).maxWidth } : null; });
    await ctx.close();
  }
  // Carol checklist empty (non-admin branch)
  {
    const { ctx, page } = await ctxFor("carol", "1440-light");
    await page.goto(`${B}/clients/${ID.checklistEmptyClientId}?tab=onboarding`, { waitUntil: "load" });
    await settle(page);
    await page.getByText("No checklists yet.").first().waitFor({ timeout: 15000 }).catch(() => {});
    out.reads.carolChecklistEmpty = await page.evaluate(emptyStruct, "No checklists yet.");
    await ctx.close();
  }
  await browser.close();
  fs.writeFileSync(path.join(L.OUT, "verify.json"), JSON.stringify(out));
  console.log(JSON.stringify(out, null, 1).slice(0, 200));
})().catch((e) => { console.error("VERIFY FAIL", e); process.exit(1); });
