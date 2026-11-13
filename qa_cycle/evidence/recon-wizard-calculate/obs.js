// Real-browser observation for the "calculate on entering review" fix (e2e mock stack :3001).
const { chromium } = require(process.env.PW || '../../../frontend/node_modules/@playwright/test');
const fs = require('fs');
const D = __dirname;
const log = [];
const L = (...a) => { const s = a.join(' '); console.log(s); log.push(s); };
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  page.on('console', m => { if (m.type() === 'error') L('[console.error]', m.text()); });
  page.on('pageerror', e => L('[pageerror]', e.message));
  const shot = async n => { await page.screenshot({ path: `${D}/${n}.png`, fullPage: true }); L('[shot]', n); };

  await page.goto('http://localhost:3001/mock-login', { waitUntil: 'load' });
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/org\//, { timeout: 60000 });

  await page.goto('http://localhost:3001/org/e2e-test-org/trust-accounting/reconciliation/new', { waitUntil: 'load' });
  await page.getByTestId('account-select').waitFor({ timeout: 60000 });
  await page.getByTestId('period-end-input').fill('2026-11-30');
  await page.getByTestId('step-next-btn').click();
  await page.getByTestId('file-input').setInputFiles(`${D}/recon-statement.csv`);
  await page.getByTestId('upload-result').waitFor({ timeout: 60000 });
  await page.getByTestId('step-next-btn').click();
  await page.getByTestId('auto-match-btn').click();
  await page.getByTestId('auto-match-result').waitFor({ timeout: 60000 });
  L('[step3]', (await page.getByTestId('auto-match-result').innerText()).replace(/\n/g, ' | '));
  await page.getByTestId('step-next-btn').click();

  await page.getByTestId('reconciliation-split-pane').waitFor({ timeout: 60000 });
  L('[step4 progress]', (await page.getByTestId('match-progress').innerText()).replace(/\n/g, ' '));
  const pane = await page.getByTestId('reconciliation-split-pane').innerText();
  const i = pane.indexOf('Three-Way Balance Check');
  L('[step4 balances]', pane.slice(i, i + 400).replace(/\n/g, ' | '));
  const completeBtn = page.getByTestId('complete-reconciliation-btn');
  const enabled = await completeBtn.isEnabled();
  L('[step4] Complete enabled on entry (no manual action):', enabled);
  await shot('01-step4-on-entry-1440');
  await page.setViewportSize({ width: 1024, height: 768 });
  await shot('02-step4-on-entry-1024');
  await page.setViewportSize({ width: 1440, height: 1000 });

  if (enabled) {
    await completeBtn.click();
    await page.getByTestId('step-complete').waitFor({ timeout: 60000 });
    L('[step5]', (await page.getByTestId('step-complete').innerText()).replace(/\n/g, ' | ').slice(0, 300));
    await shot('03-step5-complete-1440');
  }
  L('[summary] pageerrors/console errors:', log.filter(l => l.startsWith('[pageerror]') || l.startsWith('[console.error]')).length);
  fs.writeFileSync(`${D}/obs-log.txt`, log.join('\n'));
  await browser.close();
})().catch(e => { L('[script error]', e.stack); fs.writeFileSync(`${D}/obs-log.txt`, log.join('\n')); process.exit(1); });
