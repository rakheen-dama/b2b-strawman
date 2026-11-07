# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-14-conveyancing.spec.ts >> Day 14 — Conveyancing matter + OTP acceptance (Phase 67) >> Alice: Generate Offer to Purchase document with conveyancing clauses
- Location: e2e/tests/legal-depth-ii/day-14-conveyancing.spec.ts:354:7

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: page.waitForLoadState: Test timeout of 30000ms exceeded.
```

# Page snapshot

```yaml
- generic:
  - link:
    - /url: "#main-content"
    - text: Skip to content
  - generic:
    - complementary:
      - generic:
        - generic: Kazi
      - generic:
        - generic: E2E Test Organization
      - button:
        - img
        - text: Search...
        - generic: ⌘K
      - navigation:
        - generic:
          - button [expanded]:
            - generic: Today
            - img
          - generic:
            - link:
              - /url: /org/e2e-test-org/dashboard
              - img
              - text: Home
            - link:
              - /url: /org/e2e-test-org/my-work
              - img
              - text: My work
            - link:
              - /url: /org/e2e-test-org/diary
              - img
              - text: Diary
        - generic:
          - button [expanded]:
            - generic: Matters
            - img
          - generic:
            - link:
              - /url: /org/e2e-test-org/matters
              - img
              - text: Matters
            - link:
              - /url: /org/e2e-test-org/matters/new
              - img
              - text: New matter
            - link:
              - /url: /org/e2e-test-org/conflict-check
              - img
              - text: Conflict check
            - link:
              - /url: /org/e2e-test-org/legal/adverse-parties
              - img
              - text: Adverse parties
            - link:
              - /url: /org/e2e-test-org/ai/reviews
              - img
              - text: AI reviews
        - generic:
          - button [expanded]:
            - generic: Clients
            - img
          - generic:
            - link:
              - /url: /org/e2e-test-org/clients
              - img
              - text: Clients
            - link:
              - /url: /org/e2e-test-org/compliance
              - img
              - text: FICA & compliance
            - link:
              - /url: /org/e2e-test-org/compliance/requests
              - img
              - text: Data requests
        - generic:
          - button:
            - generic: Fees
            - img
        - generic:
          - button:
            - generic: Trust
            - img
        - generic:
          - button:
            - generic: Firm
            - img
      - generic:
        - link:
          - /url: /platform-admin/access-requests
          - img
          - text: Platform Admin
      - generic:
        - link:
          - /url: /org/e2e-test-org/notifications
          - img
          - text: Notifications
      - generic:
        - generic: AO
        - generic:
          - paragraph: Alice Owner
          - paragraph: alice@e2e-test.local
    - generic:
      - banner:
        - navigation:
          - link:
            - /url: /org/e2e-test-org/dashboard
            - text: E2E Test Organization
          - generic:
            - img
            - link:
              - /url: /org/e2e-test-org/matters
              - text: Matters
          - generic:
            - img
            - generic: Khumalo Property Holdings — Erf 1234 Sandton Transfer mw6d4i47a634
        - generic:
          - generic:
            - generic: e2e-test-org
          - generic:
            - button: AO
          - generic:
            - button "Notifications":
              - img
              - generic: 15 unread notifications
              - generic: "15"
      - main:
        - generic:
          - generic:
            - generic:
              - generic:
                - link:
                  - /url: /org/e2e-test-org/matters
                  - img
                  - text: Back to Matters
                - generic:
                  - button:
                    - img
                    - text: Generate Document
                    - img
                  - button:
                    - img
                    - text: Generate Statement of Account
                  - button:
                    - img
                    - generic: More actions
              - generic:
                - generic:
                  - generic:
                    - heading [level=1]: Khumalo Property Holdings — Erf 1234 Sandton Transfer mw6d4i47a634
                    - generic:
                      - generic: Active
                      - generic: CONVEYANCING
                    - generic:
                      - link:
                        - /url: /org/e2e-test-org/clients/1a5cbb52-7573-4d91-a990-25c1de85edce
                        - text: Khumalo Property Holdings mw6d4i47a634
                  - generic:
                    - button:
                      - img
                      - text: Close Matter
                    - button:
                      - img
                      - text: Complete Matter
                    - button:
                      - img
              - generic:
                - tablist:
                  - tab:
                    - text: Details
                    - img
                  - tab [selected]: Overview
                  - tab:
                    - text: Work
                    - img
                  - tab:
                    - text: Fees
                    - img
                  - tab:
                    - text: Client
                    - img
                  - tab: Diary
                  - tab:
                    - text: Activity
                    - img
                - tabpanel:
                  - generic:
                    - generic:
                      - generic:
                        - generic:
                          - generic:
                            - generic: Healthy
                        - paragraph: "Client: Khumalo Property Holdings mw6d4i47a634"
                    - group:
                      - generic:
                        - generic:
                          - generic:
                            - generic: 3/5 setup steps complete
                          - generic:
                            - generic:
                              - generic: 60%
                        - img
                    - generic:
                      - link:
                        - /url: "?tab=time"
                        - generic:
                          - generic:
                            - generic:
                              - img
                            - generic: 0.0h
                            - generic: Hours logged
                      - link:
                        - /url: "?tab=tasks"
                        - generic:
                          - generic:
                            - generic:
                              - img
                            - generic: 0%
                            - generic: Task completion
                      - link:
                        - /url: "?tab=trust"
                        - generic:
                          - generic:
                            - generic:
                              - img
                            - generic: R 0,00
                            - generic: Trust balance
                      - link:
                        - /url: "?tab=statements"
                        - generic:
                          - generic:
                            - generic:
                              - img
                            - generic: R 0,00
                            - generic: Outstanding fee notes
                    - generic:
                      - generic:
                        - generic:
                          - generic:
                            - img
                            - text: FICA
                          - generic: Done
                      - generic:
                        - paragraph: Completed 7 Nov 2026
                        - link:
                          - /url: /org/e2e-test-org/clients/1a5cbb52-7573-4d91-a990-25c1de85edce?tab=onboarding
                          - text: View checklist
                    - generic:
                      - generic:
                        - generic: Upcoming Deadlines
                      - generic:
                        - paragraph: No upcoming deadlines.
  - generic:
    - heading [level=2]: Command Palette
    - paragraph: Search pages and settings
  - region "Notifications alt+T"
  - alert: Matters
  - 'dialog "Generate: Offer to PurchaseStep 2 of 2" [active] [ref=e2]':
    - generic [ref=e3]:
      - 'heading "Generate: Offer to PurchaseStep 2 of 2" [level=2] [ref=e5]':
        - text: "Generate: Offer to Purchase"
        - generic [ref=e6]: Step 2 of 2
      - iframe [ref=e12]:
        - generic [active] [ref=f1e1]:
          - heading "E2E Test Organization" [level=1] [ref=f1e2]
          - heading "Offer to Purchase" [level=2] [ref=f1e3]
          - separator [ref=f1e4]
          - paragraph [ref=f1e5]:
            - strong [ref=f1e6]: "Matter:"
            - text: Khumalo Property Holdings — Erf 1234 Sandton Transfer mw6d4i47a634
          - separator [ref=f1e7]
          - heading "Parties" [level=3] [ref=f1e8]
          - paragraph [ref=f1e9]:
            - strong [ref=f1e10]: "Purchaser:"
            - text: Khumalo Property Holdings mw6d4i47a634
          - paragraph [ref=f1e11]:
            - strong [ref=f1e12]: "Seller:"
            - text: Johannes van der Merwe
          - separator [ref=f1e13]
          - heading "Property" [level=3] [ref=f1e14]
          - paragraph [ref=f1e15]:
            - strong [ref=f1e16]: "Property Address:"
            - text: 12 Rivonia Road, Sandton, 2196
          - paragraph [ref=f1e17]:
            - strong [ref=f1e18]: "Erf Number:"
            - text: "1234"
          - paragraph [ref=f1e19]:
            - strong [ref=f1e20]: "Deeds Office:"
            - text: Johannesburg
          - separator [ref=f1e21]
          - heading "Purchase Price & Occupation" [level=3] [ref=f1e22]
          - paragraph [ref=f1e23]:
            - strong [ref=f1e24]: "Purchase Price (ZAR):"
          - paragraph [ref=f1e25]:
            - strong [ref=f1e26]: "Occupation Date:"
          - separator [ref=f1e27]
          - heading "Conditions of Sale" [level=3] [ref=f1e28]
          - generic [ref=f1e29]:
            - paragraph [ref=f1e30]: The property is sold voetstoots, meaning that Khumalo Property Holdings mw6d4i47a634 accepts the property in its current condition, including any patent and latent defects, and Johannes van der Merwe shall not be liable for any defects, whether disclosed or undisclosed, save as expressly required by law.
            - paragraph [ref=f1e31]: Where the Consumer Protection Act 68 of 2008 applies to this transaction, the rights of Khumalo Property Holdings mw6d4i47a634 as a consumer shall not be excluded or limited by the voetstoots provisions above. In particular, section 55 of the Consumer Protection Act protects a consumer-buyer from a supplier-seller in the ordinary course of business, and those statutory rights remain unaffected.
          - generic [ref=f1e32]:
            - paragraph [ref=f1e33]: Occupation of the property shall be given to Khumalo Property Holdings mw6d4i47a634 on the occupation date stipulated in the offer to purchase, which date may precede or follow the date of registration of transfer.
            - paragraph [ref=f1e34]: Should occupation be given prior to registration, Khumalo Property Holdings mw6d4i47a634 shall pay occupational rent to the seller, in advance, from the occupation date until the date of registration of transfer. Should occupation be given after registration, the seller shall pay occupational rent to Khumalo Property Holdings mw6d4i47a634 for the period between registration and occupation. Occupational rent shall be calculated on a monthly basis and apportioned pro-rata for part months.
          - generic [ref=f1e35]:
            - paragraph [ref=f1e36]: This sale is subject to the suspensive condition that Khumalo Property Holdings mw6d4i47a634 obtains approval in principle for a mortgage bond, from a registered financial institution, in the amount and on terms acceptable to Khumalo Property Holdings mw6d4i47a634, within twenty-one (21) business days of the signature date of the offer to purchase.
            - paragraph [ref=f1e37]: If bond approval is not obtained within the stipulated period (as it may be extended in writing by agreement between the parties), this agreement shall lapse and be of no further force or effect, and any deposit paid shall be refunded to Khumalo Property Holdings mw6d4i47a634 without interest or deduction, save for any bank charges actually incurred.
          - generic [ref=f1e38]:
            - paragraph [ref=f1e39]: Khumalo Property Holdings mw6d4i47a634 shall be liable for the payment of transfer duty to the South African Revenue Service in accordance with the Transfer Duty Act 40 of 1949, calculated on the purchase price of the property.
            - paragraph [ref=f1e40]: Where the seller is a registered value-added tax vendor and the sale constitutes a taxable supply, value-added tax shall be payable in lieu of transfer duty, and the purchase price shall be deemed to include value-added tax at the prevailing statutory rate unless expressly stated otherwise.
          - generic [ref=f1e41]:
            - paragraph [ref=f1e42]: E2E Test Organization is an accountable institution as contemplated in the Financial Intelligence Centre Act 38 of 2001 ("FICA"). Each party undertakes to provide E2E Test Organization with all documents and information reasonably required to enable E2E Test Organization to comply with its customer due diligence obligations under FICA.
            - paragraph [ref=f1e43]: Failure by any party to provide the required FICA documentation timeously may delay the registration of transfer, and the defaulting party shall be liable for any holding costs, additional disbursements, or penalties arising from such delay.
          - generic [ref=f1e44]:
            - paragraph [ref=f1e45]: "The seller shall, at the seller's cost, obtain a rates clearance certificate from the relevant municipality in accordance with section 118 of the Local Government: Municipal Systems Act 32 of 2000, confirming that all amounts due for municipal services and rates for the two years preceding the date of application have been paid in full."
            - paragraph [ref=f1e46]: E2E Test Organization shall not be obliged to lodge the transfer documents at the Deeds Office until a valid rates clearance certificate has been issued.
          - generic [ref=f1e47]:
            - paragraph [ref=f1e48]: This agreement shall be governed by and construed in accordance with the laws of the Republic of South Africa, and the parties hereby irrevocably submit to the jurisdiction of the South African courts in relation to any dispute arising out of or in connection with this agreement.
            - paragraph [ref=f1e49]: Notwithstanding that any amount in dispute may exceed the monetary jurisdiction of the Magistrate's Court, the parties consent to the jurisdiction of the Magistrate's Court having territorial jurisdiction in terms of section 45 of the Magistrates' Courts Act 32 of 1944, at the election of the party instituting proceedings.
          - separator [ref=f1e50]
          - paragraph [ref=f1e51]: Signed at _________________ on this _____ day of ______________ 20___.
          - paragraph [ref=f1e52]: ____________________________
          - paragraph [ref=f1e53]:
            - strong [ref=f1e54]: "Purchaser:"
            - text: Khumalo Property Holdings mw6d4i47a634
          - paragraph [ref=f1e55]: ____________________________
          - paragraph [ref=f1e56]:
            - strong [ref=f1e57]: "Seller:"
            - text: Johannes van der Merwe
          - separator [ref=f1e58]
          - paragraph
      - generic [ref=e13]:
        - button "Back" [ref=e14]:
          - img
          - text: Back
        - button "Download PDF" [ref=e15]:
          - img
          - text: Download PDF
        - button "Save to Documents" [ref=e16]:
          - img
          - text: Save to Documents
      - button "Close" [ref=e17]:
        - img
        - generic [ref=e18]: Close
