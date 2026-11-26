// 665B OBS step 4: Data protection + Data requests interactions (Alice, 1440×900 light unless noted).
// One save per reversible save model, everything else opened and cancelled:
//  - Jurisdiction (Radix Select, saves on change): listbox opened, options read, Escape. NOT saved —
//    the stored value is ZA and EU/BR are disabled, so there is no other value to pick, and Radix
//    does not fire onValueChange for the current value. The first-selection AlertDialog is
//    unreachable (only when the jurisdiction is null).
//  - Information officer (per-card Save, disabled while pristine): name + " (665B)" → Save, then the
//    original name → Save (restored). Message element + timing recorded each time.
//  - Retention policy row (per-row icon Save): the "Audit Event" row (stored FLAG, which the select
//    can show) saved unchanged. The "delete" rows (Time Entry / Document / Comment) are NOT saved:
//    their select shows Anonymize for a stored "delete" (recorded as a defect crop).
//  - Execute Purge: window.confirm read and DISMISSED. Run Retention Check: not clicked (it writes
//    lastEvaluatedAt, RetentionService.java:94). Generate PAIA Manual: not clicked (creates a document).
//  - Processing register: Export Register (toast only), Add Activity dialog opened → Cancel, first
//    row Edit → "Update" unchanged (dialog save, restored by definition), first row Delete →
//    window.confirm DISMISSED.
//  - DSAR link "View DSAR Requests →" followed. Data requests: "Log New Request" dialog fields read →
//    Cancel; row actions read, not clicked.
//  - 1024×768: Add Activity / Edit / Log DSAR dialogs measured (submit in viewport?).
// Verifies the restore with GET /api/settings, /retention-policies, /processing-activities,
// /data-requests (values, not updatedAt). Output: $RAW/dataprot.json + tiles.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const R = { capturedAt: new Date().toISOString(), saves: [], dialogs: {}, notSaved: [] };
const consoleSink = [];
const tiles = [];
const lab = { label: "" };
const DP = `${L.BASE}/settings/firm?tab=data-protection`;
const DR = `${L.BASE}/settings/firm?tab=data-requests`;
const shot = async (target, key, caption, opt = {}) => {
  const f = path.join(L.RAW, `${key}.png`);
  await target.screenshot({ path: f, ...opt });
  tiles.push([key, f, caption]);
};
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(400); };
const card = (p, h2) => p.locator("div.rounded-lg.border").filter({ has: p.getByRole("heading", { level: 2, name: h2, exact: true }) }).first();

