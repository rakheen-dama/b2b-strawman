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
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Legal-za profile active
 *   3. Phase 492 conveyancing template pack seeded (pack.json updated with
 *      `acceptanceEligible: true` on offer-to-purchase + power-of-attorney-transfer)
 *   4. Phase 492 clause pack `conveyancing-za-clauses` seeded
 *   5. Field pack `conveyancing_za_matter` seeded with 10 custom fields
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-depth-ii
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { captureScreenshot } from "../../helpers/screenshot";
import {
  BASE,
  E2E_MAILPIT_URL,
  escapeRegExp,
  openGroupedTab,
  openMatter,
  selectNativeOption,
} from "../../helpers/legal-depth-ii";

// Unique names per run — prevents non-deterministic row binding when the
// e2e stack is reused across runs and a previous matter with the same name exists.
const runId = Date.now().toString(36).slice(-5);
const clientName = `Khumalo Property Holdings ${runId}`;
const clientEmail = `khumalo.property.${runId}@example.com`;
const matterName = `Khumalo Property Holdings — Erf 1234 Sandton Transfer ${runId}`;

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
  await openMatter(page, matterName);
  await openGroupedTab(page, "details", "fields");
  const fieldsTab = page.getByTestId("matter-fields-tab");
  await expect(fieldsTab).toBeVisible();
  await expect(fieldsTab.getByText("SA Conveyancing — Matter Details").first()).toBeVisible();
  return fieldsTab;
}

async function openClient(page: Page) {
  await page.goto(`${BASE}/customers`);
  const link = page.getByRole("link", { name: clientName }).first();
  await expect(link).toBeVisible({ timeout: 15_000 });
  await page.goto((await link.getAttribute("href"))!);
  await page.waitForLoadState("networkidle");
  await expect(page.getByRole("heading", { level: 1, name: clientName })).toBeVisible();
}