```

# Test source

```ts
  275 | 
  276 |     await page.waitForLoadState("networkidle");
  277 |     await captureScreenshot(page, "day-14-conveyancing-matter-custom-fields");
  278 |     await captureScreenshot(page, "conveyancing-matter-detail-custom-fields", { curated: true });
  279 |   });
  280 | 
  281 |   // ── 14.5-14.6: Fill minimum field subset ──────────────────────────
  282 |   test("Alice: Fill property_address, erf_number, deeds_office, seller and purchaser", async ({
  283 |     page,
  284 |   }) => {
  285 |     await loginAs(page, "alice");
  286 |     const fieldsTab = await openMatterFieldsTab(page);
  287 | 
  288 |     await fieldsTab.locator("#cf-property_address").fill("12 Rivonia Road, Sandton, 2196");
  289 |     await fieldsTab.locator("#cf-erf_number").fill("1234");
  290 |     await fieldsTab.locator("#cf-deeds_office").selectOption("JOHANNESBURG");
  291 |     // G88-F12 (scenario 14.5, founder authorisation 2026-11-03): fill Seller and Purchaser
  292 |     await fieldsTab.locator("#cf-seller_name").fill(SELLER_NAME);
  293 |     await fieldsTab.locator("#cf-purchaser_name").fill(clientName());
  294 |     await fieldsTab.getByRole("button", { name: "Save Custom Fields" }).click();
  295 |     await expect(fieldsTab.getByText("Saved successfully")).toBeVisible({ timeout: 15_000 });
  296 | 
  297 |     // 14.6: values persist and re-render after a reload
  298 |     await page.reload();
  299 |     await openGroupedTab(page, "details", "fields");
  300 |     const reloaded = page.getByTestId("matter-fields-tab");
  301 |     await expect(reloaded.locator("#cf-property_address")).toHaveValue(
  302 |       "12 Rivonia Road, Sandton, 2196"
  303 |     );
  304 |     await expect(reloaded.locator("#cf-erf_number")).toHaveValue("1234");
  305 |     await expect(reloaded.locator("#cf-deeds_office")).toHaveValue("JOHANNESBURG");
  306 |     await expect(reloaded.locator("#cf-seller_name")).toHaveValue(SELLER_NAME);
  307 |     await expect(reloaded.locator("#cf-purchaser_name")).toHaveValue(clientName());
  308 | 
  309 |     await page.waitForLoadState("networkidle");
  310 |     await captureScreenshot(page, "day-14-conveyancing-fields-populated");
  311 |   });
  312 | 
  313 |   // ── 14.7: Generate Offer to Purchase document ─────────────────────
  314 |   test("Alice: Generate Offer to Purchase document and save it to the matter", async ({ page }) => {
  315 |     await loginAs(page, "alice");
  316 |     await openMatter(page, matterName());
  317 | 
  318 |     await page.getByRole("button", { name: /Generate Document/ }).click();
  319 |     await page.getByRole("menuitem", { name: "Offer to Purchase" }).click();
  320 | 
  321 |     const dialog = page.getByRole("dialog", { name: /Generate: Offer to Purchase/ });
  322 |     await expect(dialog).toBeVisible({ timeout: 15_000 });
  323 |     // Phase 88 626H (B-7): since 626G (conveyancing-za-clauses v2) the OTP has clause
  324 |     // associations, so the dialog opens on the clause step (Step 1 of 2); the preview renders
  325 |     // after "Next: Preview". Clause assertions live in the 14.8 test below.
  326 |     await dialog.getByRole("button", { name: /Next/ }).click();
  327 |     const preview = dialog.frameLocator('iframe[title="Document Preview"]').locator("body");
  328 |     // The preview merges the client, matter and the Day 14 conveyancing fields
  329 |     await expect(preview).toContainText("Offer to Purchase", { timeout: 15_000 });
  330 |     // G88-F12: both parties come from the matter's seller / purchaser fields
  331 |     await expect(preview).toContainText(`Purchaser: ${clientName()}`);
  332 |     await expect(preview).toContainText(`Seller: ${SELLER_NAME}`);
  333 |     await expect(preview).toContainText("Property Address: 12 Rivonia Road, Sandton, 2196");
  334 |     await expect(preview).toContainText("Erf Number: 1234");
  335 |     await expect(preview).toContainText("Deeds Office: Johannesburg");
  336 | 
  337 |     await dialog.getByRole("button", { name: "Save to Documents" }).click();
  338 |     await expect(dialog.getByText("Document saved successfully")).toBeVisible({ timeout: 15_000 });
  339 |     await page.waitForLoadState("networkidle");
  340 |     await captureScreenshot(page, "day-14-otp-document-generated");
  341 |     await dialog.getByRole("button", { name: "Close" }).first().click();
  342 |     await expect(dialog).toBeHidden();
  343 | 
  344 |     await openGroupedTab(page, "work", "generated");
  345 |     await expect(page.getByRole("row", { name: /Offer to Purchase/ })).toBeVisible({
  346 |       timeout: 15_000,
  347 |     });
  348 |   });
  349 | 
  350 |   // ── 14.8: OTP carries the conveyancing clause pack ────────────────
  351 |   // Regression (was FIXME depth-ii:otp-clause-associations, G-B18): conveyancing-za-clauses v2
  352 |   // associates the OTP's 7 embedded clauses (626G), so the dialog offers the clause step and
  353 |   // Conditions of Sale is filled.
  354 |   test("Alice: Generate Offer to Purchase document with conveyancing clauses", async ({ page }) => {
  355 |     await loginAs(page, "alice");
  356 |     await openMatter(page, matterName());
  357 |     await page.getByRole("button", { name: /Generate Document/ }).click();
  358 |     await page.getByRole("menuitem", { name: "Offer to Purchase" }).click();
  359 | 
  360 |     const dialog = page.getByRole("dialog", { name: /Generate: Offer to Purchase/ });
  361 |     await expect(dialog).toBeVisible({ timeout: 15_000 });
  362 |     // Clause step (Step 1 of 2) lists the conveyancing clause pack
  363 |     await expect(dialog).toContainText("Step 1 of 2");
  364 |     await expect(dialog).toContainText("Voetstoots");
  365 |     await dialog.getByRole("button", { name: /Next/ }).click();
  366 | 
  367 |     const preview = dialog.frameLocator('iframe[title="Document Preview"]').locator("body");
  368 |     await expect(preview).toContainText("Conditions of Sale", { timeout: 15_000 });
  369 |     await expect(preview).toContainText(/voetstoots/i);
  370 |     // G88-F12 (HIGH): the SELLER is not liable for defects, the PURCHASER accepts the property
  371 |     await expect(preview).toContainText(`meaning that ${clientName()} accepts the property`);
  372 |     await expect(preview).toContainText(`and ${SELLER_NAME} shall not be liable`);
  373 |     await expect(preview).toContainText(/suspensive/i);
  374 |     await expect(preview).toContainText(/transfer duty/i);
> 375 |     await page.waitForLoadState("networkidle");
      |                ^ Error: page.waitForLoadState: Test timeout of 30000ms exceeded.
  376 |     await captureScreenshot(page, "otp-document-with-clauses", { curated: true });
  377 |   });
  378 | 
  379 |   // ── 14.9-14.11: Send for Acceptance (Phase 28 flow) ───────────────
  380 |   test("Alice: Send OTP for Acceptance via Phase 28 flow", async ({ page }) => {
  381 |     await loginAs(page, "alice");
  382 |     await openMatter(page, matterName());
  383 |     await openGroupedTab(page, "work", "generated");
  384 | 
  385 |     const otpRow = page.getByRole("row", { name: /Offer to Purchase/ }).first();
  386 |     await expect(otpRow).toBeVisible({ timeout: 15_000 });
  387 |     // Offered because the OTP template is acceptanceEligible and the matter has a client
  388 |     await otpRow.getByTitle("Send for Acceptance").click();
  389 | 
  390 |     const dialog = page.getByRole("dialog", { name: /Send for Acceptance/ });
  391 |     await expect(dialog).toBeVisible();
  392 |     // Recipient = the client's portal contact (auto-provisioned from the client email)
  393 |     await dialog.locator("#recipient").click();
  394 |     await page.getByRole("option", { name: new RegExp(escapeRegExp(clientEmail())) }).click();
  395 |     await dialog.locator("#expiryDays").fill("7");
  396 |     await dialog.getByRole("button", { name: "Send", exact: true }).click();
  397 |     await expect(dialog.getByText("Acceptance request sent.")).toBeVisible({ timeout: 15_000 });
  398 |     await expect(dialog).toBeHidden({ timeout: 15_000 });
  399 | 
  400 |     await expect(otpRow).toContainText("Awaiting Acceptance", { timeout: 15_000 });
  401 |     await page.waitForLoadState("networkidle");
  402 |     await captureScreenshot(page, "day-14-otp-acceptance-request-sent");
  403 |     await captureScreenshot(page, "otp-acceptance-request", { curated: true });
  404 | 
  405 |     // 14.11: the acceptance request email (with its magic link) reaches the client
  406 |     // (waitForEmail polls for the message whose subject matches, then fetches that message)
  407 |     const message = await waitForEmail(clientEmail(), {
  408 |       subject: "for acceptance: offer-to-purchase",
  409 |     });
  410 |     expect(message.Subject).toMatch(/review .* for acceptance: offer-to-purchase/i);
  411 |     expect(`${message.HTML}\n${message.Text}`).toMatch(/\/accept\/[A-Za-z0-9_-]{16,}/);
  412 |   });
  413 | });
  414 | 
```