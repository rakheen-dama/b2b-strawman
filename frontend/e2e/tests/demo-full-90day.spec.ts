import { test, expect, type Page } from "@playwright/test";
import { loginAs, loginAsPlatformAdmin, logout } from "../fixtures/keycloak-auth";
import { clearMailbox, waitForEmail, extractOtp, extractInviteLink } from "../helpers/mailpit";

/**
 * Full 90-Day Legal-ZA Demo — Zero to Hero
 *
 * Records the entire lifecycle from clean state:
 *   Access request → Admin approval → KC registration → Firm setup →
 *   3 client lifecycles → Fee notes → Trust accounting → Reports
 *
 * Prerequisites:
 *   1. Run: bash compose/scripts/demo-cleanup.sh
 *   2. Run: cd frontend && E2E_AUTH_MODE=keycloak npx playwright test demo-full-90day \
 *        --config e2e/playwright.config.ts --headed --timeout 600000
 *
 * Video output: frontend/test-results/.../video.webm
 */

// ── Constants ────────────────────────────────────────────────────────
const SLUG = "mathebula-partners";
const BASE = `/org/${SLUG}`;
const BEAT = 2000; // pause at key moments
const QUICK = 1000; // brief pause between actions
const LONG = 3000; // linger on wow moments

const ACTORS = {
  thandi: {
    email: "thandi@mathebula-test.local",
    password: "SecureP@ss1",
    first: "Thandi",
    last: "Mathebula",
  },
  bob: { email: "bob@mathebula-test.local", password: "SecureP@ss2", first: "Bob", last: "Ndlovu" },
  carol: {
    email: "carol@mathebula-test.local",
    password: "SecureP@ss3",
    first: "Carol",
    last: "Mokoena",
  },
};

// ── Config ───────────────────────────────────────────────────────────
test.use({
  video: { mode: "on", size: { width: 1440, height: 900 } },
  viewport: { width: 1440, height: 900 },
  launchOptions: { slowMo: 50 },
});

// ── Helpers ──────────────────────────────────────────────────────────
async function wait(page: Page, ms: number) {
  await page.waitForTimeout(ms);
}

async function nav(page: Page, path: string, pause = BEAT) {
  await page.goto(path.startsWith("/") ? path : `${BASE}/${path}`);
  await page.waitForLoadState("networkidle").catch(() => {});
  await wait(page, pause);
}

async function clickTab(page: Page, name: string, pause = QUICK) {
  const tab = page.getByRole("tab", { name: new RegExp(name, "i") });
  if (await tab.isVisible().catch(() => false)) {
    await tab.click();
    await wait(page, pause);
  }
}

async function fillField(page: Page, label: string, value: string) {
  const field = page.getByLabel(label, { exact: false }).first();
  if (await field.isVisible().catch(() => false)) {
    await field.fill(value);
  }
}

/**
 * KC org invite flow: identity-first login page → enter email → registration form.
 * Handles the intermediate login step that `registerFromInvite` doesn't.
 */
