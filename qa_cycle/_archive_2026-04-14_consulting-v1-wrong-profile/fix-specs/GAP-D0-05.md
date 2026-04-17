# Fix Spec: GAP-D0-05 — Engagement templates not pre-seeded from accounting-za profile

## Problem
Settings > Engagement Templates page shows "No project templates yet." The accounting-za vertical profile does not seed engagement (project) templates with pre-populated task lists. Expected templates: Year-End Pack, Monthly Bookkeeping, Tax Return (Individual), Tax Return (Trust), VAT Return, Payroll, Annual Financial Statements. Without these, every engagement must be created manually without pre-populated tasks, eliminating the "wow moment" of automatic task lists. Reported at Day 0 checkpoint 0.41.

## Root Cause (hypothesis)
The project template pack seeder (`ProjectTemplatePackSeeder.java`) reads from `classpath:project-template-packs/*.json`. Only `legal-za.json` exists in that directory. There is no `accounting-za.json` file.

The accounting-za vertical profile JSON (`vertical-profiles/accounting-za.json`) does NOT include a "project-template" pack entry in its packs section. The profile's `"packs"` object has: `field`, `compliance`, `template` (document templates), `clause`, `automation`, `request` — but NO `"project-template"` key. This is a gap in the profile definition AND a missing pack file.

Note: `"template": ["accounting-za"]` refers to document templates (engagement letters, invoices), not project templates with task lists.

## Fix
1. **Create the pack file** `backend/src/main/resources/project-template-packs/accounting-za.json` with the following templates and tasks (modelled after `legal-za.json`):

   - **Year-End Pack** (Annual Financial Statements): Trial balance review, Journal entries, Financial statement draft, Director approval, CIPC filing, Tax computation, Final package
   - **Monthly Bookkeeping**: Bank reconciliation, Creditors reconciliation, Debtors reconciliation, VAT calculation, Management accounts, Month-end close
   - **Tax Return — Individual**: Collect IRP5/IT3a, Medical aid certificates, Rental schedule, Retirement fund certificates, Prepare ITR12, SARS eFiling submission, Review & sign-off
   - **Tax Return — Company**: Collect trial balance, Tax computation, Provisional tax review, Prepare ITR14, Supporting schedules, SARS eFiling submission, Director sign-off
   - **VAT Return**: Collect invoices & receipts, VAT reconciliation, Prepare VAT201, SARS submission, Payment instruction

   Each template should include `name`, `namePattern`, `description`, `billableDefault`, `tasks` array with `name`, `description`, `priority`, `assigneeRole`, `billable`, `estimatedHours`.

2. **Register the pack** in the vertical profile: Add `"project-template": ["accounting-za"]` to the `packs` object in `backend/src/main/resources/vertical-profiles/accounting-za.json`.

3. **Verify the `VerticalProfileRegistry`** parses the `project-template` pack key and triggers `ProjectTemplatePackSeeder`. Check `PackReconciliationRunner` or provisioning flow to confirm the seeder is invoked.

## Scope
Backend / Seed data
Files to modify:
- `backend/src/main/resources/vertical-profiles/accounting-za.json` — add `"project-template"` pack reference
Files to create:
- `backend/src/main/resources/project-template-packs/accounting-za.json` — engagement template definitions with tasks
Migration needed: no (pack seeder runs at provisioning and reconciliation)

## Verification
Re-provision a fresh accounting-za tenant (or trigger pack reconciliation). Navigate to Settings > Engagement Templates. Verify at least 5 templates appear with pre-populated task lists.

## Estimated Effort
M (30 min - 2 hr) — mostly authoring the JSON pack content
