const { chromium } = require(process.argv[2]);
(async () => {
  const b = await chromium.launch();
  for (const who of ['user_e2e_bob', 'user_e2e_carol']) {
    const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } });
    const p = await ctx.newPage(); const logs = [];
    p.on('console', m => { if (m.type() === 'error') logs.push(`${p.url()} :: ${m.text().slice(0, 90)}`); });
    p.on('requestfailed', r => { if (/userinfo/.test(r.url())) logs.push(`REQFAILED ${r.url()} ${r.failure()?.errorText}`); });
    await p.goto('http://localhost:3001/mock-login');
    await p.locator('#user-select').selectOption(who);
    await p.getByRole('button', { name: 'Sign In' }).click();
    await p.waitForURL(/\/dashboard/); await p.waitForLoadState('networkidle').catch(() => {}); await p.waitForTimeout(3000);
    const n1 = logs.length;
    await p.reload({ waitUntil: 'load' }); await p.waitForLoadState('networkidle').catch(() => {}); await p.waitForTimeout(3000);
    console.log(who, 'afterSettledLogin=', n1, 'afterReload=', logs.length - n1, JSON.stringify(logs));
    await ctx.close();
  }
  await b.close();
})();
