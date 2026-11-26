// 665C OBS step 4: one reversible save per save model, through the UI as Alice (1440×900 light).
//   Naming › "Save Settings"            — pattern "" → "OBS-{YYYY}-{SEQ}" → Save → message; then "" → Save
//                                          (the action sends `pattern || null`, so null is restored).
//   Request settings › "Save Settings"  — 7 → 8 → Save → message; then 8 → 7 → Save. NOTE: the stored
//                                          value was null (the form shows the default 7); the UI has no
//                                          path back to null, so it ends as 7 (same effective value).
//   Tags                                — "Add Tag" → Create Tag "OBS 665C Tag" (#0D9488) → Create; its
//                                          "Edit" → name + " (edited)" → Save; its "Delete" → Delete.
//   Custom fields › Field groups        — Matters entity tab: "Add Group" → "OBS 665C Group" → Create
//                                          Group; "Actions for OBS 665C Group" → Delete → Delete.
// For each: the button text, disabled before / during, the message (text, tag, role, aria-live, class),
// any sonner toast, the time from click to message, and the API value before / after.
// Writes $RAW/saves.json + tiles; 05-assemble merges them.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const out = { at: new Date().toISOString(), saves: [] };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };
const log = (o) => { out.saves.push(o); console.log(JSON.stringify(o)); };
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
  const sec = btn && btn.closest('[data-slot="settings-section"]');
  const m = (row ? [...row.children].find((c) => c !== btn && c.tagName === "P") : null) || (sec && sec.querySelector('[data-slot="card-footer"] p')) || null;
  return { button: btn ? { text: btn.textContent.trim(), disabled: btn.disabled } : null, message: m ? { text: m.textContent.trim(), tag: m.tagName.toLowerCase(), role: m.getAttribute("role"), ariaLive: m.getAttribute("aria-live"), cls: m.className } : null };
};

