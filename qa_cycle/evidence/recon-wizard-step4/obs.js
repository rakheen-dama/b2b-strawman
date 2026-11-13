const { chromium } = require('../../../frontend/node_modules/@playwright/test');
const fs = require('fs');
const D = __dirname;
const log = [];
const L = (...a) => { const s = a.join(' '); console.log(s); log.push(s); };
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  page.on('console', m => L('[console.' + m.type() + ']', m.text()));
  page.on('pageerror', e => L('[pageerror]', e.message, '\n', e.stack));
  page.on('response', async r => {
    const req = r.request();
    const h = req.headers();
    if (req.method() === 'POST' && (h['next-action'] || r.url().includes('/api/'))) {
      let body = '';
      try { body = await r.text(); } catch (e) { body = '<unreadable ' + e.message + '>'; }
      L('[response]', req.method(), r.url(), r.status(), 'next-action=' + (h['next-action'] || '-'), '\n  reqBody=', (req.postData() || '').slice(0, 300), '\n  respBody=', body.slice(0, 3000));
    }
  });
  const shot = async n => { await page.screenshot({ path: `${D}/${n}.png`, fullPage: true }); L('[shot]', n); };

  await page.goto('http://localhost:3001/mock-login', { waitUntil: 'load' });
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/org\//, { timeout: 60000 });
  L('[nav] logged in at', page.url());

  await page.goto('http://localhost:3001/org/e2e-test-org/trust-accounting/reconciliation/new', { waitUntil: 'load' });
  await page.getByTestId('account-select').waitFor({ timeout: 60000 });
  await page.getByTestId('period-end-input').fill('2026-11-30');
  await shot('01-step1-select-account');
  await page.getByTestId('step-next-btn').click();

  await page.getByTestId('bank-statement-upload').waitFor();
  await page.getByTestId('file-input').setInputFiles(`${D}/recon-statement.csv`);
  await page.getByTestId('upload-result').waitFor({ timeout: 60000 });
  await shot('02-step2-uploaded');
  await page.getByTestId('step-next-btn').click();

  await page.getByTestId('auto-match-btn').waitFor();
  await page.getByTestId('auto-match-btn').click();
  await page.getByTestId('auto-match-result').waitFor({ timeout: 60000 });
  await shot('03-step3-auto-matched');
  await page.getByTestId('step-next-btn').click();

  await page.getByTestId('reconciliation-split-pane').waitFor({ timeout: 60000 });
  await shot('04-step4-review-1440');
  const lineCount = await page.locator('[data-testid^="bank-line-"]').count();
  const progress = await page.getByTestId('match-progress').innerText();
  L('[step4] bank lines rendered:', lineCount, '| progress:', progress.replace(/\n/g, ' '));
  await page.setViewportSize({ width: 1024, height: 768 });
  await shot('05-step4-review-1024');
  const errs = log.filter(l => l.startsWith('[pageerror]') || l.startsWith('[console.error]'));
  L('[summary] pageerrors/console errors:', errs.length);
  fs.writeFileSync(`${D}/obs-log.txt`, log.join('\n'));
  await browser.close();
})().catch(async e => { L('[script error]', e.stack); fs.writeFileSync(`${D}/obs-log.txt`, log.join('\n')); process.exit(1); });
