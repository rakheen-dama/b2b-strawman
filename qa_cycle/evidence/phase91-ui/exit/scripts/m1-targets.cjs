// 670A M1 target lists per group (brief M-4, M1 scope): shell, settings-<family> (32 tabs),
// settings-sub (11 sub-pages, ids from ../ids.json), platform-admin, marketing.
// Each target: { key, surface, url, sheet?, personas: { who: [variant ids] }, act?, collapsed?, shell?, expect404? }
const L = require("./lib.cjs");

const B = L.BASE;
const AC = { alice: L.V4, carol: L.V4 };
const ids = L.readIds();

const TABS = {
  firm: [["general", "General"], ["roles", "Roles"], ["audit-trail", "Audit trail"], ["data-protection", "Data protection"], ["data-requests", "Data requests"], ["compliance", "Compliance"], ["features", "Features"], ["subscription", "Subscription"]],
  matters: [["templates", "Templates"], ["naming", "Naming"], ["custom-fields", "Custom fields"], ["tags", "Tags"], ["checklists", "Checklists"], ["request-templates", "Request templates"], ["request-settings", "Request settings"], ["take-on", "Take-on"]],
  documents: [["templates", "Templates"], ["clauses", "Clauses"], ["acceptance", "Acceptance"], ["email", "Email"]],
  fees: [["tariffs", "Rates"], ["tax", "Tax"], ["time-recording", "Time recording"], ["fee-runs", "Fee runs"], ["collections", "Collections"], ["trust-accounting", "Trust accounting"]],
  connections: [["xero", "Integrations"], ["mcp", "MCP"], ["ai", "AI"], ["notifications", "Notifications"], ["packs", "Packs"], ["automations", "Automations"]],
};
const FAM = { firm: "Firm", matters: "Matters", documents: "Documents", fees: "Fees & Trust", connections: "Connections" };

const settingsFamily = (f) => TABS[f].map(([id, label]) => ({ key: `${f}-${id}`, surface: `Settings › ${FAM[f]} › ${label}`, url: `${B}/settings/${f}?tab=${id}`, personas: AC }));

const SUB = [
  ["connections-ai-history", "Settings › Connections › AI › history", `/settings/connections/ai/history`],
  ["connections-automations-executions", "Settings › Connections › Automations › executions", `/settings/connections/automations/executions`],
  ["connections-xero-sync-log", "Settings › Connections › Integrations › Xero sync log", `/settings/connections/xero/sync-log`],
  ["documents-templates-new", "Settings › Documents › Templates › new", `/settings/documents/templates/new`],
  ["documents-templates-edit", "Settings › Documents › Templates › <id>/edit", `/settings/documents/templates/${ids.documentTemplateId}/edit`],
  ["matters-checklists-new", "Settings › Matters › Checklists › new", `/settings/matters/checklists/new`],
  ["matters-checklists-detail", "Settings › Matters › Checklists › <id>", `/settings/matters/checklists/${ids.checklistId}`],
  ["matters-checklists-edit", "Settings › Matters › Checklists › <id>/edit (PLATFORM → redirects to detail)", `/settings/matters/checklists/${ids.checklistId}/edit`],
  // The edit form itself: an ORG_CUSTOM checklist created through the UI by m1-data.cjs.
  ["matters-checklists-edit-custom", "Settings › Matters › Checklists › <custom id>/edit (form)", `/settings/matters/checklists/${ids.checklistCustomId}/edit`, "matters-checklists-edit"],
  ["matters-request-templates-new", "Settings › Matters › Request templates › new", `/settings/matters/request-templates/new`],
  ["matters-request-templates-detail", "Settings › Matters › Request templates › <id>", `/settings/matters/request-templates/${ids.requestTemplateId}`],
  ["matters-templates-detail", "Settings › Matters › Templates › <id>", `/settings/matters/templates/${ids.matterTemplateId}`],
].map(([key, surface, p, sheet]) => ({ key, surface, url: `${B}${p}`, personas: AC, ...(sheet ? { sheet } : {}) }));

