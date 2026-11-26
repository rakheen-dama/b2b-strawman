// 665F OBS step 3: open-and-cancel dialogs + toggle-and-revert instant switches, through the UI as Alice
// (1440×900 light; the Automations rule switch's position also probed at 1024). Adapted from
// 665E/before/scripts/03-interactions.cjs (not edited). NOTHING destructive is confirmed:
//   Integrations › KYC "Configure" → dialog → probe → Escape.
//   Integrations › Payment / AI provider comboboxes → options probed → Escape (no selection).
//   Integrations › counts only (never clicked): "Connect to Xero" (would start the Xero OAuth flow),
//     "Disconnect" (XeroConnectionCard, only when CONNECTED — the seed has no Xero connection, 404),
//     "Remove Key" / "Remove" (only with a key / provider — none configured), IntegrationCard
//     "Enabled" switches (only with a provider — none), "Send Test Email" (would send mail).
//   MCP › switch "Enabled" (off) → POPIA consent dialog "Acknowledge POPIA responsibility" → probe →
//     "Cancel" (the switch is a dialog opener when off; "Revoke access" only exists when enabled).
//   Packs › Available › "Show all packs" switch on → count → off (client-side filter, no write).
//   Packs › Installed › "common" (canUninstall=true) "Uninstall" → alertdialog "Uninstall Pack" →
//     probe → "Cancel". "legal-za" Uninstall (disabled) → hover → blockingReason tooltip probed.
//   Automations › switch "Toggle Investment Maturity Reminder" ON → OFF (toast) → ON (toast):
//     two instant writes, net no change (API before / after recorded; updatedAt moves).
//   Notifications › "Task Assigned" in-app switch ON → OFF → "Save preferences" → message probed →
//     ON → "Save preferences": two saves, net no change (API before / after recorded).
//   Execution Log › "All Rules" / "All Statuses" comboboxes → options → Escape.
//   Sync Log › "All States" combobox → options → Escape.
// Writes $RAW/interactions.json + tiles; 05-assemble merges them.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const out = { at: new Date().toISOString(), steps: [], api: {} };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };
const log = (o) => { out.steps.push(o); console.log(JSON.stringify(o).slice(0, 900)); };
const shot = async (p, key, caption, opt = {}) => {
  const f = path.join(L.RAW, `${key}.png`);
  await p.screenshot({ path: f, ...opt });
  tiles.push([key, f, caption]);
};
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(500); };
const DLG = (sel) => {
  const d = [...document.querySelectorAll(sel)].pop();
  if (!d) return null;
  const t = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
  const r = d.getBoundingClientRect();
  const lbl = (e) => e.getAttribute("aria-label") || (e.id && document.querySelector(`label[for="${CSS.escape(e.id)}"]`) ? t(document.querySelector(`label[for="${CSS.escape(e.id)}"]`)) : null);
  return {
    role: d.getAttribute("role"),
    name: d.getAttribute("aria-labelledby") ? t(document.getElementById(d.getAttribute("aria-labelledby").split(" ")[0]) || d) : null,
    description: d.getAttribute("aria-describedby") ? t(document.getElementById(d.getAttribute("aria-describedby").split(" ")[0]) || d).slice(0, 300) : null,
    box: { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) },
    headings: [...d.querySelectorAll("h1,h2,h3,[data-slot$=title]")].map((h) => `${h.tagName.toLowerCase()}${h.className.includes("sr-only") ? "(sr-only)" : ""}:${t(h)}`),
    buttons: [...d.querySelectorAll("button,[role=button]")].map((b) => `${b.getAttribute("aria-label") || t(b) || "(unnamed)"}${b.disabled ? "[disabled]" : ""}${b.getAttribute("data-variant") ? `{${b.getAttribute("data-variant")}}` : ""}`),
    controls: [...d.querySelectorAll("input,textarea,select,[role=combobox],[role=radio],[role=switch]")].filter((e) => e.type !== "hidden").map((e) => ({ tag: e.tagName.toLowerCase(), type: e.type || e.getAttribute("role"), id: e.id || undefined, label: lbl(e), value: (e.value || e.getAttribute("aria-checked") || t(e)).slice(0, 60) })),
    text: t(d).slice(0, 400),
  };
};
const toastText = async (p) => p.evaluate(() => [...document.querySelectorAll("[data-sonner-toast]")].map((e) => (e.innerText || "").replace(/\s+/g, " ").trim()));
const count = async (loc) => loc.count();

