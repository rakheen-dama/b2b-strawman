const { chromium } = require('/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp2/frontend/node_modules/@playwright/test');
const URL_ = process.argv[2];
(async () => {
  const b = await chromium.launch(); const ctx = await b.newContext();
  const t0=Date.now(); const log=(...a)=>console.log(((Date.now()-t0)/1000).toFixed(2), ...a);
  let page = await ctx.newPage();
  await page.goto('http://localhost:3001/mock-login'); await page.getByRole('button',{name:'Sign In'}).click(); await page.waitForURL(/dashboard/);
  // step A: like :314 — generate + save
  await page.goto(URL_);
  await page.getByRole('button',{name:/Generate Document/}).click();
  await page.getByRole('menuitem',{name:'Offer to Purchase'}).click();
  let dialog = page.getByRole('dialog',{name:/Generate: Offer to Purchase/});
  await dialog.getByRole('button',{name:/Next/}).click();
  await dialog.frameLocator('iframe[title="Document Preview"]').locator('body').getByText('Offer to Purchase').first().waitFor({timeout:15000});
  await dialog.getByRole('button',{name:'Save to Documents'}).click();
  await dialog.getByText('Document saved successfully').waitFor({timeout:15000});
  log('A saved');
  await page.close();
  // step B: like :354 in a fresh page
  page = await ctx.newPage();
  const inflight = new Map();
  page.on('request', r=>{inflight.set(r, r.method()+' '+r.url().slice(0,120)+' frame='+(r.frame()===page.mainFrame()?'main':'child')); log('REQ', r.method(), r.url().slice(0,110));});
  page.on('requestfinished', r=>inflight.delete(r)); page.on('requestfailed', r=>inflight.delete(r));
  page.on('framenavigated', f=>log('NAV', f===page.mainFrame()?'main':'child', f.url().slice(0,80)));
  page.on('frameattached', f=>log('ATTACH child'));
  page.on('framedetached', f=>log('DETACH child'));
  await page.goto(URL_);
  log('B loaded');
  await page.getByRole('button',{name:/Generate Document/}).click();
  await page.getByRole('menuitem',{name:'Offer to Purchase'}).click();
  dialog = page.getByRole('dialog',{name:/Generate: Offer to Purchase/});
  await dialog.getByRole('button',{name:/Next/}).click();
  await dialog.frameLocator('iframe[title="Document Preview"]').locator('body').getByText('Conditions of Sale').first().waitFor({timeout:15000});
  log('B preview rendered');
  try { await page.waitForLoadState('networkidle',{timeout:12000}); log('NETWORKIDLE reached'); }
  catch(e){ log('NETWORKIDLE TIMEOUT; in-flight:', [...inflight.values()]); }
  await b.close();
})();
