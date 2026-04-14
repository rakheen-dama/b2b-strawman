# QA Cycle Status — Accounting-ZA 90-Day Demo Walkthrough (Keycloak) — 2026-04-14

## Current State

- **QA Position**: Day 22 (Days 0-21 complete; 3 clients active, 4 engagements created, 15.5h logged across 3 engagements, budget set on year-end pack; continued multi-engagement work + profitability next)
- **Cycle**: 1
- **Dev Stack**: READY
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_2026-04-14`
- **Scenario**: `qa/testplan/demos/accounting-za-90day-keycloak-v2.md`
- **Focus**: Fresh 90-day accounting-ZA demo walkthrough against the real Keycloak dev stack. Exercises the accounting vertical profile (terminology, FICA packs, document templates, time/billing/profitability) end-to-end.
- **Auth Mode**: Keycloak (real OIDC — platform admin `padmin@docteams.local` is pre-seeded via `keycloak-bootstrap.sh`; all other users come through the onboarding flow).

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend (kc mode) | http://localhost:3000 | UP |
| Backend (local+keycloak profile) | http://localhost:8080 | UP |
| Gateway (BFF) | http://localhost:8443 | UP |
| Portal | http://localhost:3002 | UP |
| Keycloak | http://localhost:8180 | UP |
| Mailpit UI | http://localhost:8025 | UP |
| Postgres (docteams) | localhost:5432 | UP |

## Gap Tracker

| GAP_ID | Day / Checkpoint | Severity | Status | Summary | Owner | Retries |
|--------|------------------|----------|--------|---------|-------|---------|
| GAP-D0-01 | Day 0 / 0.11 | LOW | OPEN | Access requests page tab switching broken -- Pending tab stays selected regardless of which tab is clicked | Dev | 0 |
| GAP-D0-02 | Day 0 / 0.30 | LOW | OPEN | Rate cards not pre-seeded from accounting-za vertical profile; all members show "Not set" | Dev | 0 |
| GAP-D0-03 | Day 0 / 0.38 | MED | OPEN | Create Engagement dialog title says "Create Project" -- terminology not translated for accounting-za profile | Dev | 0 |
| GAP-D0-04 | Day 0 / 0.37 | LOW | OPEN | Trust-specific required fields (Trust Registration Number, Trust Deed Date, Trust Type) shown with asterisks for all entity types in New Client Step 2 | Dev | 0 |
| GAP-D0-05 | Day 0 / 0.41 | MED | OPEN | Engagement templates not pre-seeded from accounting-za profile -- no Year-End Pack, Monthly Bookkeeping, Tax Return templates | Dev | 0 |
| GAP-D0-06 | Day 0 / 0.43 | LOW | OPEN | Automation rules not pre-seeded from accounting-za profile; feature toggle disabled by default | Dev | 0 |
| GAP-D0-07 | Day 2 / 2.2 | MED | OPEN | No onboarding checklist seeded for accounting-za profile -- no FICA/KYC items; manual activation required | Dev | 0 |

## Legend

- **Status**: OPEN → SPEC_READY → FIXED → VERIFIED | REOPENED | STUCK | WONT_FIX
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-14 — Cycle initialized. Prior cycle (2026-04-13, legal-za) archived to `qa_cycle/_archive_2026-04-13_legal-90day-kc-v2/`. Branch `bugfix_cycle_2026-04-14` created from main. Fresh accounting-ZA scenario run from Day 0.
- 2026-04-14 — **Infra Agent: cleanup & startup complete.** Deleted Keycloak org "Mathebula & Partners" (legal-za leftover). Dropped tenant schema `tenant_5039f2d497cf` (100 tables). Truncated 6 public tables (access_requests, org_schema_mapping, organizations, processed_webhooks, subscription_payments, subscriptions). Cleared Mailpit inbox. Restarted backend + gateway. All 4 services healthy (backend:8080, gateway:8443, frontend:3000, portal:3002). Docker infra up (Postgres, Keycloak, Mailpit, LocalStack). `padmin@docteams.local` verified present and enabled. Dev stack marked READY.
- 2026-04-14 — **QA Agent: Day 0 Phases A-E, H (sidebar), I (billing) executed.** 42 checkpoints: 36 PASS, 2 PARTIAL, 8 DEFERRED (field promotion detail, templates, automations, modules). 0 FAIL. 2 LOW gaps found (GAP-D0-01: tab switching, GAP-D0-02: rate pre-seeding). Org "Thornton & Associates" created with accounting-za profile, 3 Keycloak users registered (Thandi/Owner, Bob/Admin, Carol/Member), ZAR currency, VAT 15%, brand colour #1B5E20 set. No tier upgrade UI found. No legal module leakage. Screenshots captured.
- 2026-04-14 — **QA Agent: Days 0 (deferred)-3 executed.** 67 total checkpoints: 56 PASS, 5 PARTIAL, 4 FAIL, 1 DEFERRED, 1 N/A. 5 new gaps found (GAP-D0-03 through GAP-D0-07). Day 0 deferred items completed: custom field packs verified (19 client fields, 5 engagement fields, trust variant present), field promotion inline confirmed, progressive disclosure check complete (no legal modules). Templates NOT pre-seeded (GAP-D0-05). Automations NOT pre-seeded (GAP-D0-06). Rate cards manually set (Thandi R1500, Bob R850, Carol R450). Day 1: Sipho Dlamini client created as Bob (PROSPECT, all promoted fields inline). Day 2: Onboarded (no checklist seeded, GAP-D0-07) then manually activated. Day 3: Tax return engagement created manually (no template, GAP-D0-05). Create dialog says "Create Project" not "Create Engagement" (GAP-D0-03).
- 2026-04-14 — **QA Agent: Days 4-6 executed.** 16 checkpoints: 10 PASS, 6 N/A (template-dependent). Day 4: Kgosi Holdings (Pty) Ltd created as Thandi with all promoted fields (wow moment screenshot captured), onboarded+activated. Detail page shows Registration Number, Tax Number, Entity Type, Financial Year End, Contact, Address all inline. Day 5: Monthly Bookkeeping engagement created manually. Day 6: Year-End Pack engagement created manually. 3 engagements total across 2 active clients. Profitability page loads (empty). No new gaps.
- 2026-04-14 — **QA Agent: Days 7-14 executed.** 8 checkpoints: 8 PASS. All 3 users added to all engagements. Time logging tested end-to-end: Carol 1.5h on Sipho tax return (R450/hr), Thandi 2h on year-end pack (R1,500/hr), Bob 3h on bookkeeping (R850/hr), Carol 2h on bookkeeping (R450/hr), Bob 1h on year-end pack (R850/hr). Document upload verified (bank statement). Comments posted on tasks (Thandi @Bob + Bob reply). Budget configured on year-end pack (40h / R60,000 / ZAR / 80% alert). Budget tracking shows correct consumed hours and amounts. No new gaps.
- 2026-04-14 — **QA Agent: Days 15-21 executed.** 9 checkpoints: 8 PASS, 1 N/A, 1 DEFERRED. Moroka Family Trust created with Trust type. Step 2 correctly surfaced trust-specific fields (Registration Number, Deed Date, Trust Type) from `accounting-za-customer-trust` variant. Trust activated via ONBOARDING → ACTIVE (auto-activation, GAP-D0-07). Trust AFS engagement created with 4 manual tasks. Thandi logged 2.5h and Bob logged 4h on trust tasks — billing rate snapshots correct. 4 engagements total across 3 active clients. No new gaps.