async function registerViaInvite(
  page: Page,
  inviteLink: string,
  email: string,
  firstName: string,
  lastName: string,
  password: string
) {
  // Full session teardown: KC logout → gateway logout → clear all cookies
  await page.context().clearCookies();
  // Hit KC logout directly (no redirect — just invalidate server-side session)
  await page.goto("http://localhost:8180/realms/docteams/protocol/openid-connect/logout");
  await page.waitForTimeout(1000);
  // Also hit gateway logout to clear Spring Security session
  await page.goto("http://localhost:8443/logout");
  await page.waitForTimeout(1000);
  // Final cookie sweep after all logout redirects complete
  await page.context().clearCookies();

  await page.goto(inviteLink);

  // KC may show login form or registration form depending on the invite token type
  await page.waitForLoadState("networkidle").catch(() => {});

  // Check if we got the registration form directly
  const firstNameField = page.locator("#firstName");
  const usernameField = page.locator("#username");

  const isRegistration = await firstNameField.isVisible({ timeout: 3_000 }).catch(() => false);

  if (!isRegistration) {
    // Identity-first login page — clear any pre-filled email, enter the invited email
    await usernameField.waitFor({ timeout: 10_000 });
    await usernameField.clear();
    await usernameField.fill(email);
    await page.locator('input[name="login"], button[type="submit"]').first().click();

    // Wait for either registration form or password field
    await page.waitForSelector("#firstName, #password", { timeout: 15_000 });

    const isNowRegistration = await firstNameField.isVisible().catch(() => false);
    if (!isNowRegistration) {
      // KC shows password field — user might already exist, or this is a combined form
      // Check for a "Register" link
      const registerLink = page.getByText("Register").or(page.locator('a[href*="registration"]'));
      if (await registerLink.isVisible().catch(() => false)) {
        await registerLink.click();
        await firstNameField.waitFor({ timeout: 10_000 });
      }
    }
  }

  // Now fill registration form
  await firstNameField.fill(firstName);
  await page.locator("#lastName").fill(lastName);
  await page.locator("#password").fill(password);
  await page.locator("#password-confirm").fill(password);
  await page.locator('button[type="submit"], input[type="submit"]').first().click();

  // Wait for redirect back to the app
  await page.waitForURL((url) => !url.pathname.includes("/realms/"), { timeout: 20_000 });
}

async function switchUser(page: Page, actor: typeof ACTORS.thandi) {
  await logout(page);
  await loginAs(page, actor.email, actor.password);
  await page.waitForLoadState("networkidle").catch(() => {});
  await wait(page, QUICK);
}

