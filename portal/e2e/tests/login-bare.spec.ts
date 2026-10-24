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
 * OPT-IN: the `login-bare` project is registered only when LOGIN_BARE=1, so
 * the default all-projects run skips it. Run it with
 * `pnpm test:e2e:login-bare`, which sets the flag and selects the project;
 * the config then self-serves a PRODUCTION portal on :3002
 * (`pnpm run build && pnpm run start`, NEXT_PUBLIC_PORTAL_API_URL passed
 * through). It must not run against `next dev`: there React StrictMode runs
 * the effect in app/auth/exchange/page.tsx twice, so the single-use token is
 * POSTed twice and the second POST's 401 can show "Link expired or invalid"
 * (known pre-existing bug; follow-up: exchange double-POST under next dev).
 * A dev server already on :3002 is reused (`reuseExistingServer`), so the spec
 * counts exchange POSTs and fails with that diagnosis rather than flaking.
 *
 * SKIP_PORTAL_BASELINES=true skips it, like the neighbouring portal specs.
 *
 * Assertions and errors on the magic-link path print `token=<redacted>`, never
 * the token itself.
 *
 * Defaults target the e2e seed (compose/seed/seed.sh): portal contact
 * contact@acme.example.com, client Acme Corp, matter "Website Redesign".
 */
const EMAIL = process.env.LOGIN_BARE_EMAIL || "contact@acme.example.com";
const ORG_SLUG = process.env.PORTAL_ORG_SLUG || "e2e-test-org";
const ORG_NAME = process.env.PORTAL_ORG_NAME || "E2E Test Organization";
const MATTER = process.env.LOGIN_BARE_MATTER || "Website Redesign";
const SKIP = process.env.SKIP_PORTAL_BASELINES === "true";

const LINK_SUBJECT_PREFIX = "Your portal access link from";
const REQUEST_LINK_PATH = "/portal/auth/request-link";
const EXCHANGE_PATH = "/portal/auth/exchange";

const redactToken = (s: string) =>
  s.replace(/token=[^&\s"']+/g, "token=<redacted>");

const escapeRegExp = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

test.describe("Bare /login — email-only portal sign-in", () => {
  test.beforeAll(async () => {
    test.skip(
      SKIP,
      "SKIP_PORTAL_BASELINES=true — login-bare disabled. Unset with the seeded e2e stack to execute.",
    );
  });

  test("fresh browser: bare /login → one branded link in Mailpit → /matters", async ({
    page,
  }) => {
    // Covers the 30 s Mailpit poll, the 3 s negative check, both navigations
    // and the exchange; the config default (30 s) would cut the mail poll off.
    test.setTimeout(90_000);
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
    // Time-bounded negative check, not proof: a second mail arriving after
    // this 3 s window would go unnoticed. The backend N-firm case is pinned by
    // PortalAuthIntegrationTest (624B).
    await page.waitForTimeout(3_000);
    const freshLinks = (await listMessages(EMAIL)).filter(
      (m) =>
        !before.includes(m.ID) && m.Subject.startsWith(LINK_SUBJECT_PREFIX),
    );
    expect(
      freshLinks.map((m) => m.Subject),
      "one firm knows this email → exactly one magic-link email",
    ).toEqual([`${LINK_SUBJECT_PREFIX} ${ORG_NAME}`]);

    // The token is single-use but still a credential: assert on the redacted
    // path so a failure never prints it. An empty token is not redacted (it
    // stays `token=`), so it still fails the match.
    const path = extractExchangePath(mail);
    expect(redactToken(path)).toMatch(
      new RegExp(
        `^/auth/exchange\\?token=<redacted>&orgId=${escapeRegExp(ORG_SLUG)}$`,
      ),
    );

    // 4. Follow the link (replayed against baseURL: e2e mail links point at
    //    :3002). Record every exchange POST, wait for either outcome, and
    //    check the POST count before the landing page so a dev-mode double
    //    exchange fails with its diagnosis instead of a bare timeout.
    let exchangePosts = 0;
    page.on("request", (r) => {
      if (r.method() === "POST" && new URL(r.url()).pathname === EXCHANGE_PATH)
        exchangePosts++;
    });
    try {
      await page.goto(path);
    } catch (e) {
      throw new Error(redactToken(e instanceof Error ? e.message : String(e)));
    }
    const mattersHeading = page.getByRole("heading", { name: "Your matters" });
    const linkExpired = page.getByText(/Link expired or invalid/i);
    await expect(mattersHeading.or(linkExpired).first()).toBeVisible();

    expect(
      exchangePosts,
      `exchange POSTed ${exchangePosts} times — known pre-existing dev-mode ` +
        "StrictMode double effect in app/auth/exchange/page.tsx (624D " +
        "follow-up); run this project against a production build " +
        "(next build && next start)",
    ).toBe(1);

    expect(
      new URL(page.url()).pathname,
      "exchange did not land on /matters (Login Failed page shown?)",
    ).toBe("/matters");
    await expect(mattersHeading).toBeVisible();
    await expect(page.getByText(MATTER).first()).toBeVisible();
    expect(
      await page.evaluate(() => localStorage.getItem("portal_last_org_id")),
    ).toBe(ORG_SLUG);
    expect(
      await page.evaluate(() => localStorage.getItem("portal_jwt")),
    ).toBeTruthy();
  });
});
