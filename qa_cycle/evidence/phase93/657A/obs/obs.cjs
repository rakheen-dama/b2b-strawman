// 657A OBS — real-browser observation of the trust take-on registry (e2e mock stack :3001, built @05812c045).
// Usage: node obs.cjs   (one run; writes screenshots, JSON and obs-log.txt into this directory)
// No SQL, no writes except the deliberate crafted refusals of check 3 (sent with the browser session's own token).
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-657A/frontend/node_modules/@playwright/test');
const fs = require('fs');
const crypto = require('crypto');
const D = __dirname;
const BASE = 'http://localhost:3001';
const API = 'http://localhost:8081';
const SLUG = 'e2e-test-org';
const ORG = `${BASE}/org/${SLUG}`;
const NDLOVU = { id: '212958bf-ca90-4446-b96f-200393c42b2c', name: 'Ndlovu Family Trust' };
const KHUMALO = { id: '38a2f48e-ccb6-4031-ba53-084f3cae8e59', name: 'Khumalo Holdings' };
const G_ID = '9b2141a6-f46d-423e-81e6-c6c05b337641';
const G_LIST_ID = '302306b2-0c53-463d-a4f2-02a573614eec';
const IB_LIST_ID = '1b8ca8a5-e77c-40e8-bcde-53ce3240f0a2';
const LINE_ID = 'e01a8f2c-ac2f-42ce-b3f2-57b929b6997f'; // G statement line "DEPOSIT NDLOVU FAMILY TRUST" (AUTO_MATCHED)

const log = [];
const L = (...a) => { const s = a.map(x => (typeof x === 'string' ? x : JSON.stringify(x))).join(' '); console.log(s); log.push(s); };
const results = {};
let page, context, currentLabel = 'init', token;
const consoleErrors = [];
const norm = s => s.replace(/[  ]/g, ' ');
const shot = async n => { await page.screenshot({ path: `${D}/${n}.png`, fullPage: true }); L('[shot]', `${n}.png`); };
const GENERIC_FORBIDDEN = /take-on|takeon|take on|brought forward|b\/f|cutover/i;
let takeOnLabels = [];
function forbiddenHits(text) {
  const t = norm(text);
  const hits = [];
  const m = t.match(GENERIC_FORBIDDEN);
  if (m) hits.push(m[0]);
  for (const l of takeOnLabels) if (t.includes(l)) hits.push(l);
  return hits;
}

async function login(user) {
  await page.goto(`${BASE}/mock-login`, { waitUntil: 'load' });
  await page.locator('#user-select').selectOption(user);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });
  L('[login]', user, page.url());
  const c = (await context.cookies()).find(x => x.name === 'mock-auth-token');
  if (!c) throw new Error('no mock-auth-token cookie after login');
  token = c.value;
  L('[session] mock-auth-token cookie present, length', token.length);
}

// The browser session's own credentials: the frontend server sends this cookie's JWT as a Bearer (lib/api/client.ts)
const authHeaders = extra => ({ Authorization: `Bearer ${token}`, ...extra });

