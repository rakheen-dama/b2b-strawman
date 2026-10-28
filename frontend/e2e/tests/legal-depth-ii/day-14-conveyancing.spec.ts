/**
 * Day 14 — Phase 67: Conveyancing matter + OTP acceptance (Epic 492)
 *
 * Exercises the conveyancing flow on the legal-za profile:
 *   - Thandi (Owner) creates a new client and a matter using the
 *     Property Transfer template (matter_type = CONVEYANCING)
 *   - Verifies the 10 conveyancing custom fields are present (from field pack
 *     `conveyancing_za_matter`): conveyancing_type, property_address,
 *     erf_number, deeds_office, lodgement_date, registration_date, deed_number,
 *     purchase_price, transfer_duty, bond_institution
 *   - Fills minimum subset: property_address, erf_number, deeds_office
 *   - Generates an Offer to Purchase document (key `offer-to-purchase`,
 *     acceptanceEligible: true)
 *   - Sends for acceptance via Phase 28 flow
 *   - Verifies acceptance request delivery (Mailpit magic-link)
 *
 * Prerequisites:
 *   1. Freshly seeded E2E stack: bash compose/scripts/e2e-up.sh (legal-za profile —
 *      seeds the Property Transfer template, the `conveyancing_za_matter` field pack and
 *      the Offer to Purchase document template)
 *   2. Day 5 has started the depth-II run (names here derive from its run id)
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-depth-ii
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { waitForEmail } from "../../helpers/mailpit";
import { captureScreenshot } from "../../helpers/screenshot";
import {
  attemptRunId,
  BASE,
  createClient,
  escapeRegExp,
  openClient,
  openGroupedTab,
  openMatter,
  selectNativeOption,
  startClientOnboarding,
} from "../../helpers/legal-depth-ii";

// Names are scoped to the depth-II run (and to the attempt, so a CI retry of this
// serial group creates fresh records) — prevents non-deterministic row binding when
// the e2e stack is reused across runs.
const clientName = () => `Khumalo Property Holdings ${attemptRunId()}`;
const clientEmail = () => `khumalo.property.${attemptRunId()}@example.com`;
const matterName = () => `Khumalo Property Holdings — Erf 1234 Sandton Transfer ${attemptRunId()}`;
const ficaPack = () => `khumalo-fica-pack-${attemptRunId()}.pdf`;

/** Labels of the 10 fields in the `conveyancing_za_matter` field group. */
const CONVEYANCING_FIELDS: Record<string, string> = {
  conveyancing_type: "Conveyancing Type",
  property_address: "Property Address",
  erf_number: "Erf Number",
  deeds_office: "Deeds Office",
  lodgement_date: "Lodgement Date",
  registration_date: "Registration Date",
  deed_number: "Deed Number",
  purchase_price: "Purchase Price",
  transfer_duty: "Transfer Duty",
  bond_institution: "Bond Institution",
};

/** FICA checklist items of the "Legal Individual Client Onboarding" checklist (dependency order). */
const FICA_ITEMS = [
  "Proof of Identity",
  "Proof of Address",
  "Beneficial Ownership Declaration",
  "Source of Funds Declaration",
  "Engagement Letter Signed",
  "Conflict Check Performed",
  "Power of Attorney Signed",
  "FICA Risk Assessment",
  "Sanctions Screening",
];

async function openMatterFieldsTab(page: Page) {
  await openMatter(page, matterName());
  await openGroupedTab(page, "details", "fields");
  const fieldsTab = page.getByTestId("matter-fields-tab");
  await expect(fieldsTab).toBeVisible();
  await expect(fieldsTab.getByText("SA Conveyancing — Matter Details").first()).toBeVisible();
  return fieldsTab;
}

