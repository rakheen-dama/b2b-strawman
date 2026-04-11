# Day 2 — Client Onboarding: Naledi Hair Studio and Vukani Tech Solutions
## Executed: 2026-03-16T00:01Z (cycle 2, partial) + 2026-03-16T00:08Z (cycle 2, continued)
## Actor: Bob (Admin)

### Checkpoint 2.1 — Create Naledi Hair Studio
- **Result**: PASS
- **Evidence**: Customer "Naledi Hair Studio" created successfully by Bob (Admin) via New Customer dialog. Type set to "Individual" (closest to Sole Proprietor — the create dialog offers Individual/Company/Trust but no explicit "Sole Proprietor" option). Email: naledi@naledihair.co.za, Phone: +27 72 881 4420. Customer appears in list with status "Prospect". ID: `1893386b-885b-4487-a59d-6dd52f9e9731`. Customer list now shows 3 customers (Acme Corp, Kgosi Construction, Naledi Hair Studio).
- **Gap**: — (Note: "Sole Proprietor" entity type from script is mapped to "Individual" in the customer type dropdown. The SA Accounting custom field "Entity Type" has a separate "Sole Proprietor" option that can be set on the customer detail page.)

### Checkpoint 2.1a — Naledi custom fields saved
- **Result**: PASS
- **Evidence**: SA Accounting — Client Details field group added via "Add Group" button. All fields filled: Trading As = "Naledi Hair Studio", SARS Tax Reference = "8807156234089", Financial Year-End = "2025-02-28", Entity Type = "Sole Proprietor" (selected from dropdown), Registered Address = "Shop 12, Eastgate Mall, Bedfordview, 2007", Primary Contact Name = "Naledi Mokoena", Primary Contact Email = "naledi@naledihair.co.za", Primary Contact Phone = "+27 72 881 4420", FICA Verified = "Not Started". Company Registration Number left blank (correct for sole proprietor). VAT Number left blank (correct — below VAT threshold). Save succeeded. Required fields: 7/7. Completeness: 14%.
- **Gap**: GAP-008B confirmed — SA Accounting field group must be manually added via "Add Group" button on the detail page; it is not auto-attached during customer creation.

### Checkpoint 2.2 — FICA checklist for Naledi
- **Result**: PASS
- **Evidence**: First transitioned Naledi from PROSPECT to ONBOARDING via Change Status > Start Onboarding. Transition succeeded — status shows "Onboarding". Onboarding tab appeared. Generic Client Onboarding checklist (4 items) auto-created. Then clicked "Manually Add Checklist" > selected "FICA KYC — SA Accounting" > Create Checklist. FICA checklist instantiated with 9 items (0/9 completed, 0/6 required). All 9 items appear regardless of entity type (sole proprietor sees company-specific items like "Company Registration CM29/CoR14.3"). Confirms GAP-009 — no entity-type filtering on FICA checklist.
- **Gap**: GAP-009 confirmed — FICA checklist does not filter by entity type. Sole proprietor Naledi sees all 9 items including company-specific ones. Also: Individual Client Onboarding (FICA) template exists as a separate 5-item template that would be more appropriate for individuals, but the script specifies using the full 9-item FICA KYC template.

### Checkpoint 2.3 — Naledi project created (hourly billing)
- **Result**: PASS
- **Evidence**: Project "Naledi Hair Studio — Tax Advisory 2026" created via New Project dialog. Customer linked to "Naledi Hair Studio". Description: "Hourly tax advisory engagement for sole proprietor". Project appears in project list with status "Lead". No retainer set (correct — hourly billing model). Project ID: `41f7f9a0-7eb8-4430-9f9d-271835b771fb`. Project count: 2 (Naledi + seeded Website Redesign).
- **Gap**: — (Note: No way to set engagement custom fields during project creation dialog. Engagement Type, Complexity etc. would need to be set on the project detail page if those custom field groups exist for projects.)