async function check1Types() {
  currentLabel = 'transactions-page';
  const seen = [];
  const onResp = r => { if (r.url().includes('trust-transaction-types')) seen.push(`${r.status()} ${r.url()}`); };
  page.on('response', onResp);
  await page.goto(`${ORG}/trust-accounting/transactions?dateFrom=2026-10-01&dateTo=2026-10-31`, { waitUntil: 'load' });
  await page.getByRole('heading', { level: 1, name: 'Transactions' }).waitFor({ timeout: 60000 });
  await page.waitForTimeout(1500);
  page.off('response', onResp);
  L('[c1] browser-visible requests to /api/trust-transaction-types while loading Transactions (RSC fetches server-side):', seen);
  // The same endpoint, fetched with the logged-in session's token from the browser context
  const resp = await page.request.get(`${API}/api/trust-transaction-types`, { headers: authHeaders() });
  const body = await resp.json();
  fs.writeFileSync(`${D}/types-endpoint.json`, JSON.stringify(body, null, 2) + '\n');
  const takeOn = body.filter(t => t.takeOnOnly === true);
  const notTakeOn = body.filter(t => t.takeOnOnly === false);
  const cleared = body.filter(t => t.clearedAtCutover === true);
  const r = {
    status: resp.status(),
    total: body.length,
    takeOnOnly: takeOn.map(t => ({ code: t.code, label: t.label, ledger: t.ledger, origin: t.origin, clearedAtCutover: t.clearedAtCutover, reversal: t.reversal, requiresApproval: t.requiresApproval })),
    takeOnOnlyAllTAKEON: takeOn.every(t => t.code.startsWith('TAKEON_')),
    takeOnOnlyCount: takeOn.length,
    notTakeOnCount: notTakeOn.length,
    notTakeOnAnyTAKEON: notTakeOn.some(t => t.code.startsWith('TAKEON_')),
    missingFlags: body.filter(t => typeof t.takeOnOnly !== 'boolean' || typeof t.clearedAtCutover !== 'boolean').map(t => t.code),
    clearedAtCutover: cleared.map(t => t.code),
    clearedOutsideTakeOn: cleared.filter(t => !t.takeOnOnly).map(t => t.code),
    preExistingCodes: notTakeOn.map(t => t.code),
  };
  takeOnLabels = takeOn.map(t => t.label);
  results.c1 = r;
  L('[c1 types endpoint]', r);
  return body;
}

async function pills(label) {
  const box = page.getByTestId('type-filters');
  await box.waitFor({ timeout: 60000 });
  const links = await box.locator('a').allInnerTexts();
  const text = await box.innerText();
  L(`[${label} type pills] count(excl. All)=${links.length - 1}`, links);
  return { links, text, hits: forbiddenHits(text) };
}

async function dialogInfo(dialog) {
  const text = await dialog.innerText();
  const comboLabels = await dialog.locator('[role="combobox"], select, button[aria-haspopup="listbox"]').evaluateAll(els => els.map(e => (e.getAttribute('data-testid') || '') + ' :: ' + (e.getAttribute('aria-label') || e.innerText || '').trim()));
  const radios = await dialog.locator('[role="radio"]').evaluateAll(els => els.map(e => e.getAttribute('value') || e.id));
  return { text: norm(text).replace(/\n+/g, ' | ').slice(0, 1500), comboLabels, radios, hits: forbiddenHits(text) };
}

async function closeDialog(dialog) {
  const cancel = dialog.getByRole('button', { name: 'Cancel', exact: true });
  if (await cancel.count()) await cancel.first().click(); else await page.keyboard.press('Escape');
  await dialog.waitFor({ state: 'hidden', timeout: 15000 });
}

