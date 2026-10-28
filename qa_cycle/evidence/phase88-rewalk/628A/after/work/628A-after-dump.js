async (page) => {
  const B = 'http://localhost:3001/org/e2e-test-org';
  const C = '/clients/b8fa0d8d-e5eb-4591-a751-fde566340d65?tab=';
  const M = '/matters/efa16531-5837-4265-9ed9-e6cbfbf1b139?tab=';
  const paths = ['/my-work','/compliance','/reports/profitability',C+'overview',C+'details',C+'documents',C+'rates',C+'financials','/invoices/09486b5c-5684-4f36-947f-adffec674dbd','/invoices/collections',M+'tasks',M+'time',M+'rates',M+'financials',M+'adverse-parties','/settings/firm?tab=data-protection','/settings/firm?tab=compliance','/settings/matters?tab=naming','/settings/matters?tab=tags','/settings/matters?tab=checklists','/settings/documents?tab=templates','/settings/fees?tab=tariffs','/settings/fees?tab=tax','/settings/fees?tab=fee-runs','/settings/fees?tab=collections','/settings/connections?tab=xero','/settings/connections?tab=notifications','/settings/connections?tab=automations','/settings/matters/templates/52fe60b4-fedb-47cb-aed3-3059fc7636fe','/settings/matters/checklists/new','/settings/connections/xero/sync-log','/reports'];
  const out = {};
  for (const p of paths) {
    await page.goto(B + p, {waitUntil:'load'}); await page.waitForTimeout(2500);
    out[p] = await page.evaluate(() => {
      const m = document.querySelector('main') || document.body;
      const attrs = [...m.querySelectorAll('[placeholder],[aria-label],[title]')].map(e => ['placeholder','aria-label','title'].map(a => e.getAttribute(a) ? a + '=' + e.getAttribute(a) : null).filter(Boolean).join(' ')).filter(Boolean);
      return {text: m.innerText, attrs};
    });
  }
  await page.evaluate((o) => localStorage.setItem('__628A_dump', JSON.stringify(o)), out);
  return Object.keys(out).length;
}
