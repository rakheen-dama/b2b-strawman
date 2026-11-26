// OBS-after copy of before/scripts/04-saves.cjs. Mechanical changes only (not product behaviour):
//  (1) the lib require path; (2) MSG / the wait also look for the message in the SettingsSection
//  card-footer / SettingsActionBar status slot (665D moved it out of the button's own flex row;
//  the old sibling lookup is tried first); the currency message's card lookup also accepts
//  [data-slot=settings-section]; (3) OBS-before left an inactive "OBS 665D Rate", so the new row is
//  the same-name row that still has a Deactivate button (nth index fixed before deactivating) — the
//  old exact-name locator would hit 2 rows (strict-mode error).
// 665D OBS step 4: one save per save model, through the UI as Alice (1440×900 light). Saves re-submit
// the CURRENT form values where the model allows it; whatever is created is removed again (rates)
// or left in its end state (tax rate — the UI only deactivates).
//   Rates › Default Currency selector   — combobox → pick the current currency (ZAR) → instant save →
//                                          message under the selector.
//   Rates › billing rate dialog         — Carol's row "Add rate for Carol Member" → "Add Rate" (550, today)
//                                          → "Create Rate"; "Edit billing rate for Carol Member" →
//                                          "Edit Billing Rate" (560) → "Save Changes"; "Delete billing rate
//                                          for Carol Member" → alertdialog "Delete Billing Rate" → "Delete".
//   Tax › Tax Configuration "Save Settings" (per-card, current values)
//   Tax › Tax rates                     — "Add Tax Rate" → "OBS 665D Rate" 5% → "Create"; then
//                                          "Deactivate OBS 665D Rate" → alertdialog → "Deactivate".
//   Time recording / Fee runs / Collections › "Save Settings" (form-level, current values)
// For each: the button text, disabled before / during, the message (text, tag, role, aria-live, class,
// where it sits), any sonner toast, the time from click to message, and the API state before / after.
// Writes $RAW/saves.json + tiles; 05-assemble merges them.
const path = require("path");
const fs = require("fs");
const L = require("../../before/scripts/lib.cjs"); // OBS-after copy: shared lib from before/

// (4) The backend rejects a duplicate tax-rate name ("A tax rate with this name already exists.", seen
// at 20:46Z on the first after run), so OBS-after creates "OBS 665D Rate after" (TAX_RATE_NAME).
const RATE = process.env.TAX_RATE_NAME || L.NAMES.taxRate;
const out = { at: new Date().toISOString(), taxRateName: RATE, saves: [] };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };
const log = (o) => { out.saves.push(o); console.log(JSON.stringify(o).slice(0, 900)); };
const shot = async (p, key, caption, opt = {}) => {
  const f = path.join(L.RAW, `${key}.png`);
  await p.screenshot({ path: f, ...opt });
  tiles.push([key, f, caption]);
};
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(400); };
const toasts = (p) => p.locator("[data-sonner-toast]").evaluateAll((els) => els.map((e) => `${e.getAttribute("data-type") || ""}:${(e.innerText || "").replace(/\s+/g, " ").trim()}`));
const MSG = () => {
  const btn = [...document.querySelectorAll("#main-content button")].find((b) => b.textContent.trim() === "Save Settings");
  const row = btn && btn.parentElement;
  const bar = btn && btn.closest('[data-slot="card-footer"],[data-slot="settings-action-bar"]');
  const m = row ? ([...row.children].find((c) => c !== btn && c.tagName === "P") || (bar && bar.querySelector("p"))) : null;
  const card = btn && (btn.closest("div.rounded-lg.border") || btn.closest('[data-slot="settings-section"]'));
  return {
    button: btn ? { text: btn.textContent.trim(), disabled: btn.disabled, insideCard: card ? (card.querySelector("h2") || {}).textContent || "(card)" : null } : null,
    message: m ? { text: m.textContent.trim(), tag: m.tagName.toLowerCase(), role: m.getAttribute("role"), ariaLive: m.getAttribute("aria-live"), cls: m.className, placement: bar ? `${bar.getAttribute("data-slot")} status slot (left of the button), ${card ? "inside the section" : "page-level, below the sections"}` : "right of the Save Settings button (same flex row)", statusWrapperCls: m.parentElement.className } : null,
  };
};