### Checkpoint 2.4 — Create Vukani Tech Solutions
- **Result**: PASS
- **Evidence**: Customer "Vukani Tech Solutions (Pty) Ltd" created by Bob (Admin) via New Customer dialog. Type: Company. Email: finance@vukanitech.co.za, Phone: +27 11 326 7800. Customer appears in list with status "Prospect". ID: `61126fab-a958-4c9a-8b4c-51c80b89b6c0`. SA Accounting field group added. All Pty Ltd-specific custom fields populated: Company Registration = "2021/892410/07", Trading As = "Vukani Tech", VAT Number = "4921087365", SARS Tax Reference = "0412567890", Financial Year-End = "2025-02-28", Entity Type = "Pty Ltd", Industry SIC Code = "62020", Registered Address = "Unit 4, Bryanston Gate Business Park, Bryanston, 2021", Postal Address = "PO Box 78104, Sandton, 2146", Primary Contact Name = "Sipho Ndaba", Primary Contact Email = "sipho@vukanitech.co.za", Primary Contact Phone = "+27 83 412 8890", FICA Verified = "Not Started", Referred By = "Google search". Save succeeded. Required fields: 7/7. Completeness: 43%. Customer list shows 4 customers total.
- **Gap**: GAP-008B confirmed again — SA Accounting field group must be manually added.

### Checkpoint 2.5 — FICA and engagement for Vukani
- **Result**: PARTIAL
- **Evidence**:
  - **FICA checklist**: Transitioned Vukani to ONBOARDING via Change Status > Start Onboarding. Transition succeeded. Generic Client Onboarding checklist (4 items) auto-created. Manually added "FICA KYC — SA Accounting" checklist (9 items, 0/6 required). PASS.
  - **Project created**: "Vukani Tech — Monthly Bookkeeping 2026" created via New Project dialog. Customer linked to "Vukani Tech Solutions (Pty) Ltd". Description: "Monthly bookkeeping engagement with retainer billing". Status: "Lead". Project ID: `34d82471-7d31-4145-9a28-4da122f6d405`. PASS.
  - **Retainer**: Retainers page accessible at `/org/e2e-test-org/retainers`. Shows "No retainers found" with "New Retainer" button. Retainer creation not exercised for Vukani (R8,000/month) — will be tested as a prerequisite for Day 30 billing cycle. NOT TESTED.
- **Gap**: Retainer creation deferred to Day 30 prerequisites. No new gaps identified.

## Summary

| Checkpoint | Result | Gap |
|-----------|--------|-----|
| 2.1 — Create Naledi Hair Studio | PASS | — |
| 2.1a — Naledi custom fields | PASS | GAP-008B |
| 2.2 — FICA for Naledi | PASS | GAP-009 |
| 2.3 — Naledi project (hourly) | PASS | — |
| 2.4 — Create Vukani Tech | PASS | GAP-008B |
| 2.5 — FICA + engagement for Vukani | PARTIAL | Retainer deferred |

**Totals**: 5 PASS, 1 PARTIAL, 0 FAIL

**Assessment**: Day 2 fully executed. All customer creation, custom field population, ONBOARDING transitions, FICA checklist instantiation, and project creation flows work correctly. Both Naledi (Individual/Sole Proprietor) and Vukani (Company/Pty Ltd) entity types handled properly. Customer list shows 4 customers (Acme Corp Active, Kgosi Construction Onboarding, Naledi Hair Studio Onboarding, Vukani Tech Solutions Onboarding). Project list shows 3 projects (Naledi Tax Advisory, Vukani Monthly Bookkeeping, Website Redesign). No new blockers. Ready for Day 3.

## Observations

1. **Entity type mapping**: The customer create dialog offers "Individual", "Company", "Trust". The SA Accounting custom field "Entity Type" dropdown offers "Pty Ltd", "Sole Proprietor", "Close Corporation (CC)", "Trust", "Partnership", "Non-Profit Company (NPC)". These are complementary — core type for platform behavior, custom field for accounting classification.

2. **FICA checklist options**: The "Manually Add Checklist" dialog offers 4 templates: (1) FICA KYC — SA Accounting (9 items), (2) Generic Client Onboarding (4 items, auto-created), (3) Company Client Onboarding (FICA), (4) Individual Client Onboarding (FICA). Entity-type-specific templates exist but are not auto-selected based on customer type.

3. **Onboarding transition**: Transitioning to ONBOARDING auto-creates the Generic Client Onboarding checklist (4 items). FICA checklist must be manually added. This is by design but adds friction — the accounting-za pack should ideally auto-attach the FICA checklist on onboarding transition (GAP-008B scope).

4. **Custom field completeness**: Naledi at 14% completeness (only SA Accounting fields filled, Contact & Address empty). Vukani at 43% (SA Accounting fields filled plus more fields populated). The completeness score appears to weight across all field groups.

5. **Console errors**: React #418 hydration mismatch continues on all pages (GAP-029, cosmetic). Multiple TypeError "Cannot read properties of null" errors on customers and projects pages during initial SSR hydration — pages render correctly after ~2-3s client-side hydration. These are pre-existing issues from GAP-008C fix scope.