async function saveSettings(p, key, inputSel, value, label) {
  const before = await p.evaluate(MSG);
  await p.locator(inputSel).fill(String(value));
  const t0 = Date.now();
  const btn = p.getByRole("button", { name: "Save Settings" });
  await btn.click();
  const during = await p.evaluate(MSG);
  let msgAt = null;
  await p.waitForFunction(() => { const b = [...document.querySelectorAll("#main-content button")].find((x) => x.textContent.trim() === "Save Settings"); const m = b && ([...b.parentElement.children].find((c) => c !== b && c.tagName === "P") || (b.closest('[data-slot="settings-section"]') && b.closest('[data-slot="settings-section"]').querySelector('[data-slot="card-footer"] p'))); return !!m; }, null, { timeout: 15000, polling: 50 }).then(() => { msgAt = Date.now() - t0; }).catch(() => {});
  await p.waitForTimeout(300);
  const after = await p.evaluate(MSG);
  await shot(p, `save__${key}`, `Alice 1440 light — ${label}: value ${JSON.stringify(value)} → "Save Settings" → "${after.message && after.message.text}"`);
  return { before, duringClick: during, after, msgMs: msgAt, toasts: await toasts(p) };
}

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  const a = await L.idpToken("alice");
  const settings = async () => { const s = (await L.apiGet(a, "/api/settings")).json || {}; return { projectNamingPattern: s.projectNamingPattern ?? null, defaultRequestReminderDays: s.defaultRequestReminderDays ?? null }; };

  // Naming
  lab.label = "save|naming";
  await p.goto(`${L.BASE}/settings/matters?tab=naming`, { waitUntil: "load" });
  await settle(p);
  const n0 = await settings();
  const inputN = await p.locator("#project-naming-pattern").inputValue();
  const n1 = await saveSettings(p, "naming-change", "#project-naming-pattern", "OBS-{YYYY}-{SEQ}", "Matters › Naming");
  const nMid = await settings();
  const n2 = await saveSettings(p, "naming-restore", "#project-naming-pattern", inputN, "Matters › Naming (restore)");
  log({ model: "Naming › Save Settings (per-card)", apiBefore: n0.projectNamingPattern, inputBefore: inputN, change: n1, apiAfterChange: nMid.projectNamingPattern, restore: n2, apiAfterRestore: (await settings()).projectNamingPattern });

  // Request settings
  lab.label = "save|request-settings";
  await p.goto(`${L.BASE}/settings/matters?tab=request-settings`, { waitUntil: "load" });
  await settle(p);
  const r0 = await settings();
  const inputR = await p.locator("#reminder-interval-days").inputValue();
  const r1 = await saveSettings(p, "request-settings-change", "#reminder-interval-days", 8, "Matters › Request settings");
  const rMid = await settings();
  const r2 = await saveSettings(p, "request-settings-restore", "#reminder-interval-days", inputR, "Matters › Request settings (restore)");
  log({ model: "Request settings › Save Settings (per-card)", apiBefore: r0.defaultRequestReminderDays, inputBefore: inputR, change: r1, apiAfterChange: rMid.defaultRequestReminderDays, restore: r2, apiAfterRestore: (await settings()).defaultRequestReminderDays });

  // Tags: create → edit → delete
  lab.label = "save|tags";
  const TAG = "OBS 665C Tag";
  await p.goto(`${L.BASE}/settings/matters?tab=tags`, { waitUntil: "load" });
  await settle(p);
  const tagsBefore = (L.list((await L.apiGet(a, "/api/tags")).json)).map((t) => t.name);
  await p.getByRole("button", { name: "Add Tag" }).first().click();
  let dlg = p.getByRole("dialog", { name: "Create Tag" });
  await dlg.waitFor();
  await dlg.getByLabel("Name", { exact: true }).fill(TAG);
  await dlg.getByLabel("Color (optional)").fill("#0D9488");
  const createBtn = dlg.getByRole("button", { name: "Create", exact: true });
  const createDisabled = await createBtn.isDisabled();
  await shot(p, "save__tag-create-dialog", `Alice 1440 light — Tags › Add Tag → Create Tag dialog filled ("${TAG}", #0D9488)`);
  let t0 = Date.now();
  await createBtn.click();
  await dlg.waitFor({ state: "hidden", timeout: 15000 });
  await p.getByRole("cell", { name: TAG, exact: true }).waitFor({ timeout: 15000 });
  const createMs = Date.now() - t0;
  const createToasts = await toasts(p);
  await shot(p, "save__tag-created", `Alice 1440 light — Tags after Create: row "${TAG}" listed (${createMs} ms); toasts ${JSON.stringify(createToasts)}`);
  await p.getByRole("button", { name: `Edit ${TAG}`, exact: true }).click();
  dlg = p.getByRole("dialog", { name: "Edit Tag" });
  await dlg.waitFor();
  await dlg.getByLabel("Name", { exact: true }).fill(`${TAG} (edited)`);
  t0 = Date.now();
  await dlg.getByRole("button", { name: "Save", exact: true }).click();
  await dlg.waitFor({ state: "hidden", timeout: 15000 });
  await p.getByRole("cell", { name: `${TAG} (edited)`, exact: true }).waitFor({ timeout: 15000 });
  const editMs = Date.now() - t0;
  const editToasts = await toasts(p);
  await p.getByRole("button", { name: `Delete ${TAG} (edited)`, exact: true }).click();
  const del = p.getByRole("alertdialog", { name: "Delete Tag" });
  await del.waitFor();
  const delText = (await del.innerText()).replace(/\s+/g, " ");
  await shot(p, "save__tag-delete-confirm", `Alice 1440 light — Tags › Delete "${TAG} (edited)" → AlertDialog "Delete Tag"`);
  t0 = Date.now();
  await del.getByRole("button", { name: "Delete", exact: true }).click();
  await del.waitFor({ state: "hidden", timeout: 15000 });
  await p.getByRole("cell", { name: `${TAG} (edited)`, exact: true }).waitFor({ state: "detached", timeout: 15000 });
  const delMs = Date.now() - t0;
  log({ model: "Tags › dialog create / edit / AlertDialog delete", tagsBefore, create: { dialog: "Create Tag", button: "Create", disabledWhenFilled: createDisabled, ms: createMs, toasts: createToasts, message: "none — dialog closes, row appears" }, edit: { dialog: "Edit Tag", button: "Save", ms: editMs, toasts: editToasts }, delete: { dialog: "Delete Tag", text: delText, button: "Delete", ms: delMs, toasts: await toasts(p) }, tagsAfter: L.list((await L.apiGet(a, "/api/tags")).json).map((t) => t.name) });

  // Custom fields: field group create → delete (Matters entity tab)
  lab.label = "save|custom-fields";
  const GRP = "OBS 665C Group";
  await p.goto(`${L.BASE}/settings/matters?tab=custom-fields`, { waitUntil: "load" });
  await settle(p);
  const groupsBefore = L.list((await L.apiGet(a, "/api/field-groups?entityType=PROJECT")).json).map((g) => g.name);
  const innerSelected = await p.locator('#main-content [role="tablist"]:not([aria-label="Matters settings"]) [role="tab"][aria-selected="true"]').innerText().catch(() => null);
  await p.getByRole("button", { name: "Add Group" }).click();
  dlg = p.getByRole("dialog", { name: "Add Field Group" });
  await dlg.waitFor();
  await dlg.getByLabel("Name", { exact: true }).fill(GRP);
  await p.waitForTimeout(200);
  const slug = await dlg.locator("#fg-slug").inputValue().catch(() => null);
  await shot(p, "save__group-create-dialog", `Alice 1440 light — Custom fields (${innerSelected}) › Add Group → "Add Field Group" filled ("${GRP}", slug "${slug}")`);
  t0 = Date.now();
  await dlg.getByRole("button", { name: "Create Group", exact: true }).click();
  await dlg.waitFor({ state: "hidden", timeout: 15000 });
  await p.getByRole("button", { name: `Actions for ${GRP}`, exact: true }).waitFor({ timeout: 15000 });
  const gCreateMs = Date.now() - t0;
  const gCreateToasts = await toasts(p);
  await p.getByRole("button", { name: `Actions for ${GRP}`, exact: true }).click();
  await p.getByRole("menuitem", { name: "Delete" }).click();
  const gdel = p.getByRole("alertdialog", { name: "Delete Field Group" });
  await gdel.waitFor();
  const gDelText = (await gdel.innerText()).replace(/\s+/g, " ");
  t0 = Date.now();
  await gdel.getByRole("button", { name: "Delete", exact: true }).click();
  await gdel.waitFor({ state: "hidden", timeout: 15000 });
  await p.getByRole("button", { name: `Actions for ${GRP}`, exact: true }).waitFor({ state: "detached", timeout: 15000 });
  const gDelMs = Date.now() - t0;
  log({ model: "Custom fields › Field group dialog create / AlertDialog delete", innerTab: innerSelected, groupsBefore, create: { dialog: "Add Field Group", button: "Create Group", slug, ms: gCreateMs, toasts: gCreateToasts }, delete: { dialog: "Delete Field Group", text: gDelText, ms: gDelMs, toasts: await toasts(p) }, groupsAfter: L.list((await L.apiGet(a, "/api/field-groups?entityType=PROJECT")).json).map((g) => g.name), inactiveGroupsNote: "the API list after delete is what GET /api/field-groups returns (a deleted group may be soft-deactivated)" });

  await ctx.close();
  await browser.close();
  out.console = consoleSink;
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "saves.json"), JSON.stringify(out));
  console.log("console", consoleSink.length, "tiles", tiles.length);
})();