async function snapshot() {
  const t = await L.idpToken("alice");
  const s = (await L.apiGet(t, "/api/settings")).json || {};
  return {
    settings: { j: s.dataProtectionJurisdiction, n: s.informationOfficerName, e: s.informationOfficerEmail },
    retention: ((await L.apiGet(t, "/api/settings/retention-policies")).json || []).map((x) => `${x.recordType}:${x.retentionDays}:${x.action}:${x.active}:${x.lastEvaluatedAt}`),
    processing: (((await L.apiGet(t, "/api/settings/processing-activities?size=100")).json || {}).content || []).map((x) => `${x.category}|${x.description}|${x.legalBasis}|${x.dataSubjects}|${x.retentionPeriod}|${x.recipients}`),
    dsar: ((await L.apiGet(t, "/api/data-requests")).json || []).map((x) => `${x.id}:${x.status}`),
  };
}
const DIALOG = () => {
  const d = document.querySelector('[role="dialog"],[role="alertdialog"]');
  if (!d) return null;
  const txt = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
  const b = d.getBoundingClientRect();
  const labelFor = (e) => { if (e.id) { const l = d.querySelector(`label[for="${CSS.escape(e.id)}"]`); if (l) return txt(l); } return e.getAttribute("aria-label"); };
  const buttons = [...d.querySelectorAll("button")].map((x) => ({ name: x.getAttribute("aria-label") || txt(x), disabled: x.disabled, type: x.type }));
  const submit = [...d.querySelectorAll('button[type="submit"]')][0] || [...d.querySelectorAll("button")].reverse().find((x) => /^(Log Request|Add Activity|Update|Confirm|Save)/.test(txt(x)));
  const sb = submit ? submit.getBoundingClientRect() : null;
  return {
    role: d.getAttribute("role"),
    title: txt(d.querySelector("h2") || d),
    description: d.querySelector("p") ? txt(d.querySelector("p")) : null,
    fields: [...d.querySelectorAll("input,textarea,select")].map((e) => ({ tag: e.tagName.toLowerCase(), type: e.type, id: e.id, name: e.name || undefined, label: labelFor(e), placeholder: e.placeholder || undefined, value: e.value, required: e.required || undefined, options: e.tagName === "SELECT" ? [...e.options].map((o) => `${o.value}=${txt(o)}${o.selected ? "*" : ""}`) : undefined })),
    buttons,
    box: { top: Math.round(b.top), bottom: Math.round(b.bottom), height: Math.round(b.height), viewport: [innerWidth, innerHeight], overflowY: getComputedStyle(d).overflowY, maxHeight: getComputedStyle(d).maxHeight },
    submit: sb ? { text: txt(submit), top: Math.round(sb.top), bottom: Math.round(sb.bottom), inViewport: sb.top >= 0 && sb.bottom <= innerHeight } : null,
  };
};
async function waitMsg(p, re, timeout = 15000) {
  const t0 = Date.now();
  const loc = p.getByText(re).first();
  await loc.waitFor({ state: "visible", timeout });
  const ms = Date.now() - t0;
  const el = await loc.evaluate((e) => ({ tag: e.tagName.toLowerCase(), role: e.getAttribute("role"), cls: e.className, text: e.innerText.trim(), ariaLive: e.getAttribute("aria-live") || (e.closest("[aria-live]") || {}).getAttribute?.("aria-live") || null }));
  return { ms, el, loc };
}

