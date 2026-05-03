# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: portal/portal-proposal-acceptance.spec.ts >> PROP-03: Portal Proposal Acceptance >> No unresolved variables in portal proposal view
- Location: e2e/tests/portal/portal-proposal-acceptance.spec.ts:324:7

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: locator.click: Test timeout of 30000ms exceeded.
Call log:
  - waiting for getByRole('link').first()
    - locator resolved to <a href="#main-content" class="sr-only focus:not-sr-only focus:fixed focus:top-4 focus:left-4 focus:z-[100] focus:rounded-md focus:bg-slate-950 focus:px-4 focus:py-2 focus:text-sm focus:font-medium focus:text-white focus:shadow-lg">Skip to content</a>
  - attempting click action
    2 × waiting for element to be visible, enabled and stable
      - element is visible, enabled and stable
      - scrolling into view if needed
      - done scrolling
      - element is outside of the viewport
    - retrying click action
    - waiting 20ms
    2 × waiting for element to be visible, enabled and stable
      - element is visible, enabled and stable
      - scrolling into view if needed
      - done scrolling
      - element is outside of the viewport
    - retrying click action
      - waiting 100ms
    55 × waiting for element to be visible, enabled and stable
       - element is visible, enabled and stable
       - scrolling into view if needed
       - done scrolling
       - element is outside of the viewport
     - retrying click action
       - waiting 500ms

```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
  - link "Skip to content" [ref=e2] [cursor=pointer]:
    - /url: "#main-content"
  - generic [ref=e4]:
    - generic [ref=e5]:
      - heading "Kazi Portal" [level=1] [ref=e6]
      - paragraph [ref=e7]: Access your shared documents and projects
    - generic [ref=e10]:
      - generic [ref=e11]:
        - text: Email address
        - textbox "Email address" [ref=e12]:
          - /placeholder: you@example.com
      - generic [ref=e13]:
        - text: Organization
        - textbox "Organization" [ref=e14]:
          - /placeholder: your-organization
      - button "Send Magic Link" [ref=e15]:
        - img
        - text: Send Magic Link
      - button "Already have a token? Enter it here" [ref=e16]
  - region "Notifications alt+T"
  - alert [ref=e17]
```

# Test source

```ts
  243 |     const createRes = await fetch(`${BACKEND_URL}/api/proposals`, {
  244 |       method: "POST",
  245 |       headers: { Authorization: `Bearer ${jwt}`, "Content-Type": "application/json" },
  246 |       body: JSON.stringify({
  247 |         title: `Decline Test ${RUN_ID}`,
  248 |         customerId: activeCustomer.id,
  249 |         feeModel: "FIXED",
  250 |         fixedFeeAmount: 10000,
  251 |         fixedFeeCurrency: "ZAR",
  252 |       }),
  253 |     });
  254 | 
  255 |     if (!createRes.ok) {
  256 |       test.skip(true, "Could not create proposal for decline test");
  257 |       return;
  258 |     }
  259 | 
  260 |     const proposal = await createRes.json();
  261 | 
  262 |     // Get portal contacts and send
  263 |     const contactsRes = await fetch(
  264 |       `${BACKEND_URL}/api/customers/${activeCustomer.id}/portal-contacts`,
  265 |       {
  266 |         headers: { Authorization: `Bearer ${jwt}` },
  267 |       }
  268 |     );
  269 |     if (!contactsRes.ok) {
  270 |       test.skip(true, "No portal contacts for decline test");
  271 |       return;
  272 |     }
  273 |     const contacts = await contactsRes.json();
  274 |     if (!Array.isArray(contacts) || contacts.length === 0) {
  275 |       test.skip(true, "No portal contacts configured");
  276 |       return;
  277 |     }
  278 | 
  279 |     await fetch(`${BACKEND_URL}/api/proposals/${proposal.id}/send`, {
  280 |       method: "POST",
  281 |       headers: { Authorization: `Bearer ${jwt}`, "Content-Type": "application/json" },
  282 |       body: JSON.stringify({ portalContactId: contacts[0].id }),
  283 |     });
  284 | 
  285 |     // Navigate to proposal in portal
  286 |     await loginAsPortalContact(page, portalJwt);
  287 |     await page.goto(`/portal/proposals/${proposal.id}`);
  288 |     await page.waitForLoadState("networkidle");
  289 | 
  290 |     const declineButton = page.getByRole("button", { name: /Decline/i });
  291 |     const canDecline = await declineButton.isVisible({ timeout: 5000 }).catch(() => false);
  292 | 
  293 |     if (!canDecline) {
  294 |       test.skip(true, "Decline button not visible");
  295 |       return;
  296 |     }
  297 | 
  298 |     await declineButton.click();
  299 | 
  300 |     // Fill in the decline reason in the dialog
  301 |     const dialog = page.getByRole("dialog");
  302 |     const dialogVisible = await dialog.isVisible({ timeout: 5000 }).catch(() => false);
  303 |     if (dialogVisible) {
  304 |       const reasonInput = page.getByPlaceholder(/reason/i);
  305 |       if (await reasonInput.isVisible({ timeout: 2000 }).catch(() => false)) {
  306 |         await reasonInput.fill("Budget constraints for this quarter");
  307 |       }
  308 |       // Click Decline Proposal in dialog
  309 |       const confirmDecline = dialog.getByRole("button", { name: /Decline Proposal/i });
  310 |       await confirmDecline.click();
  311 |     }
  312 | 
  313 |     await page.waitForTimeout(3000);
  314 | 
  315 |     // Verify declined status
  316 |     const hasDeclined = await page
  317 |       .getByText("Declined")
  318 |       .first()
  319 |       .isVisible({ timeout: 5000 })
  320 |       .catch(() => false);
  321 |     expect(hasDeclined).toBeTruthy();
  322 |   });
  323 | 
  324 |   test("No unresolved variables in portal proposal view", async ({ page }) => {
  325 |     if (!portalJwt) {
  326 |       test.skip(true, "Portal auth not available");
  327 |       return;
  328 |     }
  329 | 
  330 |     await loginAsPortalContact(page, portalJwt);
  331 |     await page.goto("/portal/proposals");
  332 |     await page.waitForLoadState("networkidle");
  333 | 
  334 |     // Navigate to the first visible proposal
  335 |     const proposalLink = page.getByRole("link").first();
  336 |     const hasLink = await proposalLink.isVisible({ timeout: 5000 }).catch(() => false);
  337 | 
  338 |     if (!hasLink) {
  339 |       test.skip(true, "No proposals visible in portal");
  340 |       return;
  341 |     }
  342 | 
> 343 |     await proposalLink.click();
      |                        ^ Error: locator.click: Test timeout of 30000ms exceeded.
  344 |     await page.waitForLoadState("networkidle");
  345 | 
  346 |     // Check for unresolved template variables ({{ }})
  347 |     const bodyText = await page.locator("body").innerText();
  348 |     const hasUnresolved = /\{\{.*?\}\}/.test(bodyText);
  349 |     expect(hasUnresolved).toBe(false);
  350 |   });
  351 | });
  352 | 
```