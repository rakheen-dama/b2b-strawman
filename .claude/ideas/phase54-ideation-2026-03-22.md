# Phase 54 Ideation — Keycloak Dev E2E Test Suite
**Date**: 2026-03-22

## Decision
Playwright E2E test suite running against the full Keycloak dev stack, validating accounting firm onboarding, pack seeding, member invites, and RBAC through the product UI.

## Rationale
- Mock IDP was a Clerk workaround — with Keycloak under full control, it's redundant
- Tests should validate the real auth path (Keycloak login page, invitation emails, registration)
- Existing 90-day lifecycle test plan (`tasks/48-lifecycle-script.md`) is manual — this automates the foundational onboarding portion
- Pack verification ensures the provisioning pipeline actually works end-to-end (seeder → DB → API → UI)

## Key Design Preferences
- **No auth shortcuts** — real Keycloak login page interaction via Playwright, no token injection
- **Only platform admin is scripted** — org creation, member onboarding all through product UI
- **Keycloak manages identity + org registry** — product manages tenant roles (OrgRole in DB)
- **Mailpit API** (`localhost:8025`) for email assertions (OTP extraction, invite link extraction)
- **Docker Compose updated** to build all services from source (backend, frontend, gateway, portal)

## Known Gaps Surfaced
1. **Rate card auto-seeding**: Profile defines `rateCardDefaults` but provisioning may not seed `BillingRate`/`CostRate` — needs investigation
2. **Tax/currency defaults**: Profile defines VAT 15% and ZAR but provisioning may not seed these into `OrgSettings`
3. **Docker Compose**: Currently only infrastructure services — needs app services (backend, frontend, gateway) built from source

## Scope
- Onboarding flow (access request → approval → registration)
- All 8 accounting-ZA packs verified via UI (fields, compliance, templates, clauses, automations, requests)
- Member invite → registration → RBAC (owner/admin/member)
- Existing E2E test migration from mock IDP to Keycloak stack
- Out of scope: full lifecycle operations (time, invoicing, profitability), CI/CD, portal flows

## Next Step
`/architecture requirements/claude-code-prompt-phase54.md`