async function saveSettings(p, key, label) {
  const before = await p.evaluate(MSG);
  const t0 = Date.now();
  await p.getByRole("button", { name: "Save Settings" }).click();
  const during = await p.evaluate(MSG);
  let msgAt = null;
  await p.waitForFunction(() => { const b = [...document.querySelectorAll("#main-content button")].find((x) => x.textContent.trim() === "Save Settings"); const m = b && [...b.parentElement.children].find((c) => c !== b && c.tagName === "P"); const bar = b && b.closest('[data-slot="card-footer"],[data-slot="settings-action-bar"]'); return !!(m || (bar && bar.querySelector("p"))) && !b.disabled; }, null, { timeout: 15000, polling: 50 }).then(() => { msgAt = Date.now() - t0; }).catch(() => {});
  await p.waitForTimeout(300);
  const after = await p.evaluate(MSG);
  await shot(p, `save__${key}`, `Alice 1440 light — ${label}: current values → "Save Settings" → "${after.message && after.message.text}" (${msgAt} ms)`, { fullPage: true });
  return { before, duringClick: during, after, msgMs: msgAt, toasts: await toasts(p) };
}

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  const a = await L.idpToken("alice");
  const state = () => L.feesState(a);
  const go = async (tab) => { await p.goto(`${L.BASE}/settings/fees?tab=${tab}`, { waitUntil: "load" }); await settle(p); };

  try {
    // ---- Rates: default currency (instant save on select) ----
    lab.label = "save|rates-currency";
    await go("tariffs");
    const c0 = (await state()).currency;
    const combo = p.locator("#main-content").getByRole("combobox").first();
    const comboText = (await combo.innerText()).trim();
    await combo.click();
    await p.getByPlaceholder("Search currency...").waitFor();
    const cur = c0.defaultCurrency;
    const opt = p.getByRole("option").filter({ hasText: new RegExp(`^${cur} — `) }).first();
    const t0 = Date.now();
    await opt.click();
    let curMsg = null;
    await p.locator("#main-content p").filter({ hasText: /Default currency updated\.|Failed|error/i }).first().waitFor({ timeout: 15000 }).then(() => {}).catch(() => {});
    const curMs = Date.now() - t0;
    curMsg = await p.evaluate(() => { const m = [...document.querySelectorAll("#main-content p")].find((x) => /Default currency updated\.|Failed to update currency|unexpected error/.test(x.textContent)); if (!m) return null; const card = m.closest("div.rounded-lg.border") || m.closest('[data-slot="settings-section"]'); return { text: m.textContent.trim(), tag: "p", role: m.getAttribute("role"), ariaLive: m.getAttribute("aria-live"), cls: m.className, placement: `inside the card "${card && card.querySelector("h2") ? card.querySelector("h2").textContent.trim() : "?"}", under the header row` }; });
    await shot(p, "save__rates-currency", `Alice 1440 light — Rates › Default Currency: re-pick "${comboText}" → "${curMsg && curMsg.text}" (${curMs} ms)`);
    log({ model: "Rates › Default Currency selector (instant save on select)", apiBefore: c0, comboText, picked: cur, message: curMsg, ms: curMs, toasts: await toasts(p), apiAfter: (await state()).currency });

    // ---- Rates: billing-rate add / edit / delete via dialogs (Carol's row) ----
    lab.label = "save|rates-billing";
    await go("tariffs");
    const br0 = (await state()).billingRates;
    const row = p.getByTestId("billing-rate-member");
    await row.getByRole("button", { name: "Add rate for Carol Member" }).click();
    let dlg = p.getByRole("dialog", { name: "Add Rate" });
    await dlg.waitFor();
    const today = new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" });
    await dlg.getByLabel("Hourly Rate").fill("550");
    await dlg.getByLabel("Effective From").fill(today);
    const addCurrency = (await dlg.getByRole("combobox").innerText()).trim();
    await shot(p, "save__rate-add-dialog", `Alice 1440 light — Rates › Carol's row "Add Rate" → dialog "Add Rate" filled (Billing Rate, 550, ${addCurrency}, from ${today})`);
    let t = Date.now();
    await dlg.getByRole("button", { name: "Create Rate" }).click();
    await dlg.waitFor({ state: "hidden", timeout: 15000 });
    await row.getByRole("button", { name: "Edit billing rate for Carol Member" }).waitFor({ timeout: 15000 });
    const addMs = Date.now() - t;
    const addRowText = (await row.innerText()).replace(/\s+/g, " ");
    const addToasts = await toasts(p);
    await shot(p, "save__rate-added", `Alice 1440 light — Rates after Create Rate: Carol's row "${addRowText}" (${addMs} ms); toasts ${JSON.stringify(addToasts)}`);
    const brMid = (await state()).billingRates;
    await row.getByRole("button", { name: "Edit billing rate for Carol Member" }).click();
    dlg = p.getByRole("dialog", { name: "Edit Billing Rate" });
    await dlg.waitFor();
    const editBefore = await dlg.getByLabel("Hourly Rate").inputValue();
    await dlg.getByLabel("Hourly Rate").fill("560");
    t = Date.now();
    await dlg.getByRole("button", { name: "Save Changes" }).click();
    await dlg.waitFor({ state: "hidden", timeout: 15000 });
    await row.filter({ hasText: "560" }).waitFor({ timeout: 15000 });
    const editMs = Date.now() - t;
    const editRowText = (await row.innerText()).replace(/\s+/g, " ");
    const editToasts = await toasts(p);
    await row.getByRole("button", { name: "Delete billing rate for Carol Member" }).click();
    const del = p.getByRole("alertdialog", { name: "Delete Billing Rate" });
    await del.waitFor();
    const delText = (await del.innerText()).replace(/\s+/g, " ");
    await shot(p, "save__rate-delete-confirm", `Alice 1440 light — Rates › "Delete billing rate for Carol Member" → alertdialog "Delete Billing Rate"`);
    t = Date.now();
    await del.getByRole("button", { name: "Delete", exact: true }).click();
    await del.waitFor({ state: "hidden", timeout: 15000 });
    await row.getByRole("button", { name: "Add rate for Carol Member" }).waitFor({ timeout: 15000 });
    const delMs = Date.now() - t;
    log({ model: "Rates › billing rate dialog add / edit / AlertDialog delete", apiBefore: br0, add: { dialog: "Add Rate", button: "Create Rate", currencyPreselected: addCurrency, ms: addMs, rowText: addRowText, toasts: addToasts, message: "none — dialog closes, row updates" }, apiAfterAdd: brMid, edit: { dialog: "Edit Billing Rate", valueBefore: editBefore, button: "Save Changes", ms: editMs, rowText: editRowText, toasts: editToasts }, delete: { dialog: "Delete Billing Rate", text: delText, button: "Delete", ms: delMs, toasts: await toasts(p) }, apiAfter: (await state()).billingRates });

    // ---- Tax configuration (per-card Save Settings) ----
    lab.label = "save|tax";
    await go("tax");
    const tx0 = (await state()).tax;
    const formTax = { regNumber: await p.locator("#tax-registration-number").inputValue(), regLabel: await p.locator("#tax-registration-label").inputValue(), taxLabel: await p.locator("#tax-label").inputValue(), inclusive: await p.locator("#tax-inclusive").getAttribute("aria-checked") };
    const taxSave = await saveSettings(p, "tax-settings", "Tax › Tax Configuration");
    log({ model: "Tax › Tax Configuration Save Settings (per-card)", apiBefore: tx0, formValues: formTax, ...taxSave, apiAfter: (await state()).tax });

    // ---- Tax rate create → deactivate ----
    lab.label = "save|tax-rate";
    const tr0 = (await state()).taxRates;
    await p.getByRole("button", { name: "Add Tax Rate" }).first().click();
    dlg = p.getByRole("dialog", { name: "Add Tax Rate" });
    await dlg.waitFor();
    await dlg.getByLabel("Name", { exact: true }).fill(RATE);
    await dlg.getByLabel("Rate (%)").fill("5");
    await shot(p, "save__tax-rate-create-dialog", `Alice 1440 light — Tax › "Add Tax Rate" → dialog filled ("${RATE}", 5%)`);
    t = Date.now();
    await dlg.getByRole("button", { name: "Create", exact: true }).click();
    await dlg.waitFor({ state: "hidden", timeout: 15000 });
    const sameName = p.getByTestId("tax-rate-row").filter({ has: p.getByRole("cell", { name: RATE, exact: true }) });
    await sameName.filter({ has: p.getByRole("button", { name: `Deactivate ${RATE}` }) }).waitFor({ timeout: 15000 });
    const newIdx = await sameName.evaluateAll((rs, label) => rs.findIndex((r) => [...r.querySelectorAll("button")].some((b) => b.textContent.trim() === label)), `Deactivate ${RATE}`);
    const newRow = sameName.nth(newIdx);
    const createMs = Date.now() - t;
    const createRowText = (await newRow.innerText()).replace(/\s+/g, " ");
    const createToasts = await toasts(p);
    await newRow.getByRole("button", { name: `Deactivate ${RATE}` }).click();
    const dd = p.getByRole("alertdialog", { name: "Deactivate Tax Rate" });
    await dd.waitFor();
    const ddText = (await dd.innerText()).replace(/\s+/g, " ");
    await shot(p, "save__tax-rate-deactivate-confirm", `Alice 1440 light — Tax › "Deactivate ${RATE}" → alertdialog "Deactivate Tax Rate"`);
    t = Date.now();
    await dd.getByRole("button", { name: "Deactivate", exact: true }).click();
    await dd.waitFor({ state: "hidden", timeout: 15000 });
    await newRow.filter({ hasText: "Inactive" }).waitFor({ timeout: 15000 });
    const deMs = Date.now() - t;
    const deRowText = (await newRow.innerText()).replace(/\s+/g, " ");
    const deButtons = await newRow.getByRole("button").evaluateAll((bs) => bs.map((b) => b.textContent.trim()));
    await shot(p, "save__tax-rate-deactivated", `Alice 1440 light — Tax after Deactivate: "${deRowText}" (${deMs} ms) — the row stays, Inactive; buttons left: ${JSON.stringify(deButtons)}`, { fullPage: true });
    log({ model: "Tax › tax rate dialog create / AlertDialog deactivate", apiBefore: tr0.map((r) => `${r.name}:${r.active}`), create: { dialog: "Add Tax Rate", button: "Create", ms: createMs, rowText: createRowText, toasts: createToasts, message: "none — dialog closes, row appears" }, deactivate: { dialog: "Deactivate Tax Rate", text: ddText, button: "Deactivate", ms: deMs, rowText: deRowText, buttonsLeft: deButtons, toasts: await toasts(p) }, apiAfter: (await state()).taxRates.filter((r) => r.name === RATE) });

    // ---- Time recording / Fee runs / Collections (form-level Save Settings, current values) ----
    for (const [tab, k, label] of [["time-recording", "timeRecording", "Time recording"], ["fee-runs", "feeRuns", "Fee runs (batch billing)"], ["collections", "collections", "Collections"]]) {
      lab.label = `save|${tab}`;
      await go(tab);
      const s0 = (await state())[k];
      const form = await p.evaluate(() => [...document.querySelectorAll('#main-content input,#main-content [role="switch"],#main-content [role="checkbox"]')].map((e) => `${e.id || e.getAttribute("aria-label") || "?"}=${e.tagName === "BUTTON" ? e.getAttribute("aria-checked") : e.value}`));
      const r = await saveSettings(p, tab, label);
      log({ model: `${label} › Save Settings (form-level, current values)`, apiBefore: s0, formValues: form, ...r, apiAfter: (await state())[k] });
    }
  } finally {
    await ctx.close();
    await browser.close();
    out.console = consoleSink;
    out.tiles = tiles;
    fs.writeFileSync(path.join(L.RAW, "saves.json"), JSON.stringify(out));
    console.log("console", consoleSink.length, "tiles", tiles.length);
  }
})();
