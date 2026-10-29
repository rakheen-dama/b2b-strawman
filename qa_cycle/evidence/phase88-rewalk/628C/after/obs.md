# 628C observe — AFTER (PR #1705 head 918a698d6, existing tenant `tenant_7d218705360b`, e2e stack :3001/:8081, 2026-10-28 23:47–23:57Z, 1440x900)

Procedure: firm edit made through the UI on the main-built stack (pre-628C), then `docker compose -f compose/docker-compose.e2e.yml up -d --build --no-deps backend frontend` from `/Users/rakheendama/Projects/2026/worktree-epic-628C` (exit 0, `/tmp/628C/obs-rebuild.log`; no e2e-down, the tenant was kept). Frontend container bundle contains `optionLabels` (R-4). Backend healthy at 23:52Z.

## Firm-edit setup
Settings › Matters › Custom fields › Field Groups › "Project Info" › Edit: description set to `Firm-edited 628C description`, name unchanged (00a, 00b). DB before the rebuild: `project_info | Project Info | Firm-edited 628C description | 23:50:19`. The backend `PUT /api/field-groups/{id}` ignores `fieldDefinitionIds` (`FieldGroupService.update`), so group membership was not touched.

## Checks

| # | Check | Expected | Observed | Result | Evidence |
|---|---|---|---|---|---|
| O1 | Rebuild from PR worktree | backend+frontend built from 918a698d6, healthy | both `Built`, exit 0, backend UP, FE chunk contains `optionLabels` | PASS | obs-rebuild.log |
| O2 | Reconcile ran for the existing tenant | Runner: 1 tenant, 0 failed | "checked 1 tenants, 1 succeeded, 0 failed" | PASS | reconcile-messages.txt |
| O3 | FIELD reconcile | gate v2→v3 / v1→v2 / v2→v3, APPLIED lines | common-project v2→v3: group name APPLIED, category description APPLIED; conveyancing-za-project v1→v2 and legal-za-project v2→v3: group description APPLIED | PASS | reconcile-messages.txt |
| O4 | Firm edit survives | "Reconcile skipped (edited)" for the project_info description; DB name=Matter Info, description=firm text | log: `field_group 'project_info' field description differs from the shipped value — left alone`; DB `Matter Info / Firm-edited 628C description`; UI shows both | PASS | reconcile-messages.txt, sql.txt A2, 01 |
| O5 | DOCUMENT_TEMPLATE reconcile | common v3→v4, name+description APPLIED, bodies refreshed | 4 APPLIED (name+desc × project-summary, invoice-cover-letter); installer "0 new, 3 refreshed, 0 re-pinned"; no per-entry `content` APPLIED line (bodies came in through the installer refresh path); DB content text nodes have 0 capitalised legacy words | PASS | reconcile-messages.txt, sql.txt A7/A7c |
| O6 | CLAUSE reconcile | legal-za-clauses 1→2, standard-clauses 1→2 | 3 body APPLIED (legal-za-fees-hourly, payment-terms, fee-schedule); text-node scan: only `fica-compliance` "customer due diligence" (FICA, allow-listed) | PASS | reconcile-messages.txt, sql-clause.txt |
| O7 | REPORT reconcile | standard-reports 1→2 | 13 APPLIED (timesheet desc/params/body; invoice-aging and project-profitability name/desc/params/cols/body); template_body legacy-word scan 11 rows → 0 | PASS | reconcile-messages.txt, sql.txt A4–A4d |
| O8 | AUTOMATION reconcile | automation-common 3→4 | 10 APPLIED (name, description, action config text for both rules); "Re-pinned content hash of 2 rules" | PASS | reconcile-messages.txt, sql.txt A6/A6b |
| O9 | pack_install versions | common-project 3, legal-za-project 3, conveyancing-za-project 2, common 4, automation-common 4, standard-reports 2, legal-za-clauses 2, standard-clauses 2 | exactly those; others unchanged (legal-za 9, conveyancing-za-clauses 2, …) | PASS | sql.txt A1 |
| O10 | timesheet parameter_schema has optionLabels | `optionLabels` present, value still `project` | present; UI submits `groupBy:"project"` | PASS | sql.txt A4b, network request body |
| O11 | Custom fields (Matters tab) UI | Matter Info + legal descriptions | Category "Matter category or type"; groups "Matter Info / Firm-edited 628C description", "…property transfer matters (matter type Conveyancing)", "South African legal-specific fields for matters"; 0 legacy words in panel | PASS | 01 |
| O12 | Reports list | names, descriptions, "Matter" category heading | "Fee Note Aging Report / Outstanding fee notes grouped by age bucket."; heading "Matter" → "Matter Profitability Report / Revenue vs. cost per matter for a date range."; Timesheet "…grouped by member, matter, or date…" | PASS | 02 |
| O13 | Timesheet params + Group By | label "Matter"; options Member/Matter/Date; run grouped by Matter works | param label "Matter"; listbox `Member, Matter, Date`; run → 1 row grouped by matter name, no error; request body `groupBy:"project"` | PASS | 03a, 03b |
| O14 | Timesheet PDF | prints the label | "Grouped by: matter" (lowercase by design: template maps `project`→`'matter'`, same case as `member`/`date`) | PASS | timesheet-report.pdf.txt, 03c |
| O15 | Matter Profitability page | params Matter/Client; cols Matter/Client; breadcrumb | h1 + last crumb "Matter Profitability Report"; params "Matter","Client"; columns "Matter","Client"; run OK | PASS | 04 |
| O16 | Matter Profitability exports | headers say Matter/Client | CSV `# Matter Profitability Report` / `Matter,Client,…`; PDF title "Matter Profitability Report", headers `MATTER CLIENT …` | PASS | project-profitability-report.csv/.pdf(.txt), 05c |
| O17 | Fee Note Aging page | params Client; cols Fee Note #/Client; breadcrumb | h1 + last crumb "Fee Note Aging Report"; param "Client"; columns "Fee Note #","Client"; run OK (1 row) | PASS | 05 |
| O18 | Fee Note Aging exports | headers say Fee Note/Client | CSV `Fee Note #,Client,…`; PDF title "Fee Note Aging Report", `FEE NOTE # CLIENT`, bucket counts "N fee notes" | PASS | invoice-aging-report.csv/.pdf(.txt), 05b |
| O19 | Settings › Documents templates | Matter Summary Report, Fee Note Cover Letter | "Fee Note Cover Letter / Cover letter for fee notes"; "Matter Summary Report / Matter summary report template" | PASS | 06 |
| O20 | Matter › Generate Document menu | "Matter Summary Report" | present; preview shows "Matter Summary Report", "Client:", "Matter Lead:" | PASS | 09, 09b |
| O21 | Automations list | new names + descriptions | "Fee Estimate Alert Escalation / Notifies admins when a matter fee estimate threshold is reached"; "Overdue Fee Note Reminder / Sends email to the client when a fee note becomes overdue, then notifies the matter lead after 7 days"; 0 legacy words in panel | PASS | 08 |
| O22 | Request templates (were clean) | still clean | 0 hits UI + DB | PASS | 07, sql.txt A5 |
| O23 | Backend errors | no ERROR/500 | 0 ERROR lines; WARNs are startup / font fallback / LibreOffice-absent only | PASS | backend-full.log |

