# Consulting Vertical — Curated Screenshots

This directory holds curated PNG captures of the `consulting-za` vertical profile for marketing, blog, walkthrough, and demo use.

## Status

Placeholder. PNGs are populated manually after a clean Playwright run of `frontend/e2e/tests/consulting-lifecycle/screenshots.spec.ts`, or captured from a live `consulting-za` tenant on the Keycloak dev stack.

## Prescribed shots (mirrors the Day-by-day wow moments in `qa/testplan/demos/consulting-agency-90day-keycloak.md`)

1. `dashboard-with-utilization-widget.png` — Dashboard with `TeamUtilizationWidget` rendering and `en-ZA-consulting` terminology active (Clients, Time Logs, Billing Rates).
2. `project-with-campaign-type.png` — Project detail page showing `campaign_type` from the `consulting_za_engagement` field group (e.g., `WEBSITE_BUILD`).
3. `creative-brief-request-form.png` — 10-question creative brief request form (3 required + 2 file-upload markers visible).
4. `monthly-retainer-report-pdf.png` — Generated `monthly-retainer-report` PDF preview with all four `retainer.*` variables resolved.
5. `sow-with-agency-clauses.png` — `statement-of-work` PDF preview with the three required `requiredSlugs` clauses pre-included (`consulting-payment-terms`, `consulting-ip-ownership`, `consulting-change-requests`).

## Convention

Mirrors `documentation/screenshots/legal-vertical/`. Curated captures are non-regression — they are intended for human-readable artifacts (blog posts, sales decks, README walkthroughs), not Playwright baselines. Regression baselines live separately under `frontend/e2e/screenshots/consulting-lifecycle/`.
