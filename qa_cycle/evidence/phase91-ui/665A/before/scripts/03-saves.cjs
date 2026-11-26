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
    return { text: (await loc.innerText()).trim(), ms: Date.now() - t0, tag: await loc.evaluate((e) => `${e.tagName.toLowerCase()}${e.getAttribute("role") ? `[role=${e.getAttribute("role")}]` : ""}.${e.className}`), loc };
  } catch (e) {
    return { stall: true, ms: Date.now() - t0 };
  }
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
        return { role: txt(h), text: txt(card), cardRight: Math.round(cb.right), cardWidth: Math.round(cb.width), badges: badges.map((b) => ({ text: txt(b), right: Math.round(b.getBoundingClientRect().right), whiteSpace: getComputedStyle(b).whiteSpace })), maxBadgeRight: badges.length ? Math.max(...badges.map((b) => Math.round(b.getBoundingClientRect().right))) : null };
      });
    });
    out.kinks.rolesPageHOverflow1024 = await p.evaluate(() => document.scrollingElement.scrollWidth - document.scrollingElement.clientWidth);
    await go(p, "subscription");
    out.kinks.subscription = await p.evaluate(() => {
      const txt = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
      const main = document.querySelector("#main-content");
      return { h1: [...main.querySelectorAll("h1")].map(txt), tabLabel: txt(main.querySelector('[role="tab"][aria-selected="true"]')), chips: [...main.querySelectorAll('[data-slot="badge"]')].map(txt), body: [...main.querySelectorAll('[data-slot="card"] p')].map(txt) };
    });
    await ctx.close();
  }

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
    const rec = { model, tab, detail, message: msg.stall ? null : msg.text, messageElement: msg.tag, ms: msg.ms, stall: !!msg.stall, serverActions: actions.slice(n0), console: consoleSink.slice(c0), apiAfter: verify ? await verify() : undefined };
    out.saves.push(rec);
    console.log(JSON.stringify({ model, message: rec.message, ms: rec.ms, stall: rec.stall, actions: rec.serverActions.map((a) => a.status), apiAfter: rec.apiAfter }));
  };
  const row = (loc) => loc.locator("xpath=..");
  const card = (loc) => loc.locator("xpath=ancestor::div[contains(@class,'rounded-lg')][1]");

  // ---------- General: page-level Save Settings ----------
  await go(p, "general");
  const brand = p.getByLabel("Brand Color", { exact: true });
  const brandShown = await brand.inputValue();
  await run(
    "General · page-level 'Save Settings'",
    "general",
    async () => { await brand.fill(""); await p.getByRole("button", { name: "Save Settings" }).click(); return { brandColorShown: brandShown, brandColorSent: "(cleared → undefined → null)" }; },
    /Settings saved successfully\.|Failed to save settings|must be a valid hex/,
    row,
    async () => { const s = await apiState(); return { brandColor: s.brandColor, documentFooterText: s.documentFooterText, defaultCurrency: s.defaultCurrency, taxLabel: s.taxLabel, taxInclusive: s.taxInclusive, taxRegistrationNumber: s.taxRegistrationNumber }; },
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
