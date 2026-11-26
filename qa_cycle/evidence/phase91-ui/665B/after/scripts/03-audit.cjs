// 665B OBS step 3: Audit trail paging + filter behaviour (Alice, 1440×900 light; read-only — only
// URL/filter changes, row expand, menus opened and closed; nothing is exported or saved).
// Records: page size, pager text / nav label / Previous-Next state, URL after Next and Previous,
// filters kept across pages, filter change resets to page 1, the "pending" state while a page fetch
// is in flight (the RSC request is held 3 s by page.route), zero-results state (Entity ID filter with
// an unused UUID; group preset sentinel), out-of-range ?page=, every filter's options, row expand,
// entity-link href pattern per entity type + where each lands (K-45), the export menu, and the
// interactive SEL-COMPAT checks (preset option, event-type search, spec event keys).
// Output: $RAW/audit.json + tiles (05 merges).
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const R = { capturedAt: new Date().toISOString(), steps: {}, selCompat: {} };
const consoleSink = [];
const tiles = [];
const lab = { label: "" };
const TAB = `${L.BASE}/settings/firm?tab=audit-trail`;
const UNUSED_UUID = "00000000-0000-4000-8000-000000000665";
const shot = async (target, key, caption, opt = {}) => {
  const f = path.join(L.RAW, `${key}.png`);
  await target.screenshot({ path: f, ...opt });
  tiles.push([key, f, caption]);
};
const rel = (u) => u.replace(L.BASE, "");
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(400); };

const STATE = () => {
  const main = document.querySelector("main#main-content");
  const txt = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
  const nav = main.querySelector('nav[aria-label="Audit log pagination"]');
  const cards = [...main.querySelectorAll('[data-slot="card"],[data-slot="settings-section"]')];
  const events = cards.find((c) => /^Events/.test(txt(c.querySelector('[data-slot="card-title"],h2') || c)));
  const title = events && events.querySelector('[data-slot="card-title"],h2');
  const table = events && events.querySelector("table");
  const rows = [...main.querySelectorAll('tr[data-testid^="audit-row-"]:not([data-testid^="audit-row-details-"])')];
  const cs = (e) => (e ? { opacity: getComputedStyle(e).opacity, pointerEvents: getComputedStyle(e).pointerEvents, ariaBusy: e.getAttribute("aria-busy"), cls: (e.className || "").toString().slice(0, 160) } : null);
  const empty = events && (events.querySelector('[data-slot="empty-state"]') || [...events.querySelectorAll("p")].find((p) => /^(No |The audit log)/.test(txt(p))));
  return {
    url: location.pathname + location.search,
    eventsTitle: title ? txt(title) : null,
    loadingSpan: title ? [...title.querySelectorAll("span")].map(txt).filter((t) => /Loading/.test(t)) : [],
    rowCount: rows.length,
    firstRowId: rows[0] ? rows[0].dataset.testid.replace("audit-row-", "") : null,
    actorsOnPage: [...new Set(rows.map((r) => txt(r.querySelector('[data-testid="actor-display"]') || r.children[4])))],
    severitiesOnPage: [...new Set(rows.map((r) => (r.querySelector('[data-testid="severity-pill"]') || {}).dataset?.severity))],
    pager: nav ? { label: nav.getAttribute("aria-label"), text: txt(nav), prev: { disabled: nav.querySelectorAll("button")[0].disabled, cls: nav.querySelectorAll("button")[0].className }, next: { disabled: nav.querySelectorAll("button")[1].disabled, cls: nav.querySelectorAll("button")[1].className } } : null,
    eventsCard: cs(events),
    tableContainer: cs(table && table.parentElement),
    table: cs(table),
    anyAriaBusy: [...document.querySelectorAll("[aria-busy]")].map((e) => `${e.tagName.toLowerCase()}[aria-busy=${e.getAttribute("aria-busy")}]`),
    anyDimmed: [...main.querySelectorAll("*")].filter((e) => +getComputedStyle(e).opacity < 1 && e.getBoundingClientRect().height > 100).map((e) => `${e.tagName.toLowerCase()}.${(e.className || "").toString().slice(0, 60)} opacity=${getComputedStyle(e).opacity}`).slice(0, 5),
    emptyState: empty ? { text: txt(empty), height: Math.round(empty.getBoundingClientRect().height), cardHeight: Math.round(events.getBoundingClientRect().height) } : null,
    filterTriggers: ["audit-filter-actor", "audit-filter-entity-type", "audit-filter-event-type"].map((id) => `${id}=${txt(document.getElementById(id) || document.body).slice(0, 60)}`),
    dateInputs: ["audit-filter-from", "audit-filter-to", "audit-filter-entity-id"].map((id) => `${id}=${(document.getElementById(id) || {}).value}`),
    severityPressed: [...main.querySelectorAll('[data-testid^="severity-toggle-"]')].map((b) => `${txt(b)}:${b.getAttribute("aria-pressed")}`),
    clearFilters: !!main.querySelector('[data-testid="audit-clear-filters"]'),
    banner: (main.querySelector('[data-testid="audit-preset-multi-event-banner"]') || {}).innerText || null,
  };
};

