// 642B observe-first "before" capture (task 642.6). Read-only: drives the e2e mock-auth stack
// (frontend :3001, backend :8081) as Alice (owner) and Carol (member). Screenshots the client
// detail page (default tab) at 1024x768 / 1440x900 in light + dark, dumps header DOM facts,
// tab-bar position, money strings on the default tab and console errors/warnings, records what
// each user sees on the Clients list, and probes (GET only) the backend reads the 642B hero will
// source, as each user, to record their shape and status codes. Writes obs-before.json (compact).
// Run: node obs-before.cjs   (nvm node v20; @playwright/test from this repo's frontend, or $PW_PATH)
const path = require("path");
const fs = require("fs");
// @playwright/test: $PW_PATH if set, else resolved from this repo's frontend/ (after `pnpm install`).
const PW_PATH =
  process.env.PW_PATH ||
  require.resolve("@playwright/test", {
    paths: [path.resolve(__dirname, "../../../../../frontend")],
  });
const { chromium } = require(PW_PATH);

const BASE = "http://localhost:3001";
const API = "http://localhost:8081";
const IDP = "http://localhost:8090";
const SLUG = "e2e-test-org";
const OUT = __dirname;

const USERS = {
  alice: { userId: "user_e2e_alice", orgRole: "org:owner" },
  carol: { userId: "user_e2e_carol", orgRole: "org:member" },
};
const CLIENTS = {
  rich: { id: "658fb311-67e2-475f-8bc0-43be675c7b39", note: "Sipho Ndlovu (ONBOARDING; 2 matters, 1 ACTIVE + 1 CLOSED; trust R 3 750,00; no fee notes outstanding)" },
  fx: { id: "3f3219bf-f8cd-47ce-bb59-f1bf1020330e", note: "Acme Corp (ACTIVE; 1 matter; INV-0001 ZAR 8 625,00 SENT + INV-0003 USD 115,00 SENT (created via UI, setup-usd-feenote.cjs) + ZAR draft; no trust)" },
};
const VIEWPORTS = { 1024: { w: 1024, h: 768 }, 1440: { w: 1440, h: 900 } };
const FULL = [
  [1024, "light"],
  [1024, "dark"],
  [1440, "light"],
  [1440, "dark"],
];

// [user, clientKey, combos, tab (null = default), screenshot?]
const PLAN = [
  ["alice", "rich", FULL, null, true],
  ["carol", "rich", FULL, null, true],
  ["alice", "fx", [[1440, "light"]], null, true],
  ["alice", "fx", [[1440, "light"]], "invoices", true],
  ["carol", "fx", [[1440, "light"]], null, false],
];

async function login(page, userId) {
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.selectOption("#user-select", userId);
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/org\/e2e-test-org\/dashboard/, { timeout: 60000 });
}

