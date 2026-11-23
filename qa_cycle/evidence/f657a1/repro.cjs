// F-657A-1 browser reproduction: log time from the Tasks tab, open Fees → Time as soon as the
// dialog closes (what day-75-complex.spec.ts:224 does), and record whether the Time panel shows
// the new entry, then whether it ever catches up without a reload.
// Usage: node repro.cjs <matterId> <iterations> <cpuThrottleRate> <tag>
const { chromium } = require(process.env.PW_TEST || '../../../frontend/node_modules/@playwright/test');
const fs = require('fs');

const BASE = 'http://localhost:3001';
const SLUG = 'e2e-test-org';
const [matterId, iterArg = '5', rateArg = '1', tag = 'run'] = process.argv.slice(2);
const ITER = Number(iterArg);
const RATE = Number(rateArg);
const TASK = 'Obtain Letters of Executorship';
const out = [];
const L = (...a) => { const s = a.map(x => (typeof x === 'string' ? x : JSON.stringify(x))).join(' '); console.log(s); out.push(s); };

async function openGroupedTab(page, groupId, tabId) {
  const group = page.getByTestId(`tab-group-${groupId}`).filter({ visible: true });
  const item = page.getByTestId(`tab-item-${tabId}`).filter({ visible: true });
  await group.waitFor({ state: 'visible', timeout: 15000 });
  for (let attempt = 0; attempt < 3; attempt++) {
    await group.click();
    const open = await item.waitFor({ state: 'visible', timeout: 2000 }).then(() => true, () => false);
    if (open) { await item.click(); return; }
  }
  throw new Error(`menu for ${groupId} never opened`);
}

const entries = async page => {
  const t = await page.getByRole('tabpanel').innerText();
  const m = t.replace(/\s+/g, ' ').match(/Entries (\d+)/i);
  return m ? Number(m[1]) : NaN;
};

// The Time panel has rendered its summary cards (polls the accessible tabpanel, like entries()).
async function waitEntries(page) {
  for (let k = 0; k < 60; k++) {
    const n = await entries(page).catch(() => NaN);
    if (!Number.isNaN(n)) return n;
    await page.waitForTimeout(500);
  }
  throw new Error('Time panel summary never rendered');
}

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: RATE });
  await page.goto(`${BASE}/mock-login`, { waitUntil: 'load' });
  await page.locator('#user-select').selectOption('user_e2e_alice');
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 60000 });
  L(`[${tag}] cpu throttle x${RATE}, ${ITER} iterations, matter ${matterId}`);

  let stale = 0, staleForever = 0;
  for (let i = 1; i <= ITER; i++) {
    await page.goto(`${BASE}/org/${SLUG}/matters/${matterId}?tab=time`, { waitUntil: 'load' });
    await page.getByRole('tabpanel').waitFor({ timeout: 30000 });
    await waitEntries(page);
    const before = await entries(page);

    await openGroupedTab(page, 'work', 'tasks');
    const row = page.getByRole('tabpanel').getByRole('row')
      .filter({ has: page.getByRole('button', { name: `Open task detail for ${TASK}` }) });
    await row.getByRole('button', { name: 'Log Time' }).click();
    const dialog = page.getByRole('dialog', { name: 'Log Time' });
    await dialog.waitFor();
    await dialog.locator('#time-hours').fill('0');
    await dialog.locator('#time-minutes').fill('5');
    await dialog.getByRole('textbox', { name: /^Description/ }).fill(`F-657A-1 repro ${tag} #${i}`);
    await dialog.getByRole('button', { name: 'Log Time' }).click();
    await dialog.waitFor({ state: 'hidden', timeout: 15000 });
    const tClosed = Date.now();
    await openGroupedTab(page, 'finance', 'time');           // immediately, as the spec does
    await waitEntries(page);
    const atOpen = await entries(page);

    // Does the mounted panel ever catch up without a reload? (spec waits 15 s; bell poll is 30 s)
    let caughtUpAfter = null;
    for (let s = 0; s < 35; s++) {
      if ((await entries(page)) === before + 1) { caughtUpAfter = (Date.now() - tClosed) / 1000; break; }
      await page.waitForTimeout(1000);
    }
    if (atOpen !== before + 1 && caughtUpAfter === null) await page.screenshot({ path: `${__dirname}/${tag}-iter${i}-stale-before-reload.png`, fullPage: true }).catch(() => {});
    await page.reload({ waitUntil: 'load' });
    await waitEntries(page);
    const afterReload = await entries(page);

    const isStale = atOpen !== before + 1;
    if (isStale) stale++;
    if (isStale && caughtUpAfter === null) staleForever++;
    L(`[${tag}] #${i} before=${before} atTimeTabOpen=${atOpen} expected=${before + 1} ` +
      `caughtUpWithoutReload=${caughtUpAfter === null ? 'NO (35 s)' : caughtUpAfter.toFixed(1) + ' s'} afterReload=${afterReload}` +
      (isStale ? '  <-- STALE' : ''));
  }
  L(`[${tag}] SUMMARY stale-at-open ${stale}/${ITER}; never caught up without reload ${staleForever}/${ITER}`);
  fs.writeFileSync(`${__dirname}/${tag}.log`, out.join('\n') + '\n');
  await browser.close();
})().catch(e => { console.error(e); fs.writeFileSync(`${__dirname}/${tag}.log`, out.join('\n') + '\nERROR ' + e.stack + '\n'); process.exit(1); });