async function check2(types) {
  results.c2 = { dialogs: {} };
  // (a) Transactions page pills
  const offered = types.filter(t => !t.takeOnOnly);
  const tp = await pills('c2 transactions');
  await shot('s2-01-transactions-type-pills');
  const expectedTx = ['All', ...offered.map(t => t.label)];
  results.c2.transactionsPills = { count: tp.links.length - 1, expectedCount: offered.length, equalsOfferedList: JSON.stringify(tp.links) === JSON.stringify(expectedTx), forbiddenHits: tp.hits };
  L('[c2 transactions pills == offered registry labels in order]', results.c2.transactionsPills);

  // (b) Record Transaction menu and each dialog
  currentLabel = 'record-menu';
  await page.getByTestId('record-transaction-button').click();
  const menu = page.getByRole('menu');
  await menu.waitFor();
  const items = await menu.getByRole('menuitem').allInnerTexts();
  L('[c2 Record Transaction menu items]', items);
  results.c2.menuItems = items;
  results.c2.menuHits = forbiddenHits(items.join(' '));
  await shot('s2-02-record-transaction-menu');
  await page.keyboard.press('Escape');
  await menu.waitFor({ state: 'hidden', timeout: 15000 });
  let n = 3;
  for (const item of items) {
    currentLabel = `dialog ${item}`;
    await page.getByTestId('record-transaction-button').click();
    await page.getByRole('menu').waitFor({ timeout: 15000 });
    await page.waitForTimeout(400); // the menu's open animation (the item re-mounts while it runs)
    await page.getByRole('menuitem', { name: item, exact: true }).click();
    const dialog = page.getByRole('dialog').first();
    await dialog.waitFor({ timeout: 15000 });
    await page.waitForTimeout(800);
    const info = await dialogInfo(dialog);
    const title = await dialog.getByRole('heading').first().innerText().catch(() => item);
    info.title = title;
    results.c2.dialogs[`transactions: ${item}`] = info;
    L(`[c2 dialog "${item}"]`, info);
    await shot(`s2-${String(n++).padStart(2, '0')}-dialog-${item.toLowerCase().replace(/[^a-z]+/g, '-')}`);
    await closeDialog(dialog);
  }

  // (c) Reversal still offered on a reversible row (canOfferReversal reads the full registry)
  currentLabel = 'reversal';
  const revButtons = page.locator('[data-testid^="reversal-action-"] [data-testid="reverse-button"]');
  const revCount = await revButtons.count();
  const rowCount = await page.locator('[data-testid^="transaction-row-"]').count();
  L('[c2 rows on page / rows offering Reverse]', rowCount, revCount);
  results.c2.reversal = { rowsOnPage: rowCount, reverseButtons: revCount };
  if (revCount > 0) {
    const rowEl = page.locator('[data-testid^="transaction-row-"]').filter({ has: page.getByTestId('reverse-button') }).first();
    const rowText = norm(await rowEl.innerText()).replace(/\s+/g, ' ');
    results.c2.reversal.firstRow = rowText;
    L('[c2 first row offering Reverse]', rowText);
    await rowEl.getByTestId('reverse-button').click();
    const dialog = page.getByRole('dialog', { name: 'Reverse Transaction' });
    await dialog.waitFor({ timeout: 15000 });
    const info = await dialogInfo(dialog);
    results.c2.dialogs['transactions: Reverse Transaction'] = info;
    L('[c2 dialog "Reverse Transaction"]', info);
    await shot(`s2-${String(n++).padStart(2, '0')}-dialog-reverse-transaction`);
    await closeDialog(dialog);
  }

  // (d) Client ledger pages: pills
  for (const c of [NDLOVU, KHUMALO]) {
    currentLabel = `client-ledger ${c.name}`;
    await page.goto(`${ORG}/trust-accounting/client-ledgers/${c.id}`, { waitUntil: 'load' });
    await page.getByTestId('client-detail-page').waitFor({ timeout: 60000 });
    const p = await pills(`c2 ledger ${c.name}`);
    const offeredLedger = offered.filter(t => t.ledger === 'CLIENT' || t.ledger === 'CLIENT_INVESTMENT' || t.code === 'REVERSAL');
    const unfilteredLedger = types.filter(t => t.ledger === 'CLIENT' || t.ledger === 'CLIENT_INVESTMENT' || t.code === 'REVERSAL');
    results.c2[`ledgerPills ${c.name}`] = {
      count: p.links.length - 1,
      expectedCount: offeredLedger.length,
      wouldBeWithoutFilter: unfilteredLedger.length,
      equalsOfferedList: JSON.stringify(p.links) === JSON.stringify(['All', ...offeredLedger.map(t => t.label)]),
      forbiddenHits: p.hits,
    };
    L(`[c2 ledger pills ${c.name}]`, results.c2[`ledgerPills ${c.name}`]);
    await shot(`s2-${String(n++).padStart(2, '0')}-client-ledger-pills-${c.name.split(' ')[0].toLowerCase()}`);
  }

  // (e) Client page Trust card quick actions (Record Deposit / Record Payment / Fee Transfer)
  currentLabel = 'client page trust card';
  // The client page's Trust tab (customer-grouped-tabs.tsx: Tabs value "trust", under the Fees group)
  await page.goto(`${ORG}/clients/${NDLOVU.id}?tab=trust`, { waitUntil: 'load' });
  const card = page.getByTestId('trust-balance-card');
  // isVisible() does not wait: wait for the card (the client page streams in) before judging it absent
  if (await card.waitFor({ timeout: 60000 }).then(() => true, () => false)) {
    await shot(`s2-${String(n++).padStart(2, '0')}-client-page-trust-card`);
    for (const btn of ['Record Deposit', 'Record Payment', 'Fee Transfer']) {
      currentLabel = `client card ${btn}`;
      await card.getByRole('button', { name: btn, exact: true }).click();
      const dialog = page.getByRole('dialog').first();
      await dialog.waitFor({ timeout: 15000 });
      await page.waitForTimeout(800);
      const info = await dialogInfo(dialog);
      results.c2.dialogs[`client page trust card: ${btn}`] = info;
      L(`[c2 client card dialog "${btn}"]`, info);
      await shot(`s2-${String(n++).padStart(2, '0')}-client-card-${btn.toLowerCase().replace(/[^a-z]+/g, '-')}`);
      await closeDialog(dialog);
    }
  } else {
    results.c2.clientCard = 'trust-balance-card not visible';
    L('[c2 client page] trust-balance-card not visible');
    await shot(`s2-${String(n++).padStart(2, '0')}-client-page-no-trust-card`);
  }

  // (f) Investments page: Place Investment (the one other create dialog on a trust page with no statement upload)
  currentLabel = 'investments';
  await page.goto(`${ORG}/trust-accounting/investments`, { waitUntil: 'load' });
  await page.getByTestId('investments-page').waitFor({ timeout: 60000 });
  await page.getByRole('button', { name: 'Place Investment', exact: true }).click();
  {
    const dialog = page.getByRole('dialog').first();
    await dialog.waitFor({ timeout: 15000 });
    await page.waitForTimeout(800);
    const info = await dialogInfo(dialog);
    results.c2.dialogs['investments: Place Investment'] = info;
    L('[c2 dialog "Place Investment"]', info);
    await shot(`s2-${String(n++).padStart(2, '0')}-dialog-place-investment`);
    await closeDialog(dialog);
  }
  results.c2.anyDialogHits = Object.entries(results.c2.dialogs).filter(([, v]) => v.hits.length).map(([k, v]) => `${k}: ${v.hits}`);
  L('[c2 dialogs with a take-on hit]', results.c2.anyDialogHits);
}

