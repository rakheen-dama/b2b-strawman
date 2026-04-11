import { test, expect } from "@playwright/test";
import { loginAs, registerFromInvite } from "../../fixtures/keycloak-auth";
import { clearMailbox, waitForEmail, extractInviteLink } from "../../helpers/mailpit";
import { loadState, hasState, type E2eState } from "../../helpers/e2e-state";

let state: E2eState;
let ADMIN_EMAIL: string;
let MEMBER_EMAIL: string;

test.describe.serial("Keycloak Member Invite & RBAC", () => {
  test.skip(!hasState(), "Requires onboarding.spec.ts to run first (state file missing)");

  test.beforeAll(() => {
    state = loadState();
    ADMIN_EMAIL = `bob-${state.runId}@thornton-za.e2e-test.local`;
    MEMBER_EMAIL = `carol-${state.runId}@thornton-za.e2e-test.local`;
  });

  test("1. Owner logs in and upgrades plan", async ({ page }) => {
    await loginAs(page, state.ownerEmail, state.ownerPassword);
    await page.waitForURL(/\/org\//);

    await page.goto(`/org/${state.orgSlug}/settings/billing`);
    await page.waitForLoadState("networkidle");

    // Click "Upgrade to Pro"
    await page.getByRole("button", { name: /upgrade to pro/i }).click();

    // Confirm in AlertDialog — button text is "Upgrade Now"
    await expect(page.getByRole("alertdialog").or(page.getByRole("dialog"))).toBeVisible({
      timeout: 5_000,
    });
    await page.getByRole("button", { name: /upgrade now/i }).click();

    // Wait for upgrade to complete
    await page.waitForTimeout(3000);

    // Verify plan shows Pro
    await expect(page.getByText(/professional|pro/i)).toBeVisible({
      timeout: 5_000,
    });
  });

  test("2. Owner invites admin (Bob)", async ({ page }) => {
    await clearMailbox();
    await loginAs(page, state.ownerEmail, state.ownerPassword);
    await page.waitForURL(/\/org\//);

    await page.goto(`/org/${state.orgSlug}/team`);
    await page.waitForLoadState("networkidle");

    // Click invite button
    await page.getByRole("button", { name: /invite/i }).click();

    // Fill invite form
    await page.getByTestId("invite-email-input").fill(ADMIN_EMAIL);

    // Select Admin role
    await page.getByTestId("role-select").click();
    await page.getByRole("option", { name: /admin/i }).click();

    // Submit
    await page.getByTestId("invite-member-btn").click();

    // Wait for invitation to process
    await page.waitForTimeout(2000);
  });

  test("3. Bob registers from invitation email", async ({ page }) => {
    const email = await waitForEmail(ADMIN_EMAIL, {
      subject: "invitation",
      timeout: 30_000,
    });
    const inviteLink = extractInviteLink(email);
    expect(inviteLink).toBeTruthy();

    await registerFromInvite(page, inviteLink, "Bob", "Ndlovu", "SecureP@ss2");
    await page.waitForLoadState("networkidle");
    await expect(page.locator("body")).not.toContainText("Sign in");
  });

  test("4. Owner invites member (Carol)", async ({ page }) => {
    await clearMailbox();
    await loginAs(page, state.ownerEmail, state.ownerPassword);
    await page.waitForURL(/\/org\//);

    await page.goto(`/org/${state.orgSlug}/team`);
    await page.waitForLoadState("networkidle");

    await page.getByRole("button", { name: /invite/i }).click();
    await page.getByTestId("invite-email-input").fill(MEMBER_EMAIL);
    await page.getByTestId("role-select").click();
    await page.getByRole("option", { name: /member/i }).click();
    await page.getByTestId("invite-member-btn").click();
    await page.waitForTimeout(2000);
  });

  test("5. Carol registers from invitation email", async ({ page }) => {
    const email = await waitForEmail(MEMBER_EMAIL, {
      subject: "invitation",
      timeout: 30_000,
    });
    const inviteLink = extractInviteLink(email);
    await registerFromInvite(page, inviteLink, "Carol", "Mokoena", "SecureP@ss3");
    await page.waitForLoadState("networkidle");
    await expect(page.locator("body")).not.toContainText("Sign in");
  });

  test("6. RBAC: Admin (Bob) can access settings", async ({ page }) => {
    await loginAs(page, ADMIN_EMAIL, "SecureP@ss2");
    await page.waitForURL(/\/org\//);

    // Admin should be able to access settings
    await page.goto(`/org/${state.orgSlug}/settings`);
    await expect(page.locator("body")).not.toContainText("Something went wrong");
    await expect(page.locator("body")).not.toContainText(/don.t have access/i);

    // Admin should be able to access team page
    await page.goto(`/org/${state.orgSlug}/team`);
    await expect(page.locator("body")).not.toContainText("Something went wrong");
  });

  test("7. RBAC: Member (Carol) cannot access financial settings", async ({ page }) => {
    await loginAs(page, MEMBER_EMAIL, "SecureP@ss3");
    await page.waitForURL(/\/org\//);

    // Member should NOT be able to access rates
    await page.goto(`/org/${state.orgSlug}/settings/rates`);
    await expect(page.locator("body")).toContainText(/don.t have access|permission/i);

    // Member should NOT be able to access profitability
    await page.goto(`/org/${state.orgSlug}/profitability`);
    await expect(page.locator("body")).toContainText(/don.t have access|permission/i);
  });
});
