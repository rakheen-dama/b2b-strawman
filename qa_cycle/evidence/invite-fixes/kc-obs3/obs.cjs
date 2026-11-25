// OBS script for PR #1859 (invitation fixes) against the Keycloak dev stack.
// Usage: node obs.cjs <step> [args...]
// Drives a clean Chromium via the Playwright library (not MCP, not claude-in-chrome).
// Every step writes screenshots + a JSON/text log into this directory.
const { chromium } = require('/Users/rakheendama/Projects/2026/wt-invite-fixes/frontend/node_modules/@playwright/test');
const fs = require('fs');
const path = require('path');

const OUT = __dirname;
const APP = 'http://localhost:3000';
const MAILPIT = 'http://localhost:8025';
const SLUG = 'mathebula-partners';
const OWNER = { email: 'thandi@mathebula-test.local', password: 'SecureP@ss1' };
const INVITEE_PW = 'ObsP@ssw0rd!1';

const step = process.argv[2];
const args = process.argv.slice(3);
const logLines = [];
function log(...a) {
  const line = `[${new Date().toISOString()}] ${a.map((x) => (typeof x === 'string' ? x : JSON.stringify(x))).join(' ')}`;
  console.log(line);
  logLines.push(line);
}
function flushLog(name) {
  fs.appendFileSync(path.join(OUT, `${name}.log.txt`), logLines.join('\n') + '\n');
}
async function shot(page, name) {
  const p = path.join(OUT, `${name}.png`);
  await page.screenshot({ path: p, fullPage: true });
  log('screenshot', p);
}
async function text(page, sel = 'body') {
  return (await page.locator(sel).first().innerText()).replace(/\n{2,}/g, '\n');
}

function attachNetLog(page, tag) {
  page.on('response', async (resp) => {
    const u = resp.url();
    if (/localhost:(8443|8080)/.test(u) || /\/api\//.test(u)) {
      const s = resp.status();
      let body = '';
      if (/capabilities|\/bff\/me|\/api\/me/.test(u)) {
        try { body = (await resp.text()).slice(0, 2000); } catch {}
      }
      log(`[net:${tag}]`, s, resp.request().method(), u, body);
    }
    if (resp.status() >= 500) log(`[net:${tag}] 5xx`, resp.status(), u);
  });
  page.on('console', (m) => { if (m.type() === 'error') log(`[console:${tag}]`, m.text().slice(0, 500)); });
  page.on('pageerror', (e) => log(`[pageerror:${tag}]`, String(e).slice(0, 500)));
}