async function check3() {
  currentLabel = 'crafted';
  const out = [];
  const at = new Date().toISOString();
  // 3a: record-from-statement-line is the only trust write whose body names what is posted (StatementLineClassification)
  const key = crypto.randomUUID();
  const body3a = { classification: 'TAKEON_CLIENT', notes: '657A OBS crafted request' };
  const r3a = await page.request.post(`${API}/api/bank-statement-lines/${LINE_ID}/record`, {
    headers: authHeaders({ 'Content-Type': 'application/json', 'Idempotency-Key': key }),
    data: body3a,
  });
  const t3a = await r3a.text();
  out.push(`# 3a  ${at}\nPOST ${API}/api/bank-statement-lines/${LINE_ID}/record\nIdempotency-Key: ${key}\nAuthorization: Bearer <mock-auth-token cookie of Alice's browser session>\n${JSON.stringify(body3a)}\n→ HTTP ${r3a.status()}\n${t3a}\n`);
  // 3b: the registry endpoint is read-only
  const body3b = { code: 'TAKEON_CLIENT' };
  const r3b = await page.request.post(`${API}/api/trust-transaction-types`, {
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    data: body3b,
  });
  const t3b = await r3b.text();
  out.push(`# 3b  ${new Date().toISOString()}\nPOST ${API}/api/trust-transaction-types\n${JSON.stringify(body3b)}\n→ HTTP ${r3b.status()}\n${t3b}\n`);
  // 3c (control): the same endpoint and line with a real classification reaches the service and is refused there
  // (the line is AUTO_MATCHED, StatementLines.requireUnmatched runs before anything is written) — so 3a's 400 is the
  // body's TAKEON_CLIENT value, not the route, the auth or the line.
  const key3c = crypto.randomUUID();
  const body3c = { classification: 'FUND_INTEREST', notes: '657A OBS control request' };
  const r3c = await page.request.post(`${API}/api/bank-statement-lines/${LINE_ID}/record`, {
    headers: authHeaders({ 'Content-Type': 'application/json', 'Idempotency-Key': key3c }),
    data: body3c,
  });
  const t3c = await r3c.text();
  out.push(`# 3c (control)  ${new Date().toISOString()}\nPOST ${API}/api/bank-statement-lines/${LINE_ID}/record\nIdempotency-Key: ${key3c}\n${JSON.stringify(body3c)}\n→ HTTP ${r3c.status()}\n${t3c}\n`);
  fs.writeFileSync(`${D}/crafted-post.txt`, out.join('\n'));
  results.c3 = { r3a: r3a.status(), r3aBody: t3a, r3b: r3b.status(), r3bBody: t3b, r3c: r3c.status(), r3cBody: t3c };
  L('[c3 crafted]', results.c3);
}

