const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-638A-before/frontend/node_modules/@playwright/test');
(async () => {
  const b = await chromium.launch({ headless: true });
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } });
  const p = await ctx.newPage();
  await p.goto('http://localhost:3001/mock-login', { waitUntil: 'load' });
  await p.getByRole('button', { name: 'Sign In' }).click();
  await p.waitForURL('**/org/e2e-test-org/dashboard');
  await p.locator("h1").first().waitFor();
  await p.locator('h1').first().waitFor();
  await p.waitForTimeout(1000);
  const r = await p.evaluate(() => { const n0=document.querySelector("nav[aria-label=\"Main navigation\"]"); const cs=getComputedStyle(n0); return {scrollH:n0.scrollHeight, clientH:n0.clientHeight, overflowY:cs.overflowY, scrollTop:n0.scrollTop, homeCls:n0.querySelector("a").className}; const _u = (() => {
    const nav = document.querySelector('nav[aria-label="Main navigation"]');
    const links = [...nav.querySelectorAll('a')].slice(0, 6).map((a) => ({ text: a.textContent.trim(), href: a.getAttribute('href'), attrs: [...a.attributes].filter(x => x.name !== 'class' && x.name !== 'href').map(x => `${x.name}=${x.value}`), cls: a.className, bg: getComputedStyle(a).backgroundColor, color: getComputedStyle(a).color }));
    return { links, ls: Object.fromEntries(Object.keys(localStorage).map(k => [k, localStorage.getItem(k)])), headerH: 0 }; })();
  });
  console.log(JSON.stringify(r, null, 1));
  await b.close();
})();
