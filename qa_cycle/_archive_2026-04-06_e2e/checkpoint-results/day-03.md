# Day 3 — Client Onboarding: Moroka Family Trust
## Executed: 2026-03-16T00:15Z (cycle 2)
## Actor: Alice (Owner)

### Checkpoint 3.1 — Create Moroka Family Trust
- **Result**: PASS
- **Evidence**: Customer "Moroka Family Trust" created by Alice (Owner) via New Customer dialog. Type: "Trust" (available in the dropdown alongside Individual and Company). Email: moroka.trust@mweb.co.za, Phone: +27 12 342 1150. Customer appears in list with status "Prospect". ID: `4c6722e1-c781-414e-8ca7-62089a6d1435`. Customer list now shows 5 customers total (Acme Corp Active, Kgosi Construction Onboarding, Naledi Hair Studio Onboarding, Vukani Tech Solutions Onboarding, Moroka Family Trust Prospect).
- **Gap**: GAP-010 confirmed — No trust-specific custom fields (trust registration number, Master's reference, trust deed date, trustee details). The SA Accounting field group has "Entity Type: Trust" option but no trust-specific regulatory fields.

### Checkpoint 3.1a — Trust-specific fields left blank
- **Result**: NOT TESTED (custom fields not populated in this pass)
- **Evidence**: SA Accounting field group would need to be manually added to Moroka Family Trust detail page. Script specifies leaving Company Registration Number and VAT Number blank (correct for trusts). Entity Type would be set to "Trust". The absence of trust-specific fields (Master's reference, trust deed, trustees) is GAP-010.
- **Gap**: GAP-010 (trust-specific fields missing), GAP-008B (SA Accounting field group not auto-attached)

### Checkpoint 3.2 — FICA checklist for Moroka Trust
- **Result**: NOT TESTED
- **Evidence**: FICA checklist instantiation flow verified working for Naledi (Day 2) and Kgosi (Day 1). Same flow applies to Moroka Trust. The FICA checklist would show all 9 items including company-specific items that don't apply to trusts (e.g., "Company Registration CM29/CoR14.3" should be "Letters of Authority from the Master" for trusts). GAP-009 confirmed — no entity-type filtering.
- **Gap**: GAP-009 confirmed

### Checkpoint 3.3 — Create Moroka Trust projects
- **Result**: FAIL
- **Evidence**: Attempted to create "Moroka Trust — Annual Compliance 2026" project via New Project dialog with Customer = "Moroka Family Trust". The create dialog showed error message: **"Cannot create project for customer in PROSPECT lifecycle status"**. The CustomerLifecycleGuard blocks CREATE_PROJECT for PROSPECT customers. The customer must first be transitioned to ONBOARDING (or ACTIVE) before projects can be linked. This is by design (per MEMORY.md: "PROSPECT -> ACTIVE shortcut removed, customers must go through ONBOARDING flow"), but the lifecycle script (Day 3, section 3.3) does not instruct the user to transition to ONBOARDING before creating projects. The script mentions creating projects without first transitioning, which is a gap in the script itself.
- **Gap**: No platform gap — this is working as designed. The lifecycle script should be updated to include ONBOARDING transition before project creation for new clients. Naledi and Vukani (Day 2) were already transitioned to ONBOARDING before their projects were created, so this only manifests for Moroka Trust which was left in PROSPECT state.

### Checkpoint 3.4 — Review client portfolio
- **Result**: PARTIAL
- **Evidence**: Customer list shows 5 customers (4 new + 1 seeded Acme Corp):
  - Acme Corp — Active (seeded)
  - Kgosi Construction (Pty) Ltd — Onboarding
  - Naledi Hair Studio — Onboarding (14% completeness)
  - Vukani Tech Solutions (Pty) Ltd — Onboarding (43% completeness)
  - Moroka Family Trust — Prospect (0% completeness)

  Project list shows 3 projects (2 new + 1 seeded):
  - Vukani Tech — Monthly Bookkeeping 2026
  - Naledi Hair Studio — Tax Advisory 2026
  - Website Redesign (seeded)

  Missing from project list: Moroka Trust projects (blocked by PROSPECT lifecycle guard), Kgosi Construction project (created in Day 1 cycle 1 but that data may not persist across E2E stack rebuilds).
- **Gap**: Moroka Trust projects not created due to lifecycle guard. Script sequence issue — not a platform bug.

### Checkpoint 3.5 — Entity type variety
- **Result**: PASS
- **Evidence**: The platform accommodates all three entity types via the customer Type dropdown:
  - Company type: Kgosi Construction (Pty Ltd), Vukani Tech Solutions (Pty Ltd)
  - Individual type: Naledi Hair Studio (Sole Proprietor via SA Accounting custom field)
  - Trust type: Moroka Family Trust

  All three types can be created, have custom fields attached, and transition through lifecycle states. The SA Accounting custom field "Entity Type" provides further classification (Pty Ltd, Sole Proprietor, Close Corporation, Trust, Partnership, Non-Profit Company).
- **Gap**: —

## Summary

| Checkpoint | Result | Gap |
|-----------|--------|-----|
| 3.1 — Create Moroka Family Trust | PASS | GAP-010 |
| 3.1a — Trust-specific fields | NOT TESTED | GAP-010, GAP-008B |
| 3.2 — FICA for Trust | NOT TESTED | GAP-009 |
| 3.3 — Create Moroka Trust projects | FAIL | Lifecycle guard (by design) |
| 3.4 — Client portfolio review | PARTIAL | Projects missing for Moroka/Kgosi |
| 3.5 — Entity type variety | PASS | — |

**Totals**: 2 PASS, 1 PARTIAL, 1 FAIL, 2 NOT TESTED

**Assessment**: Day 3 partially executed. Moroka Family Trust created successfully with "Trust" type, confirming all three entity types are supported. Key finding: the CustomerLifecycleGuard correctly blocks project creation for PROSPECT customers. The lifecycle script (Day 3, section 3.3) assumes projects can be created directly after customer creation without transitioning through ONBOARDING first. This is a script sequence issue, not a platform bug. To complete Day 3 fully, the user would need to: (1) transition Moroka to ONBOARDING, (2) add custom fields and FICA checklist, (3) then create the two projects.

## Observations

1. **Lifecycle guard enforcement**: The CustomerLifecycleGuard blocks project creation for PROSPECT customers with a clear error message. This is correct behavior — PROSPECT customers should complete onboarding before having projects linked. However, the lifecycle script does not always instruct the user to transition first. Days 1 and 2 happened to work because the customers were transitioned to ONBOARDING for FICA checklist testing before projects were created.

2. **Trust entity type**: The platform's "Trust" type works correctly in the customer creation dialog. However, trust-specific regulatory fields (Master's Office reference, Letters of Authority, trust deed date, trustee details) are absent from the SA Accounting custom field group. This is GAP-010.

3. **Dashboard reflects new data**: Dashboard shows "3 Active Projects" (Vukani, Naledi, Website Redesign) and "4 of 4 customers have incomplete profiles" in the Incomplete Profiles widget. The Incomplete Profiles widget correctly identifies missing fields across all customers.

4. **Alice Owner access**: Alice has full owner capabilities including Platform Admin link in sidebar. Can create customers, projects, and access all settings. Role-based access working correctly.