// Shell: dashboard expanded / collapsed (cookie sidebar_state), plus the in-app ⌘/Ctrl+B toggle.
const toggle = async (page) => {
  const st = () => page.evaluate(() => { const e = document.querySelector('[data-slot="sidebar"][data-state]') || document.querySelector("[data-state][data-collapsible]"); return e ? e.getAttribute("data-state") : null; });
  const before = await st();
  await page.locator("#main-content").click({ position: { x: 5, y: 5 } }).catch(() => {});
  await page.keyboard.press("ControlOrMeta+b");
  await page.waitForTimeout(700);
  const after = await st();
  const cookie = await page.evaluate(() => (document.cookie.match(/sidebar_state=([^;]+)/) || [])[1] || null);
  return { before, after, cookie, toggled: before !== after };
};
const SHELL = [
  { key: "dashboard-expanded", surface: "Shell › dashboard (rail expanded)", url: `${B}/dashboard`, personas: AC, sheet: "dashboard-expanded" },
  { key: "dashboard-collapsed", surface: "Shell › dashboard (rail collapsed, cookie)", url: `${B}/dashboard`, personas: AC, collapsed: true, sheet: "dashboard-collapsed" },
  { key: "dashboard-ctrl-b", surface: "Shell › dashboard after Ctrl+B (expanded → collapsed in-app)", url: `${B}/dashboard`, personas: { alice: ["1440-light", "1440-dark"], carol: ["1440-light"] }, act: toggle, sheet: "dashboard-collapsed" },
];

// Platform admin (outside /org/, always light): Alice 1024 + 1440 light; access-requests per status
// tab; Carol once per page at 1440 light → Next's 404.
const tab = (name) => async (page) => { await page.locator("#main-content, body").first().getByRole("tab", { name, exact: true }).first().click(); await page.waitForTimeout(800); return { tab: name, selected: await page.evaluate(() => { const t = document.querySelector('[role="tab"][aria-selected="true"]'); return t ? t.textContent.trim() : null; }) }; };
const PA = [
  { key: "pa-access-pending", surface: "Platform admin › Access requests › Pending (default)", url: `${L.PA}/access-requests`, personas: { alice: L.PL }, sheet: "access-requests" },
  { key: "pa-access-all", surface: "Platform admin › Access requests › All", url: `${L.PA}/access-requests`, personas: { alice: L.PL }, act: tab("All"), sheet: "access-requests" },
  { key: "pa-access-approved", surface: "Platform admin › Access requests › Approved", url: `${L.PA}/access-requests`, personas: { alice: L.PL }, act: tab("Approved"), sheet: "access-requests" },
  { key: "pa-access-rejected", surface: "Platform admin › Access requests › Rejected", url: `${L.PA}/access-requests`, personas: { alice: L.PL }, act: tab("Rejected"), sheet: "access-requests" },
  { key: "pa-billing", surface: "Platform admin › Billing", url: `${L.PA}/billing`, personas: { alice: L.PL }, sheet: "billing-demo-carol" },
  { key: "pa-demo", surface: "Platform admin › Demo", url: `${L.PA}/demo`, personas: { alice: L.PL }, sheet: "billing-demo-carol" },
  { key: "pa-carol-access", surface: "Platform admin › Access requests (Carol → 404)", url: `${L.PA}/access-requests`, personas: { carol: ["1440-light"] }, expect404: ["carol"], sheet: "billing-demo-carol" },
  { key: "pa-carol-billing", surface: "Platform admin › Billing (Carol → 404)", url: `${L.PA}/billing`, personas: { carol: ["1440-light"] }, expect404: ["carol"], sheet: "billing-demo-carol" },
  { key: "pa-carol-demo", surface: "Platform admin › Demo (Carol → 404)", url: `${L.PA}/demo`, personas: { carol: ["1440-light"] }, expect404: ["carol"], sheet: "billing-demo-carol" },
];

// Marketing `/`: fresh unauthenticated context; stored kazi-theme=dark (OS light), stored dark + OS
// dark, and the no-preference control. Expect no html.dark in all three (ADR-371: dark is /org/* only).
const MKT_VARIANTS = {
  "1440-stored-dark": { w: 1440, h: 900, store: "dark", expectDark: false },
  "1440-stored-dark-os-dark": { w: 1440, h: 900, store: "dark", expectDark: false, colorScheme: "dark" },
  "1440-no-pref": { w: 1440, h: 900, store: null, expectDark: false },
};
const MKT = [{ key: "marketing-root", surface: "Marketing /", url: `${L.APP}/`, personas: { anon: Object.keys(MKT_VARIANTS) }, shell: false,
  // Pre-existing (Phase 90 637B README:99): Next prefetches the mock-mode "Get Started" link /sign-up,
  // a route that does not exist in mock-auth mode → 404 + one console error per load.
  known: [/^fetch http:\/\/localhost:3001\/sign-up\?_rsc=/] }];

const GROUPS = {
  shell: { targets: SHELL },
  ...Object.fromEntries(Object.keys(TABS).map((f) => [`settings-${f}`, { targets: settingsFamily(f) }])),
  "settings-sub": { targets: SUB },
  "platform-admin": { targets: PA },
  marketing: { targets: MKT, personas: ["anon"], meta: { variants: MKT_VARIANTS } },
};
module.exports = { GROUPS, TABS, FAM };