async function loginOwner(browser) {
  const ctx = await browser.newContext({ viewport: { width: 1400, height: 1000 } });
  const page = await ctx.newPage();
  attachNetLog(page, 'owner');
  await page.goto(`${APP}/dashboard`);
  await page.waitForURL(/\/realms\//, { timeout: 30000 });
  await page.locator('#username').fill(OWNER.email);
  if (await page.locator('#password').isVisible()) {
    await page.locator('#password').fill(OWNER.password);
    await page.locator('input[name="login"], button[type="submit"]').first().click();
  } else {
    await page.locator('input[name="login"], button[type="submit"]').first().click();
    await page.waitForSelector('#password', { timeout: 15000 });
    await page.locator('#password').fill(OWNER.password);
    await page.locator('input[name="login"], button[type="submit"]').first().click();
  }
  await page.waitForURL((u) => !u.pathname.includes('/realms/') && u.port === '3000', { timeout: 30000 });
  await page.waitForLoadState('networkidle').catch(() => {});
  log('owner logged in, url', page.url());
  return { ctx, page };
}

async function mailpitLatest(to, sinceIso) {
  const deadline = Date.now() + 60000;
  while (Date.now() < deadline) {
    const r = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent('to:' + to)}`);
    const d = await r.json();
    const msgs = (d.messages || []).filter((m) => !sinceIso || m.Created >= sinceIso);
    if (msgs.length) {
      msgs.sort((a, b) => (a.Created < b.Created ? 1 : -1));
      const full = await (await fetch(`${MAILPIT}/api/v1/message/${msgs[0].ID}`)).json();
      return full;
    }
    await new Promise((r) => setTimeout(r, 2000));
  }
  throw new Error('no email for ' + to);
}
function inviteLinkFrom(msg) {
  const html = msg.HTML || '';
  const hrefs = [...html.matchAll(/href="([^"]+)"/g)].map((m) => m[1].replace(/&amp;/g, '&'));
  const cand = hrefs.find((h) => /accept-invite|realms\//.test(h)) || (msg.Text || '').match(/https?:\/\/\S+/)?.[0];
  return cand;
}

async function gotoTeam(page) {
  await page.goto(`${APP}/org/${SLUG}/team`);
  await page.waitForLoadState('networkidle').catch(() => {});
}

async function invite(page, email, roleName, shotPrefix) {
  await gotoTeam(page);
  await page.getByTestId('invite-email-input').fill(email);
  await page.getByTestId('role-select').click();
  await page.getByRole('option', { name: roleName, exact: true }).click();
  await shot(page, `${shotPrefix}-invite-filled`);
  log('customise control present?', await page.getByText(/Customi[sz]e for this user/i).count());
  await page.getByTestId('invite-member-btn').click();
  await page.waitForTimeout(4000);
  await shot(page, `${shotPrefix}-invite-sent`);
  const ok = page.getByTestId('invite-success');
  log('invite-success count', await ok.count());
  if (await ok.count()) log('invite-success text:', await ok.innerText());
  log('after invite body text:', (await text(page, 'main')).slice(0, 3000));
}

async function openPending(page) {
  await page.getByRole('tab', { name: /Pending Invitations/ }).click();
  await page.waitForTimeout(1500);
}

async function registerFromLink(browser, link, first, last, { blockComplete = false } = {}, shotPrefix) {
  const ctx = await browser.newContext({ viewport: { width: 1400, height: 1000 } });
  const page = await ctx.newPage();
  attachNetLog(page, 'invitee');
  if (blockComplete) {
    // Stop at the end of Keycloak: never let the browser reach Kazi's post-registration
    // bounce (/accept-invite/complete -> gateway OAuth2 kickoff), so Kazi never sees the user.
    await ctx.route(/localhost:3000\/accept-invite\/complete|localhost:8443\//, (route) => {
      log('BLOCKED (do not open Kazi):', route.request().url());
      return route.abort();
    });
  }
  page.on('framenavigated', (f) => { if (f === page.mainFrame()) log('nav ->', f.url()); });
  await page.goto(link);
  await page.waitForTimeout(5000);
  await shot(page, `${shotPrefix}-link-landing`);
  log('landing url', page.url());
  log('landing text', (await text(page)).slice(0, 1500));
  return { ctx, page };
}

async function fillRegistration(page, first, last, shotPrefix) {
  await page.waitForSelector('#firstName', { timeout: 20000 });
  await page.locator('#firstName').fill(first);
  await page.locator('#lastName').fill(last);
  await page.locator('#password').fill(INVITEE_PW);
  await page.locator('#password-confirm').fill(INVITEE_PW);
  await shot(page, `${shotPrefix}-kc-register-filled`);
  await page.locator('button[type="submit"], input[type="submit"]').first().click();
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  try {
    if (step === 'probe') {
      const { page } = await loginOwner(browser);
      await shot(page, 'p0-owner-dashboard');
      await gotoTeam(page);
      await shot(page, 'p0-team');
      log((await text(page, 'main')).slice(0, 3000));
      await page.goto(`${APP}/org/${SLUG}/settings/firm?tab=roles`);
      await page.waitForLoadState('networkidle').catch(() => {});
      await shot(page, 'p0-roles');
      log((await text(page, 'main')).slice(0, 3000));
    } else if (step === 'step1') {
      // Owner creates custom role "Partner OBS": View Trust + Matter Management + Sign off trust take-on
      const { page } = await loginOwner(browser);
      await page.goto(`${APP}/org/${SLUG}/settings/firm?tab=roles`);
      await page.waitForLoadState('networkidle').catch(() => {});
      await page.getByRole('button', { name: 'New Role' }).click();
      await page.locator('#role-name').fill('Partner OBS');
      await page.locator('#role-description').fill('OBS PR #1859 custom role');
      for (const cap of ['VIEW_TRUST', 'PROJECT_MANAGEMENT', 'SIGN_OFF_TRUST_TAKE_ON']) {
        await page.getByTestId(`role-capability-${cap}`).click();
      }
      await shot(page, 's1-role-dialog-filled');
      await page.getByRole('dialog').getByRole('button', { name: /^Create/ }).click();
      await page.waitForTimeout(3000);
      await shot(page, 's1-role-created');
      log((await text(page, 'main')).slice(0, 3000));
    } else if (step === 'step2') {
      const [email] = args;
      const { page } = await loginOwner(browser);
      await gotoTeam(page);
      // FR-7: select the custom role and look for any customisation control
      await page.getByTestId('role-select').click();
      await shot(page, 's2-role-options');
      log('role options:', await page.getByRole('option').allInnerTexts());
      await page.getByRole('option', { name: 'Partner OBS', exact: true }).click();
      await page.waitForTimeout(800);
      await shot(page, 's2-partner-obs-selected');
      log('FR-7 "Customize for this user" count:', await page.getByText(/Customi[sz]e for this user/i).count());
      log('FR-7 checkbox count in invite section:', await page.locator('main [role="checkbox"]').count());
      log('invite section text:', (await text(page, 'main')).slice(0, 2500));
      await invite(page, email, 'Partner OBS', 's2');
      await openPending(page);
      await shot(page, 's2-pending-tab');
      log('pending tab text:', (await text(page, 'main')).slice(0, 3000));
    } else if (step === 'step3') {
      const [email, since] = args;
      const msg = await mailpitLatest(email, since);
      log('mailpit id', msg.ID, 'subject', msg.Subject, 'from', msg.From?.Address, 'to', msg.To?.map((t) => t.Address));
      const link = inviteLinkFrom(msg);
      log('invite link', link);
      const { page } = await registerFromLink(browser, link, 'Pat', 'Partner', {}, 's3');
      await fillRegistration(page, 'Pat', 'Partner', 's3');
      await page.waitForURL((u) => u.port === '3000' && !/accept-invite/.test(u.pathname), { timeout: 60000 }).catch((e) => log('waitForURL err', String(e)));
      await page.waitForLoadState('networkidle').catch(() => {});
      await page.waitForTimeout(4000);
      await shot(page, 's3-invitee-landing');
      log('invitee landing url', page.url());
      log('invitee landing text', (await text(page)).slice(0, 3000));
      // The app's own BFF calls, as the invitee (GET only)
      for (const u of ['http://localhost:8443/bff/me', 'http://localhost:8443/api/me/capabilities']) {
        const r = await page.request.get(u);
        log('GET', u, r.status(), (await r.text()).slice(0, 2000));
      }
      await gotoTeam(page);
      await page.waitForTimeout(2000);
      await shot(page, 's3-invitee-team');
      log('invitee team text', (await text(page, 'main')).slice(0, 3000));
      const o = await loginOwner(browser);
      await gotoTeam(o.page);
      await o.page.waitForTimeout(2000);
      await shot(o.page, 's3-owner-team');
      log('owner team text', (await text(o.page, 'main')).slice(0, 3000));
    } else if (step === 'step4-invite-revoke') {
      const [email] = args;
      const { page } = await loginOwner(browser);
      await invite(page, email, 'Member', 's4');
      await openPending(page);
      await shot(page, 's4-pending-before-revoke');
      log('pending before', (await text(page, 'main')).slice(0, 3000));
      const row = page.getByTestId('pending-invite-row').filter({ hasText: email });
      log('row count before', await row.count());
      await row.getByRole('button', { name: 'Revoke' }).click();
      await page.waitForTimeout(4000);
      await shot(page, 's4-pending-after-revoke');
      log('alert count', await page.getByTestId('revoke-invitation-error').count());
      log('row count after', await page.getByTestId('pending-invite-row').filter({ hasText: email }).count());
      log('pending after', (await text(page, 'main')).slice(0, 3000));
      await page.reload();
      await page.waitForLoadState('networkidle').catch(() => {});
      await openPending(page);
      await shot(page, 's4-pending-after-reload');
      log('row count after reload', await page.getByTestId('pending-invite-row').filter({ hasText: email }).count());
    } else if (step === 'step4-follow') {
      const [email, since] = args;
      const msg = await mailpitLatest(email, since);
      log('mailpit id', msg.ID, 'subject', msg.Subject, 'to', msg.To?.map((t) => t.Address));
      const link = inviteLinkFrom(msg);
      log('invite link', link);
      const { page } = await registerFromLink(browser, link, 'Rev', 'Oked', {}, 's4-follow');
      const hasForm = await page.locator('#firstName').count();
      log('registration form present?', hasForm);
      if (hasForm) {
        // If Keycloak still offers registration, try to complete it: this is the failure case.
        await fillRegistration(page, 'Rev', 'Oked', 's4-follow');
        await page.waitForTimeout(8000);
        await shot(page, 's4-follow-after-register');
        log('after register url', page.url(), (await text(page)).slice(0, 2000));
      }
    } else if (step === 'invite-only') {
      const [email, roleName, prefix] = args;
      const { page } = await loginOwner(browser);
      await invite(page, email, roleName, prefix);
      await openPending(page);
      await shot(page, `${prefix}-pending-tab`);
      log('pending tab text:', (await text(page, 'main')).slice(0, 3000));
    } else if (step === 'step5-accept-kc-only') {
      const [email, since] = args;
      const msg = await mailpitLatest(email, since);
      log('mailpit id', msg.ID, 'subject', msg.Subject, 'to', msg.To?.map((t) => t.Address));
      const link = inviteLinkFrom(msg);
      log('invite link', link);
      const { page } = await registerFromLink(browser, link, 'Late', 'Revoke', { blockComplete: true }, 's5');
      await fillRegistration(page, 'Late', 'Revoke', 's5');
      await page.waitForTimeout(8000);
      await shot(page, 's5-after-kc-register');
      log('after register url', page.url());
      log('after register text', (await text(page)).slice(0, 1500));
    } else if (step === 'step5-revoke') {
      const [email] = args;
      const { page } = await loginOwner(browser);
      await gotoTeam(page);
      await openPending(page);
      await shot(page, 's5-pending-before-revoke');
      const row = page.getByTestId('pending-invite-row').filter({ hasText: email });
      log('row count before', await row.count());
      await row.getByRole('button', { name: 'Revoke' }).click();
      await page.waitForTimeout(4000);
      await shot(page, 's5-revoke-refused');
      const alert = page.getByTestId('revoke-invitation-error');
      log('alert count', await alert.count());
      if (await alert.count()) log('alert role', await alert.getAttribute('role'), 'text:', await alert.innerText());
      log('row count after', await page.getByTestId('pending-invite-row').filter({ hasText: email }).count());
      log('pending after', (await text(page, 'main')).slice(0, 3000));
    } else if (step === 'step5-invitee-login') {
      const [email] = args;
      const ctx = await browser.newContext({ viewport: { width: 1400, height: 1000 } });
      const page = await ctx.newPage();
      attachNetLog(page, 'invitee');
      await page.goto(`${APP}/dashboard`);
      await page.waitForURL(/\/realms\//, { timeout: 30000 });
      await page.locator('#username').fill(email);
      if (!(await page.locator('#password').isVisible())) {
        await page.locator('input[name="login"], button[type="submit"]').first().click();
        await page.waitForSelector('#password', { timeout: 15000 });
      }
      await page.locator('#password').fill(INVITEE_PW);
      await page.locator('input[name="login"], button[type="submit"]').first().click();
      await page.waitForURL((u) => u.port === '3000', { timeout: 60000 });
      await page.waitForLoadState('networkidle').catch(() => {});
      await page.waitForTimeout(4000);
      await shot(page, 's5-invitee-landing');
      log('invitee landing url', page.url(), (await text(page)).slice(0, 2000));
      for (const u of ['http://localhost:8443/bff/me', 'http://localhost:8443/api/me/capabilities']) {
        const r = await page.request.get(u);
        log('GET', u, r.status(), (await r.text()).slice(0, 2000));
      }
    } else if (step === 'step5-revoke-reload') {
      // Re-run (revoke always wins): revoke, then reload and confirm the row is gone.
      const [email] = args;
      const { page } = await loginOwner(browser);
      await gotoTeam(page);
      await openPending(page);
      await shot(page, 's5a-pending-before-revoke');
      const row = page.getByTestId('pending-invite-row').filter({ hasText: email });
      log('row count before', await row.count());
      await row.getByRole('button', { name: 'Revoke' }).click();
      await page.waitForTimeout(4000);
      await shot(page, 's5a-after-revoke');
      const alert = page.getByTestId('revoke-invitation-error');
      log('alert count', await alert.count());
      if (await alert.count()) log('alert text:', await alert.innerText());
      log('row count after', await page.getByTestId('pending-invite-row').filter({ hasText: email }).count());
      log('pending after', (await text(page, 'main')).slice(0, 2000));
      await page.reload();
      await page.waitForLoadState('networkidle').catch(() => {});
      await openPending(page);
      await shot(page, 's5a-after-reload');
      log('row count after reload', await page.getByTestId('pending-invite-row').filter({ hasText: email }).count());
    } else if (step === 'step5-bc') {
      // 5b: the revoked invitee signs in to Kazi -> refused. 5c: owner re-invites, invitee reloads.
      const [email, reinviteRole] = args;
      const ctx = await browser.newContext({ viewport: { width: 1400, height: 1000 } });
      const page = await ctx.newPage();
      attachNetLog(page, 'invitee');
      await page.goto(`${APP}/dashboard`);
      await page.waitForURL(/\/realms\//, { timeout: 30000 });
      await page.locator('#username').fill(email);
      if (!(await page.locator('#password').isVisible())) {
        await page.locator('input[name="login"], button[type="submit"]').first().click();
        await page.waitForSelector('#password', { timeout: 15000 });
      }
      await page.locator('#password').fill(INVITEE_PW);
      await page.locator('input[name="login"], button[type="submit"]').first().click();
      await page.waitForURL((u) => u.port === '3000', { timeout: 60000 });
      await page.waitForLoadState('networkidle').catch(() => {});
      await page.waitForTimeout(4000);
      await shot(page, 's5b-invitee-home');
      log('5b home url', page.url());
      const ar = page.getByTestId('access-revoked');
      log('5b access-revoked count', await ar.count());
      if (await ar.count()) log('5b access-revoked role', await ar.getAttribute('role'), 'text:', await ar.innerText());
      log('5b home text', (await text(page)).slice(0, 2000));
      for (const u of ['http://localhost:8443/bff/me', 'http://localhost:8443/api/me/capabilities']) {
        const r = await page.request.get(u);
        log('5b GET', u, r.status(), (await r.text()).slice(0, 1500));
      }
      await page.goto(`${APP}/org/${SLUG}/matters`);
      await page.waitForLoadState('networkidle').catch(() => {});
      await page.waitForTimeout(3000);
      await shot(page, 's5b-invitee-matters');
      log('5b matters url', page.url());
      log('5b matters text', (await text(page)).slice(0, 2000));
      // 5c: owner re-invites the same email (separate context, same browser)
      const o = await loginOwner(browser);
      await invite(o.page, email, reinviteRole, 's5c');
      await openPending(o.page);
      await shot(o.page, 's5c-pending-after-reinvite');
      log('5c pending text', (await text(o.page, 'main')).slice(0, 2000));
      // the person reloads Kazi in their own (still signed-in) context
      await page.goto(`${APP}/org/${SLUG}/dashboard`);
      await page.reload();
      await page.waitForLoadState('networkidle').catch(() => {});
      await page.waitForTimeout(4000);
      await shot(page, 's5c-invitee-home-after-reinvite');
      log('5c home url', page.url());
      log('5c access-revoked count', await page.getByTestId('access-revoked').count());
      log('5c home text', (await text(page)).slice(0, 1500));
      for (const u of ['http://localhost:8443/bff/me', 'http://localhost:8443/api/me/capabilities']) {
        const r = await page.request.get(u);
        log('5c GET', u, r.status(), (await r.text()).slice(0, 1500));
      }
      await gotoTeam(page);
      await page.waitForTimeout(2000);
      await shot(page, 's5c-invitee-team');
      log('5c invitee team text', (await text(page, 'main')).slice(0, 2500));
    } else if (step === 'invitee-login') {
      // R1: the re-invited person signs in to Kazi (password set at KC registration).
      const [email] = args;
      const ctx = await browser.newContext({ viewport: { width: 1400, height: 1000 } });
      const page = await ctx.newPage();
      attachNetLog(page, 'invitee');
      await page.goto(`${APP}/dashboard`);
      await page.waitForURL(/\/realms\//, { timeout: 30000 });
      await page.locator('#username').fill(email);
      if (!(await page.locator('#password').isVisible())) {
        await page.locator('input[name="login"], button[type="submit"]').first().click();
        await page.waitForSelector('#password', { timeout: 15000 });
      }
      await page.locator('#password').fill(INVITEE_PW);
      await page.locator('input[name="login"], button[type="submit"]').first().click();
      await page.waitForURL((u) => u.port === '3000', { timeout: 60000 });
      await page.waitForLoadState('networkidle').catch(() => {});
      await page.waitForTimeout(4000);
      await shot(page, 'r1-invitee-home');
      log('home url', page.url());
      log('access-revoked count', await page.getByTestId('access-revoked').count());
      log('home text', (await text(page)).slice(0, 1200));
      for (const u of ['http://localhost:8443/bff/me', 'http://localhost:8443/api/me/capabilities']) {
        const r = await page.request.get(u);
        log('GET', u, r.status(), (await r.text()).slice(0, 1500));
      }
    } else if (step === 'step5-remove') {
      const [email] = args;
      const { page } = await loginOwner(browser);
      await gotoTeam(page);
      await page.waitForTimeout(1500);
      await shot(page, 's5-owner-team-before-remove');
      log('team text', (await text(page, 'main')).slice(0, 3000));
      const row = page.getByTestId(`member-row-${email}`);
      log('member row count', await row.count(), 'row text', (await row.innerText()).replace(/\s+/g, ' '));
      log('buttons/menus on row', await row.locator('button, [role="menuitem"], a').allInnerTexts());
      await row.click();
      await page.waitForTimeout(2500);
      await shot(page, 's5-owner-member-panel');
      const panel = page.getByRole('dialog');
      log('panel text', (await panel.innerText()).slice(0, 3000));
      log('panel buttons', await panel.getByRole('button').allInnerTexts());
      log('any "Remove" control on page', await page.getByRole('button', { name: /remove|deactivate|delete/i }).allInnerTexts());
      log('any "Remove" menuitem on page', await page.getByRole('menuitem', { name: /remove|deactivate|delete/i }).count());
    } else {
      throw new Error('unknown step ' + step);
    }
  } catch (e) {
    log('ERROR', String(e && e.stack ? e.stack : e));
    process.exitCode = 1;
  } finally {
    flushLog(step);
    await browser.close();
  }
})();