// ── Main Test ────────────────────────────────────────────────────────
test("Legal-ZA 90-Day Demo — Zero to Hero", async ({ page }) => {
  test.setTimeout(600_000); // 10 minutes max

  // ╔══════════════════════════════════════════════════════════════════╗
  // ║  DAY 0 Phase A — ACCESS REQUEST & OTP                         ║
  // ╚══════════════════════════════════════════════════════════════════╝
  await clearMailbox();

  // Landing page
  await page.goto("http://localhost:3000");
  await wait(page, BEAT);

  // Navigate to /request-access
  await page.goto("http://localhost:3000/request-access");
  await page.waitForSelector('[data-testid="request-access-form"]', { timeout: 10_000 });
  await wait(page, BEAT);

  // Fill text fields using data-testid
  await page.locator('[data-testid="email-input"]').fill(ACTORS.thandi.email);
  await page.locator('[data-testid="full-name-input"]').fill("Thandi Mathebula");
  await page.locator('[data-testid="org-name-input"]').fill("Mathebula & Partners");

  // Country — Shadcn Select: click trigger → click option
  await page.locator('[data-testid="country-select"]').click();
  await page.getByRole("option", { name: "South Africa" }).click();
  await wait(page, 300);

  // Industry — Shadcn Select
  await page.locator('[data-testid="industry-select"]').click();
  await page.getByRole("option", { name: "Legal Services" }).click();
  await wait(page, 300);

  await wait(page, QUICK);

  // Submit
  await page.locator('[data-testid="submit-request-btn"]').click();

  // Wait for OTP step
  await page.waitForSelector('[data-testid="otp-input"]', { timeout: 15_000 });
  await wait(page, BEAT);

  // Get OTP from Mailpit
  const otpEmail = await waitForEmail(ACTORS.thandi.email, {
    subject: "verification",
    timeout: 30_000,
  });
  const otp = extractOtp(otpEmail);

  // Fill OTP (single input, maxLength=6)
  await page.locator('[data-testid="otp-input"]').fill(otp);

  // Verify
  await page.locator('[data-testid="verify-otp-btn"]').click();

  // Wait for success
  await page.waitForSelector('[data-testid="success-message"]', { timeout: 15_000 });
  await wait(page, LONG);

  // ╔══════════════════════════════════════════════════════════════════╗
  // ║  DAY 0 Phase B — PLATFORM ADMIN APPROVAL                      ║
  // ╚══════════════════════════════════════════════════════════════════╝
  await logout(page);
  await loginAsPlatformAdmin(page);
  await wait(page, QUICK);

  await nav(page, "/platform-admin/access-requests");

  const mathebula = page.getByText("Mathebula").first();
  if (await mathebula.isVisible().catch(() => false)) {
    await mathebula.click();
    await wait(page, QUICK);
  }

  const approveBtn = page.getByRole("button", { name: /approve/i }).first();
  if (await approveBtn.isVisible().catch(() => false)) {
    await approveBtn.click();
    await wait(page, QUICK);
    const confirmBtn = page.getByRole("button", { name: /confirm|approve|yes/i }).last();
    if (await confirmBtn.isVisible().catch(() => false)) {
      await confirmBtn.click();
    }
    await wait(page, LONG); // provisioning takes a moment
  }

  // ╔══════════════════════════════════════════════════════════════════╗
  // ║  DAY 0 Phase C — OWNER KC REGISTRATION                        ║
  // ╚══════════════════════════════════════════════════════════════════╝
  const inviteEmail = await waitForEmail(ACTORS.thandi.email, {
    subject: "invitation",
    timeout: 45000,
  });
  const inviteLink = extractInviteLink(inviteEmail);

  await logout(page);
  await registerViaInvite(
    page,
    inviteLink,
    ACTORS.thandi.email,
    ACTORS.thandi.first,
    ACTORS.thandi.last,
    ACTORS.thandi.password
  );
  await page.waitForLoadState("networkidle").catch(() => {});
  await wait(page, LONG); // 📸 WOW: Dashboard with legal nav

  // ╔══════════════════════════════════════════════════════════════════╗
  // ║  DAY 0 Phase D — TEAM INVITES                                  ║
  // ╚══════════════════════════════════════════════════════════════════╝
  await nav(page, `${BASE}/team`);

  // Invite form is inline (not a dialog) — fill email, select role, submit
  // Invite Bob as Admin
  await page.locator('[data-testid="invite-email-input"]').fill(ACTORS.bob.email);
  await page.locator('[data-testid="role-select"]').click();
  await page.getByRole("option", { name: "Admin" }).click();
  await wait(page, 300);
  await page.locator('[data-testid="invite-member-btn"]').click();
  await page.waitForSelector("text=Invitation sent", { timeout: 15_000 });
  await wait(page, BEAT);

  // Invite Carol as Member (form resets after success)
  await page.locator('[data-testid="invite-email-input"]').fill(ACTORS.carol.email);
  // Role defaults to Member — no need to change
  await page.locator('[data-testid="invite-member-btn"]').click();
  await page.waitForSelector("text=Invitation sent", { timeout: 15_000 });
  await wait(page, BEAT);

  // Bob registers
  const bobInvite = await waitForEmail(ACTORS.bob.email, { subject: "invitation", timeout: 30000 });
  await logout(page);
  await registerViaInvite(
    page,
    extractInviteLink(bobInvite),
    ACTORS.bob.email,
    ACTORS.bob.first,
    ACTORS.bob.last,
    ACTORS.bob.password
  );
  await wait(page, QUICK);

  // Carol registers
  const carolInvite = await waitForEmail(ACTORS.carol.email, {
    subject: "invitation",
    timeout: 30000,
  });
  await logout(page);
  await registerViaInvite(
    page,
    extractInviteLink(carolInvite),
    ACTORS.carol.email,
    ACTORS.carol.first,
    ACTORS.carol.last,
    ACTORS.carol.password
  );
  await wait(page, QUICK);

  // ╔══════════════════════════════════════════════════════════════════╗
  // ║  DAY 0 Phase E-K — FIRM SETTINGS                               ║
  // ╚══════════════════════════════════════════════════════════════════╝
  await switchUser(page, ACTORS.thandi);

  await nav(page, `${BASE}/settings`);
  await nav(page, `${BASE}/settings/rates`);
  await nav(page, `${BASE}/settings/custom-fields`);
  await nav(page, `${BASE}/settings/templates`);
  await nav(page, `${BASE}/settings/modules`);
  await nav(page, `${BASE}/trust-accounting`);
  await nav(page, `${BASE}/settings/billing`);

  // ╔══════════════════════════════════════════════════════════════════╗
  // ║  DAY 1 — CONFLICT CHECK & CLIENT CREATION                     ║
  // ╚══════════════════════════════════════════════════════════════════╝
  await switchUser(page, ACTORS.bob);

  // Conflict check
  await nav(page, `${BASE}/conflict-check`);
  const searchInput = page.getByPlaceholder(/search|name/i).first();
  if (await searchInput.isVisible().catch(() => false)) {
    await searchInput.fill("Sipho Dlamini");
    await page
      .getByRole("button", { name: /search|check/i })
      .first()
      .click();
    await wait(page, LONG); // 📸 WOW: Conflict check CLEAR
  }

  // Create Sipho Dlamini
  await nav(page, `${BASE}/customers`);
  const newClientBtn = page.getByRole("button", { name: /new client|add client/i }).first();
  if (await newClientBtn.isVisible().catch(() => false)) {
    await newClientBtn.click();
    await wait(page, QUICK);
    await fillField(page, "Name", "Sipho Dlamini");
    await fillField(page, "Email", "sipho.dlamini@email.co.za");
    await fillField(page, "Tax Number", "4840210567");
    await page
      .getByRole("button", { name: /create|save|submit/i })
      .last()
      .click();
    await wait(page, BEAT);
  }

  // Client detail
  await page
    .getByText("Sipho Dlamini")
    .first()
    .click()
    .catch(() => {});
  await wait(page, BEAT);

  // ╔══════════════════════════════════════════════════════════════════╗
  // ║  DAY 3 — MATTER FROM TEMPLATE                                  ║
  // ╚══════════════════════════════════════════════════════════════════╝
  await nav(page, `${BASE}/projects`);
  const newMatter = page.getByRole("button", { name: /new|create/i }).first();
  if (await newMatter.isVisible().catch(() => false)) {
    await newMatter.click();
    await wait(page, QUICK);
    const litigationTpl = page.getByText(/litigation/i).first();
    if (await litigationTpl.isVisible().catch(() => false)) {
      await litigationTpl.click();
      await wait(page, QUICK);
    }
    await fillField(page, "name", "Sipho Dlamini v. Standard Bank (civil)");
    await page
      .getByRole("button", { name: /create|save|next/i })
      .last()
      .click();
    await wait(page, BEAT);
  }

  // Matter detail with all tabs
  const siphoMatter = page.getByText("Sipho Dlamini v.").first();
  if (await siphoMatter.isVisible().catch(() => false)) {
    await siphoMatter.click();
    await wait(page, BEAT);
    await clickTab(page, "action items", QUICK);
    await clickTab(page, "time", QUICK);
    await clickTab(page, "documents", QUICK);
    await clickTab(page, "activity", BEAT);
    // 📸 WOW: Matter detail
  }

  // ╔══════════════════════════════════════════════════════════════════╗
  // ║  DAYS 8-21 — MOROKA FAMILY TRUST                               ║
  // ╚══════════════════════════════════════════════════════════════════╝
  await switchUser(page, ACTORS.thandi);

  await nav(page, `${BASE}/customers`);
  const newClient2 = page.getByRole("button", { name: /new client|add client/i }).first();
  if (await newClient2.isVisible().catch(() => false)) {
    await newClient2.click();
    await wait(page, QUICK);
    await fillField(page, "Name", "Moroka Family Trust");
    await fillField(page, "Email", "trust@moroka.co.za");
    await fillField(page, "Tax Number", "9876543210");
    await page
      .getByRole("button", { name: /create|save/i })
      .last()
      .click();
    await wait(page, BEAT);
  }

  await nav(page, `${BASE}/projects`);
  const newMatter2 = page.getByRole("button", { name: /new|create/i }).first();
  if (await newMatter2.isVisible().catch(() => false)) {
    await newMatter2.click();
    await wait(page, QUICK);
    const estateTpl = page.getByText(/deceased estate|estate/i).first();
    if (await estateTpl.isVisible().catch(() => false)) {
      await estateTpl.click();
      await wait(page, QUICK);
    }
    await fillField(page, "name", "Estate Late Peter Moroka");
    await page
      .getByRole("button", { name: /create|save|next/i })
      .last()
      .click();
    await wait(page, BEAT);
  }

  // Trust Accounting
  await nav(page, `${BASE}/trust-accounting`);
  await wait(page, LONG);

  // ╔══════════════════════════════════════════════════════════════════╗
  // ║  DAYS 22-35 — LERATO MTHEMBU (RAF CLAIM)                      ║
  // ╚══════════════════════════════════════════════════════════════════╝
  await switchUser(page, ACTORS.bob);

  await nav(page, `${BASE}/customers`);
  const newClient3 = page.getByRole("button", { name: /new client|add client/i }).first();
  if (await newClient3.isVisible().catch(() => false)) {
    await newClient3.click();
    await wait(page, QUICK);
    await fillField(page, "Name", "Lerato Mthembu");
    await fillField(page, "Email", "lerato@mthembu.co.za");
    await fillField(page, "Tax Number", "9001015800084");
    await page
      .getByRole("button", { name: /create|save/i })
      .last()
      .click();
    await wait(page, BEAT);
  }

  await nav(page, `${BASE}/projects`);
  const newMatter3 = page.getByRole("button", { name: /new|create/i }).first();
  if (await newMatter3.isVisible().catch(() => false)) {
    await newMatter3.click();
    await wait(page, QUICK);
    const litigationTpl2 = page.getByText(/litigation/i).first();
    if (await litigationTpl2.isVisible().catch(() => false)) {
      await litigationTpl2.click();
      await wait(page, QUICK);
    }
    await fillField(page, "name", "Lerato Mthembu — RAF Claim");
    await page
      .getByRole("button", { name: /create|save|next/i })
      .last()
      .click();
    await wait(page, BEAT);
  }

  // Court Calendar
  await nav(page, `${BASE}/court-calendar`);
  await wait(page, LONG); // 📸 WOW: Court calendar

  // Conflict Check
  await nav(page, `${BASE}/conflict-check`);
  await wait(page, BEAT);

  // Adverse Parties
  await nav(page, `${BASE}/adverse-parties`);
  await wait(page, BEAT);

  // ╔══════════════════════════════════════════════════════════════════╗
  // ║  DAYS 36-60 — FEE NOTES & INVOICING                           ║
  // ╚══════════════════════════════════════════════════════════════════╝
  await switchUser(page, ACTORS.thandi);

  // Browse all 3 clients
  await nav(page, `${BASE}/customers`);
  await wait(page, BEAT);

  // Browse all matters
  await nav(page, `${BASE}/projects`);
  await wait(page, BEAT);

  // Fee Notes
  await nav(page, `${BASE}/invoices`);
  await wait(page, BEAT);

  // ╔══════════════════════════════════════════════════════════════════╗
  // ║  DAYS 61-90 — REPORTS & FINAL SWEEP                           ║
  // ╚══════════════════════════════════════════════════════════════════╝

  // Profitability
  await nav(page, `${BASE}/profitability`);
  await wait(page, LONG); // 📸 WOW: Profitability

  // Reports
  await nav(page, `${BASE}/reports`);
  await wait(page, BEAT);

  // My Work
  await nav(page, `${BASE}/my-work`);
  await wait(page, BEAT);

  // ── Carol's perspective ───────────────────────────────────────────
  await switchUser(page, ACTORS.carol);
  await nav(page, `${BASE}/dashboard`);
  await wait(page, BEAT);
  await nav(page, `${BASE}/my-work`);
  await wait(page, BEAT);

  // ── Final sweep as Thandi ─────────────────────────────────────────
  await switchUser(page, ACTORS.thandi);

  // All settings
  await nav(page, `${BASE}/settings`);
  await nav(page, `${BASE}/settings/rates`);
  await nav(page, `${BASE}/settings/custom-fields`);
  await nav(page, `${BASE}/settings/templates`);

  // Billing (no tier gates)
  await nav(page, `${BASE}/settings/billing`);
  await wait(page, BEAT);

  // Team
  await nav(page, `${BASE}/team`);
  await wait(page, BEAT);

  // Dashboard — final frame
  await nav(page, `${BASE}/dashboard`);
  await wait(page, LONG);
});