## The 23 BEFORE hits

| # | BEFORE string | AFTER | Status |
|---|---|---|---|
| 1 | `Project category or type` | `Matter category or type` | fixed (O11) |
| 2 | `Project Info` (group name) | `Matter Info` | fixed (O4/O11) |
| 3 | `Common project metadata fields` | `Firm-edited 628C description` | intentionally left alone: firm edit preserved (O4). The shipped v3 value is reached only on unedited tenants |
| 4 | conveyancing group description `(projects with matter_type = CONVEYANCING)` | `…(matter type Conveyancing)` | fixed |
| 5 | `…for matter (project) entities` | `…for matters` | fixed |
| 6 | `Invoice Aging Report` | `Fee Note Aging Report` (list, h1, crumb, CSV, PDF) | fixed |
| 7 | `Outstanding invoices grouped by age bucket.` | `Outstanding fee notes grouped by age bucket.` | fixed |
| 8 | `Project Profitability Report` | `Matter Profitability Report` (list, h1, crumb, CSV, PDF) | fixed |
| 9 | `Revenue vs. cost per project…` | `…per matter…` | fixed |
| 10 | `…by member, project, or date…` | `…by member, matter, or date…` | fixed |
| 11 | category heading `Project` | `Matter` | fixed (Java label map, R-10) |
| 12 | param label `Project` | `Matter` (timesheet, profitability) | fixed |
| 13 | param label `Customer` | `Client` (profitability, aging) | fixed |
| 14 | Group By option `project` | `Matter` (value still `project`) | fixed (R-4) |
| 15 | cols `PROJECT`/`CUSTOMER` | `MATTER`/`CLIENT` | fixed |
| 16 | cols `INVOICE #`/`CUSTOMER` | `FEE NOTE #`/`CLIENT` | fixed |
| 17 | `Project Summary Report` | `Matter Summary Report` (settings + Generate menu) | fixed |
| 18 | `Project summary report template` | `Matter summary report template` | fixed |
| 19 | `Invoice Cover Letter` | `Fee Note Cover Letter` | fixed |
| 20 | `Cover letter for invoices` | `Cover letter for fee notes` | fixed |
| 21 | `Notifies admins when a project budget threshold is reached` | `…a matter fee estimate threshold is reached` | fixed (R-8) |
| 22 | `Overdue Invoice Reminder` | `Overdue Fee Note Reminder` | fixed |
| 23 | `Sends email to customer when invoice becomes overdue, then notifies project owner…` | `Sends email to the client when a fee note becomes overdue, then notifies the matter lead…` | fixed |

Latent §B items were checked in the DB too: the action configs now read "Fee note … is overdue", "Your fee note …", "Matter {{project.name}} has reached its fee estimate threshold." Report template bodies have 0 legacy words. §C email HTML was not observed: no emails were triggered in this run. It is classpath HTML, so it needs no reconcile.

## Notes (not 628C failures)
- N1: the `org_settings.*_pack_status` jsonb for FIELD, REPORT and CLAUSE still holds the old versions (field [2,2,1,2,2], report [1], clause [2,1,1]). TEMPLATE and AUTOMATION were updated (common 4, automation-common 4). `pack_install` is the ledger the gate reads. The jsonb is only the OQ #14 backfill source when no `pack_install` row exists (`AbstractPackSeeder.appliedVersion`), so this has no functional effect here. It is shared-seeder behaviour, and R-16 puts it outside 628C's scope.
- N2: the frontend log has 4× `Failed to find Server Action` between 23:52:20 and 23:53:31Z. These came from the browser tab still open from the pre-rebuild build (deployment skew) and stopped once the page was reloaded.
- N3 (pre-existing, unrelated): a console error on the field-group dialog: slug `pattern="^[a-z][a-z0-9_-]*$"` is invalid under the `v` flag. The Fee Note Aging summary cards show raw keys (`Bucket1_30Count`…). The PDF says "1 fee notes" (singular not handled; it was "N invoices" before).