async function token(user) {
  const res = await fetch(`${IDP}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userId: USERS[user].userId, orgId: "org_e2e_test", orgSlug: SLUG, orgRole: USERS[user].orgRole }),
  });
  return (await res.json()).access_token;
}

async function apiGet(tok, p) {
  const res = await fetch(`${API}${p}`, { headers: { Authorization: `Bearer ${tok}` } });
  const text = await res.text();
  let body;
  try {
    body = JSON.parse(text);
  } catch {
    body = text.slice(0, 300);
  }
  return { path: p, status: res.status, body };
}

// GET-only probes of the reads 642.7 will use (shape + status per role).
async function probes(user) {
  const tok = await token(user);
  const out = [];
  const settings = await apiGet(tok, "/api/settings");
  out.push({ path: settings.path, status: settings.status, defaultCurrency: settings.body && settings.body.defaultCurrency, enabledModules: settings.body && settings.body.enabledModules });
  const accounts = await apiGet(tok, "/api/trust-accounts");
  out.push(accounts);
  const primary = Array.isArray(accounts.body) ? accounts.body.find((a) => a.isPrimary && a.status === "ACTIVE") : null;
  const accountId = primary ? primary.id : "d12292b0-def3-46c1-b92b-d7dd06c43b7c";
  for (const key of ["rich", "fx"]) {
    const id = CLIENTS[key].id;
    out.push(await apiGet(tok, `/api/trust-accounts/${accountId}/client-ledgers/${id}`));
    out.push(await apiGet(tok, `/api/collections/debtors/${id}`));
    out.push(await apiGet(tok, `/api/clients/${id}/fica-status`));
    const matters = await apiGet(tok, `/api/clients/${id}/matters`);
    out.push({
      path: matters.path,
      status: matters.status,
      body: Array.isArray(matters.body) ? matters.body.map((m) => ({ id: m.id, name: m.name, keys: Object.keys(m), status: m.status === undefined ? "(absent)" : m.status })) : matters.body,
    });
  }
  return out;
}

async function listFacts(page) {
  await page.goto(`${BASE}/org/${SLUG}/clients`, { waitUntil: "load", timeout: 90000 });
  // A member gets a "You don't have access to Clients" page with no h1 — don't fail on it.
  await page.locator("h1").first().waitFor({ state: "visible", timeout: 20000 }).catch(() => {});
  await page.waitForTimeout(2000);
  return page.evaluate(() => {
    const txt = (el) => (el ? el.textContent.replace(/\s+/g, " ").trim() : null);
    const visible = (el) => el.offsetParent !== null;
    const head = [...document.querySelectorAll("#main-content thead th")].filter(visible).map(txt);
    const rows = [...document.querySelectorAll('[data-testid="client-row"]')].filter(visible).map((r) => ({
      cells: [...r.querySelectorAll("td")].map(txt),
      href: (r.querySelector("a[href*='/clients/']") || {}).getAttribute ? r.querySelector("a[href*='/clients/']").getAttribute("href") : null,
    }));
    return { url: location.pathname, h1: txt(document.querySelector("h1")), mainText: (txt(document.querySelector("#main-content")) || "").slice(0, 300), head, rows, footer: txt([...document.querySelectorAll('[data-testid="clients-footer"]')].find(visible)) };
  });
}

async function domFacts(page) {
  return page.evaluate(() => {
    const q = (s) => document.querySelector(s);
    const txt = (el) => (el ? el.textContent.replace(/\s+/g, " ").trim() : null);
    const has = (s) => !!q(s);
    const card = q('[data-testid="client-header-card"]');
    const actions = q('[data-testid="client-header-actions"]');
    const tablist = q('[data-testid="grouped-tab-bar"]') || q('[role="tablist"]');
    const rect = (el) => {
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return { x: Math.round(r.x), y: Math.round(r.y + window.scrollY), w: Math.round(r.width), h: Math.round(r.height) };
    };
    const panel = q('[role="tabpanel"][data-state="active"]');
    const panelText = txt(panel) || "";
    const money = panelText.match(/(R|US\$|\$|€|£)\s?-?[\d\s ]+[,.]\d{2}/g) || [];
    const h1s = [...document.querySelectorAll("h1")].map((h) => ({ testid: h.getAttribute("data-testid"), text: txt(h) }));
    return {
      url: location.pathname + location.search,
      htmlClass: document.documentElement.className,
      notOnList: /isn't on your list|not on your list/i.test(document.body.innerText),
      mainTextHead: txt(q("#main-content")) ? txt(q("#main-content")).slice(0, 300) : null,
      headerCardPresent: !!card,
      headerCardTag: card ? card.tagName : null,
      headerCardRect: rect(card),
      headerCardText: txt(card),
      h1Count: h1s.length,
      h1s,
      clientName: txt(q('h1[data-testid="client-name"]')),
      customerStatusBadge: has('[data-testid="customer-status-badge"]') ? txt(q('[data-testid="customer-status-badge"]')) : null,
      lifecycleBadge: has('[data-testid="lifecycle-badge"]') ? txt(q('[data-testid="lifecycle-badge"]')) : null,
      clientContact: has('[data-testid="client-contact"]') ? txt(q('[data-testid="client-contact"]')) : null,
      clientContext: has('[data-testid="client-context"]') ? txt(q('[data-testid="client-context"]')) : null,
      clientHeaderFields: has('[data-testid="client-header-fields"]') ? txt(q('[data-testid="client-header-fields"]')) : null,
      clientHeaderActionsPresent: !!actions,
      clientHeaderActions: actions
        ? [...actions.querySelectorAll("button, a")].map((b) => ({ text: txt(b), testid: b.getAttribute("data-testid"), ariaLabel: b.getAttribute("aria-label") }))
        : null,
      smartPrimaryAction: has('[data-testid="smart-primary-action"]') ? txt(q('[data-testid="smart-primary-action"]')) : null,
      tabBarTestid: tablist ? tablist.getAttribute("data-testid") : null,
      tabBarRect: rect(tablist),
      tabs: [...document.querySelectorAll('[role="tab"]')].map((t) => ({ testid: t.getAttribute("data-testid"), text: txt(t), selected: t.getAttribute("aria-selected") })),
      panelTextHead: panelText.slice(0, 700),
      panelMoney: money,
      outstandingMentions: (panelText.match(/[^.]{0,60}outstanding[^.]{0,60}/gi) || []).slice(0, 5),
      trustMentions: (panelText.match(/[^.]{0,60}trust[^.]{0,60}/gi) || []).slice(0, 5),
      scrollWidth: document.documentElement.scrollWidth,
    };
  });
}

(async () => {
  const browser = await chromium.launch();
  const results = { capturedAt: new Date().toISOString(), lists: {}, probes: {}, loads: [] };
  for (const user of ["alice", "carol"]) {
    results.probes[user] = await probes(user);
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await context.newPage();
    let consoleBuf = [];
    page.on("console", (m) => {
      if (m.type() === "error" || m.type() === "warning") consoleBuf.push({ type: m.type(), text: m.text().slice(0, 600) });
    });
    page.on("pageerror", (e) => consoleBuf.push({ type: "pageerror", text: String(e).slice(0, 600) }));
    await login(page, USERS[user].userId);
    await page.evaluate(() => localStorage.setItem("kazi-theme", "light"));
    consoleBuf = [];
    results.lists[user] = { facts: await listFacts(page), console: consoleBuf };
    console.log(`list ${user}: ${results.lists[user].facts.rows.length} rows, console=${consoleBuf.length}`);

    for (const [u, clientKey, combos, tab, shoot] of PLAN.filter((p) => p[0] === user)) {
      const client = CLIENTS[clientKey];
      for (const [vpKey, theme] of combos) {
        const vp = VIEWPORTS[vpKey];
        await page.setViewportSize({ width: vp.w, height: vp.h });
        await page.evaluate((t) => localStorage.setItem("kazi-theme", t), theme);
        consoleBuf = [];
        const url = `${BASE}/org/${SLUG}/clients/${client.id}${tab ? `?tab=${tab}` : ""}`;
        const resp = await page.goto(url, { waitUntil: "load", timeout: 90000 });
        let h1ok = true;
        try {
          await page.locator("h1").first().waitFor({ state: "visible", timeout: 60000 });
          await page.locator('[role="tabpanel"][data-state="active"]').first().waitFor({ state: "visible", timeout: 20000 }).catch(() => {});
        } catch {
          h1ok = false;
        }
        await page.waitForTimeout(2500); // client islands hydrate / late console warnings land
        const file = shoot ? `${u}-${clientKey}${tab ? `-${tab}` : ""}-${vp.w}-${theme}.png` : null;
        if (file) await page.screenshot({ path: path.join(OUT, file), fullPage: true });
        const facts = await domFacts(page);
        results.loads.push({ user: u, client: clientKey, clientId: client.id, clientNote: client.note, tab: tab || "(default)", viewport: `${vp.w}x${vp.h}`, theme, httpStatus: resp ? resp.status() : null, h1ok, file, facts, console: consoleBuf });
        console.log(`captured ${u}/${clientKey}/${tab || "default"} ${vp.w} ${theme} status=${resp && resp.status()} h1=${h1ok} html=${facts.htmlClass} card=${facts.headerCardPresent} tabY=${facts.tabBarRect && facts.tabBarRect.y} console=${consoleBuf.length}`);
      }
    }
    // Does the header smart action do anything? Click it (a confirm dialog would be cancelled by
    // closing the context; nothing is confirmed) and count dialogs / URL change afterwards.
    results.smartActionClicks = results.smartActionClicks || [];
    for (const clientKey of [...new Set(PLAN.filter((p) => p[0] === user).map((p) => p[1]))]) {
      await page.setViewportSize({ width: 1440, height: 900 });
      await page.evaluate(() => localStorage.setItem("kazi-theme", "light"));
      const url = `${BASE}/org/${SLUG}/clients/${CLIENTS[clientKey].id}`;
      await page.goto(url, { waitUntil: "load", timeout: 90000 });
      const btn = page.getByTestId("smart-primary-action");
      await btn.waitFor({ state: "visible", timeout: 30000 });
      await page.waitForTimeout(1500);
      const label = await btn.innerText();
      await btn.click();
      await page.waitForTimeout(2500);
      const dialogsAfterClick = await page.locator('[role="dialog"], [role="alertdialog"]').count();
      results.smartActionClicks.push({ user, client: clientKey, label, dialogsAfterClick, urlChanged: page.url() !== url });
      console.log(`smart action ${user}/${clientKey} "${label}" dialogs=${dialogsAfterClick} urlChanged=${page.url() !== url}`);
    }
    await context.close();
  }
  await browser.close();
  fs.writeFileSync(path.join(OUT, "obs-before.json"), JSON.stringify(results));
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
