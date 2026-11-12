// 637B run 2: fetch raw server HTML (logged in) and locate the theme init script relative to <head>/<body>.
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-637B/frontend/node_modules/@playwright/test');
const fs = require('fs');
const OUT = '/Users/rakheendama/Projects/2026/worktree-epic-637B/qa_cycle/evidence/phase90-ui/637B';
const BASE = 'http://localhost:3001';
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await page.goto(BASE + '/mock-login', { waitUntil: 'load' });
  await page.getByRole('button', { name: 'Sign In' }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 30000 });
  const report = {};
  for (const path of ['/org/e2e-test-org/dashboard', '/']) {
    const res = await ctx.request.get(BASE + path, { maxRedirects: 0 });
    const html = await res.text();
    const marker = 'localStorage.getItem("kazi-theme")';
    const iHeadOpen = html.indexOf('<head');
    const iHeadClose = html.indexOf('</head>');
    const iBody = html.indexOf('<body');
    const iMarker = html.indexOf(marker);
    const iScriptOpen = html.lastIndexOf('<script', iMarker);
    const iScriptClose = html.indexOf('</script>', iMarker);
    // what precedes the init script inside <head>
    const headBefore = html.slice(iHeadOpen, iScriptOpen);
    const tagsBefore = (headBefore.match(/<(meta|link|script|style|title)\b[^>]*>/g) || []).map((t) => t.slice(0, 120));
    report[path] = {
      status: res.status(), length: html.length, iHeadOpen, iScriptOpen, iMarker, iHeadClose, iBody,
      insideHead: iHeadOpen >= 0 && iScriptOpen > iHeadOpen && iScriptClose < iHeadClose && iHeadClose < iBody,
      occurrences: html.split(marker).length - 1,
      tagsBeforeInHead: tagsBefore,
      excerpt: html.slice(iScriptOpen, iScriptClose + 9),
      contextAroundBody: html.slice(iHeadClose, iBody + 200),
    };
    fs.writeFileSync(`${OUT}/head-check-${path === '/' ? 'root' : 'dashboard'}.html`, html);
  }
  fs.writeFileSync(`${OUT}/head-check-report.json`, JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
  await browser.close();
})().catch((e) => { console.error('HEAD CHECK FAILED', e); process.exit(1); });
