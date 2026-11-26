// 665A OBS-AFTER copy of ../../before/scripts/03-saves.cjs. Adaptations (README "Scripts"):
//   C1 General "Save Settings": the stack now stores brandColor "#1B3A4B" (before: null). As before,
//      the Brand Color field is cleared and saved (persists null), then a SECOND save types the
//      original value back and saves (restore). Both saves are recorded.
//   C2 awaitMessage also records where the message renders: its data-slot ancestor chain
//      (settings-action-bar / card-footer / card-content …) and its box.
//   C3 kinks.subscription reads `[data-slot=settings-section]` as well as `[data-slot=card]`;
//      rolesSystemCards also records whether a badge's label is truncated.
//   C4 new read-only probes (`out.probes`): K-104 Roles at 1024 light AND dark (overflow, Member card
//      badges, labels; shots for sheet-k104); K-106 copy visibility; K-39 contrast of the General
//      hints/counters and the letterhead counter, light and dark (canvas-composited, WCAG);
//      Retention Policies table geometry at 1024/1440 × light/dark and rows shown vs GET; the
//      Retention Days focus test (≥3 keystrokes, then the original retyped — never saved); Delete →
//      window.confirm → dismissed → row still present.
// 665A OBS step 3: one save per save model, as Alice (owner), 1440×900 light, through the UI only —
// every save is a data no-op or is restored by a second save (verified by GET after each):
//   General  · page-level "Save Settings" (general-settings-form) — Brand Color field cleared first:
//              the form shows "#000000" for a stored null and would otherwise persist it.
//   General  · per-card "Save letterhead" — one space typed into "Address line 1" (makes the form
//              dirty; the request trims it to null = the stored value).
//   General  · instant-save Select "Portal digest cadence" — current → other → back to current.
//   Compliance · per-card "Save retention period" (Matter retention, value unchanged). The Compliance
//              "General Settings" card's "Save Settings" is NOT clicked: dormancy/deadline are null in
//              the store and the form would persist its 365/30 display fallbacks.
//   Features · one Switch (Bulk Billing Runs) off → on.
// Plus read-only probes: Phase 93 FICA review-interval inputs (labels, testids, values, light/dark
// crops) and the register rows K-103/K-104/K-106 (Roles system cards, Member badges, Subscription).
// Records each status message text, time to appear, the server-action POST statuses, console. A save
// whose message does not appear within 20 s is recorded as STALL (never reloaded to mask it).
// Writes $RAW/saves.json + tiles (crops); 04-assemble writes ../saves.json and the sheets.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const out = { at: new Date().toISOString(), saves: [], fica: null, kinks: {}, apiBefore: null, apiAfter: null };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };
const shotEl = async (loc, key, caption) => {
  const f = path.join(L.RAW, `${key}.png`);
  await loc.screenshot({ path: f });
  tiles.push([key, f, caption]);
};
const shot = async (p, key, caption, opt = {}) => {
  const f = path.join(L.RAW, `${key}.png`);
  await p.screenshot({ path: f, ...opt });
  tiles.push([key, f, caption]);
};
async function apiState() {
  const t = await L.idpToken("alice");
  const s = (await L.apiGet(t, "/api/settings")).json;
  const m = (await L.apiGet(t, "/api/settings/modules")).json;
  return { brandColor: s.brandColor, documentFooterText: s.documentFooterText, defaultCurrency: s.defaultCurrency, taxRegistrationNumber: s.taxRegistrationNumber, taxLabel: s.taxLabel, taxInclusive: s.taxInclusive, letterhead: s.letterhead, portalDigestCadence: s.portalDigestCadence, legalMatterRetentionYears: s.legalMatterRetentionYears, dormancyThresholdDays: s.dormancyThresholdDays, dataRequestDeadlineDays: s.dataRequestDeadlineDays, ficaReviewMonths: [s.ficaReviewMonthsLow, s.ficaReviewMonthsMedium, s.ficaReviewMonthsHigh], modules: m.modules.map((x) => `${x.id}:${x.enabled}`) };
}
async function go(p, tab) {
  lab.label = `saves|alice|${tab}`;
  await p.goto(`${L.BASE}/settings/firm?tab=${tab}`, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(400);
}
// Waits for a message matching `re` (as text anywhere in main); returns its text + ms, or a STALL.
async function awaitMessage(p, re, t0) {
  const loc = p.locator("#main-content").getByText(re).first();
  try {
    await loc.waitFor({ state: "visible", timeout: 20000 });
    const where = await loc.evaluate((e) => { const slots = []; for (let a = e; a && a.id !== "main-content"; a = a.parentElement) if (a.dataset && a.dataset.slot) slots.push(a.dataset.slot + (a.dataset.slot === "settings-section" && a.querySelector("h2") ? `(${a.querySelector("h2").textContent.trim()})` : "")); const r = e.getBoundingClientRect(); return { slots, box: { left: Math.round(r.left), top: Math.round(r.top), width: Math.round(r.width) } }; });
    return { text: (await loc.innerText()).trim(), ms: Date.now() - t0, tag: await loc.evaluate((e) => `${e.tagName.toLowerCase()}${e.getAttribute("role") ? `[role=${e.getAttribute("role")}]` : ""}.${e.className}`), where, loc };
  } catch (e) {
    return { stall: true, ms: Date.now() - t0 };
  }
}

// ---------- C4 read-only probes (out.probes) ----------
// In-page: painted colour behind `el` (ancestor backgrounds composited outermost-first over white on a
// 1×1 sRGB canvas), the text colour painted over it, and the WCAG ratio.
const CONTRAST = (el) => {
  const draw = (layers) => {
    const cv = document.createElement("canvas");
    cv.width = cv.height = 1;
    const x = cv.getContext("2d", { colorSpace: "srgb" });
    x.fillStyle = "#fff";
    x.fillRect(0, 0, 1, 1);
    for (const c of layers) { x.fillStyle = "rgba(0,0,0,0)"; x.fillStyle = c; x.fillRect(0, 0, 1, 1); }
    const d = x.getImageData(0, 0, 1, 1).data;
    return [d[0], d[1], d[2]];
  };
  const chain = [];
  for (let e = el; e; e = e.parentElement) chain.push(getComputedStyle(e).backgroundColor);
  chain.reverse();
  const cs = getComputedStyle(el);
  const bg = draw(chain);
  const fg = draw([...chain, cs.color]);
  const lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
  const [l1, l2] = [lum(fg), lum(bg)].sort((a, b) => b - a);
  const hex = (a) => "#" + a.map((v) => v.toString(16).padStart(2, "0")).join("");
  return { text: (el.textContent || "").trim().slice(0, 60), className: el.className, color: cs.color, fg: hex(fg), bg: hex(bg), ratio: +((l1 + 0.05) / (l2 + 0.05)).toFixed(2), fontSize: cs.fontSize };
};
// Retention Policies section: table geometry + rows as shown.
const RETENTION = () => {
  const txt = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
  const h = [...document.querySelectorAll("#main-content h2")].find((x) => txt(x) === "Retention Policies");
  const sec = h && h.closest('[data-slot="settings-section"]');
  if (!sec) return null;
  const t = sec.querySelector("table");
  let sc = t && t.parentElement;
  while (sc && sc !== sec && !["auto", "scroll", "hidden"].includes(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
  const sr = sc.getBoundingClientRect();
  const secR = sec.getBoundingClientRect();
  const se = document.scrollingElement;
  const ths = [...t.querySelectorAll("thead th")].map((th) => { const r = th.getBoundingClientRect(); return { text: txt(th), left: Math.round(r.left), right: Math.round(r.right), fullyVisible: r.left >= sr.left - 1 && r.right <= sr.right + 1, partlyVisible: r.left < sr.right - 1 }; });
  const rows = [...t.querySelectorAll("tbody tr")].map((tr) => {
    const sels = [...tr.querySelectorAll("select")].map((x) => ({ value: x.value, shown: x.selectedOptions[0] ? x.selectedOptions[0].text : null, disabled: x.disabled }));
    const inp = tr.querySelector("input");
    const del = [...tr.querySelectorAll("button")].find((b) => txt(b) === "Delete");
    const dr = del ? del.getBoundingClientRect() : null;
    return { recordType: sels[0], triggerEvent: sels[1], retentionDays: inp && inp.value, action: sels[2], active: (tr.querySelector('[role="switch"]') || {}).ariaChecked, deleteVisibleInScroller: dr ? dr.right <= sr.right + 1 && dr.left >= sr.left - 1 : null };
  });
  return { tableWidth: Math.round(t.getBoundingClientRect().width), tableScrollWidth: t.scrollWidth, scroller: { overflowX: getComputedStyle(sc).overflowX, clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, scrollable: sc.scrollWidth > sc.clientWidth, left: Math.round(sr.left), right: Math.round(sr.right) }, section: { left: Math.round(secR.left), right: Math.round(secR.right) }, pageHOverflow: se.scrollWidth - se.clientWidth, headers: ths, hiddenColumns: ths.filter((x) => !x.fullyVisible).map((x) => x.text), rows };
};

async function probes(browser, S, shot, shotEl) {
  const P = { k104: {}, k106: null, k39: {}, retention: { geometry: {}, rowsApi: null, focus: null, deleteConfirm: null } };
  const t = await L.idpToken("alice");
  P.retention.rowsApi = ((await L.apiGet(t, "/api/retention-policies")).json || []).map((x) => `${x.recordType}/${x.triggerEvent}:${x.retentionDays}:${x.action}:${x.active}`);
  for (const theme of ["light", "dark"]) {
    for (const [w, h] of [[1024, 768], [1440, 900]]) {
      const ctx = await L.newCtx(browser, S, { width: w, height: h, theme, extra: L.EXTRA });
      const p = await ctx.newPage();
      L.watch(p, consoleSink, lab);
      if (w === 1024) {
        // K-104
        await go(p, "roles");
        const k = await p.evaluate(() => {
          const txt = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
          const se = document.scrollingElement;
          const member = [...document.querySelectorAll("#main-content h3")].find((x) => txt(x) === "Member").closest('[data-slot="role-card"]');
          const cb = member.getBoundingClientRect();
          const badges = [...member.querySelectorAll('[data-slot="badge"]')].filter((b) => txt(b) !== "System").map((b) => { const r = b.getBoundingClientRect(); const sp = b.querySelector("span") || b; return { text: txt(b), title: b.getAttribute("title"), left: Math.round(r.left), right: Math.round(r.right), insideCard: r.left >= cb.left && r.right <= cb.right, truncated: sp.scrollWidth > sp.clientWidth + 1 }; });
          return { pageHOverflow: se.scrollWidth - se.clientWidth, memberCard: { left: Math.round(cb.left), right: Math.round(cb.right), width: Math.round(cb.width) }, badges, allInside: badges.every((b) => b.insideCard), newLabelsShown: ["Generate statement of account", "View legal registers", "Manage disbursements"].map((l) => ({ label: l, shown: badges.some((b) => b.text === l) })), rawEnumShown: badges.filter((b) => /^[A-Z_ ]+$/.test(b.text) && b.text.length > 4).map((b) => b.text) };
        });
        P.k104[theme] = k;
        const member = p.locator('[data-slot="role-card"]').filter({ has: p.getByRole("heading", { level: 3, name: "Member", exact: true }) });
        await member.scrollIntoViewIfNeeded();
        await shot(p, `k104__roles-1024-${theme}-vp`, `K-104 Alice Roles 1024×768 ${theme} (scrolled to the Member card): page overflow ${k.pageHOverflow}px (before ${theme === "light" ? 81 : 86}); badges inside card ${k.allInside}`);
        await shotEl(member, `k104__member-card-1024-${theme}`, `K-104 Member card 1024 ${theme}: card ${k.memberCard.left}–${k.memberCard.right}; badges ${k.badges.map((b) => `${b.text} →${b.right}`).join(", ")}`);
        if (theme === "light") {
          await go(p, "subscription");
          P.k106 = await p.evaluate(() => {
            const txt = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
            const main = document.querySelector("#main-content");
            const vis = (e) => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0 && r.top < innerHeight; };
            const tb = main.querySelector('[data-slot="page-header-toolbar"]');
            const sec = main.querySelector('[data-slot="settings-section"]');
            return { h1: txt(main.querySelector("h1")), h1InViewport: vis(main.querySelector("h1")), toolbarChips: tb ? [...tb.querySelectorAll('[data-slot="badge"]')].map(txt) : null, chipsInViewport: tb ? vis(tb) : null, sectionHeading: sec && sec.querySelector("h2") ? txt(sec.querySelector("h2")) : null, body: sec ? txt(sec.querySelector("p")) : null, bodyInViewport: sec ? vis(sec.querySelector("p")) : null };
          });
        }
      }
      // Retention geometry (every size × theme)
      await go(p, "compliance");
      P.retention.geometry[`${w}-${theme}`] = await p.evaluate(RETENTION);
      if (w === 1024) {
        const sec = p.locator('[data-slot="settings-section"]').filter({ has: p.getByRole("heading", { level: 2, name: "Retention Policies" }) });
        await shotEl(sec, `retention__1024-${theme}`, `Compliance › Retention Policies 1024 ${theme}: hidden columns ${JSON.stringify(P.retention.geometry[`${w}-${theme}`].hiddenColumns)}; scroller ${P.retention.geometry[`${w}-${theme}`].scroller.clientWidth}/${P.retention.geometry[`${w}-${theme}`].scroller.scrollWidth}`);
      }
      if (w === 1440) {
        // K-39 contrast on General
        await go(p, "general");
        P.k39[theme] = await p.evaluate((fnSrc) => {
          const CONTRAST = eval(`(${fnSrc})`);
          const txt = (e) => (e.textContent || "").replace(/\s+/g, " ").trim();
          const secOf = (title) => { const h = [...document.querySelectorAll("#main-content h2")].find((x) => txt(x) === title); return h && h.closest('[data-slot="settings-section"]'); };
          const find = (root, re) => root && [...root.querySelectorAll("p,span,div")].find((e) => re.test(txt(e)) && ![...e.children].some((c) => re.test(txt(c))));
          const pick = { "Branding hint (logo)": find(secOf("Branding"), /^PNG, JPG, or SVG\. Max 2 MB\.$/), "Branding counter (footer text)": find(secOf("Branding"), /^\d+\/500 characters$/), "Tax Label hint": find(secOf("Tax Configuration"), /^Label shown on fee notes/), "Letterhead counter (payment reference note)": find(secOf("Letterhead"), /^\d+\/500 characters$/), "Client Portal hint": find(secOf("Client Portal"), /^How often clients receive/) };
          return Object.fromEntries(Object.entries(pick).map(([k, e]) => [k, e ? CONTRAST(e) : null]));
        }, CONTRAST.toString());
        await shotEl(p.locator('[data-slot="settings-section"]').filter({ has: p.getByRole("heading", { level: 2, name: "Branding" }) }), `k39__branding-${theme}`, `K-39 Branding section 1440 ${theme}: hint ${P.k39[theme]["Branding hint (logo)"] && P.k39[theme]["Branding hint (logo)"].ratio}:1, counter ${P.k39[theme]["Branding counter (footer text)"] && P.k39[theme]["Branding counter (footer text)"].ratio}:1`);
      }
      await ctx.close();
    }
  }
  // Retention Days focus test + Delete confirm-cancel (1440 light). Nothing is saved.
  {
    const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
    const p = await ctx.newPage();
    L.watch(p, consoleSink, lab);
    await go(p, "compliance");
    const sec = p.locator('[data-slot="settings-section"]').filter({ has: p.getByRole("heading", { level: 2, name: "Retention Policies" }) });
    const input = sec.locator("tbody tr").first().locator('input[type="number"]');
    const original = await input.inputValue();
    await input.click();
    await input.evaluate((e) => { e.dataset.obsMark = "focus-probe"; });
    await p.keyboard.press("End");
    const steps = [];
    const check = async (key) => {
      const st = await p.evaluate(() => { const a = document.activeElement; return { activeIsInput: !!a && a.tagName === "INPUT", sameNode: !!a && a.dataset.obsMark === "focus-probe", value: a && a.value, caret: a && a.type === "text" ? a.selectionStart : null }; });
      steps.push({ key, ...st });
    };
    for (const k of ["1", "2", "3"]) { await p.keyboard.type(k); await p.waitForTimeout(150); await check(k); }
    await p.keyboard.press("ControlOrMeta+A");
    for (const k of original.split("")) { await p.keyboard.type(k); await p.waitForTimeout(100); await check(`retype ${k}`); }
    const restored = await input.inputValue();
    await shotEl(sec, "retention__focus-test", `Retention Days focus test (1440 light): typed 1,2,3 then retyped "${original}" — same node focused after every key: ${steps.every((x) => x.sameNode)}; final value "${restored}" (not saved)`);
    P.retention.focus = { row: 1, original, steps, sameNodeThroughout: steps.every((x) => x.sameNode && x.activeIsInput), valuesAfter123: steps.slice(0, 3).map((x) => x.value), restoredValue: restored, restoredEqualsOriginal: restored === original, saved: false };
    // Delete → window.confirm → dismiss
    const rowsBefore = await sec.locator("tbody tr").count();
    let dlg = null;
    p.once("dialog", async (d) => { dlg = { type: d.type(), message: d.message() }; await d.dismiss(); });
    await sec.locator("tbody tr").first().getByRole("button", { name: "Delete", exact: true }).click();
    await p.waitForTimeout(800);
    const rowsAfter = await sec.locator("tbody tr").count();
    const apiAfter = ((await L.apiGet(await L.idpToken("alice"), "/api/retention-policies")).json || []).map((x) => `${x.recordType}/${x.triggerEvent}:${x.retentionDays}:${x.action}:${x.active}`);
    P.retention.deleteConfirm = { dialog: dlg, dismissed: !!dlg, rowsBefore, rowsAfter, rowStillPresent: rowsAfter === rowsBefore, apiUnchanged: JSON.stringify(apiAfter) === JSON.stringify(P.retention.rowsApi) };
    await ctx.close();
  }
  return P;
}

(async () => {
  out.apiBefore = await apiState();
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");

  // ---------- read-only probes at 1024 light: K-103 / K-104 / K-106 ----------
  {
    const ctx = await L.newCtx(browser, S, { width: 1024, height: 768, theme: "light", extra: L.EXTRA });
    const p = await ctx.newPage();
    L.watch(p, consoleSink, lab);
    await go(p, "roles");
    out.kinks.rolesSystemCards = await p.evaluate(() => {
      const txt = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
      return [...document.querySelectorAll("#main-content h3")].filter((h) => ["Owner", "Admin", "Member"].includes(txt(h))).map((h) => {
        const card = h.closest("div.rounded-lg");
        const cb = card.getBoundingClientRect();
        const badges = [...card.querySelectorAll('[data-slot="badge"]')].filter((b) => txt(b) !== "System");
        return { role: txt(h), text: txt(card), cardRight: Math.round(cb.right), cardWidth: Math.round(cb.width), badges: badges.map((b) => { const sp = b.querySelector("span") || b; return { text: txt(b), title: b.getAttribute("title"), right: Math.round(b.getBoundingClientRect().right), whiteSpace: getComputedStyle(b).whiteSpace, truncated: sp.scrollWidth > sp.clientWidth + 1 }; }), maxBadgeRight: badges.length ? Math.max(...badges.map((b) => Math.round(b.getBoundingClientRect().right))) : null };
      });
    });
    out.kinks.rolesPageHOverflow1024 = await p.evaluate(() => document.scrollingElement.scrollWidth - document.scrollingElement.clientWidth);
    await go(p, "subscription");
    out.kinks.subscription = await p.evaluate(() => {
      const txt = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
      const main = document.querySelector("#main-content");
      return { h1: [...main.querySelectorAll("h1")].map(txt), tabLabel: txt(main.querySelector('[role="tab"][aria-selected="true"]')), chips: [...main.querySelectorAll('[data-slot="badge"]')].map(txt), body: [...main.querySelectorAll('[data-slot="card"] p,[data-slot="settings-section"] p')].map(txt), sectionHeads: [...main.querySelectorAll('[data-slot="settings-section"] h2')].map(txt) };
    });
    await ctx.close();
  }
  out.probes = await probes(browser, S, shot, shotEl);
  console.log("PROBES", JSON.stringify(out.probes).slice(0, 4000));

  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  const actions = [];
  const t00 = Date.now();
  p.on("response", (r) => {
    const q = r.request();
    if (q.method() === "POST" && q.headers()["next-action"]) actions.push({ status: r.status(), at: Date.now() - t00, label: lab.label });
  });
  const run = async (model, tab, act, re, cropOf, verify) => {
    const n0 = actions.length;
    const c0 = consoleSink.length;
    const t0 = Date.now();
    const detail = await act();
    const msg = await awaitMessage(p, re, t0);
    const key = `save__${model.replace(/\W+/g, "_")}`;
    if (!msg.stall) await shotEl(cropOf(msg.loc), key, `${model}: "${msg.text}" after ${msg.ms} ms`);
    else await shot(p, key, `${model}: STALL — no message after ${msg.ms} ms`);
    await p.waitForLoadState("networkidle").catch(() => {});
    await p.waitForTimeout(800);
    const rec = { model, tab, detail, message: msg.stall ? null : msg.text, messageElement: msg.tag, messageWhere: msg.where, ms: msg.ms, stall: !!msg.stall, serverActions: actions.slice(n0), console: consoleSink.slice(c0), apiAfter: verify ? await verify() : undefined };
    out.saves.push(rec);
    console.log(JSON.stringify({ model, message: rec.message, ms: rec.ms, stall: rec.stall, actions: rec.serverActions.map((a) => a.status), apiAfter: rec.apiAfter }));
  };
  const row = (loc) => loc.locator("xpath=..");
  const card = (loc) => loc.locator("xpath=ancestor::div[contains(@class,'rounded-lg')][1]");

  // ---------- General: page-level Save Settings (C1: clear → save, then retype original → save) ----------
  await go(p, "general");
  const brand = p.getByLabel("Brand Color", { exact: true });
  const brandShown = await brand.inputValue();
  const genVerify = async () => { const s = await apiState(); return { brandColor: s.brandColor, documentFooterText: s.documentFooterText, defaultCurrency: s.defaultCurrency, taxLabel: s.taxLabel, taxInclusive: s.taxInclusive, taxRegistrationNumber: s.taxRegistrationNumber }; };
  await run(
    "General · page-level 'Save Settings' (1/2 Brand Color cleared)",
    "general",
    async () => { await brand.fill(""); await p.getByRole("button", { name: "Save Settings" }).click(); return { brandColorShown: brandShown, brandColorSent: "(cleared → undefined → null)" }; },
    /Settings saved successfully\.|Failed to save settings|must be a valid hex/,
    row,
    genVerify,
  );
  await go(p, "general");
  const brandShownAfterClear = await brand.inputValue();
  await run(
    "General · page-level 'Save Settings' (2/2 Brand Color restored)",
    "general",
    async () => { await brand.fill(brandShown); await p.getByRole("button", { name: "Save Settings" }).click(); return { brandColorShownAfterNullSave: brandShownAfterClear, brandColorSent: brandShown }; },
    /Settings saved successfully\.|Failed to save settings|must be a valid hex/,
    row,
    genVerify,
  );

  // ---------- General: per-card Save letterhead ----------
  await go(p, "general");
  const addr = p.getByLabel("Address line 1", { exact: true });
  const saveLh = p.getByRole("button", { name: "Save letterhead" });
  const lhDisabledBefore = await saveLh.isDisabled();
  await run(
    "General · per-card 'Save letterhead'",
    "general",
    async () => { await addr.fill(" "); const en = await saveLh.isEnabled(); await saveLh.click(); return { typed: "one space in Address line 1", saveDisabledWhenPristine: lhDisabledBefore, saveEnabledWhenDirty: en }; },
    /Letterhead saved\.|Fix the highlighted fields|An unexpected error occurred/,
    row,
    async () => { const s = await apiState(); return { letterheadAllNull: Object.values(s.letterhead || {}).every((v) => v === null), addressLine1: s.letterhead && s.letterhead.addressLine1 }; },
  );
  out.letterheadAfterSave = { saveDisabled: await saveLh.isDisabled(), addressLine1Value: await addr.inputValue() };

  // ---------- General: instant-save Select (portal digest cadence), there and back ----------
  await go(p, "general");
  const cad = p.getByRole("combobox", { name: "Portal digest cadence" });
  const original = (await cad.innerText()).trim();
  const other = original === "Weekly" ? "Bi-weekly" : "Weekly";
  for (const [i, target] of [[1, other], [2, original]]) {
    await run(
      `General · instant-save Select '${target}' (${i}/2)`,
      "general",
      async () => {
        // the previous save's message auto-dismisses after 3 s; wait it out so a stale one is not read
        await p.locator("#main-content").getByText("Portal digest cadence updated.").waitFor({ state: "hidden", timeout: 6000 }).catch(() => {});
        await cad.click();
        await p.getByRole("option", { name: target, exact: true }).click();
        return { from: i === 1 ? original : other, to: target };
      },
      /Portal digest cadence updated\.|Failed to update portal digest cadence/,
      card,
      async () => (await apiState()).portalDigestCadence,
    );
    await p.waitForFunction(() => !document.querySelector("#portal-digest-cadence").disabled && !document.querySelector("#portal-digest-cadence").hasAttribute("data-disabled"), null, { timeout: 15000 }).catch(() => {});
  }

  // ---------- Compliance: per-card Save retention period (unchanged value) ----------
  await go(p, "compliance");
  const yrs = await p.getByLabel("Matter retention period (years)").inputValue();
  await run(
    "Compliance · per-card 'Save retention period'",
    "compliance",
    async () => { await p.getByRole("button", { name: "Save retention period" }).click(); return { value: yrs }; },
    /Matter retention period updated\.|Failed to update the matter retention period|unexpected error/,
    row,
    async () => (await apiState()).legalMatterRetentionYears,
  );
  out.complianceSettingsSaveNotClicked = "Compliance › General Settings 'Save Settings' not clicked: dormancyThresholdDays and dataRequestDeadlineDays are null in GET /api/settings; the form displays the 365 / 30 fallbacks and would persist them (compliance.tsx:79-80).";

  // ---------- Phase 93 FICA review-interval inputs (read-only) ----------
  out.fica = await p.evaluate(() => {
    const txt = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
    const fs = document.querySelector('[data-testid="fica-review-intervals"]');
    if (!fs) return null;
    return {
      container: `${fs.tagName.toLowerCase()}[data-testid=fica-review-intervals]`,
      legend: txt(fs.querySelector("legend")),
      description: txt(fs.querySelector("p")),
      inputs: [...fs.querySelectorAll("input")].map((i) => ({ label: txt(document.querySelector(`label[for="${i.id}"]`)), id: i.id, testid: i.dataset.testid, type: i.type, min: i.min, max: i.max, step: i.step, inputMode: i.inputMode, value: i.value })),
      saveButton: "the card's 'Save Settings' (ComplianceSettingsForm.tsx:201) — saves dormancy, deadline and the three intervals together",
      card: txt(fs.closest("div.rounded-lg").querySelector("h2")),
    };
  });
  await shotEl(p.getByTestId("fica-review-intervals"), "fica__light", "Compliance › General Settings — FICA review intervals fieldset, Alice 1440 light");

  // ---------- Features: one switch off → on ----------
  await go(p, "features");
  const sw = p.getByRole("switch", { name: "Toggle Bulk Billing Runs" });
  for (const [i, want] of [[1, "false"], [2, "true"]]) {
    const n0 = actions.length;
    const c0 = consoleSink.length;
    const t0 = Date.now();
    const before = await sw.getAttribute("aria-checked");
    await sw.click();
    let ok = true;
    try {
      await p.waitForFunction(([w]) => { const s = document.querySelector("#feature-switch-bulk_billing"); return s && s.getAttribute("aria-checked") === w && !s.disabled; }, [want], { timeout: 20000 });
    } catch (e) { ok = false; }
    await p.waitForLoadState("networkidle").catch(() => {});
    await p.waitForTimeout(800);
    const alert = await p.locator("#main-content [role=alert]").allInnerTexts();
    const key = `save__features_${i}`;
    await shotEl(p.getByTestId("feature-card-bulk_billing"), key, `Features · Switch 'Bulk Billing Runs' ${before} → ${await sw.getAttribute("aria-checked")} (${i}/2) after ${Date.now() - t0} ms; no status message by design${alert.length ? `; alert: ${alert.join(" ")}` : ""}`);
    const rec = { model: `Features · instant Switch 'Bulk Billing Runs' (${i}/2)`, tab: "features", detail: { before, want }, message: alert.length ? alert.join(" ") : null, messageNote: "FeaturesSettingsForm has no success message (optimistic switch + router.refresh)", ms: Date.now() - t0, stall: !ok, serverActions: actions.slice(n0), console: consoleSink.slice(c0), apiAfter: (await apiState()).modules };
    out.saves.push(rec);
    console.log(JSON.stringify({ model: rec.model, stall: rec.stall, ms: rec.ms, actions: rec.serverActions.map((a) => a.status), apiAfter: rec.apiAfter }));
  }
  await ctx.close();

  // FICA fieldset, dark
  {
    const c2 = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "dark", extra: L.EXTRA });
    const p2 = await c2.newPage();
    L.watch(p2, consoleSink, lab);
    await go(p2, "compliance");
    await shotEl(p2.getByTestId("fica-review-intervals"), "fica__dark", "Compliance › General Settings — FICA review intervals fieldset, Alice 1440 dark");
    await c2.close();
  }
  await browser.close();
  out.apiAfter = await apiState();
  out.restored = JSON.stringify(out.apiBefore) === JSON.stringify(out.apiAfter);
  out.console = consoleSink;
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "saves.json"), JSON.stringify(out));
  console.log("restored", out.restored, "console", consoleSink.length, "tiles", tiles.length);
  if (!out.restored) console.log("BEFORE", JSON.stringify(out.apiBefore), "\nAFTER", JSON.stringify(out.apiAfter));
  console.log("FICA", JSON.stringify(out.fica));
  console.log("KINKS", JSON.stringify(out.kinks));
})();
