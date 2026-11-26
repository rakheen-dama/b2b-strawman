// 665F OBS-after step 7 (NEW, authorised as optional by the orchestrator brief): the MCP "Revoke
// access" destructive section, reachable only while the connector is enabled. Everything through the
// UI as Alice; no external side effects (McpEnablementService.enable/revoke only write the tenant's
// consent rows + the MCP OrgIntegration row; no outbound call).
//   1. light 1440: MCP › switch "Enabled" (off) → consent dialog → "I acknowledge & enable".
//   2. light: probe the destructive section (border / header wash / background) and the "Revoke access"
//      button colour vs var(--destructive); crop; "Revoke access" → alertdialog → Cancel.
//   3. dark 1440: the same probe + crop; "Revoke access" → alertdialog → screenshot → Cancel.
//   4. light: switch "Enabled" (on) → the same alertdialog → "Revoke access" (confirm) → disabled again.
// API GET /api/integrations/mcp/status before / after each step.
// Writes $RAW/mcp-roundtrip.json + tiles.
// Usage: PHASE=after node 07-mcp-roundtrip.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const out = { at: new Date().toISOString(), steps: [], tiles: [], console: [] };
const lab = { label: "" };
const log = (o) => { out.steps.push(o); console.log(JSON.stringify(o).slice(0, 1500)); };
const PROBE = () => {
  const ref = document.createElement("span"); ref.style.color = "var(--destructive)"; document.body.appendChild(ref);
  const tok = getComputedStyle(ref).color; ref.style.color = "var(--muted-foreground)"; const muted = getComputedStyle(ref).color; ref.remove();
  const sec = document.querySelector('main#main-content [data-slot="settings-section"][data-variant="destructive"]');
  if (!sec) return { section: null, destructiveToken: tok };
  const cs = getComputedStyle(sec);
  const b = [...sec.querySelectorAll("button")].find((x) => /Revoke access/.test(x.textContent));
  const bcs = b ? getComputedStyle(b) : null;
  const hdr = sec.querySelector('[data-slot="card-header"]');
  return {
    destructiveToken: tok, mutedForeground: muted,
    section: { cls: sec.className.slice(0, 200), borderTopColor: cs.borderTopColor, backgroundColor: cs.backgroundColor, hasHeader: !!hdr, headerBg: hdr ? getComputedStyle(hdr).backgroundColor : null, headings: sec.querySelectorAll("h1,h2,h3").length, text: (sec.innerText || "").trim() },
    revokeButton: b ? { text: b.textContent.trim(), variant: b.getAttribute("data-variant"), cls: b.className.slice(0, 160), color: bcs.color, borderColor: bcs.borderTopColor, background: bcs.backgroundColor, equalsToken: bcs.color === tok } : null,
  };
};
const DLG = () => { const d = [...document.querySelectorAll('[role="alertdialog"]')].pop(); if (!d) return null; return { title: (d.querySelector("h2") || {}).textContent, buttons: [...d.querySelectorAll("button")].map((b) => `${b.textContent.trim()}{${b.getAttribute("data-variant")}}${b.disabled ? "[disabled]" : ""}`), text: (d.innerText || "").replace(/\s+/g, " ").trim().slice(0, 300) }; };