async function check4() {
  results.c4 = {};
  const expect = {
    [NDLOVU.name]: { general: 'R 69 380,00', invested: null, total: null },
    [KHUMALO.name]: { general: 'R 31 000,00', invested: 'R 20 095,00', total: 'R 51 095,00' },
  };
  let n = 1;
  for (const c of [NDLOVU, KHUMALO]) {
    currentLabel = `ledger ${c.name}`;
    await page.goto(`${ORG}/trust-accounting/client-ledgers/${c.id}`, { waitUntil: 'load' });
    await page.getByTestId('client-detail-page').waitFor({ timeout: 60000 });
    const breakdown = page.getByTestId('ledger-totals-breakdown');
    await breakdown.waitFor({ timeout: 30000 });
    const dd = async id => (await page.getByTestId(id).count()) ? norm(await page.getByTestId(id).locator('dd').innerText()).trim() : null;
    const got = {
      general: await dd('ledger-total-balance'),
      invested: await dd('ledger-total-invested'),
      total: await dd('ledger-total-held'),
      broughtForwardRowCount: await page.getByTestId('ledger-total-brought-forward').count(),
      breakdown: norm(await breakdown.innerText()).replace(/\n+/g, ' | '),
      pageHasBroughtForwardText: /brought forward/i.test(await page.locator('main').innerText()),
    };
    const api = await page.request.get(`${API}/api/trust-accounts/${G_ID}/client-ledgers/${c.id}`, { headers: authHeaders() });
    const apiBody = await api.json();
    got.apiBroughtForward = apiBody.broughtForward;
    got.apiBalance = apiBody.balance;
    const e = expect[c.name];
    got.matches = got.general === e.general && got.invested === e.invested && got.total === e.total && got.broughtForwardRowCount === 0 && !got.pageHasBroughtForwardText;
    results.c4[c.name] = got;
    L(`[c4 ledger ${c.name}]`, got, 'expected', e);
    await shot(`s4-${String(n++).padStart(2, '0')}-ledger-card-${c.name.split(' ')[0].toLowerCase()}`);
  }

  currentLabel = 'reconciliation list page';
  await page.goto(`${ORG}/trust-accounting/reconciliation`, { waitUntil: 'load' });
  await page.getByTestId('reconciliations-table').waitFor({ timeout: 60000 });
  const reconRows = await page.locator('[data-testid^="reconciliation-row-"]').allInnerTexts();
  results.c4.reconciliations = reconRows.map(r => norm(r).replace(/\s+/g, ' '));
  L('[c4 reconciliations]', results.c4.reconciliations);
  await shot(`s4-${String(n++).padStart(2, '0')}-reconciliations`);

  for (const [id, label, want] of [
    [G_LIST_ID, 'g', ['101 630,00', '69 380,00', '31 000,00', '1 250,00']],
    [IB_LIST_ID, 'ib', ['20 095,00']],
  ]) {
    currentLabel = `monthly list ${label}`;
    await page.goto(`${ORG}/trust-accounting/reconciliation/lists/${id}`, { waitUntil: 'load' });
    await page.getByTestId('monthly-list-page').waitFor({ timeout: 60000 });
    const txt = norm(await page.getByTestId('monthly-list-page').innerText());
    const stat = {};
    for (const s of ['Adjusted bank balance', 'Cashbook', 'Trust creditors', 'Difference']) {
      const m = txt.match(new RegExp(`${s}\\s*\\n\\s*(-?R [0-9 ]+,\\d\\d)`));
      stat[s] = m ? m[1] : null;
    }
    const creditorRows = (await page.getByTestId('monthly-list-creditors').locator('tbody tr').allInnerTexts()).map(r => norm(r).replace(/\s+/g, ' '));
    results.c4[`list ${label}`] = {
      stat,
      creditorRows,
      containsAll: want.every(w => txt.includes(w)),
      broughtForwardRows: await page.getByTestId('monthly-list-brought-forward').count(),
      hasBf: /B\/f at take-on/.test(txt),
    };
    L(`[c4 monthly list ${label}]`, results.c4[`list ${label}`]);
    await shot(`s4-${String(n++).padStart(2, '0')}-monthly-list-${label}`);
  }

  currentLabel = 'dashboard';
  await page.goto(`${ORG}/trust-accounting`, { waitUntil: 'load' });
  const card = page.getByTestId('trust-integrity-card');
  await card.waitFor({ timeout: 60000 });
  await page.getByTestId('integrity-books-balance').waitFor({ timeout: 30000 }).catch(() => {});
  results.c4.dashboard = {
    integrityCard: norm(await card.innerText()).replace(/\n+/g, ' | '),
    booksBalance: await page.getByTestId('integrity-books-balance').innerText().catch(() => null),
    noFlags: await page.getByTestId('integrity-no-flags').isVisible().catch(() => false),
    flags: await page.getByTestId('integrity-flags').count(),
    summary: await page.getByTestId('summary-cards').innerText().then(t => norm(t).replace(/\n+/g, ' | ')).catch(() => null),
  };
  L('[c4 dashboard]', results.c4.dashboard);
  await shot(`s4-${String(n++).padStart(2, '0')}-trust-dashboard`);
}

(async () => {
  const startedAt = new Date().toISOString();
  L('[start UTC]', startedAt);
  const browser = await chromium.launch();
  context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  page = await context.newPage();
  page.on('console', m => { if (m.type() === 'error') { const s = `[console.error @${currentLabel}] ${m.text()}`; consoleErrors.push(s); L(s); } });
  page.on('pageerror', e => { const s = `[pageerror @${currentLabel}] ${e.message}`; consoleErrors.push(s); L(s); });
  try {
    await login('user_e2e_alice');
    const types = await check1Types();
    await check2(types);
    await check4();
    await check3();
  } catch (e) {
    L('[script error]', e.stack);
    await shot('zz-failure').catch(() => {});
    process.exitCode = 1;
  }
  results.consoleErrors = consoleErrors;
  L('[end UTC]', new Date().toISOString());
  L('[summary] console/page errors:', consoleErrors.length);
  fs.writeFileSync(`${D}/results.json`, JSON.stringify(results, null, 2) + '\n');
  fs.writeFileSync(`${D}/obs-log.txt`, log.join('\n') + '\n');
  await browser.close();
})();
