// 642B "after" capture (task 642.10) — a copy of ../before/obs-before.cjs with minimal changes:
// this stack's client ids (+ "vis" and "full" clients), a hero-tile dump in domFacts, the Open
// matters / ledger / debtors expectations in probes, ?tab=trust loads (TrustBalanceCard cross-check),
// 390/768 responsive loads and the New Client / Edit Client dialogs. Read-only: drives the e2e
// mock-auth stack (frontend :3001, backend :8081) as Alice (owner) and Carol (member). Screenshots the
// client detail page at 1024x768 / 1440x900 in light + dark, dumps header DOM facts, tiles, tab-bar
// position and console errors/warnings. Writes obs-after.json (compact).
// Run: node obs-after.cjs   (nvm node v20; @playwright/test from this repo's frontend, or $PW_PATH)
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
  rich: { id: "14fcc109-3ff2-4abc-a66e-854bbd5761c1", note: "Sipho Ndlovu mwdzyk4n12b3 (ONBOARDING; 2 matters, 1 ACTIVE + 1 CLOSED; trust R 3 750,00; no fee notes)" },
  fx: { id: "b64b3f6f-9b1f-4f07-bbc4-f7595fc7cf14", note: "Acme Corp (ACTIVE; 1 ACTIVE matter; INV-0001 ZAR 8 625,00 SENT + INV-0006 USD 115,00 SENT (created via UI, setup-usd-feenote.cjs) + ZAR draft; no ledger card)" },
  vis: { id: "d4bc5234-ac81-4148-8b2c-ad83bfd8ee5d", note: "Sipho Ndlovu mwdzupuo0b2c (ACTIVE; 2 ACTIVE matters, Carol is a member of 1; INV-0003 ZAR 5 482,63 SENT + ZAR draft; no ledger card)" },
  full: { id: "7643c065-b4ce-4cd2-adec-eb7f340957dd", note: "Moroka Family Trust mwdzupuo0b2c (ONBOARDING; 1 ACTIVE matter; trust R 241 521,50; INV-0002 ZAR PARTIALLY_PAID)" },
};
const VIEWPORTS = { 390: { w: 390, h: 844 }, 768: { w: 768, h: 1024 }, 1024: { w: 1024, h: 768 }, 1440: { w: 1440, h: 900 } };
const FULL = [
  [1024, "light"],
  [1024, "dark"],
  [1440, "light"],
  [1440, "dark"],
];

