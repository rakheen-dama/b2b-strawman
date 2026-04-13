import { test, expect } from "@playwright/test";
import { clearMailbox, waitForEmail, extractInviteLink } from "../helpers/mailpit";

/**
 * Debug test: isolate the KC invite → registration flow.
 * Requires: demo-cleanup.sh already run, stack up, org + Thandi already registered.
 */

test.use({ viewport: { width: 1440, height: 900 } });

test("debug: extract and inspect invite link", async ({ page }) => {
  test.setTimeout(120_000);

  // Check what emails exist in Mailpit right now
  const res = await fetch("http://localhost:8025/api/v1/messages?limit=20");
  const data = await res.json();
  console.log(`Mailpit has ${data.total} messages:`);
  for (const msg of data.messages || []) {
    console.log(`  TO: ${msg.To?.[0]?.Address} | SUBJ: ${msg.Subject}`);
  }

  // Find Carol's invite email specifically
  try {
    const carolEmail = await waitForEmail("carol@mathebula-test.local", {
      subject: "invitation",
      timeout: 5000,
    });
    console.log(`Carol invite email found: ${carolEmail.Subject}`);
    console.log(`Carol email TO: ${carolEmail.To?.[0]?.Address}`);

    const inviteLink = extractInviteLink(carolEmail);
    console.log(`Extracted invite link: ${inviteLink}`);

    // Navigate to it and see what KC shows
    await page.goto(inviteLink);
    await page.waitForTimeout(3000);

    // Snapshot the page
    const snapshot = await page.accessibility.snapshot();
    console.log("Page title:", await page.title());
    console.log("Page URL:", page.url());

    // Check what's in the form
    const emailField = page.locator("#username");
    if (await emailField.isVisible().catch(() => false)) {
      const emailValue = await emailField.inputValue();
      console.log(`#username value: "${emailValue}"`);
    }

    const firstNameField = page.locator("#firstName");
    if (await firstNameField.isVisible().catch(() => false)) {
      console.log("#firstName is visible — registration form!");
    } else {
      console.log("#firstName NOT visible — login form, not registration");
    }

    await page.screenshot({ path: "qa_cycle/screenshots/debug-kc-invite.png" });
  } catch (e) {
    console.log(`No Carol invite email found: ${e}`);

    // Try Bob's instead
    try {
      const bobEmail = await waitForEmail("bob@mathebula-test.local", {
        subject: "invitation",
        timeout: 5000,
      });
      console.log(`Bob invite: ${bobEmail.Subject}`);
      const bobLink = extractInviteLink(bobEmail);
      console.log(`Bob link: ${bobLink}`);
    } catch {
      console.log("No Bob invite email either");
    }
  }
});
