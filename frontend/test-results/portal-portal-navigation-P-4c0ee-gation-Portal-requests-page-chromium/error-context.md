# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: portal/portal-navigation.spec.ts >> PORTAL-03: Portal Navigation >> Portal requests page
- Location: e2e/tests/portal/portal-navigation.spec.ts:279:7

# Error details

```
Error: expect(received).toBeTruthy()

Received: false
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
  198 |     await page.waitForLoadState("networkidle");
  199 | 
  200 |     // Verify project detail page loads
  201 |     await expect(page.locator("body")).not.toContainText("Something went wrong");
  202 | 
  203 |     // Should show project name heading
  204 |     const hasProjectName = await page
  205 |       .getByRole("heading", { level: 1 })
  206 |       .isVisible({ timeout: 10000 })
  207 |       .catch(() => false);
  208 |     expect(hasProjectName).toBeTruthy();
  209 | 
  210 |     // Should show tabs (Documents, Tasks, Comments)
  211 |     const hasDocumentsTab = await page
  212 |       .getByText(/Documents/)
  213 |       .first()
  214 |       .isVisible({ timeout: 5000 })
  215 |       .catch(() => false);
  216 |     const hasTasksTab = await page
  217 |       .getByText(/Tasks/)
  218 |       .first()
  219 |       .isVisible({ timeout: 5000 })
  220 |       .catch(() => false);
  221 |     expect(hasDocumentsTab || hasTasksTab).toBeTruthy();
  222 | 
  223 |     // Should show "Back to Projects" link
  224 |     await expect(page.getByText("Back to Projects")).toBeVisible();
  225 |   });
  226 | 
  227 |   test("Portal documents page", async ({ page }) => {
  228 |     if (!portalJwt) {
  229 |       test.skip(true, "Portal auth not available");
  230 |       return;
  231 |     }
  232 | 
  233 |     await loginAsPortalContact(page, portalJwt);
  234 |     await page.goto("/portal/documents");
  235 |     await page.waitForLoadState("networkidle");
  236 | 
  237 |     // Check if authentication worked — portal may redirect or error
  238 |     const currentUrl = page.url();
  239 |     const bodyText = await page.locator("body").innerText();
  240 |     if (
  241 |       bodyText.includes("Something went wrong") ||
  242 |       bodyText.includes("Error") ||
  243 |       !currentUrl.includes("/documents")
  244 |     ) {
  245 |       test.skip(true, "Portal documents page not accessible — auth may not be working");
  246 |       return;
  247 |     }
  248 | 
  249 |     // Should show documents heading or content
  250 |     const heading = page.getByRole("heading", { name: /Documents/i }).first();
  251 |     const hasPage = await heading.isVisible({ timeout: 5000 }).catch(() => false);
  252 |     const hasContent = await page
  253 |       .getByText(/document|All Shared/i)
  254 |       .first()
  255 |       .isVisible({ timeout: 3000 })
  256 |       .catch(() => false);
  257 |     expect(hasPage || hasContent).toBeTruthy();
  258 | 
  259 |     // Should show document list or empty state
  260 |     const hasTable = await page
  261 |       .getByRole("table")
  262 |       .first()
  263 |       .isVisible({ timeout: 5000 })
  264 |       .catch(() => false);
  265 |     const hasEmptyState = await page
  266 |       .getByText(/No.*documents/i)
  267 |       .first()
  268 |       .isVisible({ timeout: 3000 })
  269 |       .catch(() => false);
  270 |     const hasDocList = await page
  271 |       .getByText(/document|letter|All Shared/i)
  272 |       .first()
  273 |       .isVisible({ timeout: 3000 })
  274 |       .catch(() => false);
  275 | 
  276 |     expect(hasTable || hasEmptyState || hasDocList).toBeTruthy();
  277 |   });
  278 | 
  279 |   test("Portal requests page", async ({ page }) => {
  280 |     if (!portalJwt) {
  281 |       test.skip(true, "Portal auth not available");
  282 |       return;
  283 |     }
  284 | 
  285 |     await loginAsPortalContact(page, portalJwt);
  286 |     await page.goto("/portal/requests");
  287 |     await page.waitForLoadState("networkidle");
  288 | 
  289 |     // Check if authentication worked
  290 |     const bodyText = await page.locator("body").innerText();
  291 |     if (bodyText.includes("Something went wrong") || bodyText.includes("Error")) {
  292 |       test.skip(true, "Portal requests page returned an error — auth may not be working");
  293 |       return;
  294 |     }
  295 | 
  296 |     const heading = page.getByRole("heading", { name: /Requests/i }).first();
  297 |     const hasPage = await heading.isVisible({ timeout: 5000 }).catch(() => false);
> 298 |     expect(hasPage).toBeTruthy();
      |                     ^ Error: expect(received).toBeTruthy()
  299 | 
  300 |     // Should show request list with tabs (Open/Completed) or empty state
  301 |     const hasOpenTab = await page
  302 |       .getByText("Open")
  303 |       .first()
  304 |       .isVisible({ timeout: 3000 })
  305 |       .catch(() => false);
  306 |     const hasCompletedTab = await page
  307 |       .getByText("Completed")
  308 |       .first()
  309 |       .isVisible({ timeout: 3000 })
  310 |       .catch(() => false);
  311 |     const hasEmptyState = await page
  312 |       .getByText(/No.*requests/i)
  313 |       .first()
  314 |       .isVisible({ timeout: 3000 })
  315 |       .catch(() => false);
  316 | 
  317 |     expect(hasOpenTab || hasCompletedTab || hasEmptyState).toBeTruthy();
  318 |   });
  319 | 
  320 |   test("No firm-side leakage in portal", async ({ page }) => {
  321 |     if (!portalJwt) {
  322 |       test.skip(true, "Portal auth not available");
  323 |       return;
  324 |     }
  325 | 
  326 |     await loginAsPortalContact(page, portalJwt);
  327 | 
  328 |     // Check all portal pages for firm-side navigation leakage
  329 |     const portalPages = [
  330 |       "/portal/projects",
  331 |       "/portal/documents",
  332 |       "/portal/requests",
  333 |       "/portal/proposals",
  334 |     ];
  335 | 
  336 |     for (const portalPage of portalPages) {
  337 |       await page.goto(portalPage);
  338 |       await page.waitForLoadState("networkidle");
  339 | 
  340 |       const bodyText = await page.locator("body").innerText();
  341 | 
  342 |       // Portal should NOT expose firm-side navigation items
  343 |       // These are sidebar items from the firm's org-scoped layout
  344 |       const firmOnlyItems = [
  345 |         "Settings",
  346 |         "Team",
  347 |         "Reports",
  348 |         "Profitability",
  349 |         "Invoices",
  350 |         "My Work",
  351 |         "Resources",
  352 |         "Compliance",
  353 |       ];
  354 | 
  355 |       // Check the portal header nav specifically (not general page content)
  356 |       // The portal header has: Projects, Proposals, Requests, Acceptances, Documents, Profile
  357 |       const headerNav = page.locator("header nav, header");
  358 |       const headerText = await headerNav.innerText().catch(() => "");
  359 | 
  360 |       for (const item of firmOnlyItems) {
  361 |         // Only check the header/nav area, not the entire page content
  362 |         // (a document might mention "Settings" in its content)
  363 |         const isInNav = headerText.includes(item);
  364 |         expect(isInNav).toBe(false);
  365 |       }
  366 |     }
  367 | 
  368 |     // Verify the portal nav items are the expected set
  369 |     const header = page.locator("header");
  370 |     const headerContent = await header.innerText().catch(() => "");
  371 | 
  372 |     // Portal nav should include these items
  373 |     const portalNavItems = ["Projects", "Documents", "Requests"];
  374 |     for (const item of portalNavItems) {
  375 |       expect(headerContent).toContain(item);
  376 |     }
  377 |   });
  378 | });
  379 | 
```