// [user, clientKey, combos, tab (null = default), screenshot?]
const PLAN = [
  ["alice", "rich", FULL, null, true],
  ["alice", "rich", [[1440, "light"]], "trust", false],
  ["alice", "rich", [[390, "light"], [768, "light"]], null, true],
  ["alice", "fx", [[1440, "light"], [1024, "dark"]], null, true],
  ["alice", "fx", [[1440, "light"]], "invoices", false],
  ["alice", "full", [[1440, "light"]], null, true],
  ["alice", "full", [[1440, "light"]], "trust", false],
  ["alice", "vis", [[1440, "light"]], null, false],
  ["carol", "rich", [[1024, "light"], [1440, "light"], [1440, "dark"]], null, true],
  ["carol", "rich", [[1440, "light"]], "trust", false],
  ["carol", "vis", [[1440, "light"]], null, false],
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
  const accountId = primary ? primary.id : "11bd21ba-091a-43fb-8e6a-96eab7d96d6c";
  for (const key of Object.keys(CLIENTS)) {
    const id = CLIENTS[key].id;
    // The hero's Open matters source (hero-tiles.ts): ACTIVE matters visible to this user.
    const open = await apiGet(tok, `/api/matters?customerId=${id}&status=ACTIVE`);
    out.push({ client: key, path: open.path, status: open.status, count: Array.isArray(open.body) ? open.body.length : null, names: Array.isArray(open.body) ? open.body.map((m) => m.name) : open.body });
    const ledger = await apiGet(tok, `/api/trust-accounts/${accountId}/client-ledgers/${id}`);
    out.push({ client: key, path: ledger.path, status: ledger.status, balance: ledger.body && ledger.body.balance });
    const debtor = await apiGet(tok, `/api/collections/debtors/${id}`);
    out.push({ client: key, path: debtor.path, status: debtor.status, outstandingInvoices: debtor.body && debtor.body.outstandingInvoices ? debtor.body.outstandingInvoices.map((r) => ({ n: r.invoiceNumber, balanceDue: r.balanceDue, currency: r.currency, daysOverdue: r.daysOverdue })) : debtor.body });
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
    // After: the DetailHero tile strip (641B obs-after's probe, trimmed).
    const ovf = (el) => (el ? el.scrollWidth > el.clientWidth + 1 : null);
    const strip = card ? card.querySelector('[data-slot="detail-hero-tiles"]') : null;
    const tiles = strip
      ? [...strip.children].map((wrap) => {
          const sc = wrap.querySelector('[data-slot="stat-card"]');
          const r = wrap.getBoundingClientRect();
          const value = sc && sc.querySelector('[data-slot="stat-card-value"]');
          const hint = sc && sc.querySelector('[data-slot="stat-card-hint"]');
          return {
            label: txt(sc && sc.querySelector('[data-slot="stat-card-label"]')),
            value: txt(value),
            hint: txt(hint),
            state: sc && sc.getAttribute("data-state"),
            isLink: wrap.tagName === "A",
            box: { x: Math.round(r.x), y: Math.round(r.y + window.scrollY), w: Math.round(r.width), h: Math.round(r.height) },
            valueOverflow: ovf(value),
            hintOverflow: ovf(hint),
          };
        })
      : [];
    const tops = [...new Set(tiles.map((t) => t.box.y))].sort((a, b) => a - b);
    const tilesPerRow = tops.map((y) => tiles.filter((t) => t.box.y === y).length);
    return {
      url: location.pathname + location.search,
      tileCount: tiles.length,
      tiles,
      tilesPerRow,
      trustBalancePrimary: txt(q('[data-testid="trust-balance-primary"]')),
      trustBalanceCard: txt(q('[data-testid="trust-balance-card"]')) ? txt(q('[data-testid="trust-balance-card"]')).slice(0, 300) : null,
      innerWidth: window.innerWidth,
      clientWidth: document.documentElement.clientWidth,
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
        // After: TrustBalanceCard is an SWR client fetch — wait for its primary line on ?tab=trust.
        if (tab === "trust") await page.getByTestId("trust-balance-primary").waitFor({ state: "visible", timeout: 20000 }).catch(() => {});
        await page.waitForTimeout(2500); // client islands hydrate / late console warnings land
        const file = shoot ? `${u}-${clientKey}${tab ? `-${tab}` : ""}-${vp.w}-${theme}.png` : null;
        if (file) await page.screenshot({ path: path.join(OUT, file), fullPage: true });
        const facts = await domFacts(page);
        results.loads.push({ user: u, client: clientKey, clientId: client.id, clientNote: client.note, tab: tab || "(default)", viewport: `${vp.w}x${vp.h}`, theme, httpStatus: resp ? resp.status() : null, h1ok, file, facts, console: consoleBuf });
        console.log(`captured ${u}/${clientKey}/${tab || "default"} ${vp.w} ${theme} status=${resp && resp.status()} h1=${h1ok} html=${facts.htmlClass} card=${facts.headerCardPresent} tabY=${facts.tabBarRect && facts.tabBarRect.y} console=${consoleBuf.length}`);
        console.log(`  tiles [${facts.tilesPerRow}] ${facts.tiles.map((t) => `${t.label}=${t.value}${t.hint ? ` (${t.hint})` : ""} w${t.box.w}`).join(" | ")} scrollW=${facts.scrollWidth}/${facts.innerWidth}${tab === "trust" ? ` trustCard=${facts.trustBalancePrimary}` : ""}`);
      }
    }
    // Does the header smart action do anything? Click it (a confirm dialog would be cancelled by
    // closing the context; nothing is confirmed) and count dialogs / URL change afterwards.
    results.smartActionClicks = results.smartActionClicks || [];
    for (const clientKey of [...new Set(PLAN.filter((p) => p[0] === user).map((p) => p[1]))].filter((k) => k === "rich" || k === "fx")) {
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
    // After: New Client (Clients list) and Edit Client (client page overflow) dialogs, Alice, 1024,
    // light + dark. Open only, never submitted (Escape).
    if (user === "alice") {
      results.dialogs = [];
      for (const theme of ["light", "dark"]) {
        await page.setViewportSize({ width: 1024, height: 768 });
        await page.evaluate((t) => localStorage.setItem("kazi-theme", t), theme);
        consoleBuf = [];
        await page.goto(`${BASE}/org/${SLUG}/clients`, { waitUntil: "load", timeout: 90000 });
        await page.locator("h1").first().waitFor({ state: "visible", timeout: 60000 });
        await page.waitForTimeout(2000);
        await page.getByRole("button", { name: "New Client" }).first().click();
        await page.getByRole("dialog").waitFor({ state: "visible", timeout: 15000 });
        await page.waitForTimeout(800);
        await page.screenshot({ path: path.join(OUT, `alice-dialog-create-1024-${theme}.png`) });
        results.dialogs.push({ dialog: "create", theme, ...(await dialogFacts(page)), console: consoleBuf });
        await page.keyboard.press("Escape");
        consoleBuf = [];
        await page.goto(`${BASE}/org/${SLUG}/clients/${CLIENTS.fx.id}`, { waitUntil: "load", timeout: 90000 });
        await page.locator("h1").first().waitFor({ state: "visible", timeout: 60000 });
        await page.waitForTimeout(2000);
        await page.getByTestId("client-overflow-trigger").click();
        await page.getByRole("menuitem", { name: "Edit Client" }).click();
        await page.getByRole("dialog").waitFor({ state: "visible", timeout: 15000 });
        await page.waitForTimeout(800);
        await page.screenshot({ path: path.join(OUT, `alice-dialog-edit-1024-${theme}.png`) });
        results.dialogs.push({ dialog: "edit", theme, ...(await dialogFacts(page)), console: consoleBuf });
        await page.keyboard.press("Escape");
        console.log(`dialogs ${theme}: ${results.dialogs.slice(-2).map((d) => `${d.dialog} "${d.title}" buttons=${JSON.stringify(d.buttons)}`).join(" ; ")}`);
      }
    }
    await context.close();
  }
  await browser.close();
  fs.writeFileSync(path.join(OUT, "obs-after.json"), JSON.stringify(results));
})().catch((e) => {
  console.error(e);
  process.exit(1);
});

// After: 641B obs-after's dialogFacts, trimmed to text + layout.
async function dialogFacts(page) {
  return page.evaluate(() => {
    const d = document.querySelector('[role="dialog"]');
    const txt = (el) => (el ? el.textContent.replace(/\s+/g, " ").trim() : null);
    const r = d.getBoundingClientRect();
    const f = d.querySelector('[data-slot="dialog-footer"]');
    return {
      title: txt(d.querySelector('[data-slot="dialog-title"], h2')),
      description: txt(d.querySelector('[data-slot="dialog-description"]')),
      headings: [...d.querySelectorAll("h3")].map(txt),
      labels: [...d.querySelectorAll("label")].map(txt),
      buttons: [...d.querySelectorAll("button")].map((b) => txt(b) || b.getAttribute("aria-label")),
      footer: txt(f),
      footerBorderTop: f ? getComputedStyle(f).borderTopWidth : null,
      box: { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) },
    };
  });
}