(async () => {
  R.before = await snapshot();
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  const actions = [];
  p.on("request", (r) => { if (r.method() === "POST" && r.headers()["next-action"]) actions.push({ phase: lab.label, url: r.url().replace(L.APP, "") }); });
  p.on("response", (r) => { if (r.request().method() === "POST" && r.request().headers()["next-action"]) { const a = actions.find((x) => !x.status && x.phase === lab.label); if (a) a.status = r.status(); } });

  // A. Jurisdiction
  lab.label = "jurisdiction";
  await p.goto(DP, { waitUntil: "load" });
  await settle(p);
  const jur = card(p, "Jurisdiction");
  const trig = jur.getByRole("combobox");
  R.jurisdiction = { trigger: (await trig.innerText()).trim(), ariaLabel: await trig.getAttribute("aria-label"), labelledBy: await trig.getAttribute("aria-labelledby") };
  const jurBox = await jur.boundingBox(); // before opening: an open Radix Select aria-hides the page
  await trig.click();
  await p.getByRole("listbox").waitFor();
  R.jurisdiction.options = await p.evaluate(() => [...document.querySelectorAll('[role="option"]')].map((o) => ({ text: o.innerText.replace(/\s+/g, " ").trim(), disabled: o.getAttribute("aria-disabled") === "true" || o.hasAttribute("data-disabled"), selected: o.getAttribute("aria-selected") })));
  await shot(p, "dp__jurisdiction-open", `Jurisdiction select open: ${R.jurisdiction.options.map((o) => `${o.text}${o.disabled ? " (disabled)" : ""}${o.selected === "true" ? " ✓" : ""}`).join(" / ")}`, { clip: { x: jurBox.x, y: jurBox.y, width: jurBox.width, height: jurBox.height + 140 } });
  await p.keyboard.press("Escape");
  R.notSaved.push({ model: "Jurisdiction (Select, saves on change)", why: "stored ZA; EU/BR disabled; re-selecting the current value fires nothing; first-selection AlertDialog needs a null jurisdiction" });

  // B. Information officer: change → Save → restore → Save
  lab.label = "officer";
  const off = card(p, "Information Officer");
  const nameIn = p.locator("#officer-name");
  const saveBtn = off.getByRole("button", { name: "Save", exact: true });
  const orig = await nameIn.inputValue();
  R.officer = { original: orig, saveDisabledPristine: await saveBtn.isDisabled() };
  for (const [i, val] of [[1, `${orig} (665B)`], [2, orig]]) {
    await nameIn.fill(val);
    const enabled = await saveBtn.isEnabled();
    const t0 = Date.now();
    await saveBtn.click();
    const m = await waitMsg(p, "Saved successfully.");
    await p.waitForTimeout(300);
    await shot(off, `save__officer-${i}`, `Information Officer save ${i} (${i === 1 ? "name + ' (665B)'" : "restored"}): "${m.el.text}" <${m.el.tag} class="${m.el.cls}"> role=${m.el.role}, ${m.ms} ms; Save ${await saveBtn.isDisabled() ? "disabled" : "enabled"} after`);
    R.saves.push({ model: "Information Officer — per-card Save", step: i, value: val, saveEnabledBeforeClick: enabled, message: m.el, ms: m.ms, totalMs: Date.now() - t0, saveDisabledAfter: await saveBtn.isDisabled(), messageStillVisibleAfter2s: await p.waitForTimeout(2000).then(() => m.loc.isVisible()) });
  }

  // C. Retention row: Audit Event saved unchanged; mismatch evidence for the "delete" rows.
  lab.label = "retention";
  await p.goto(DP, { waitUntil: "load" });
  await settle(p);
  const ret = card(p, "Retention Policies");
  R.retentionSelects = await ret.evaluate((c) => [...c.querySelectorAll("tbody tr")].map((tr) => { const s = tr.querySelector("select"); const n = tr.querySelector('input[type="number"]'); return { row: tr.children[0].innerText.trim(), days: n && n.value, selectValue: s && s.value, selectShows: s && s.options[s.selectedIndex] ? s.options[s.selectedIndex].text : null, lastEvaluated: tr.children[4].innerText.trim() }; }));
  await shot(ret, "defect__retention-delete-shown-as-anonymize", `DEFECT crop — Retention Policies: Time Entry / Document / Comment are stored action "delete" (ZA seed, RetentionService.java:293-296) but the Action select (ANONYMIZE | FLAG only) shows "${(R.retentionSelects[1] || {}).selectShows}"; a row Save would persist ANONYMIZE`);
  const rowSave = p.getByRole("button", { name: "Save Audit Event policy" });
  const t0 = Date.now();
  await rowSave.click();
  const sampled = [];
  for (let i = 0; i < 8; i++) { sampled.push({ ms: Date.now() - t0, disabled: await rowSave.isDisabled(), spinner: await rowSave.locator(".animate-spin").count() }); await p.waitForTimeout(100); }
  await p.waitForTimeout(800);
  R.saves.push({ model: "Retention policy row — per-row icon Save (Audit Event, unchanged)", samples: sampled, alertsAfter: await ret.getByRole("alert").allInnerTexts(), messageNearTable: (await ret.innerText()).match(/saved|updated|success/i) ? "has saved/updated text" : "no message", actions: actions.filter((a) => a.phase === "retention") });
  await shot(ret, "save__retention-row", `Retention row save (Audit Event unchanged): no message by design; button disabled+spinner while saving (${JSON.stringify(sampled.slice(0, 2))})`);
  // Execute Purge confirm → dismiss
  lab.label = "purge-confirm";
  // A native confirm blocks the click until handled: record + dismiss inside the handler.
  p.once("dialog", async (d) => { R.dialogs.executePurge = { type: d.type(), message: d.message() }; await d.dismiss(); });
  await ret.getByRole("button", { name: "Execute Purge" }).click();
  await p.waitForTimeout(500);
  R.dialogs.executePurge.afterDismissMessages = await ret.locator("p.mt-3").allInnerTexts();
  R.notSaved.push({ model: "Run Retention Check", why: "writes lastEvaluatedAt (RetentionService.java:94) — not reversible" }, { model: "Execute Purge", why: "destructive — window.confirm read and dismissed" });

  // D. Processing register
  lab.label = "processing";
  const proc = card(p, "Processing Register");
  await proc.getByRole("button", { name: "Export Register" }).click();
  const toast = p.locator("[data-sonner-toast]").first();
  await toast.waitFor({ timeout: 5000 }).catch(() => {});
  R.exportRegister = { toast: (await toast.count()) ? { text: (await toast.innerText()).trim(), type: await toast.getAttribute("data-type") } : null };
  await proc.getByRole("button", { name: "Add Activity" }).click();
  await p.getByRole("dialog").waitFor();
  await p.waitForTimeout(400);
  R.dialogs.addActivity = await p.evaluate(DIALOG);
  await shot(p.getByRole("dialog"), "dlg__add-activity", `Add Processing Activity dialog (1440×900): ${R.dialogs.addActivity.fields.length} fields; submit "${R.dialogs.addActivity.submit && R.dialogs.addActivity.submit.text}"`);
  await p.getByRole("dialog").getByRole("button", { name: "Cancel" }).click();
  await p.getByRole("dialog").waitFor({ state: "hidden" });
  const firstCat = (await proc.locator("tbody tr").first().locator("td").first().innerText()).trim();
  await proc.getByRole("button", { name: `Edit ${firstCat}`, exact: true }).click();
  await p.getByRole("dialog").waitFor();
  await p.waitForTimeout(400);
  R.dialogs.editActivity = await p.evaluate(DIALOG);
  const te = Date.now();
  await p.getByRole("dialog").getByRole("button", { name: "Update" }).click();
  await p.getByRole("dialog").waitFor({ state: "hidden", timeout: 15000 });
  await settle(p);
  R.saves.push({ model: `Processing activity — Edit dialog "Update" (${firstCat}, unchanged)`, ms: Date.now() - te, message: (await p.locator("[data-sonner-toast]").count()) ? await p.locator("[data-sonner-toast]").allInnerTexts() : "no toast / no inline message; dialog closes + router.refresh", actions: actions.filter((a) => a.phase === "processing") });
  lab.label = "processing-delete-confirm";
  p.once("dialog", async (d) => { R.dialogs.deleteActivity = { type: d.type(), message: d.message() }; await d.dismiss(); });
  await proc.getByRole("button", { name: `Delete ${firstCat}`, exact: true }).click();
  await p.waitForTimeout(400);
  R.notSaved.push({ model: "Processing activity Delete", why: "destructive — window.confirm read and dismissed" }, { model: "Generate PAIA Manual", why: "creates a generated document — not reversible" });
  // PAIA state + DSAR link
  const paia = card(p, "PAIA Manual");
  R.paia = { generateEnabled: await paia.getByRole("button", { name: "Generate PAIA Manual" }).isEnabled(), warning: await paia.getByText(/Jurisdiction required/).count() };
  lab.label = "dsar-link";
  const link = p.getByRole("link", { name: /View DSAR Requests/ });
  R.dsarLink = { href: await link.getAttribute("href") };
  await Promise.all([p.waitForURL(/tab=data-requests/), link.click()]);
  await settle(p);
  R.dsarLink.landed = p.url().replace(L.BASE, "");
  R.dsarLink.h1 = await p.locator("#main-content h1").allInnerTexts();

  // E. Data requests: Log New Request dialog → Cancel; row actions
  lab.label = "data-requests";
  await p.goto(DR, { waitUntil: "load" });
  await settle(p);
  R.dsarRowActions = await p.locator("#main-content tbody tr").first().evaluate((tr) => ({ cells: [...tr.children].map((c) => c.innerText.replace(/\s+/g, " ").trim()), buttons: [...tr.querySelectorAll("button")].map((b) => ({ ariaLabel: b.getAttribute("aria-label"), text: b.innerText.trim() })) }));
  await p.getByRole("button", { name: "Log New Request" }).click();
  await p.getByRole("dialog", { name: "Log DSAR Request" }).waitFor();
  await p.waitForTimeout(400);
  R.dialogs.logDsar = await p.evaluate(DIALOG);
  const dl = p.getByRole("dialog", { name: "Log DSAR Request" });
  R.selCompat = {
    "dp-settings.spec:186 dialog 'Log DSAR Request'": await p.getByRole("dialog", { name: "Log DSAR Request" }).count(),
    "dp-settings.spec:188 dialog label 'Subject Name' (strict fill)": await dl.getByLabel("Subject Name").count(),
    "dp-settings.spec:189 dialog label 'Client ID' (strict fill)": await dl.getByLabel("Client ID").count(),
    "dp-settings.spec:190 dialog button 'Log Request' (strict)": await dl.getByRole("button", { name: "Log Request" }).count(),
  };
  await shot(dl, "dlg__log-dsar", `Log DSAR Request dialog (1440×900): ${R.dialogs.logDsar.fields.map((f) => f.label).join(" / ")}`);
  await dl.getByRole("button", { name: "Cancel" }).click();
  await dl.waitFor({ state: "hidden" });
  await ctx.close();

  // F. 1024×768 dialog measurements (light) — open + Escape only.
  const c2 = await L.newCtx(browser, S, { width: 1024, height: 768, theme: "light", extra: L.EXTRA });
  const p2 = await c2.newPage();
  L.watch(p2, consoleSink, lab);
  lab.label = "dialogs-1024";
  R.dialogs1024 = {};
  await p2.goto(DP, { waitUntil: "load" });
  await settle(p2);
  for (const [k, open] of [["addActivity", () => card(p2, "Processing Register").getByRole("button", { name: "Add Activity" }).click()], ["editActivity", () => card(p2, "Processing Register").locator("tbody tr").first().getByRole("button", { name: /^Edit / }).click()]]) {
    await open();
    await p2.getByRole("dialog").waitFor();
    await p2.waitForTimeout(400);
    const m = await p2.evaluate(DIALOG);
    R.dialogs1024[k] = { box: m.box, submit: m.submit };
    await p2.keyboard.press("Escape");
    await p2.getByRole("dialog").waitFor({ state: "hidden" });
  }
  await p2.goto(DR, { waitUntil: "load" });
  await settle(p2);
  await p2.getByRole("button", { name: "Log New Request" }).click();
  await p2.getByRole("dialog").waitFor();
  await p2.waitForTimeout(400);
  const m3 = await p2.evaluate(DIALOG);
  R.dialogs1024.logDsar = { box: m3.box, submit: m3.submit };
  await shot(p2, "dlg__log-dsar-1024", `Log DSAR Request at 1024×768: dialog ${m3.box.top}–${m3.box.bottom}, submit "${m3.submit && m3.submit.text}" ${m3.submit && m3.submit.inViewport ? "in" : "OUT OF"} viewport`);
  await p2.keyboard.press("Escape");
  await c2.close();
  await browser.close();

  R.after = await snapshot();
  R.restored = JSON.stringify(R.before) === JSON.stringify(R.after);
  R.serverActions = actions;
  R.console = consoleSink;
  R.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "dataprot.json"), JSON.stringify(R));
  const { tiles: _t, console: _c, before, after, ...rest } = R;
  console.log(JSON.stringify(rest, null, 1).slice(0, 9000));
  console.log("restored", R.restored, "console", consoleSink.length, JSON.stringify(consoleSink).slice(0, 800));
  if (!R.restored) console.log("BEFORE", JSON.stringify(before), "\nAFTER", JSON.stringify(after));
})();