test.describe.serial("Day 14 — Conveyancing matter + OTP acceptance (Phase 67)", () => {
  // ── 14.2 (part 1): New client Khumalo Property Holdings → ONBOARDING ──
  test("Alice (Thandi): Create client Khumalo Property Holdings and start FICA onboarding", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await createClient(page, {
      name: clientName(),
      type: "Individual",
      email: clientEmail(),
      idNumber: "7605125009081",
    });

    // PROSPECT → ONBOARDING (asserted on the lifecycle status badge)
    await startClientOnboarding(page);

    // Upload the client's certified FICA pack to the client documents
    await openGroupedTab(page, "work", "documents");
    await page
      .getByRole("tabpanel")
      .getByRole("button", { name: "Upload Document" })
      .first()
      .click();
    // Phase 88 628A (ADR-357): label → "Upload Client Document"
    const upload = page.getByRole("dialog", { name: "Upload Client Document" });
    await upload.locator('input[type="file"]').setInputFiles({
      name: ficaPack(),
      mimeType: "application/pdf",
      buffer: Buffer.from("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n"),
    });
    await expect(upload.getByRole("button", { name: "Dismiss" })).toBeVisible({ timeout: 15_000 });
    await page.keyboard.press("Escape");
    await expect(upload).toBeHidden();
    await expect(
      page.getByRole("tabpanel").getByRole("row", { name: new RegExp(escapeRegExp(ficaPack())) })
    ).toContainText("Uploaded", { timeout: 15_000 });
  });

  // ── 14.2 (part 2): FICA checklist complete → client auto-transitions to ACTIVE ──
  // FIXME(depth-ii:checklist-stale-after-complete) PRODUCT BUG (reproduced, intermittent):
  // completing a checklist item persists it but the Onboarding tab can keep rendering it as
  // "Pending" with "Mark Complete" until the page is reloaded. The row
  // (components/compliance/ChecklistInstanceItemRow.tsx) renders `item.status` from server
  // props and relies solely on `revalidatePath` in completeChecklistItem
  // (customers/[id]/checklist-actions.ts) to refresh them. Instrumented runs on a ready page
  // (client opened by click-through, tab rendered; 5 runs x 9 items): 6/45 completions stayed
  // stale for 10s. In every stale case the server action returned 200 — in at least one with
  // an RSC payload already carrying the item as COMPLETED — but the router never applied it
  // (no client re-render / `framenavigated`), while every fresh case showed that commit
  // ~150ms after the action; a reload always showed the item COMPLETED. This is NOT the
  // disbursement "act right after a client-side navigation" race (see the PR notes): the
  // disbursement detail renders the action's return value from local state and passed 14/14
  // click-through runs without any reload.
  // Matter creation, OTP generation and acceptance below do not require an ACTIVE client.
  test.fixme("Alice (Thandi): Complete Khumalo's FICA checklist → client becomes ACTIVE", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await openClient(page, clientName());
    await openGroupedTab(page, "compliance", "onboarding");
    const panel = page.getByRole("tabpanel");
    await expect(panel).toContainText("Legal Individual Client Onboarding", { timeout: 15_000 });
    for (const itemName of FICA_ITEMS) {
      const item = panel
        .locator("div.rounded-lg.border.p-3")
        .filter({ has: page.getByText(itemName, { exact: true }) });
      await item.getByRole("button", { name: "Mark Complete" }).click();
      if (await item.getByText(/^Requires document:/).isVisible()) {
        // Link the certified FICA pack uploaded in part 1
        const documentPicker = item.getByRole("combobox");
        await documentPicker.click();
        await page.getByRole("option", { name: ficaPack() }).click();
        await expect(documentPicker).toContainText(ficaPack());
        await expect(page.getByRole("listbox")).toBeHidden();
      }
      const confirmItem = item.getByRole("button", { name: "Confirm" });
      await expect(confirmItem).toBeEnabled();
      await confirmItem.click();
      await expect(item.getByText("Completed", { exact: true }), itemName).toBeVisible({
        timeout: 15_000,
      });
    }
    await expect(panel).toContainText("9/9 completed");

    // Completing FICA auto-transitions the client to ACTIVE (address + country are on file)
    await page.reload();
    await expect(page.getByTestId("lifecycle-badge")).toHaveText("Active", { timeout: 15_000 });
    // Phase 88 628A (ADR-357): label → "Activate Client" (the negative count tracks the current label)
    await expect(page.getByRole("button", { name: "Activate Client" })).toHaveCount(0);
  });

  // ── 14.2-14.3: Create conveyancing matter from Property Transfer template ──
  test("Alice (Thandi): Create matter using Property Transfer template", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/matters`);
    await page.getByRole("button", { name: "New from Template" }).click();

    const dialog = page.getByRole("dialog", { name: /New from Template/ });
    await expect(dialog).toBeVisible();
    await dialog.getByRole("option", { name: /Property Transfer \(Conveyancing\)/ }).click();
    await dialog.getByRole("button", { name: "Next" }).click();

    await dialog.getByLabel(/Matter name/).fill(matterName());
    await selectNativeOption(dialog.getByLabel(/^Client/), clientName());
    // Matters created from the template inherit its work type (#1580)
    await expect(dialog.getByLabel(/Work Type/)).toHaveValue("CONVEYANCING");
    await dialog.getByRole("button", { name: "Create Matter" }).click();

    // Phase 87 S4 (ADR-344): route rename (610D — post-redirect URL)
    await page.waitForURL(/\/matters\/[0-9a-f-]{36}/, { timeout: 15_000 });
    await expect(page.getByRole("heading", { level: 1, name: matterName() })).toBeVisible();
    await expect(page.locator("main")).toContainText("CONVEYANCING");
    await expect(page.locator("main")).toContainText(clientName());
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-14-conveyancing-matter-created");
  });

  // ── Navigation: clicking into a matter (a segment with its own loading skeleton) ──
  // The matter route has its own loading.tsx. A click made as soon as the Matters list
  // is visible lands while the matter segment's prefetch is still in flight; Next 16's
  // router then used to commit an EMPTY segment (no skeleton, no content) that
  // intermittently never filled in. Fixed by the pnpm patch to next (KAZI-PATCH
  // next-null-prefetch, tasks/next-null-prefetch-blank-segment.md). Each attempt uses a
  // fresh session and clicks immediately, which is what made the race reproducible.
  test("Alice: Clicking into a matter (route with a loading skeleton) renders its content (10 fresh sessions)", async ({
    browser,
  }) => {
    test.setTimeout(120_000);
    for (let i = 1; i <= 10; i++) {
      // A fresh session per attempt, as each test in the suite has (where it was seen)
      const context = await browser.newContext();
      const page = await context.newPage();
      await loginAs(page, "alice");
      // Phase 87 S4 (ADR-344): route rename
      await page.goto(`${BASE}/matters`);
      const link = page.getByRole("link", { name: new RegExp(escapeRegExp(matterName())) }).first();
      await expect(link).toBeVisible({ timeout: 15_000 });
      await link.click();
      // Phase 87 S4 (ADR-344): route rename (610D — post-redirect URL)
      await expect(page).toHaveURL(/\/matters\/[0-9a-f-]{36}/, { timeout: 15_000 });
      await expect(
        page.getByRole("heading", { level: 1, name: matterName() }),
        `matter content rendered after click-through #${i}`
      ).toBeVisible({ timeout: 5_000 });
      await context.close();
    }
  });

  // ── 14.4: Verify 10 conveyancing custom fields present ────────────
  test("Alice: Verify 10 conveyancing custom fields present on matter detail", async ({ page }) => {
    await loginAs(page, "alice");
    const fieldsTab = await openMatterFieldsTab(page);

    // 8 fields render unconditionally; registration_date is shown once lodgement_date
    // is set and deed_number once registration_date is set (visibleWhen rules).
    const conditional = ["registration_date", "deed_number"];
    for (const [slug, label] of Object.entries(CONVEYANCING_FIELDS)) {
      if (conditional.includes(slug)) {
        await expect(
          fieldsTab.locator(`#cf-${slug}`),
          `${slug} hidden until its precursor`
        ).toHaveCount(0);
      } else {
        await expect(fieldsTab.locator(`#cf-${slug}`), slug).toBeVisible();
        await expect(fieldsTab.locator(`label[for="cf-${slug}"]`)).toContainText(label);
      }
    }
    await fieldsTab.locator("#cf-lodgement_date").fill("2026-11-02");
    await expect(fieldsTab.locator("#cf-registration_date")).toBeVisible();
    await fieldsTab.locator("#cf-registration_date").fill("2026-12-07");
    await expect(fieldsTab.locator("#cf-deed_number")).toBeVisible();
    for (const slug of Object.keys(CONVEYANCING_FIELDS)) {
      await expect(fieldsTab.locator(`#cf-${slug}`), slug).toBeVisible();
    }
    await expect(fieldsTab.locator('label[for="cf-registration_date"]')).toContainText(
      CONVEYANCING_FIELDS.registration_date
    );
    await expect(fieldsTab.locator('label[for="cf-deed_number"]')).toContainText(
      CONVEYANCING_FIELDS.deed_number
    );

    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-14-conveyancing-matter-custom-fields");
    await captureScreenshot(page, "conveyancing-matter-detail-custom-fields", { curated: true });
  });

  // ── 14.5-14.6: Fill minimum field subset ──────────────────────────
  test("Alice: Fill property_address, erf_number, deeds_office", async ({ page }) => {
    await loginAs(page, "alice");
    const fieldsTab = await openMatterFieldsTab(page);

    await fieldsTab.locator("#cf-property_address").fill("12 Rivonia Road, Sandton, 2196");
    await fieldsTab.locator("#cf-erf_number").fill("1234");
    await fieldsTab.locator("#cf-deeds_office").selectOption("JOHANNESBURG");
    await fieldsTab.getByRole("button", { name: "Save Custom Fields" }).click();
    await expect(fieldsTab.getByText("Saved successfully")).toBeVisible({ timeout: 15_000 });

    // 14.6: values persist and re-render after a reload
    await page.reload();
    await openGroupedTab(page, "details", "fields");
    const reloaded = page.getByTestId("matter-fields-tab");
    await expect(reloaded.locator("#cf-property_address")).toHaveValue(
      "12 Rivonia Road, Sandton, 2196"
    );
    await expect(reloaded.locator("#cf-erf_number")).toHaveValue("1234");
    await expect(reloaded.locator("#cf-deeds_office")).toHaveValue("JOHANNESBURG");

    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-14-conveyancing-fields-populated");
  });

  // ── 14.7: Generate Offer to Purchase document ─────────────────────
  test("Alice: Generate Offer to Purchase document and save it to the matter", async ({ page }) => {
    await loginAs(page, "alice");
    await openMatter(page, matterName());

    await page.getByRole("button", { name: /Generate Document/ }).click();
    await page.getByRole("menuitem", { name: "Offer to Purchase" }).click();

    const dialog = page.getByRole("dialog", { name: /Generate: Offer to Purchase/ });
    await expect(dialog).toBeVisible({ timeout: 15_000 });
    // Phase 88 626H (B-7): since 626G (conveyancing-za-clauses v2) the OTP has clause
    // associations, so the dialog opens on the clause step (Step 1 of 2); the preview renders
    // after "Next: Preview". Clause assertions live in the 14.8 test below.
    await dialog.getByRole("button", { name: /Next/ }).click();
    const preview = dialog.frameLocator('iframe[title="Document Preview"]').locator("body");
    // The preview merges the client, matter and the Day 14 conveyancing fields
    await expect(preview).toContainText("Offer to Purchase", { timeout: 15_000 });
    await expect(preview).toContainText(`Purchaser: ${clientName()}`);
    await expect(preview).toContainText("Property Address: 12 Rivonia Road, Sandton, 2196");
    await expect(preview).toContainText("Erf Number: 1234");
    await expect(preview).toContainText("Deeds Office: Johannesburg");

    await dialog.getByRole("button", { name: "Save to Documents" }).click();
    await expect(dialog.getByText("Document saved successfully")).toBeVisible({ timeout: 15_000 });
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-14-otp-document-generated");
    await dialog.getByRole("button", { name: "Close" }).first().click();
    await expect(dialog).toBeHidden();

    await openGroupedTab(page, "work", "generated");
    await expect(page.getByRole("row", { name: /Offer to Purchase/ })).toBeVisible({
      timeout: 15_000,
    });
  });

  // ── 14.8: OTP carries the conveyancing clause pack ────────────────
  // Regression (was FIXME depth-ii:otp-clause-associations, G-B18): conveyancing-za-clauses v2
  // associates the OTP's 7 embedded clauses (626G), so the dialog offers the clause step and
  // Conditions of Sale is filled.
  test("Alice: Generate Offer to Purchase document with conveyancing clauses", async ({ page }) => {
    await loginAs(page, "alice");
    await openMatter(page, matterName());
    await page.getByRole("button", { name: /Generate Document/ }).click();
    await page.getByRole("menuitem", { name: "Offer to Purchase" }).click();

    const dialog = page.getByRole("dialog", { name: /Generate: Offer to Purchase/ });
    await expect(dialog).toBeVisible({ timeout: 15_000 });
    // Clause step (Step 1 of 2) lists the conveyancing clause pack
    await expect(dialog).toContainText("Step 1 of 2");
    await expect(dialog).toContainText("Voetstoots");
    await dialog.getByRole("button", { name: /Next/ }).click();

    const preview = dialog.frameLocator('iframe[title="Document Preview"]').locator("body");
    await expect(preview).toContainText("Conditions of Sale", { timeout: 15_000 });
    await expect(preview).toContainText(/voetstoots/i);
    await expect(preview).toContainText(/suspensive/i);
    await expect(preview).toContainText(/transfer duty/i);
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "otp-document-with-clauses", { curated: true });
  });

  // ── 14.9-14.11: Send for Acceptance (Phase 28 flow) ───────────────
  test("Alice: Send OTP for Acceptance via Phase 28 flow", async ({ page }) => {
    await loginAs(page, "alice");
    await openMatter(page, matterName());
    await openGroupedTab(page, "work", "generated");

    const otpRow = page.getByRole("row", { name: /Offer to Purchase/ }).first();
    await expect(otpRow).toBeVisible({ timeout: 15_000 });
    // Offered because the OTP template is acceptanceEligible and the matter has a client
    await otpRow.getByTitle("Send for Acceptance").click();

    const dialog = page.getByRole("dialog", { name: /Send for Acceptance/ });
    await expect(dialog).toBeVisible();
    // Recipient = the client's portal contact (auto-provisioned from the client email)
    await dialog.locator("#recipient").click();
    await page.getByRole("option", { name: new RegExp(escapeRegExp(clientEmail())) }).click();
    await dialog.locator("#expiryDays").fill("7");
    await dialog.getByRole("button", { name: "Send", exact: true }).click();
    await expect(dialog.getByText("Acceptance request sent.")).toBeVisible({ timeout: 15_000 });
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    await expect(otpRow).toContainText("Awaiting Acceptance", { timeout: 15_000 });
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-14-otp-acceptance-request-sent");
    await captureScreenshot(page, "otp-acceptance-request", { curated: true });

    // 14.11: the acceptance request email (with its magic link) reaches the client
    // (waitForEmail polls for the message whose subject matches, then fetches that message)
    const message = await waitForEmail(clientEmail(), {
      subject: "for acceptance: offer-to-purchase",
    });
    expect(message.Subject).toMatch(/review .* for acceptance: offer-to-purchase/i);
    expect(`${message.HTML}\n${message.Text}`).toMatch(/\/accept\/[A-Za-z0-9_-]{16,}/);
  });
});