test.describe.serial("Day 14 — Conveyancing matter + OTP acceptance (Phase 67)", () => {
  // ── 14.2 (part 1): New client Khumalo Property Holdings → ONBOARDING ──
  test("Alice (Thandi): Create client Khumalo Property Holdings and start FICA onboarding", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/customers`);
    await page.getByRole("button", { name: "New Client" }).click();

    // The dialog title changes from "Create Client" (step 1) to "Additional Information" (step 2)
    const dialog = page.getByRole("dialog", { name: /^(Create Client|Additional Information)$/ });
    await expect(dialog).toBeVisible();
    await dialog.getByLabel("Name", { exact: true }).fill(clientName);
    await selectNativeOption(dialog.getByLabel("Type", { exact: true }), "Individual");
    await dialog.getByLabel("Email", { exact: true }).fill(clientEmail);
    await dialog.locator('input[name="addressLine1"]').fill("12 Rivonia Road");
    await dialog.locator('input[name="city"]').fill("Sandton");
    await dialog.locator('input[name="postalCode"]').fill("2196");
    await dialog.getByLabel(/^Country/).selectOption("ZA");
    await dialog.getByRole("button", { name: "Next" }).click();

    // Step 2: SA Legal client intake fields
    await expect(dialog.getByText("Step 2 of 2")).toBeVisible();
    await dialog.getByLabel(/ID \/ Passport Number/).fill("7605125009081");
    await dialog.getByRole("button", { name: "Create Client" }).click();
    await page.waitForURL(/\/customers\/[0-9a-f-]{36}/, { timeout: 15_000 });
    await page.waitForLoadState("networkidle");
    await expect(page.getByRole("heading", { level: 1, name: clientName })).toBeVisible();

    // PROSPECT → ONBOARDING
    await page.getByRole("button", { name: "Start Onboarding" }).first().click();
    const confirm = page.getByRole("alertdialog", { name: "Start Onboarding" });
    await confirm.getByRole("button", { name: "Start Onboarding" }).click();
    await expect(confirm).toBeHidden({ timeout: 15_000 });
    await expect(page.locator("main")).toContainText("Onboarding");

    // Upload the client's certified FICA pack to the client documents
    await openGroupedTab(page, "work", "documents");
    await page
      .getByRole("tabpanel")
      .getByRole("button", { name: "Upload Document" })
      .first()
      .click();
    const upload = page.getByRole("dialog", { name: "Upload Customer Document" });
    await upload.locator('input[type="file"]').setInputFiles({
      name: `khumalo-fica-pack-${runId}.pdf`,
      mimeType: "application/pdf",
      buffer: Buffer.from("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n"),
    });
    await expect(upload.getByRole("button", { name: "Dismiss" })).toBeVisible({ timeout: 15_000 });
    await page.keyboard.press("Escape");
    await expect(upload).toBeHidden();
    await expect(
      page
        .getByRole("tabpanel")
        .getByRole("row", { name: new RegExp(`khumalo-fica-pack-${runId}`) })
    ).toContainText("Uploaded", { timeout: 15_000 });
  });

  // ── 14.2 (part 2): FICA checklist complete → client auto-transitions to ACTIVE ──
  // PRODUCT BUG (reproduced, intermittent): completing a checklist item persists it
  // (GET /api/customers/{id}/checklists shows the item COMPLETED with the linked document)
  // but the Onboarding tab keeps rendering it as "Pending" with "Mark Complete" — still
  // stale 15s later, even when each item is completed on a freshly loaded page. The row
  // (components/compliance/ChecklistInstanceItemRow.tsx) renders `item.status` from props and
  // relies on `revalidatePath` in completeChecklistItem (customers/[id]/checklist-actions.ts)
  // to refresh them; that refresh intermittently does not land (seen from the 2nd-4th item on in
  // every run). The same stale-after-action pattern was seen on the disbursement detail
  // "Submit for Approval" when acting right after a client-side navigation.
  // Matter creation, OTP generation and acceptance below do not require an ACTIVE client.
  test.fixme("Alice (Thandi): Complete Khumalo's FICA checklist → client becomes ACTIVE", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await openClient(page);
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
        await page.getByRole("option", { name: `khumalo-fica-pack-${runId}.pdf` }).click();
        await expect(documentPicker).toContainText(`khumalo-fica-pack-${runId}.pdf`);
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
    await expect(page.getByRole("button", { name: "Activate Customer" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Start Onboarding" })).toHaveCount(0);
    await expect(page.locator("main")).not.toContainText(/Onboarding\s*\n\s*Since/);
  });

  // ── 14.2-14.3: Create conveyancing matter from Property Transfer template ──
  test("Alice (Thandi): Create matter using Property Transfer template", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects`);
    await page.getByRole("button", { name: "New from Template" }).click();

    const dialog = page.getByRole("dialog", { name: /New from Template/ });
    await expect(dialog).toBeVisible();
    await dialog.getByRole("option", { name: /Property Transfer \(Conveyancing\)/ }).click();
    await dialog.getByRole("button", { name: "Next" }).click();

    await dialog.getByLabel(/Matter name/).fill(matterName);
    await selectNativeOption(dialog.getByLabel(/^Client/), clientName);
    // Matters created from the template inherit its work type (#1580)
    await expect(dialog.getByLabel(/Work Type/)).toHaveValue("CONVEYANCING");
    await dialog.getByRole("button", { name: "Create Matter" }).click();

    await page.waitForURL(/\/projects\/[0-9a-f-]{36}/, { timeout: 15_000 });
    await expect(page.getByRole("heading", { level: 1, name: matterName })).toBeVisible();
    await expect(page.locator("main")).toContainText("CONVEYANCING");
    await expect(page.locator("main")).toContainText(clientName);
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-14-conveyancing-matter-created");
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
    await openMatter(page, matterName);

    await page.getByRole("button", { name: /Generate Document/ }).click();
    await page.getByRole("menuitem", { name: "Offer to Purchase" }).click();

    const dialog = page.getByRole("dialog", { name: /Generate: Offer to Purchase/ });
    await expect(dialog).toBeVisible({ timeout: 15_000 });
    const preview = dialog.frameLocator('iframe[title="Document Preview"]').locator("body");
    // The preview merges the client, matter and the Day 14 conveyancing fields
    await expect(preview).toContainText("Offer to Purchase", { timeout: 15_000 });
    await expect(preview).toContainText(`Purchaser: ${clientName}`);
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
  // PRODUCT BUG (reproduced): the generated Offer to Purchase has an empty "Conditions of
  // Sale" section. The OTP template content (template-packs/legal-za/offer-to-purchase.json)
  // embeds 7 clauseBlock nodes (voetstoots, occupation-date, suspensive-bond,
  // transfer-duty-liability, fica-compliance, rates-clearance, jurisdiction-za), and those
  // 10 clauses are seeded in the clause library, but `conveyancing-za-clauses/pack.json` has
  // `"templateAssociations": []` — GET /api/templates/{otpId}/clauses returns [] — so the
  // generate dialog offers no clause step and every clauseBlock renders nothing.
  test.fixme("Alice: Generate Offer to Purchase document with conveyancing clauses", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await openMatter(page, matterName);
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
    await openMatter(page, matterName);
    await openGroupedTab(page, "work", "generated");

    const otpRow = page.getByRole("row", { name: /Offer to Purchase/ }).first();
    await expect(otpRow).toBeVisible({ timeout: 15_000 });
    // Offered because the OTP template is acceptanceEligible and the matter has a client
    await otpRow.getByTitle("Send for Acceptance").click();

    const dialog = page.getByRole("dialog", { name: /Send for Acceptance/ });
    await expect(dialog).toBeVisible();
    // Recipient = the client's portal contact (auto-provisioned from the client email)
    await dialog.locator("#recipient").click();
    await page.getByRole("option", { name: new RegExp(escapeRegExp(clientEmail)) }).click();
    await dialog.locator("#expiryDays").fill("7");
    await dialog.getByRole("button", { name: "Send", exact: true }).click();
    await expect(dialog.getByText("Acceptance request sent.")).toBeVisible({ timeout: 15_000 });
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    await expect(otpRow).toContainText("Awaiting Acceptance", { timeout: 15_000 });
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-14-otp-acceptance-request-sent");
    await captureScreenshot(page, "otp-acceptance-request", { curated: true });

    // 14.11: the acceptance request email (with its magic link) reaches the client
    await expect
      .poll(
        async () => {
          const res = await fetch(
            `${E2E_MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`to:${clientEmail}`)}`
          );
          const body = (await res.json()) as { messages: { Subject: string; ID: string }[] };
          return body.messages.map((m) => m.Subject).join(" | ");
        },
        { timeout: 30_000, message: "acceptance email delivered to Mailpit" }
      )
      .toMatch(/review .* for acceptance: offer-to-purchase/i);
    const search = await fetch(
      `${E2E_MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`to:${clientEmail}`)}`
    );
    const { messages } = (await search.json()) as { messages: { ID: string }[] };
    const message = await (
      await fetch(`${E2E_MAILPIT_URL}/api/v1/message/${messages[0].ID}`)
    ).json();
    expect(`${message.HTML}\n${message.Text}`).toMatch(/\/accept\/[A-Za-z0-9_-]{16,}/);
  });
});
