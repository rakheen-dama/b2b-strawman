// 665D OBS-after step 7 (NEW in after/, no before counterpart): dark-mode + contrast + nowrap checks
// that unit tests and reviewers missed on 665C (F-665A-8 / F-665C-14 class). Alice, UI only.
//  (a) destructive ghost/plain icons: Tax "Deactivate {name}" (DESTRUCTIVE_GHOST) and Rates
//      "Delete billing rate for …" (plain button, svg text-destructive): computed colour of the button
//      and the svg vs the resolved --destructive, in light and dark, at 1440 and 1024. A billing rate
//      for Carol is created through the UI first (Add Rate) and deleted again (Delete) in `finally`.
//  (b) at 1024: every th/td of the Fees tables — white-space, whether its box is past the scroller's
//      visible right edge, and any descendant whose text is clipped (scrollWidth > clientWidth).
//  (c) WCAG contrast of status text / badges / save message on the composited background
//      (ancestor background colours alpha-composited onto the canvas colour), light and dark.
//  plus: Add trust account dialog opened + cancelled (API count unchanged); the Deactivate
//  AlertDialog opened + cancelled (destructive action colour).
// Usage: PHASE=after node 07-dark-probe.cjs   → $RAW/dark-probe.json + crops (tiles dark__*)
const path = require("path");
const fs = require("fs");
const L = require("../../before/scripts/lib.cjs");

const out = { at: new Date().toISOString(), runs: {}, rateSetup: null, trustDialog: null };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };

const HELP = () => {
  const cv = document.createElement("canvas"); cv.width = cv.height = 1;
  const cx = cv.getContext("2d", { willReadFrequently: true });
  window.__rgba = (c) => { cx.clearRect(0, 0, 1, 1); cx.fillStyle = "#000"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); const d = cx.getImageData(0, 0, 1, 1).data; return [d[0], d[1], d[2], +(d[3] / 255).toFixed(3)]; };
  // canvas fillRect premultiplies; read alpha separately via parsing when alpha < 1
  window.__parse = (c) => { const el = document.createElement("div"); el.style.color = c; document.body.appendChild(el); const s = getComputedStyle(el).color; el.remove(); const m = s.match(/rgba?\(([^)]+)\)/); if (m) { const p = m[1].split(/[ ,/]+/).filter(Boolean).map(Number); return [p[0], p[1], p[2], p[3] ?? 1]; } const r = window.__rgba(c); return r; };
  window.__solid = (c) => { cx.clearRect(0, 0, 1, 1); cx.globalCompositeOperation = "copy"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); const d = cx.getImageData(0, 0, 1, 1).data; cx.globalCompositeOperation = "source-over"; return [d[0], d[1], d[2], +(d[3] / 255).toFixed(3)]; };
  window.__bgOf = (el) => {
    const chain = []; for (let e = el; e; e = e.parentElement) chain.push(e);
    let acc = window.__solid(getComputedStyle(document.documentElement).backgroundColor || "white");
    if (acc[3] === 0) acc = [255, 255, 255, 1];
    for (const e of chain.reverse()) { const c = window.__solid(getComputedStyle(e).backgroundColor); const a = c[3]; if (a > 0) acc = [0, 1, 2].map((i) => Math.round(c[i] * a + acc[i] * (1 - a))).concat(1); }
    return acc;
  };
  const lum = (c) => { const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(c[0]) + 0.7152 * f(c[1]) + 0.0722 * f(c[2]); };
  window.__contrast = (fg, bg) => { const a = fg[3] ?? 1; const f = [0, 1, 2].map((i) => Math.round(fg[i] * a + bg[i] * (1 - a))); const [l1, l2] = [lum(f), lum(bg)].sort((x, y) => y - x); return +((l1 + 0.05) / (l2 + 0.05)).toFixed(2); };
  window.__tok = (name) => { const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim(); return { raw: v, rgb: v ? window.__solid(v) : null }; };
  window.__colorInfo = (el, label) => { const fg = window.__solid(getComputedStyle(el).color); const bg = window.__bgOf(el); return { label, text: (el.innerText || el.textContent || "").replace(/\s+/g, " ").trim().slice(0, 60), cls: (el.getAttribute("class") || "").slice(0, 160), color: getComputedStyle(el).color, fg, bg, contrast: window.__contrast(fg, bg) }; };
};

