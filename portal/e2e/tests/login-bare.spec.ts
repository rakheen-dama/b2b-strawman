import { test, expect } from "@playwright/test";
import {
  extractExchangePath,
  listMessages,
  waitForNewEmail,
} from "../helpers/mailpit";

/**
 * Phase 88 624D (U11, G-D1, ADR-351): a client signs in from a bare /login —
 * no ?orgId=, no localStorage hint — and the firm is resolved by email alone.
 *
 * The link is read from the real magic-link email in Mailpit, never from the
 * dev-profile `magicLink` echo. Each run makes exactly one request-link call:
 * the backend caps requests per email at 3 / 15 min (in-memory, cleared only
 * by a backend restart). Run this project WITHOUT PORTAL_CONTACT_EMAIL so
 * globalSetup does not spend a second request on the same address.
 *
 * Strict by design: a missing precondition (stack down, contact not seeded,
 * cap hit) fails the test; nothing is soft-asserted or skipped.
 *
 * Defaults target the e2e seed (compose/seed/seed.sh): portal contact
 * contact@acme.example.com, client Acme Corp, matter "Website Redesign".
 */
const EMAIL = process.env.LOGIN_BARE_EMAIL || "contact@acme.example.com";
const ORG_SLUG = process.env.PORTAL_ORG_SLUG || "e2e-test-org";
const ORG_NAME = process.env.PORTAL_ORG_NAME || "E2E Test Organization";
const MATTER = process.env.LOGIN_BARE_MATTER || "Website Redesign";

const LINK_SUBJECT_PREFIX = "Your portal access link from";
const REQUEST_LINK_PATH = "/portal/auth/request-link";

const escapeRegExp = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

test.describe("Bare /login — email-only portal sign-in", () => {
  test("fresh browser: bare /login → one branded link in Mailpit → /matters", async ({
    page,
  }) => {
    const before = (await listMessages(EMAIL)).map((m) => m.ID);

    // 1. Bare /login in a fresh context: no query string, no stored hint.
    await page.goto("/login");
    expect(new URL(page.url()).pathname).toBe("/login");
    expect(new URL(page.url()).search).toBe("");
    expect(
      await page.evaluate(() => localStorage.getItem("portal_last_org_id")),
    ).toBeNull();
    await expect(page.getByText("Sign in to your client portal")).toBeVisible();

    // 2. Submit the email alone.
    await page.getByLabel("Email address").fill(EMAIL);
    const requestP = page.waitForRequest(
      (r) => r.method() === "POST" && r.url().endsWith(REQUEST_LINK_PATH),
    );
    const responseP = page.waitForResponse(
      (r) =>
        r.request().method() === "POST" && r.url().endsWith(REQUEST_LINK_PATH),
    );
    await page.getByRole("button", { name: "Send Magic Link" }).click();

    expect((await requestP).postDataJSON()).toEqual({ email: EMAIL });
    const response = await responseP;
    expect(
      response.status(),
      "429 = per-email cap (3 / 15 min) hit — restart the backend " +
        "(e2e: docker restart e2e-backend) and re-run",
    ).toBe(200);
    expect(await response.json()).toMatchObject({
      message: "If an account exists, a link has been sent.",
    });

    await expect(page.getByText("Check your email.")).toBeVisible();
    await expect(page.getByText(/sent a sign-in link/i)).toBeVisible();
    await expect(
      page.getByText(/couldn't determine which organization/i),
    ).toHaveCount(0);

    // 3. Exactly one branded link arrives in Mailpit (one firm knows this
    //    email). Wait a little after the first one so a late second mail
    //    would still be counted.
    const mail = await waitForNewEmail(EMAIL, {
      exclude: before,
      subject: `${LINK_SUBJECT_PREFIX} ${ORG_NAME}`,
    });
    await page.waitForTimeout(3_000);
    const freshLinks = (await listMessages(EMAIL)).filter(
      (m) =>
        !before.includes(m.ID) && m.Subject.startsWith(LINK_SUBJECT_PREFIX),
    );
    expect(
      freshLinks.map((m) => m.Subject),
      "one firm knows this email → exactly one magic-link email",
    ).toEqual([`${LINK_SUBJECT_PREFIX} ${ORG_NAME}`]);

    const path = extractExchangePath(mail);
    expect(path).toMatch(
      new RegExp(
        `^/auth/exchange\\?token=[^&]+&orgId=${escapeRegExp(ORG_SLUG)}$`,
      ),
    );

    // 4. Follow the link (replayed against baseURL: e2e mail links point at
    //    :3002) and land on the client's matters.
    await page.goto(path);
    await page.waitForURL("**/matters");
    await expect(
      page.getByRole("heading", { name: "Your matters" }),
    ).toBeVisible();
    await expect(page.getByText(MATTER).first()).toBeVisible();
    expect(
      await page.evaluate(() => localStorage.getItem("portal_last_org_id")),
    ).toBe(ORG_SLUG);
    expect(
      await page.evaluate(() => localStorage.getItem("portal_jwt")),
    ).toBeTruthy();
  });
});