(async () => {
  const a = await L.idpToken("alice");
  const st = async () => { const s = (await L.apiGet(a, "/api/integrations/mcp/status")).json; return { effectivelyEnabled: s.effectivelyEnabled, integrationEnabled: s.integrationEnabled, consent: s.consent && { granted: s.consent.granted, action: s.consent.action, version: s.consent.version, consentedAt: s.consent.consentedAt } }; };
  const mcpRow = async () => L.list((await L.apiGet(a, "/api/integrations")).json).filter((i) => i.domain === "MCP").map((i) => ({ domain: i.domain, providerSlug: i.providerSlug, enabled: i.enabled }));
  out.apiBefore = { status: await st(), integrationsRow: await mcpRow() };
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const open = async (theme) => { const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme, extra: L.EXTRA }); const p = await ctx.newPage(); L.watch(p, out.console, lab); await p.goto(`${L.BASE}/settings/connections?tab=mcp`, { waitUntil: "load" }); await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(800); return { ctx, p }; };
  const tile = async (p, key, caption, loc) => { const f = path.join(L.RAW, `${key}.png`); if (loc) await loc.screenshot({ path: f }); else await p.screenshot({ path: f }); out.tiles.push([key, f, caption]); };

  // 1. enable (light)
  lab.label = "mcp|enable|1440-light";
  let { ctx, p } = await open("light");
  const main = () => p.locator("#main-content");
  const sw = () => main().getByRole("switch", { name: "Enabled" });
  const sw0 = await sw().getAttribute("aria-checked");
  if (sw0 !== "false") throw new Error(`MCP switch expected off, is ${sw0}`);
  await sw().click();
  const cd = p.getByRole("dialog", { name: "Acknowledge POPIA responsibility" });
  await cd.waitFor();
  await cd.getByRole("button", { name: "I acknowledge & enable" }).click();
  await cd.waitFor({ state: "hidden", timeout: 15000 });
  await p.waitForLoadState("networkidle").catch(() => {});
  await main().getByRole("button", { name: "Revoke access" }).waitFor({ timeout: 15000 });
  await p.waitForTimeout(600);
  log({ step: "enable", via: "switch → consent dialog → 'I acknowledge & enable'", switchBefore: sw0, switchAfter: await sw().getAttribute("aria-checked"), api: await st() });
  await tile(p, "mcp__enabled-light", "Alice 1440 light — MCP after 'I acknowledge & enable': Enabled, server URL + config snippet, and the untitled destructive section with 'Revoke access'");

  // 2. probe + open/cancel (light)
  const lightProbe = await p.evaluate(PROBE);
  log({ step: "probe-light", ...lightProbe });
  await tile(p, "mcp__revoke-section-light", `Alice 1440 light — MCP destructive section: border ${lightProbe.section && lightProbe.section.borderTopColor}; 'Revoke access' colour ${lightProbe.revokeButton && lightProbe.revokeButton.color} (token ${lightProbe.destructiveToken}, equal: ${lightProbe.revokeButton && lightProbe.revokeButton.equalsToken})`, main().locator('[data-slot="settings-section"][data-variant="destructive"]'));
  await main().getByRole("button", { name: "Revoke access" }).click();
  let rd = p.getByRole("alertdialog", { name: "Revoke MCP connector access?" });
  await rd.waitFor();
  await p.waitForTimeout(400);
  const dl = await p.evaluate(DLG);
  await tile(p, "mcp__revoke-dialog-light", "Alice 1440 light — 'Revoke access' → alertdialog 'Revoke MCP connector access?' (then Cancel)");
  await rd.getByRole("button", { name: "Cancel" }).click();
  await rd.waitFor({ state: "hidden" });
  log({ step: "revoke-dialog-cancel-light", dialog: dl, closedBy: "Cancel", api: await st() });
  await ctx.close();

  // 3. dark probe + open/cancel
  lab.label = "mcp|dark|1440-dark";
  ({ ctx, p } = await open("dark"));
  const darkProbe = await p.evaluate(PROBE);
  log({ step: "probe-dark", ...darkProbe });
  await tile(p, "mcp__enabled-dark", "Alice 1440 dark — MCP enabled, destructive section with 'Revoke access'");
  await tile(p, "mcp__revoke-section-dark", `Alice 1440 dark — MCP destructive section: border ${darkProbe.section && darkProbe.section.borderTopColor}; 'Revoke access' colour ${darkProbe.revokeButton && darkProbe.revokeButton.color} (token ${darkProbe.destructiveToken}, equal: ${darkProbe.revokeButton && darkProbe.revokeButton.equalsToken}; muted-foreground ${darkProbe.mutedForeground})`, main().locator('[data-slot="settings-section"][data-variant="destructive"]'));
  await main().getByRole("button", { name: "Revoke access" }).click();
  rd = p.getByRole("alertdialog", { name: "Revoke MCP connector access?" });
  await rd.waitFor();
  await p.waitForTimeout(400);
  const dd = await p.evaluate(DLG);
  await tile(p, "mcp__revoke-dialog-dark", "Alice 1440 dark — alertdialog 'Revoke MCP connector access?' (then Cancel)");
  await rd.getByRole("button", { name: "Cancel" }).click();
  await rd.waitFor({ state: "hidden" });
  log({ step: "revoke-dialog-cancel-dark", dialog: dd, closedBy: "Cancel", api: await st() });
  await ctx.close();

  // 4. disable again (light): switch off → same alertdialog → confirm
  lab.label = "mcp|disable|1440-light";
  ({ ctx, p } = await open("light"));
  await sw().click();
  rd = p.getByRole("alertdialog", { name: "Revoke MCP connector access?" });
  await rd.waitFor();
  await rd.getByRole("button", { name: "Revoke access" }).click();
  await rd.waitFor({ state: "hidden", timeout: 15000 });
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(1200);
  log({ step: "disable", via: "switch (on) → alertdialog 'Revoke MCP connector access?' → 'Revoke access'", switchAfter: await sw().getAttribute("aria-checked"), revokeButtons: await main().getByRole("button", { name: "Revoke access" }).count(), api: await st() });
  await tile(p, "mcp__disabled-again-light", "Alice 1440 light — MCP after the revoke: Disabled again, no destructive section");
  await ctx.close();
  await browser.close();
  out.apiAfter = { status: await st(), integrationsRow: await mcpRow() };
  fs.writeFileSync(path.join(L.RAW, "mcp-roundtrip.json"), JSON.stringify(out));
  console.log("before", JSON.stringify(out.apiBefore), "after", JSON.stringify(out.apiAfter), "console", out.console.length, JSON.stringify(out.console).slice(0, 500));
})();