const DESTRUCTIVE = () => {
  const main = document.querySelector("#main-content");
  const tokens = { destructive: window.__tok("--destructive"), successForeground: window.__tok("--success-foreground"), slate400: window.__solid("oklch(70.4% 0.04 256.788)") };
  const btns = [...main.querySelectorAll("button")].filter((b) => /^(Deactivate |Delete (billing|cost) rate for )/.test(b.getAttribute("aria-label") || b.textContent.trim()));
  return { tokens, buttons: btns.map((b) => { const svg = b.querySelector("svg"); return { name: b.getAttribute("aria-label") || b.textContent.trim(), button: window.__colorInfo(b, "button"), svg: svg ? { color: getComputedStyle(svg).color, stroke: getComputedStyle(svg).stroke, rgb: window.__solid(getComputedStyle(svg).color), cls: svg.getAttribute("class"), contrastOnBg: window.__contrast(window.__solid(getComputedStyle(svg).color), window.__bgOf(b)) } : null }; }) };
};
const STATUS = () => {
  const main = document.querySelector("#main-content");
  const res = [];
  for (const el of main.querySelectorAll("td span, [data-slot=badge]")) {
    const t = (el.innerText || "").trim();
    if (!t || el.offsetParent === null) continue;
    if (/^(Active|Inactive|Default|Exempt|Primary|CLOSED|Not set|Ongoing|—)$/.test(t) || el.getAttribute("data-slot") === "badge") res.push(window.__colorInfo(el, el.getAttribute("data-slot") === "badge" ? `badge:${el.getAttribute("data-variant") || ""}` : "td-span"));
  }
  for (const p of main.querySelectorAll('[data-slot="card-footer"] p, [data-slot="settings-action-bar"] p, #main-content p.mt-2')) if (p.offsetParent) res.push(window.__colorInfo(p, "save-message"));
  const notice = [...main.querySelectorAll('p[role="status"]')]; for (const n of notice) res.push(window.__colorInfo(n, "status-notice"));
  // de-dupe on label+text+contrast
  const seen = new Set(); return res.filter((r) => { const k = `${r.label}|${r.text}|${r.contrast}`; if (seen.has(k)) return false; seen.add(k); return true; });
};
const NOWRAP = () => {
  const vis = (e) => { const b = e.getBoundingClientRect(); return b.width > 0 && b.height > 0 && getComputedStyle(e).display !== "none"; };
  return [...document.querySelectorAll("#main-content table")].filter(vis).map((tb) => {
    let sc = tb.parentElement; while (sc && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
    const right = sc.getBoundingClientRect().left + sc.clientWidth;
    const cells = [...tb.querySelectorAll("th,td")].filter(vis).map((c) => {
      const b = c.getBoundingClientRect();
      const clippedDesc = [...c.querySelectorAll("*")].filter((d) => vis(d) && d.children.length === 0 && d.scrollWidth > d.clientWidth + 1 && /(hidden|clip)/.test(getComputedStyle(d).overflowX)).map((d) => `${(d.textContent || "").trim().slice(0, 40)} (${d.clientWidth}/${d.scrollWidth})`);
      return { tag: c.tagName.toLowerCase(), text: (c.innerText || "").replace(/\s+/g, " ").trim().slice(0, 50), ws: getComputedStyle(c).whiteSpace, width: Math.round(b.width), pastVisibleRightPx: Math.max(0, Math.round(b.right - right)), clippedDesc };
    });
    return { headers: [...tb.querySelectorAll("thead th")].map((h) => h.innerText.trim()).join("|"), scroller: `${sc.clientWidth}/${sc.scrollWidth}`, nowrapCells: cells.filter((c) => c.ws === "nowrap").length, cells: cells.length, pastView: cells.filter((c) => c.pastVisibleRightPx > 0).map((c) => `${c.tag}:${c.text || "(icons)"} +${c.pastVisibleRightPx}`), clipped: cells.filter((c) => c.clippedDesc.length).map((c) => ({ text: c.text, clippedDesc: c.clippedDesc })), wrapCells: cells.filter((c) => c.ws !== "nowrap").map((c) => `${c.tag}:${c.text}:${c.ws}`) };
  });
};

async function ctxFor(browser, S, w, h, theme) {
  const ctx = await L.newCtx(browser, S, { width: w, height: h, theme, extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  return { ctx, p };
}
const go = async (p, tab) => { await p.goto(`${L.BASE}/settings/fees?tab=${tab}`, { waitUntil: "load" }); await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(500); await p.evaluate(HELP); };
const crop = async (p, key, loc, caption) => { const f = path.join(L.RAW, `${key}.png`); await loc.screenshot({ path: f }); tiles.push([key, f, caption]); };

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const a = await L.idpToken("alice");
  let rateCreated = false;
  const setup = await ctxFor(browser, S, 1440, 900, "light");
  try {
    // ---- create a billing rate for Carol so a Delete button exists (UI) ----
    lab.label = "dark|rate-setup";
    await go(setup.p, "tariffs");
    const row = setup.p.getByTestId("billing-rate-member");
    await row.getByRole("button", { name: "Add rate for Carol Member" }).click();
    const dlg = setup.p.getByRole("dialog", { name: "Add Rate" }); await dlg.waitFor();
    await dlg.getByLabel("Hourly Rate").fill("550");
    await dlg.getByLabel("Effective From").fill(new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" }));
    await dlg.getByRole("button", { name: "Create Rate" }).click(); await dlg.waitFor({ state: "hidden", timeout: 15000 });
    await row.getByRole("button", { name: "Delete billing rate for Carol Member" }).waitFor({ timeout: 15000 });
    rateCreated = true;
    out.rateSetup = { created: (await L.apiGet(a, "/api/billing-rates")).json };

    // ---- trust: Add Account dialog opened + cancelled ----
    lab.label = "dark|trust-dialog";
    await go(setup.p, "trust-accounting");
    const n0 = L.list((await L.apiGet(a, "/api/trust-accounts")).json).length;
    await setup.p.getByTestId("add-trust-account-button").click();
    const td = setup.p.getByRole("dialog"); await td.waitFor();
    const tdTitle = (await td.locator('[data-slot="dialog-title"], h2').first().innerText()).trim();
    const tdButtons = await td.getByRole("button").evaluateAll((bs) => bs.map((b) => (b.getAttribute("aria-label") || b.textContent).trim()));
    const ss = path.join(L.RAW, "dark__trust-add-dialog.png"); await setup.p.screenshot({ path: ss }); tiles.push(["dark__trust-add-dialog", ss, `Alice 1440 light — Trust accounting › Add Account → dialog "${tdTitle}" (then Cancel)`]);
    await td.getByRole("button", { name: "Cancel" }).click(); await td.waitFor({ state: "hidden", timeout: 10000 });
    out.trustDialog = { title: tdTitle, buttons: tdButtons, closedByCancel: true, accountsBefore: n0, accountsAfter: L.list((await L.apiGet(a, "/api/trust-accounts")).json).length };

    for (const [w, h] of [[1440, 900], [1024, 768]])
      for (const theme of ["light", "dark"]) {
        const v = `${w}-${theme}`;
        const { ctx, p } = await ctxFor(browser, S, w, h, theme);
        const R = (out.runs[v] = {});
        lab.label = `dark|tax|${v}`;
        await go(p, "tax");
        R.tax = { destructive: await p.evaluate(DESTRUCTIVE), status: await p.evaluate(STATUS), nowrap: w === 1024 ? await p.evaluate(NOWRAP) : undefined };
        if (w === 1024) await crop(p, `dark__tax-table-${v}`, p.locator('[data-slot="settings-section"]').filter({ has: p.getByRole("heading", { name: "Tax Rates", exact: true }) }), `Alice ${w} ${theme} — Tax › Tax Rates section (DataTable; Deactivate icons, Active / Inactive, badges)`);
        if (w === 1440) {
          // the Deactivate AlertDialog: open + cancel (destructive action colour)
          await p.getByRole("button", { name: "Deactivate E2E Rate v0n1" }).click();
          const dd = p.getByRole("alertdialog", { name: "Deactivate Tax Rate" }); await dd.waitFor(); await p.waitForTimeout(300);
          R.tax.alertAction = await dd.getByRole("button", { name: "Deactivate", exact: true }).evaluate((b) => window.__colorInfo(b, "alert-action"));
          await dd.getByRole("button", { name: "Cancel" }).click(); await dd.waitFor({ state: "hidden" });
        }
        lab.label = `dark|tariffs|${v}`;
        await go(p, "tariffs");
        R.tariffs = { destructive: await p.evaluate(DESTRUCTIVE), status: await p.evaluate(STATUS), nowrap: w === 1024 ? await p.evaluate(NOWRAP) : undefined };
        if (w === 1440) await crop(p, `dark__rates-table-${v}`, p.locator('[data-slot="settings-section"]').filter({ has: p.getByTestId("billing-rate-member") }), `Alice ${w} ${theme} — Rates › Billing Rates with Carol's rate (Edit / Delete icons)`);
        if (w === 1024) { await p.getByRole("tab", { name: "Cost Rates" }).click(); await p.waitForTimeout(400); R.tariffsCost = { nowrap: await p.evaluate(NOWRAP) }; }
        lab.label = `dark|trust|${v}`;
        await go(p, "trust-accounting");
        R.trust = { status: await p.evaluate(STATUS) };
        if (w === 1440 && theme === "dark") await crop(p, `dark__trust-accounts-${v}`, p.getByTestId("trust-accounts-section"), `Alice ${w} ${theme} — Trust accounting › Trust Accounts section (Primary / Active badges)`);
        lab.label = `dark|time-recording-save|${v}`;
        if (w === 1440) {
          await go(p, "time-recording");
          await p.getByRole("button", { name: "Save Settings" }).click();
          await p.locator('[data-slot="settings-action-bar"] p').waitFor({ timeout: 15000 });
          R.saveMessage = await p.evaluate(STATUS);
        }
        await ctx.close();
        console.log(v, JSON.stringify({ taxDestr: R.tax.destructive.buttons.map((b) => `${b.name}: svg ${b.svg && b.svg.color} (${b.svg && b.svg.contrastOnBg})`).slice(0, 2), token: R.tax.destructive.tokens.destructive.raw, rateDestr: R.tariffs.destructive.buttons.map((b) => `${b.name}: svg ${b.svg && b.svg.color} (${b.svg && b.svg.contrastOnBg})`), status: R.tax.status.map((s) => `${s.label}:${s.text}=${s.contrast}`) }));
      }
  } finally {
    if (rateCreated) {
      lab.label = "dark|rate-cleanup";
      await go(setup.p, "tariffs");
      const row = setup.p.getByTestId("billing-rate-member");
      await row.getByRole("button", { name: "Delete billing rate for Carol Member" }).click();
      const del = setup.p.getByRole("alertdialog", { name: "Delete Billing Rate" }); await del.waitFor();
      await del.getByRole("button", { name: "Delete", exact: true }).click(); await del.waitFor({ state: "hidden", timeout: 15000 });
      await row.getByRole("button", { name: "Add rate for Carol Member" }).waitFor({ timeout: 15000 });
      out.rateCleanup = { billingRatesAfter: (await L.apiGet(a, "/api/billing-rates")).json };
    }
    await setup.ctx.close();
    await browser.close();
    out.console = consoleSink;
    out.tiles = tiles;
    fs.writeFileSync(path.join(L.RAW, "dark-probe.json"), JSON.stringify(out));
    console.log("cleanup", JSON.stringify(out.rateCleanup), "trustDialog", JSON.stringify(out.trustDialog), "console", consoleSink.length, "tiles", tiles.length);
  }
})();
