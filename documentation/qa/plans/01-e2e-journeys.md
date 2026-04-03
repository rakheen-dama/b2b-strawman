# Layer 1: End-to-End Business Journeys

**Product:** DocTeams — Multi-tenant B2B SaaS Practice Management Platform
**Date:** 2026-03-06
**Prepared by:** QA Team

---

## How to Read This Document

Each journey is a cross-domain scenario that exercises multiple features in sequence. Steps are numbered and linear. Verification checkpoints are marked with **CHECK:** and must be confirmed before proceeding.

**Conventions:**
- Actors are drawn from the [test team](00-overview.md#test-team) and [test customers](00-overview.md#test-customers)
- Currency is ZAR unless noted otherwise
- Rates use round numbers (R500/hr, R300/hr) for easy manual verification
- Time entries use 60-minute or 120-minute durations

---

## J1: New Client Onboarding to First Project to First Invoice

**Objective:** Validate the complete new-client lifecycle from prospect intake through first billable invoice, crossing customer lifecycle, project management, time tracking, and invoicing domains.

**Actors:** James Chen (Admin), Sofia Reyes (Member), Aiden O'Brien (Member), ben.finance@acmecorp.com (BILLING portal contact — repurposed here as a new customer contact)

**Domains Exercised:** Customer Lifecycle, Projects, Tasks, Time Tracking, Invoicing, Notifications, Audit, Customer Portal

**Preconditions:**
- Org billing rates configured (R500/hr default)
- At least one project template exists (e.g., "Standard Engagement")
- Document template for engagement letter exists

### Steps

1. **James** navigates to Customers and clicks "Add Customer".
2. **James** fills in customer details for "GlobalTech Partners" — company name, industry, primary contact email (portal-user@globaltech.com), phone, address.
3. **CHECK:** Customer is created with status PROSPECT. Customer appears in the customer list with PROSPECT badge.
4. **James** attempts to create a project for GlobalTech Partners.
5. **CHECK:** Lifecycle guard blocks project creation. Error message indicates PROSPECT customers cannot have projects.
6. **James** transitions GlobalTech Partners from PROSPECT to ONBOARDING.
7. **CHECK:** Customer status is now ONBOARDING. Onboarding checklist is generated with required items.
8. **James** completes all onboarding checklist items — KYC documents uploaded, engagement letter signed, billing details confirmed.
9. **CHECK:** Upon completing all checklist items, customer auto-transitions to ACTIVE. Status badge shows ACTIVE. Audit event recorded for each transition (PROSPECT to ONBOARDING, ONBOARDING to ACTIVE).
10. **James** creates a new project for GlobalTech Partners using the "Standard Engagement" template. Sets project name to "Annual Audit 2026", deadline to today+30.
11. **CHECK:** Project is created with status ACTIVE. Template-defined tasks, document structure, and custom fields are populated.
12. **James** assigns Sofia and Aiden as project members.
13. **CHECK:** Both members appear in the project member list. Notifications sent to Sofia and Aiden about project assignment.
14. **Sofia** navigates to My Work and sees "Annual Audit 2026" tasks.
15. **Sofia** moves task "Initial Review" to IN_PROGRESS, then logs 120 minutes of billable time against it with description "Preliminary document review".
16. **CHECK:** Time entry created with billable=true, billing status=UNBILLED, rate snapshot=R500/hr (org default).
17. **Aiden** logs 60 minutes of billable time on task "Data Collection" with description "Client data gathering call".
18. **CHECK:** Two time entries exist on the project. Project time rollup shows 180 minutes total.
19. **James** navigates to the project's Time tab and verifies unbilled time summary.
20. **James** clicks "Generate Invoice from Unbilled Time" for GlobalTech Partners.
21. **CHECK:** Invoice created in DRAFT status with two line items — Sofia 2.0hrs x R500 = R1,000 and Aiden 1.0hr x R500 = R500. Subtotal = R1,500. Time entries now marked BILLED.
22. **James** reviews the draft, adds a 15% VAT tax line, and approves the invoice.
23. **CHECK:** Invoice status transitions to APPROVED. Invoice number generated following org numbering sequence. Tax line shows R225. Total = R1,725.
24. **James** sends the invoice.
25. **CHECK:** Invoice status transitions to SENT. Notification triggered. Audit event logged for INVOICE_SENT.
26. Portal contact **portal-user@globaltech.com** opens the portal and views the invoice.
27. **CHECK:** Invoice is visible in the portal with correct line items, amounts, and total. Portal contact cannot edit or delete the invoice.

---

## J2: Proposal to Accepted to Project Kickoff to Retainer Setup

**Objective:** Validate the proposal-to-retainer pipeline — from drafting a retainer proposal through portal acceptance, automatic project creation, and opening the first retainer period.

**Actors:** Marcus Webb (Admin), Sofia Reyes (Member), alice.porter@acmecorp.com (PRIMARY portal contact)

**Domains Exercised:** Proposals, Customer Portal, Projects, Retainers, Notifications, Audit

**Preconditions:**
- Acme Corp exists and is ACTIVE
- alice.porter@acmecorp.com is configured as a PRIMARY portal contact for Acme Corp
- Org billing rates configured (R500/hr default)

### Steps

1. **Marcus** navigates to Proposals and clicks "New Proposal" for Acme Corp.
2. **Marcus** fills in proposal details — title: "Monthly Advisory Retainer", fee model: RETAINER, retainer amount: R15,000/month, allocated hours: 30, rollover policy: CARRY_FORWARD.
3. **Marcus** adds scope items: "Strategic consulting", "Monthly financial review", "Ad-hoc advisory support".
4. **CHECK:** Proposal created in DRAFT status. Fee model shows RETAINER with R15,000/month and 30 hours.
5. **Marcus** previews the proposal PDF.
6. **CHECK:** PDF renders correctly with all scope items, fee model details, and org branding.
7. **Marcus** sends the proposal to alice.porter@acmecorp.com.
8. **CHECK:** Proposal status transitions to SENT. Notification sent to Alice. Audit event logged for PROPOSAL_SENT.
9. **Alice** receives the portal link, authenticates via magic link, and views the proposal.
10. **CHECK:** Proposal is displayed in the portal with correct details. Accept and Decline buttons are visible.
11. **Alice** clicks "Accept" on the proposal.
12. **CHECK:** Proposal status transitions to ACCEPTED. Audit trail records acceptance with IP address and user agent. Notification sent to Marcus.
13. **CHECK:** A project is auto-created from the accepted proposal — project name matches proposal title, customer is Acme Corp, status is ACTIVE.
14. **CHECK:** A retainer agreement is auto-created — status ACTIVE, monthly amount R15,000, allocated hours 30, rollover policy CARRY_FORWARD.
15. **CHECK:** First retainer period is opened — status OPEN, period dates align with the current month, used hours = 0, remaining hours = 30.
16. **Marcus** navigates to the auto-created project and assigns Sofia as a project member.
17. **CHECK:** Sofia receives a notification about the project assignment.
18. **Sofia** logs 120 minutes of billable time on a retainer task.
19. **CHECK:** Retainer period's used hours updates to 2.0, remaining hours updates to 28.0. Time entry is linked to the retainer period.
20. **Marcus** views the retainer dashboard for Acme Corp.
21. **CHECK:** Dashboard shows 2.0 of 30.0 hours used (6.7%), R1,000 of R15,000 consumed, CARRY_FORWARD policy displayed.

---

## J3: Full Billing Cycle with Rate Overrides

**Objective:** Validate the 3-level rate hierarchy (org default, customer override, project override), rate snapshotting on time entries, budget tracking, and financial accuracy through invoice generation and profitability reporting.

**Actors:** Thandi Nkosi (Owner), Marcus Webb (Admin), Sofia Reyes (Member), Aiden O'Brien (Member)

**Domains Exercised:** Billing Rates, Cost Rates, Time Tracking, Budgets, Invoicing, Profitability Reports

**Preconditions:**
- Dunbar & Associates is ACTIVE with at least one project ("Tax Advisory Q2")
- Sofia and Aiden are assigned to the project
- No existing rate overrides for Dunbar & Associates

### Steps

1. **Thandi** navigates to Settings > Billing Rates and confirms the org default rate is R500/hr, effective from 2026-01-01.
2. **CHECK:** Org default rate is R500/hr. This is the fallback rate for all members.
3. **Marcus** navigates to Dunbar & Associates' rate settings and adds a customer-level rate override of R650/hr, effective today.
4. **CHECK:** Customer rate override saved. Rate hierarchy shows: Org R500 < Customer R650.
5. **Marcus** navigates to the "Tax Advisory Q2" project and adds a project-level rate override of R750/hr, effective today.
6. **CHECK:** Project rate override saved. Rate hierarchy shows: Org R500 < Customer R650 < Project R750.
7. **Thandi** navigates to Settings > Cost Rates and sets Sofia's cost rate to R300/hr and Aiden's cost rate to R250/hr, both effective today.
8. **CHECK:** Cost rates saved for both members.
9. **Marcus** configures a budget for "Tax Advisory Q2" — hours limit: 20, amount limit: R15,000, alert threshold: 75%.
10. **CHECK:** Budget created with status ON_TRACK. Hours used: 0/20, amount used: R0/R15,000.
11. **Sofia** logs 120 minutes (2.0 hrs) of billable time on a task in "Tax Advisory Q2".
12. **CHECK:** Time entry created with rate snapshot = R750/hr (project override, the highest-priority rate). Billing status = UNBILLED. Budget updates to 2.0/20 hours, R1,500/R15,000 amount.
13. **Aiden** logs 120 minutes (2.0 hrs) of billable time on a different task.
14. **CHECK:** Aiden's time entry also has rate snapshot = R750/hr. Budget updates to 4.0/20 hours, R3,000/R15,000 amount. Status remains ON_TRACK.
15. **Sofia** logs another 600 minutes (10.0 hrs) of billable time across multiple days.
16. **CHECK:** Budget updates to 14.0/20 hours (70%), R10,500/R15,000 (70%). Status still ON_TRACK (below 75% threshold).
17. **Aiden** logs 120 minutes (2.0 hrs) more.
18. **CHECK:** Budget updates to 16.0/20 hours (80%), R12,000/R15,000 (80%). Status transitions to AT_RISK (crossed 75% threshold). Notification triggered for budget alert.
19. **Sofia** logs 300 minutes (5.0 hrs) more, pushing past the budget limit.
20. **CHECK:** Budget updates to 21.0/20 hours (105%), R15,750/R15,000 (105%). Status transitions to OVER_BUDGET. Notification triggered for budget overrun.
21. **Marcus** generates an invoice from unbilled time for "Tax Advisory Q2".
22. **CHECK:** Invoice DRAFT created. Line items reflect: Sofia 17.0 hrs x R750 = R12,750; Aiden 4.0 hrs x R750 = R3,000. Subtotal = R15,750. All time entries marked BILLED.
23. **Marcus** approves and sends the invoice.
24. **CHECK:** Invoice status transitions DRAFT to APPROVED to SENT. Invoice number generated. Audit events logged for each transition.
25. **Marcus** marks the invoice as PAID with payment date = today.
26. **CHECK:** Invoice status = PAID. Audit event logged.
27. **Thandi** navigates to Profitability Reports and views the "Tax Advisory Q2" project report.
28. **CHECK:** Revenue = R15,750. Cost = (Sofia 17.0 hrs x R300 = R5,100) + (Aiden 4.0 hrs x R250 = R1,000) = R6,100. Margin = R9,650. Margin % = 61.3%. Budget shows OVER_BUDGET.
29. **Thandi** views the Dunbar & Associates customer-level profitability report.
30. **CHECK:** Customer profitability includes the "Tax Advisory Q2" project figures. Revenue, cost, and margin aggregate correctly.

---

## J4: Document Generation to Acceptance to Audit Trail

**Objective:** Validate the full document lifecycle from template customization through clause injection, PDF generation, portal-based acceptance, and immutable audit trail verification.

**Actors:** Priya Sharma (Admin), James Chen (Admin), alice.porter@acmecorp.com (PRIMARY portal contact)

**Domains Exercised:** Document Templates, Clauses, Document Generation, Document Acceptance, Customer Portal, Audit Events, Notifications

**Preconditions:**
- Acme Corp is ACTIVE with at least one project ("Advisory Services 2026")
- alice.porter@acmecorp.com is a PRIMARY portal contact
- Default template pack is seeded (includes engagement letter template)

### Steps

1. **Priya** navigates to Settings > Document Templates and locates the "Engagement Letter" template from the common pack.
2. **Priya** clones the template to create a customized version — new name: "Custom Engagement Letter".
3. **CHECK:** Cloned template created with a new slug. Content matches the original. Template status is ready for editing.
4. **Priya** edits the cloned template — updates header text, adds a payment terms section, modifies the signature block.
5. **Priya** navigates to Settings > Clauses and creates a new clause — name: "Limitation of Liability", content: standard liability limitation text, category: "Legal".
6. **CHECK:** Clause created and available for injection into templates.
7. **Priya** edits the "Custom Engagement Letter" template and injects the "Limitation of Liability" clause into the terms section.
8. **CHECK:** Template now includes the clause content in the correct position. Template preview renders the clause text.
9. **Priya** navigates to Settings > Branding (OrgSettings) and verifies/updates org logo, brand color, and footer text.
10. **CHECK:** Branding settings saved. Preview reflects updated branding.
11. **James** navigates to Acme Corp's project "Advisory Services 2026" and clicks "Generate Document".
12. **James** selects "Custom Engagement Letter" template. The context builder assembles data from the project and customer entities.
13. **CHECK:** HTML preview displays the generated document with: Acme Corp details populated, project name/dates filled, clause content rendered, org branding (logo, color, footer) applied.
14. **James** confirms generation. PDF is rendered via OpenHTMLToPDF and uploaded to S3.
15. **CHECK:** Generated document record created. PDF is downloadable. Document appears in the project's Documents tab and in the generated documents list. Audit event logged for DOCUMENT_GENERATED.
16. **James** initiates an acceptance request for the generated document, targeting alice.porter@acmecorp.com.
17. **CHECK:** Acceptance request created with status PENDING. Notification sent to Alice.
18. **James** sends the acceptance request.
19. **CHECK:** Acceptance status transitions to SENT. Alice receives a notification/email with the portal link.
20. **Alice** authenticates via magic link and navigates to the pending document in the portal.
21. **CHECK:** Document is visible. PDF can be viewed/downloaded. Accept and Decline buttons are present.
22. **Alice** views the document (opens the PDF viewer).
23. **CHECK:** Acceptance status transitions to VIEWED. Audit event logged with timestamp, IP address, and user agent.
24. **Alice** clicks "Accept" on the document.
25. **CHECK:** Acceptance status transitions to ACCEPTED. Acceptance certificate generated with: acceptance timestamp, portal contact identity, IP address, user agent, document hash.
26. **CHECK:** Notification sent to James about the acceptance. Audit event logged for DOCUMENT_ACCEPTED with full details.
27. **James** navigates to the document and views the acceptance audit trail.
28. **CHECK:** Audit trail shows the complete sequence: PENDING to SENT to VIEWED to ACCEPTED, with timestamps, actor, IP, and user agent for each transition. Trail is immutable — no edit or delete options.

---

## J5: Information Request to Portal Response to Review Cycle

**Objective:** Validate the information request workflow including creation from template, portal-based item submission, multi-round review (accept/reject), resubmission, and completion.

**Actors:** James Chen (Admin), Priya Sharma (Admin), alice.porter@acmecorp.com (PRIMARY portal contact)

**Domains Exercised:** Information Requests, Request Items, Request Templates, Customer Portal, Notifications, Audit

**Preconditions:**
- Acme Corp is ACTIVE
- alice.porter@acmecorp.com is a PRIMARY portal contact
- At least one request template exists (e.g., "KYC Document Pack")

### Steps

1. **James** navigates to Information Requests and clicks "New Request" for Acme Corp.
2. **James** selects the "KYC Document Pack" request template.
3. **CHECK:** Request created in DRAFT status. Template-defined request items are populated — e.g., "Company Registration Certificate", "Director ID Documents", "Proof of Address", "Tax Clearance Certificate".
4. **James** adds a custom request item: "Latest Annual Financial Statements".
5. **CHECK:** Request now has 5 items, all in PENDING status.
6. **James** adds a due date (today+14) and a note for the portal contact.
7. **James** sends the information request to alice.porter@acmecorp.com.
8. **CHECK:** Request status transitions to SENT. Notification sent to Alice. Audit event logged.
9. **Alice** authenticates via magic link and views the information request in the portal.
10. **CHECK:** All 5 request items are visible with PENDING status. Upload/submit controls are available for each item. Due date displayed.
11. **Alice** uploads a PDF for "Company Registration Certificate" and submits it.
12. **Alice** uploads a PDF for "Director ID Documents" and submits it.
13. **Alice** uploads a PDF for "Proof of Address" and submits it.
14. **CHECK:** Request status transitions to IN_PROGRESS (at least one item submitted). Three items now show SUBMITTED, two remain PENDING.
15. **Priya** reviews the submitted items. She accepts "Company Registration Certificate" (valid and complete).
16. **Priya** accepts "Director ID Documents" (valid).
17. **Priya** rejects "Proof of Address" with reason: "Document is older than 3 months. Please provide a recent utility bill."
18. **CHECK:** Item statuses: "Company Registration Certificate" = ACCEPTED, "Director ID Documents" = ACCEPTED, "Proof of Address" = REJECTED (with rejection reason visible), "Tax Clearance Certificate" = PENDING, "Latest Annual Financial Statements" = PENDING.
19. **CHECK:** Notification sent to Alice about the rejection with the reason.
20. **Alice** views the rejection reason in the portal.
21. **CHECK:** Rejection reason is displayed on the "Proof of Address" item. Item is re-openable for resubmission.
22. **Alice** uploads a new, recent proof of address document and resubmits.
23. **CHECK:** "Proof of Address" status returns to SUBMITTED. New document attached.
24. **Alice** uploads and submits "Tax Clearance Certificate" and "Latest Annual Financial Statements".
25. **CHECK:** All 5 items now have status SUBMITTED or ACCEPTED. No items remain PENDING.
26. **Priya** reviews and accepts the resubmitted "Proof of Address" — document is valid and recent.
27. **Priya** accepts "Tax Clearance Certificate" and "Latest Annual Financial Statements".
28. **CHECK:** All 5 items now have status ACCEPTED. Request status auto-transitions to COMPLETED.
29. **CHECK:** Notification sent to James and Alice confirming the request is complete. Audit events logged for each accept/reject action and the completion transition.
30. **James** views the completed request.
31. **CHECK:** All items show ACCEPTED with reviewer name and timestamp. Overall status is COMPLETED. No further submissions possible from the portal.

---

## J6: Budget Overrun to Alerts to Invoice Reconciliation

**Objective:** Validate budget monitoring from initial configuration through threshold alerts, overrun detection, profitability impact analysis, and invoice reconciliation against budget limits.

**Actors:** Marcus Webb (Admin), Sofia Reyes (Member), David Molefe (Member), Thandi Nkosi (Owner)

**Domains Exercised:** Budgets, Time Tracking, Notifications, Invoicing, Profitability Reports

**Preconditions:**
- Acme Corp is ACTIVE with a project "Compliance Review 2026" (status ACTIVE)
- Sofia and David are assigned to the project
- Org billing rate: R500/hr, cost rates: Sofia R300/hr, David R250/hr
- No existing budget on this project

### Steps

1. **Marcus** navigates to "Compliance Review 2026" and opens the Budget tab.
2. **Marcus** creates a budget — hours limit: 10, amount limit: R5,000, alert threshold: 80%.
3. **CHECK:** Budget created with status ON_TRACK. Hours: 0/10 (0%), Amount: R0/R5,000 (0%).
4. **Sofia** logs 120 minutes (2.0 hrs) of billable time on task "Policy Review".
5. **CHECK:** Budget updates to 2.0/10 hours (20%), R1,000/R5,000 (20%). Status remains ON_TRACK.
6. **David** logs 120 minutes (2.0 hrs) of billable time on task "Gap Analysis".
7. **CHECK:** Budget updates to 4.0/10 hours (40%), R2,000/R5,000 (40%). Status remains ON_TRACK.
8. **Sofia** logs 240 minutes (4.0 hrs) of billable time over two days.
9. **CHECK:** Budget updates to 8.0/10 hours (80%), R4,000/R5,000 (80%). Status transitions to AT_RISK (reached 80% threshold). Notification sent to Marcus (budget alert).
10. **Marcus** reviews the notification and the Budget tab.
11. **CHECK:** AT_RISK badge displayed. Notification content includes project name, current usage percentage, and budget limits.
12. **Sofia** logs 60 minutes (1.0 hr) more.
13. **CHECK:** Budget updates to 9.0/10 hours (90%), R4,500/R5,000 (90%). Status remains AT_RISK.
14. **David** logs 120 minutes (2.0 hrs) more, pushing past the budget limit.
15. **CHECK:** Budget updates to 11.0/10 hours (110%), R5,500/R5,000 (110%). Status transitions to OVER_BUDGET. Notification sent to Marcus (budget overrun).
16. **Sofia** logs 60 minutes (1.0 hr) of non-billable time on task "Internal Debrief".
17. **CHECK:** Non-billable time entry created with billing status = NON_BILLABLE. Budget hours update to 12.0/10 (total hours including non-billable). Billable amount remains R5,500 (non-billable time does not affect amount budget).
18. **Thandi** navigates to Profitability Reports and views "Compliance Review 2026".
19. **CHECK:** Revenue (unbilled) = R5,500 (11.0 billable hrs x R500). Cost = (Sofia 7.0 hrs x R300 = R2,100) + (David 4.0 hrs x R250 = R1,000) + (Sofia 1.0 non-billable hr x R300 = R300) = R3,400. Margin = R2,100. Budget indicator shows OVER_BUDGET.
20. **Marcus** generates an invoice from unbilled time for "Compliance Review 2026".
21. **CHECK:** Invoice DRAFT created. Only billable time entries included (11.0 hrs). Non-billable time excluded. Subtotal = R5,500. All billable time entries marked BILLED.
22. **Marcus** adds a note to the invoice: "Budget exceeded by 1.0 hour — approved by client".
23. **Marcus** approves and sends the invoice.
24. **CHECK:** Invoice status = SENT. Audit events logged. Invoice total (R5,500) exceeds original budget amount (R5,000) — this is informational, not blocked.
25. **Marcus** marks the invoice as PAID.
26. **Thandi** re-checks profitability report.
27. **CHECK:** Revenue now shows R5,500 (paid). Cost = R3,400. Realized margin = R2,100 (38.2%). Report reflects paid invoice status.

---

## J7: Customer Dormancy to Reactivation to Offboarding

**Objective:** Validate the full customer lifecycle tail — dormancy detection for an inactive customer, reactivation through new work, and the complete offboarding process with restricted operations.

**Actors:** James Chen (Admin), Thandi Nkosi (Owner), Yuki Tanaka (Member), carol.ops@dunbar.com (GENERAL portal contact)

**Domains Exercised:** Customer Lifecycle, Projects, Lifecycle Guards, Customer Portal, Notifications, Audit, Compliance

**Preconditions:**
- Echo Ventures exists with status DORMANT (no active projects, no recent activity)
- carol.ops@dunbar.com is set up for portal testing (used here to verify portal access revocation pattern)
- Org has standard billing rates configured

### Steps

1. **James** navigates to Customers and locates Echo Ventures.
2. **CHECK:** Echo Ventures shows status DORMANT. Last activity date is stale. No active projects listed.
3. **James** attempts to create an invoice for Echo Ventures.
4. **CHECK:** Operation is permitted (DORMANT customers can still be invoiced for past work if applicable), or lifecycle guard blocks it if the org has configured stricter dormancy rules. Verify behavior matches configured lifecycle guard rules.
5. **James** decides to reactivate Echo Ventures. He creates a new project: "Strategic Review Q3", deadline today+60.
6. **CHECK:** Customer status transitions from DORMANT to ACTIVE (reactivated by new project creation). Audit event logged for CUSTOMER_REACTIVATED. Notification sent to relevant admins.
7. **James** assigns Yuki as a project member.
8. **CHECK:** Yuki receives a notification. Yuki can see "Strategic Review Q3" in My Work.
9. **Yuki** creates tasks, logs 120 minutes of billable time, and adds a comment.
10. **CHECK:** All operations succeed for ACTIVE customer. Time entry, tasks, and comment created successfully.
11. **Yuki** completes all tasks. **James** transitions the project to COMPLETED.
12. **CHECK:** Project status = COMPLETED. Tasks are finalized.
13. **James** archives the project.
14. **CHECK:** Project status = ARCHIVED. Project is no longer editable but remains visible in project list with ARCHIVED filter.
15. **Thandi** initiates offboarding for Echo Ventures. Transitions status from ACTIVE to OFFBOARDING.
16. **CHECK:** Customer status = OFFBOARDING. Audit event logged. Notification sent to admins.
17. **James** attempts to create a new project for Echo Ventures (now OFFBOARDING).
18. **CHECK:** Lifecycle guard blocks project creation. Error message indicates OFFBOARDING customers cannot have new projects.
19. **James** attempts to create an invoice for past unbilled time.
20. **CHECK:** Invoice creation is permitted during OFFBOARDING (settling outstanding balances is part of offboarding).
21. **James** generates and sends the final invoice.
22. **CHECK:** Invoice created and sent successfully. Audit trail records the invoice actions.
23. **Thandi** completes the offboarding process — transitions Echo Ventures to OFFBOARDED.
24. **CHECK:** Customer status = OFFBOARDED. Audit event logged for CUSTOMER_OFFBOARDED.
25. **James** attempts to create a project for Echo Ventures (now OFFBOARDED).
26. **CHECK:** Lifecycle guard blocks project creation. OFFBOARDED customers cannot have any new work.
27. **James** attempts to create a task, time entry, or invoice for Echo Ventures.
28. **CHECK:** All create operations blocked by lifecycle guard. Existing data (projects, invoices, documents) remains accessible in read-only mode.

---

## J8: Multi-Role Collaboration on a Project

**Objective:** Validate role-based access control (Owner/Admin/Member) in the context of a real project workflow, ensuring Members can perform their work, Admins can manage, Owners can access org-level features, and unauthorized actions are properly blocked.

**Actors:** Thandi Nkosi (Owner), Priya Sharma (Admin), Sofia Reyes (Member), Lerato Dlamini (Member)

**Domains Exercised:** RBAC, Projects, Tasks, Time Tracking, Comments, Notifications, Budgets, Document Generation, Profitability Reports, Settings

**Preconditions:**
- Acme Corp is ACTIVE
- Org billing rates and cost rates are configured
- Document template exists for generation
- Priya, Sofia, and Lerato are org members

### Steps

1. **Thandi** navigates to Settings > Billing Rates and updates the org default rate to R550/hr.
2. **CHECK:** Rate updated. Only Owners can modify org-level settings.
3. **Thandi** navigates to Settings > Branding and updates the org footer text.
4. **CHECK:** Branding updated successfully.
5. **Priya** creates a new project for Acme Corp: "Year-End Compliance 2026", deadline today+45.
6. **CHECK:** Project created with status ACTIVE. Admins can create projects.
7. **Priya** configures a budget — hours limit: 40, amount limit: R22,000, alert threshold: 75%.
8. **CHECK:** Budget created. Admins can configure budgets.
9. **Priya** assigns Sofia and Lerato as project members.
10. **CHECK:** Both members receive assignment notifications. Project appears in their My Work views.
11. **Sofia** creates a task: "Review financial statements", assigns herself, moves it to IN_PROGRESS.
12. **CHECK:** Member can create tasks and self-assign within their assigned project.
13. **Lerato** creates a task: "Prepare compliance checklist", assigns herself.
14. **Sofia** logs 120 minutes (2.0 hrs) of billable time on her task.
15. **CHECK:** Time entry created with rate snapshot = R550/hr. Budget updates. Sofia can log time on project tasks.
16. **Lerato** logs 60 minutes (1.0 hr) of billable time on her task.
17. **Sofia** adds a comment on Lerato's task: "Can you also include the updated tax forms?" with visibility INTERNAL.
18. **CHECK:** Comment created with INTERNAL visibility. Both Sofia and Lerato can see it. Portal contacts cannot.
19. **Lerato** replies to Sofia's comment (threaded reply via parentId).
20. **CHECK:** Reply appears as a threaded comment under Sofia's original. Notification sent to Sofia.
21. **Sofia** attempts to configure the project budget.
22. **CHECK:** Action blocked. Members cannot modify budget settings. Appropriate error or UI restriction shown.
23. **Sofia** attempts to access Settings > Billing Rates.
24. **CHECK:** Action blocked. Members cannot access org-level rate settings.
25. **Lerato** attempts to generate an invoice.
26. **CHECK:** Action blocked. Members cannot generate invoices. Only Admins and Owners can.
27. **Priya** generates a document from template for the project.
28. **CHECK:** Document generated successfully. Admins can generate documents. PDF rendered with org branding.
29. **Priya** reviews unbilled time and generates an invoice.
30. **CHECK:** Invoice DRAFT created. Line items: Sofia 2.0 hrs x R550 = R1,100, Lerato 1.0 hr x R550 = R550. Subtotal = R1,650.
31. **Thandi** navigates to Profitability Reports (org-level).
32. **CHECK:** Owner can access org-level profitability reports. "Year-End Compliance 2026" appears with correct revenue/cost/margin. Members (Sofia, Lerato) cannot access this page.
33. **Thandi** views the notifications page.
34. **CHECK:** Thandi has received notifications for: project creation (by Priya), budget configuration, invoice generation. All actors see only the notifications relevant to their role and involvement.