async function clickAndWaitUrl(p, locator, re) {
  await Promise.all([p.waitForURL(re, { timeout: 30000 }), locator.click()]);
  await settle(p);
}
async function listboxOptions(p) {
  return p.evaluate(() => {
    const lb = document.querySelector('[role="listbox"]');
    if (!lb) return null;
    const out = [];
    for (const e of lb.querySelectorAll('[role="option"],[role="group"] > div:first-child, [data-slot="select-label"]')) {
      const t = (e.innerText || "").replace(/\s+/g, " ").trim();
      out.push(e.getAttribute("role") === "option" ? t : `[${t}]`);
    }
    return out;
  });
}

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);

  // 1. Landing
  lab.label = "audit|landing";
  await p.goto(TAB, { waitUntil: "load" });
  await settle(p);
  const s0 = await p.evaluate(STATE);
  R.steps.landing = s0;
  R.pageSize = s0.rowCount;

  // 2. Next → page 2 (URL), then Previous → page 1
  lab.label = "audit|next";
  await clickAndWaitUrl(p, p.getByRole("button", { name: "Next", exact: true }), /page=1/);
  R.steps.afterNext = await p.evaluate(STATE);
  await shot(p, "audit__page2", `Alice 1440 light — after Next: URL ${rel(p.url())}; pager "${R.steps.afterNext.pager.text}"`);
  lab.label = "audit|prev";
  await clickAndWaitUrl(p, p.getByRole("button", { name: "Previous", exact: true }), (u) => !/[?&]page=/.test(u.search));
  R.steps.afterPrevious = await p.evaluate(STATE);

  // 3. Pending state: hold the RSC request 3 s, sample the DOM while it is in flight.
  lab.label = "audit|pending";
  let held = 0;
  await p.route("**/settings/firm**", async (route) => {
    const h = route.request().headers();
    if (h["rsc"] === "1" || /[?&]_rsc=/.test(route.request().url())) {
      held++;
      await new Promise((r) => setTimeout(r, 3000));
    }
    await route.continue().catch(() => {});
  });
  const t0 = Date.now();
  await p.getByRole("button", { name: "Next", exact: true }).click();
  const samples = [];
  for (const ms of [150, 600, 1500]) {
    await p.waitForTimeout(Math.max(0, ms - (Date.now() - t0)));
    samples.push({ atMs: Date.now() - t0, ...(await p.evaluate(STATE)) });
    if (ms === 600) {
      const card = p.locator('[data-slot="card"],[data-slot="settings-section"]').filter({ has: p.locator('[data-slot="card-title"],h2', { hasText: /^Events/ }) });
      await shot(card, "audit__pending-events-card", `Alice 1440 light — page fetch in flight (RSC held 3 s), ${Date.now() - t0} ms after Next: Events card (top) — the only pending cue is the "Loading…" span in the card title`);
      await shot(p, "audit__pending-viewport", `Alice 1440 light — page fetch in flight, viewport: table keeps the old page at full opacity; pager unchanged`);
    }
  }
  await p.waitForURL(/page=1/, { timeout: 30000 });
  await settle(p);
  samples.push({ atMs: Date.now() - t0, after: true, ...(await p.evaluate(STATE)) });
  await p.unroute("**/settings/firm**");
  R.steps.pending = { heldRequests: held, samples };

  // 4. Filters kept across pages; filter change resets to page 1.
  lab.label = "audit|filter-actor";
  await p.goto(TAB, { waitUntil: "load" });
  await settle(p);
  await p.locator("#audit-filter-actor").click();
  R.filterOptions = { actor: await listboxOptions(p) };
  await clickAndWaitUrl(p, p.getByRole("option", { name: /^Alice Owner/ }), /actorId=/);
  R.steps.actorFiltered = await p.evaluate(STATE);
  await clickAndWaitUrl(p, p.getByRole("button", { name: "Next", exact: true }), /actorId=.*page=1|page=1.*actorId=/);
  R.steps.actorFilteredPage2 = await p.evaluate(STATE);
  await shot(p, "audit__filtered-page2", `Alice 1440 light — Actor = Alice Owner, page 2: URL ${rel(p.url())}; pager "${R.steps.actorFilteredPage2.pager.text}"; actors on page ${JSON.stringify(R.steps.actorFilteredPage2.actorsOnPage)}`);
  lab.label = "audit|filter-change-resets";
  await clickAndWaitUrl(p, p.getByTestId("severity-toggle-INFO"), /severities=INFO/);
  R.steps.severityToggledFromPage2 = await p.evaluate(STATE);
  await clickAndWaitUrl(p, p.getByRole("button", { name: "Next", exact: true }), /page=1/);
  R.steps.severityFilteredPage2 = await p.evaluate(STATE);
  await p.locator("#audit-filter-entity-type").click();
  R.filterOptions.entityType = await listboxOptions(p);
  await clickAndWaitUrl(p, p.getByRole("option", { name: /^Client/ }).first(), /entityType=customer/);
  R.steps.entityTypeChangedFromPage2 = await p.evaluate(STATE);
  await clickAndWaitUrl(p, p.getByTestId("audit-clear-filters"), (u) => !/actorId|severities|entityType/.test(u.search));
  R.steps.afterClearFilters = await p.evaluate(STATE);

  // 5. Event type combobox (day-75-85 / day-30-45 flow) + options.
  lab.label = "audit|event-type";
  await p.locator("#audit-filter-event-type").click();
  const search = p.getByPlaceholder("Search event types…");
  R.selCompat["day-75-85:81 placeholder 'Search event types…' after open (strict)"] = await search.count();
  R.filterOptions.eventTypeFirst12 = await p.evaluate(() => [...document.querySelectorAll('[cmdk-item],[role="option"]')].slice(0, 12).map((e) => (e.innerText || "").replace(/\s+/g, " ").trim()));
  R.filterOptions.eventTypeCount = await p.evaluate(() => document.querySelectorAll('[cmdk-item],[role="option"]').length);
  for (const key of ["disbursement.written_off", "matter.closure.override_used"]) {
    if ((await search.count()) === 0) { await p.locator("#audit-filter-event-type").click(); }
    await search.fill(key);
    await p.waitForTimeout(200);
    const firstItem = await p.evaluate(() => { const i = document.querySelector('[cmdk-item],[role="option"]'); return i ? (i.innerText || "").replace(/\s+/g, " ").trim() : null; });
    await Promise.all([p.waitForURL(/eventType=/, { timeout: 30000 }), search.press("Enter")]);
    await settle(p);
    const st = await p.evaluate(STATE);
    R.selCompat[`day-specs: event type '${key}' + Enter → first item / URL / rows`] = { firstItem, url: st.url, rows: st.rowCount, total: st.eventsTitle, trigger: st.filterTriggers[2] };
    await p.goto(TAB, { waitUntil: "load" });
    await settle(p);
  }

  // 6. Zero results: Entity ID = unused UUID (Enter commits).
  lab.label = "audit|zero";
  await p.locator("#audit-filter-entity-id").fill(UNUSED_UUID);
  await Promise.all([p.waitForURL(/entityId=/, { timeout: 30000 }), p.locator("#audit-filter-entity-id").press("Enter")]);
  await settle(p);
  R.steps.zeroResults = await p.evaluate(STATE);
  await shot(p, "audit__zero-results", `Alice 1440 light — zero results (Entity ID ${UNUSED_UUID}): "${R.steps.zeroResults.emptyState && R.steps.zeroResults.emptyState.text}"; pager "${R.steps.zeroResults.pager.text}"`, { fullPage: true });
  // Invalid entity id: inline error, not committed.
  await p.goto(TAB, { waitUntil: "load" });
  await settle(p);
  await p.locator("#audit-filter-entity-id").fill("abc");
  await p.locator("#audit-filter-entity-id").press("Enter");
  await p.waitForTimeout(500);
  R.steps.invalidEntityId = { url: rel(p.url()), alert: await p.locator("#audit-filter-entity-id-error").allInnerTexts(), ariaInvalid: await p.locator("#audit-filter-entity-id").getAttribute("aria-invalid") };

  // 7. Out-of-range page (no filters) and the far-future date range.
  lab.label = "audit|out-of-range";
  await p.goto(`${TAB}&page=99`, { waitUntil: "load" });
  await settle(p);
  R.steps.outOfRange = await p.evaluate(STATE);
  await shot(p, "audit__out-of-range", `Alice 1440 light — ?page=99 (no filters): "${R.steps.outOfRange.emptyState && R.steps.outOfRange.emptyState.text}"; pager "${R.steps.outOfRange.pager.text}"`);
  await p.goto(`${TAB}&from=2030-01-01T00:00:00.000Z`, { waitUntil: "load" });
  await settle(p);
  R.steps.futureFrom = await p.evaluate(STATE);

  // 8. Presets: options, Sensitive (smoke spec), Compliance (group sentinel → banner + zero rows).
  lab.label = "audit|presets";
  await p.goto(TAB, { waitUntil: "load" });
  await settle(p);
  await p.getByTestId("audit-preset-select").click();
  R.filterOptions.preset = await listboxOptions(p);
  R.selCompat["audit-smoke.spec:33 option /Sensitive/i after open (strict click)"] = await p.getByRole("option", { name: /Sensitive/i }).count();
  await clickAndWaitUrl(p, p.getByRole("option", { name: /Sensitive/i }), /severities=/);
  R.steps.presetSensitive = await p.evaluate(STATE);
  R.selCompat["audit-smoke.spec:45 event-row count after Sensitive"] = await p.locator('[data-testid^="audit-row-"]:not([data-testid^="audit-row-toggle-"]):not([data-testid^="audit-row-details-"])').count();
  await p.getByTestId("audit-preset-select").click();
  await clickAndWaitUrl(p, p.getByRole("option", { name: /^Compliance/ }), /eventType=/);
  R.steps.presetCompliance = await p.evaluate(STATE);
  await shot(p, "audit__preset-compliance", `Alice 1440 light — Compliance preset (group sentinel): banner + "${R.steps.presetCompliance.emptyState && R.steps.presetCompliance.emptyState.text}"; pager "${R.steps.presetCompliance.pager.text}"`, { fullPage: true });

  // 9. Row expand / collapse.
  lab.label = "audit|expand";
  await p.goto(TAB, { waitUntil: "load" });
  await settle(p);
  const firstToggle = p.locator('[data-testid^="audit-row-toggle-"]').first();
  R.steps.expand = { before: { label: await firstToggle.getAttribute("aria-label"), expanded: await firstToggle.getAttribute("aria-expanded") } };
  await firstToggle.click();
  await p.waitForTimeout(300);
  const details = p.locator('[data-testid^="audit-row-details-"]').first();
  R.steps.expand.after = { label: await firstToggle.getAttribute("aria-label"), expanded: await firstToggle.getAttribute("aria-expanded"), detailsRows: await p.locator('[data-testid^="audit-row-details-"]').count(), detailsText: ((await details.innerText()) || "").replace(/\s+/g, " ").slice(0, 400) };
  await shot(p, "audit__row-expanded", `Alice 1440 light — first row expanded: toggle "${R.steps.expand.after.label}", details row`);
  await firstToggle.click();
  await p.waitForTimeout(300);
  R.steps.expand.collapsed = { label: await firstToggle.getAttribute("aria-label"), detailsRows: await p.locator('[data-testid^="audit-row-details-"]').count() };

  // 10. Export menu (opened, items read, closed — nothing exported).
  lab.label = "audit|export-menu";
  await p.getByTestId("export-dropdown-trigger").click();
  await p.waitForTimeout(400);
  R.exportMenu = { trigger: ((await p.getByTestId("export-dropdown-trigger").innerText()) || "").trim(), items: await p.evaluate(() => [...document.querySelectorAll('[role="menuitem"],[data-testid^="export-dropdown-"]')].filter((e) => e.dataset.testid !== "export-dropdown-trigger").map((e) => ({ testid: e.dataset.testid, text: (e.innerText || "").replace(/\s+/g, " ").trim(), disabled: e.getAttribute("aria-disabled") || e.getAttribute("data-disabled") }))) };
  await p.keyboard.press("Escape");

  // 11. Entity links per type across every page (K-45), then where each type's first link lands.
  lab.label = "audit|entity-links";
  const byType = {};
  const literalTypes = {};
  const emptyTypes = {};
  const total = s0.pager ? +((s0.pager.text.match(/of (\d+)/) || [])[1] || 1) : 1;
  for (let pg = 0; pg < total; pg++) {
    await p.goto(`${TAB}${pg ? `&page=${pg}` : ""}`, { waitUntil: "load" });
    await settle(p);
    const found = await p.evaluate(() => ({
      links: [...document.querySelectorAll('[data-testid="entity-cell-link"]')].map((a) => [a.dataset.entityType, a.getAttribute("href"), (a.innerText || "").trim()]),
      literal: [...document.querySelectorAll('[data-testid="entity-cell-literal"]')].map((a) => a.dataset.entityType),
      empty: [...document.querySelectorAll('[data-testid="entity-cell-empty"]')].map((a) => a.dataset.entityType),
    }));
    for (const [t, href, text] of found.links) (byType[t] = byType[t] || { count: 0, samples: [] }).count++, byType[t].samples.length < 3 && byType[t].samples.push({ href, text });
    for (const t of found.literal) literalTypes[t] = (literalTypes[t] || 0) + 1;
    for (const t of found.empty) emptyTypes[t] = (emptyTypes[t] || 0) + 1;
  }
  const landing = {};
  for (const [t, v] of Object.entries(byType)) {
    landing[t] = [];
    for (const smp of v.samples.slice(0, 2)) {
      const resp = await p.goto(`${L.APP}${smp.href}`, { waitUntil: "load" }).catch((e) => null);
      await settle(p);
      const body = await p.locator("body").innerText().catch(() => "");
      landing[t].push({ href: smp.href.replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g, "{uuid}"), status: resp ? resp.status() : null, finalUrl: rel(p.url()).replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g, "{uuid}"), notFound: /not found|404|could not be found|doesn.t exist/i.test(body), somethingWentWrong: body.includes("Something went wrong"), h1: (await p.locator("#main-content h1").allInnerTexts().catch(() => [])).slice(0, 2) });
    }
  }
  R.entityLinks = { pagesScanned: total, linkTypes: Object.fromEntries(Object.entries(byType).map(([t, v]) => [t, { count: v.count, hrefPattern: v.samples[0].href.replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g, "{uuid}"), sampleText: v.samples[0].text }])), literalTypes, emptyTypes, landing };

  await browser.close();
  R.console = consoleSink;
  R.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "audit.json"), JSON.stringify(R));
  const brief = { pageSize: R.pageSize, landing: s0.pager, afterNext: [R.steps.afterNext.url, R.steps.afterNext.pager.text], afterPrevious: [R.steps.afterPrevious.url, R.steps.afterPrevious.pager.text], pending: samples.map((x) => ({ at: x.atMs, url: x.url, loading: x.loadingSpan, pager: x.pager.text, op: x.tableContainer && x.tableContainer.opacity, busy: x.anyAriaBusy, dim: x.anyDimmed })), held };
  console.log(JSON.stringify(brief));
  for (const k of ["actorFiltered", "actorFilteredPage2", "severityToggledFromPage2", "severityFilteredPage2", "entityTypeChangedFromPage2", "afterClearFilters", "zeroResults", "outOfRange", "futureFrom", "presetSensitive", "presetCompliance"]) console.log(k, JSON.stringify({ url: R.steps[k].url, title: R.steps[k].eventsTitle, rows: R.steps[k].rowCount, pager: R.steps[k].pager.text, empty: R.steps[k].emptyState, actors: R.steps[k].actorsOnPage, sev: R.steps[k].severitiesOnPage, banner: R.steps[k].banner }));
  console.log("invalidEntityId", JSON.stringify(R.steps.invalidEntityId));
  console.log("expand", JSON.stringify(R.steps.expand));
  console.log("export", JSON.stringify(R.exportMenu));
  console.log("filterOptions", JSON.stringify(R.filterOptions).slice(0, 3000));
  console.log("selCompat", JSON.stringify(R.selCompat));
  console.log("entityLinks", JSON.stringify(R.entityLinks));
  console.log("console", consoleSink.length, JSON.stringify(consoleSink).slice(0, 1500));
})();