(async () => {
  const a = await L.idpToken("alice");
  const ruleOf = async () => L.list((await L.apiGet(a, "/api/automation-rules")).json).find((r) => r.name === L.TOGGLE_RULE);
  const prefOf = async () => ((await L.apiGet(a, "/api/notifications/preferences")).json.preferences || []).find((x) => x.notificationType === L.TOGGLE_PREF.type);
  const mcpOf = async () => (await L.apiGet(a, "/api/integrations/mcp/status")).json;
  out.api.before = { rule: await ruleOf(), pref: await prefOf(), prefsN: ((await L.apiGet(a, "/api/notifications/preferences")).json.preferences || []).length, mcp: await mcpOf(), installed: L.list((await L.apiGet(a, "/api/packs/installed")).json).map((x) => x.packId) };
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  const T = (tab) => `${L.BASE}/settings/connections?tab=${tab}`;

  // ---- Integrations ----
  lab.label = "int|xero|1440";
  await p.goto(T("xero"), { waitUntil: "load" });
  await settle(p);
  const main = p.locator("#main-content");
  log({ step: "integrations-counts", connectToXero: await count(main.getByRole("button", { name: /Connect to Xero/ })) + await count(main.getByRole("link", { name: /Connect to Xero/ })), disconnect: await count(main.getByRole("button", { name: "Disconnect" })), removeKey: await count(main.getByRole("button", { name: /^Remove( Key)?$/ })), setApiKey: await count(main.getByRole("button", { name: /API Key/ })), enabledSwitches: await count(main.getByRole("switch")), sendTestEmail: await count(main.getByRole("button", { name: "Send Test Email" })), configureXeroLink: await count(main.getByRole("link", { name: /Configure Xero/ })), syncLogLink: await count(main.getByRole("link", { name: /Sync Log/i })), note: "Disconnect / Remove Key / Enabled switches are conditional on a connection / key / provider; the seed has none (GET /api/integrations all providerSlug null, /api/integrations/xero/connection 404), so they are unreachable without connecting real Xero or configuring a provider — not done." });
  const kycBtn = main.getByRole("button", { name: "Configure", exact: true });
  if ((await kycBtn.count()) === 1) {
    await kycBtn.click();
    await p.getByRole("dialog").waitFor();
    await p.waitForTimeout(400);
    const d = await p.evaluate(DLG, '[role="dialog"]');
    await shot(p, "int__kyc-configure-dialog-1440", "Alice 1440 light — Integrations › KYC Verification › \"Configure\" dialog (opened, then Escape)");
    await p.keyboard.press("Escape");
    await p.getByRole("dialog").waitFor({ state: "hidden" });
    log({ step: "kyc-configure-dialog", dialog: d, closedBy: "Escape", closed: true });
  } else log({ step: "kyc-configure-dialog", absent: true, count: await kycBtn.count() });
  for (const id of ["provider-PAYMENT", "provider-AI"]) {
    const cb = p.locator(`#${id}`);
    if (!(await cb.count())) { log({ step: `combobox-${id}`, absent: true }); continue; }
    await cb.scrollIntoViewIfNeeded();
    const before = (await cb.innerText()).trim();
    await cb.click();
    await p.getByRole("listbox").waitFor();
    const opts = await p.getByRole("option").evaluateAll((els) => els.map((e) => (e.innerText || "").trim()));
    await shot(p, `int__${id}-open-1440`, `Alice 1440 light — Integrations › #${id} open: ${opts.join(" | ")} (Escape, nothing selected)`);
    await p.keyboard.press("Escape");
    await p.waitForTimeout(300);
    log({ step: `combobox-${id}`, before, options: opts, after: (await cb.innerText()).trim(), closedBy: "Escape" });
  }

  // ---- MCP ----
  lab.label = "int|mcp|1440";
  await p.goto(T("mcp"), { waitUntil: "load" });
  await settle(p);
  const sw = main.getByRole("switch", { name: "Enabled" });
  const swBefore = await sw.getAttribute("aria-checked");
  const revokeCount = await count(main.getByRole("button", { name: "Revoke access" }));
  await sw.click();
  const cd = p.getByRole("dialog", { name: "Acknowledge POPIA responsibility" });
  await cd.waitFor();
  await p.waitForTimeout(400);
  const cdd = await p.evaluate(DLG, '[role="dialog"]');
  await shot(p, "int__mcp-consent-dialog-1440", "Alice 1440 light — MCP › switch \"Enabled\" (off) → consent dialog \"Acknowledge POPIA responsibility\" (then Cancel; not acknowledged)");
  await cd.getByRole("button", { name: "Cancel", exact: true }).click();
  await cd.waitFor({ state: "hidden" });
  await p.waitForTimeout(300);
  log({ step: "mcp-switch-consent", switchBefore: swBefore, switchAfter: await sw.getAttribute("aria-checked"), revokeAccessButtons: revokeCount, dialog: cdd, closedBy: "Cancel", note: "Revoke access / its alertdialog \"Revoke MCP connector access?\" render only when enabled (effectivelyEnabled=false) — unreachable without granting consent" });

  // ---- Packs ----
  lab.label = "int|packs|1440";
  await p.goto(T("packs"), { waitUntil: "load" });
  await settle(p);
  const showAll = p.getByRole("switch", { name: "Show all packs" });
  const nBefore = await count(p.locator('[data-testid^="pack-card-"]:visible'));
  await showAll.click();
  await settle(p);
  const nOn = await count(p.locator('[data-testid^="pack-card-"]:visible'));
  await showAll.click();
  await settle(p);
  log({ step: "packs-show-all", cardsBefore: nBefore, cardsShowAll: nOn, cardsAfter: await count(p.locator('[data-testid^="pack-card-"]:visible')), switchAfter: await showAll.getAttribute("aria-checked") });
  await p.getByRole("tab", { name: /Installed/i }).click();
  await settle(p);
  await p.waitForTimeout(800);
  const states = await p.locator('[data-testid^="pack-card-"]:visible').evaluateAll((cs) => cs.map((c) => ({ testid: c.dataset.testid, uninstall: [...c.querySelectorAll("button")].filter((b) => /Uninstall/.test(b.textContent)).map((b) => (b.disabled ? "disabled" : "enabled")).join(",") })));
  const common = p.locator('[data-testid="pack-card-common"]');
  await common.getByRole("button", { name: "Uninstall" }).click();
  const ud = p.getByRole("alertdialog", { name: "Uninstall Pack" });
  await ud.waitFor();
  await p.waitForTimeout(400);
  const udd = await p.evaluate(DLG, '[role="alertdialog"]');
  await shot(p, "int__pack-uninstall-dialog-1440", "Alice 1440 light — Packs › Installed › \"Common Templates\" › Uninstall → alertdialog \"Uninstall Pack\" (then Cancel)");
  await ud.getByRole("button", { name: "Cancel", exact: true }).click();
  await ud.waitFor({ state: "hidden" });
  log({ step: "pack-uninstall-dialog", pack: "common", installedCardStates: states, dialog: udd, closedBy: "Cancel", closed: true });
  const legal = p.locator('[data-testid="pack-card-legal-za"]');
  await legal.getByRole("button", { name: "Uninstall" }).locator("..").hover();
  await p.waitForTimeout(900);
  const tip = await p.evaluate(() => [...document.querySelectorAll("[data-radix-popper-content-wrapper]")].map((e) => (e.innerText || "").trim()));
  await shot(p, "int__pack-uninstall-blocked-tooltip-1440", `Alice 1440 light — Packs › Installed › "SA Legal Templates" Uninstall (disabled) hovered: tooltip ${JSON.stringify(tip)}`);
  log({ step: "pack-uninstall-blocked-tooltip", pack: "legal-za", tooltip: tip });
  await p.mouse.move(5, 5);

  // ---- Automations ----
  lab.label = "int|automations|1440";
  await p.goto(T("automations"), { waitUntil: "load" });
  await settle(p);
  const rs = p.getByRole("switch", { name: `Toggle ${L.TOGGLE_RULE}` });
  const r0 = await rs.getAttribute("aria-checked");
  const flips = [];
  for (const target of [r0 === "true" ? "false" : "true", r0]) {
    await rs.click();
    await p.getByText(/Rule toggled successfully|Failed to toggle rule/).first().waitFor({ timeout: 15000 }).catch(() => {});
    await p.waitForTimeout(500);
    const toast = await toastText(p);
    const api = await ruleOf();
    flips.push({ clicked: true, ariaChecked: await rs.getAttribute("aria-checked"), expected: target, toast, apiEnabled: api.enabled, apiUpdatedAt: api.updatedAt });
    if (flips.length === 1) await shot(p, "int__rule-switch-off-1440", `Alice 1440 light — Automations › "${L.TOGGLE_RULE}" switch toggled OFF (instant write, toast ${JSON.stringify(toast)}); reverted next`);
    await p.waitForTimeout(4500);
  }
  log({ step: "rule-switch-toggle-revert", rule: L.TOGGLE_RULE, before: r0, flips, after: await rs.getAttribute("aria-checked") });

  // ---- Notifications ----
  lab.label = "int|notifications|1440";
  await p.goto(T("notifications"), { waitUntil: "load" });
  await settle(p);
  const ps = p.locator(`#inapp-${L.TOGGLE_PREF.type}`);
  const p0 = await ps.getAttribute("aria-checked");
  const saves = [];
  for (const target of [p0 === "true" ? "false" : "true", p0]) {
    await ps.click();
    const saveBtn = p.getByRole("button", { name: "Save preferences" });
    const t0 = Date.now();
    await saveBtn.click();
    const msg = p.getByText(/Preferences saved successfully\.|Failed to save preferences/);
    await msg.waitFor({ timeout: 15000 });
    const ms = Date.now() - t0;
    const m = await msg.evaluate((e) => ({ text: e.textContent.trim(), tag: e.tagName.toLowerCase(), cls: e.className, role: e.getAttribute("role"), ariaLive: e.getAttribute("aria-live") || (e.closest("[aria-live]") ? e.closest("[aria-live]").getAttribute("aria-live") : null), color: getComputedStyle(e).color }));
    const api = await prefOf();
    saves.push({ target, ariaChecked: await ps.getAttribute("aria-checked"), message: m, msToMessage: ms, apiInAppEnabled: api && api.inAppEnabled });
    if (saves.length === 1) { await msg.scrollIntoViewIfNeeded(); await shot(p, "int__notif-save-1440", `Alice 1440 light — Notifications › "${L.TOGGLE_PREF.label}" In-App OFF → "Save preferences" → "${m.text}" (reverted + saved next)`); }
  }
  log({ step: "notification-toggle-save-revert", type: L.TOGGLE_PREF.type, before: p0, saves, after: await ps.getAttribute("aria-checked") });

  // ---- Execution Log + Sync Log filters ----
  for (const [url, names, key] of [["/settings/connections/automations/executions", ["All Rules", "All Statuses"], "executions"], ["/settings/connections/xero/sync-log", ["All States"], "sync-log"]]) {
    lab.label = `int|${key}|1440`;
    await p.goto(`${L.BASE}${url}`, { waitUntil: "load" });
    await settle(p);
    for (const n of names) {
      const cb = p.getByRole("combobox").filter({ hasText: n });
      await cb.click();
      await p.getByRole("listbox").waitFor();
      const opts = await p.getByRole("option").evaluateAll((els) => els.map((e) => (e.innerText || "").trim()));
      await shot(p, `int__${key}-filter-${n.replace(/\W+/g, "_")}-1440`, `Alice 1440 light — ${key} › filter "${n}" open: ${opts.join(" | ")} (Escape)`);
      await p.keyboard.press("Escape");
      await p.waitForTimeout(300);
      log({ step: `${key}-filter`, combobox: n, options: opts, closedBy: "Escape" });
    }
  }
  await ctx.close();

  // ---- 1024: where the rule switch sits (table overflow, K-114) ----
  const c2 = await L.newCtx(browser, S, { width: 1024, height: 768, theme: "light", extra: L.EXTRA });
  const q = await c2.newPage();
  L.watch(q, consoleSink, lab);
  lab.label = "int|automations|1024";
  await q.goto(T("automations"), { waitUntil: "load" });
  await settle(q);
  const pos = await q.getByRole("switch", { name: `Toggle ${L.TOGGLE_RULE}` }).evaluate((s) => { const r = s.getBoundingClientRect(); let sc = s.closest("table").parentElement; const sr = sc.getBoundingClientRect(); return { x: Math.round(r.x), right: Math.round(r.right), viewport: innerWidth, scrollerRight: Math.round(sr.left + sc.clientWidth), visibleInScroller: r.right <= sr.left + sc.clientWidth }; });
  await q.getByRole("switch", { name: `Toggle ${L.TOGGLE_RULE}` }).scrollIntoViewIfNeeded();
  await shot(q, "int__rule-switch-1024-scrolled", `Alice 1024 light — Automations rules table scrolled to bring the "${L.TOGGLE_RULE}" switch into view (at scroll 0 it sits at x=${pos.x}, past the 496 px scroller's right edge ${pos.scrollerRight})`);
  log({ step: "rule-switch-position-1024", ...pos });
  await c2.close();
  await browser.close();
  out.api.after = { rule: await ruleOf(), pref: await prefOf(), prefsN: ((await L.apiGet(a, "/api/notifications/preferences")).json.preferences || []).length, mcp: await mcpOf(), installed: L.list((await L.apiGet(a, "/api/packs/installed")).json).map((x) => x.packId) };
  out.console = consoleSink;
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "interactions.json"), JSON.stringify(out));
  console.log("api before", JSON.stringify(out.api.before).slice(0, 700));
  console.log("api after ", JSON.stringify(out.api.after).slice(0, 700));
  console.log("console", consoleSink.length, JSON.stringify([...new Set(consoleSink.map((m) => m.label + " " + m.text.slice(0, 100)))]), "tiles", tiles.length);
})();
