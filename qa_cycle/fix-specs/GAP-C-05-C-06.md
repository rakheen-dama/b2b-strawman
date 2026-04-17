# Fix Spec: GAP-C-05 + GAP-C-06 — Seeded rates unreachable + cost_rates never seeded

## Problem

Combined root cause: two gaps, one mechanism.

**GAP-C-05**: `rate-pack-consulting-za.json` seeds 8 billing_rates at the expected ZAR amounts (R1800 Creative Director → R600 Producer) but every row has `memberId = NULL, projectId = NULL, customerId = NULL` — i.e., scope `ORG_DEFAULT`. The Settings > Rates & Currency UI then shows only member-scoped rows ("Zolani Dube — Not set") and none of the 8 role-keyed rates surface. More critically, the `LogTimeDialog` on every time entry flashes "No rate card found for this combination. This time entry will have no billable rate." for all three users (Zolani/Bob/Carol) — confirmed in Day 3.1, 4.1, 6.1 evidence. Time entries save but carry no billable amount. This will produce a zero-value invoice on Day 36, zero revenue on Day 34 profitability, and zero margin everywhere.

**GAP-C-06**: `tenant_2a96bc3b208b.cost_rates` is empty after consulting-za profile seeding. The rate pack seeder populates `billing_rates` only; nothing seeds `cost_rates` at all. Without cost rates, no margin can ever be computed.

## Root Cause (confirmed via grep)

### Why seeded billing rates are unreachable

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/RatePackSeeder.java` lines 82–100 construct every `BillingRate` with `new BillingRate(null, null, null, currency, hourlyRate, ...)` — all three scope IDs null.

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateService.java::resolveRate()` lines 81–116 cascade through `PROJECT_OVERRIDE → CUSTOMER_OVERRIDE → MEMBER_DEFAULT`. There is **no `ORG_DEFAULT` branch**. The `BillingRate.getScope()` helper (`BillingRate.java:86–97`) acknowledges the `"ORG_DEFAULT"` scope string exists but nothing reads it.

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateRepository.java` — every finder method requires `br.memberId = :memberId`. No finder exists for org-default rates with null memberId.

Consequence: the 8 seeded rows are dead data. The UI can't surface them (no role column on BillingRate), and the resolver can't use them (resolver requires member-scoped rows).

### Why cost_rates is empty

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/RatePackSeeder.java` — only writes to `BillingRateRepository`. Never touches `CostRateRepository`.

File: `backend/src/main/resources/rate-packs/consulting-za.json` — contains only `rates` array (billing); no `costRates` field.

File: `backend/src/main/resources/vertical-profiles/consulting-za.json` lines 16–28 DOES define `rateCardDefaults.billingRates` (3 entries: Owner R1800 / Admin R1200 / Member R750) and `rateCardDefaults.costRates` (Owner R850 / Admin R550 / Member R375). But `VerticalProfileRegistry.java` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileRegistry.java` lines 61–88) does NOT parse `rateCardDefaults` — it reads only `profileId`, `name`, `description`, `enabledModules`, `terminologyOverrides`, `currency`, `packs`. `rateCardDefaults` is dead JSON.

Critical observation: the profile JSON already encodes a **role-aligned** set of rates (Owner/Admin/Member — matches the `OrgRole.slug` domain). Using it is strictly cleaner than picking from the 8 descriptive-keyed rate-pack rows.

## Fix (incremental — seed MEMBER_DEFAULT rates on JIT member creation)

The cleanest incremental fix that unblocks Day 8–36 without redesigning the rate-card data model:

1. Extend `VerticalProfileRegistry.ProfileDefinition` with a `rateCardDefaults` field (parse the `rateCardDefaults` block from the profile JSON).

   File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileRegistry.java`

   - Add a nested record inside the class:
     ```java
     public record RateCardDefaults(
         String currency,
         List<RoleRate> billingRates,
         List<RoleRate> costRates) {}
     public record RoleRate(String roleName, BigDecimal hourlyRate) {}
     ```
   - Add `RateCardDefaults rateCardDefaults` as the 8th positional field of `ProfileDefinition`.
   - In the constructor loop (around line 86), parse `root.path("rateCardDefaults")` into a `RateCardDefaults` (null-safe: if absent, pass null).
   - Map the role names in JSON (`Owner`, `Admin`, `Member`) to `Roles.ORG_OWNER` / `Roles.ORG_ADMIN` / `Roles.ORG_MEMBER` slug values when consumed (step 2).

2. On JIT member creation, seed a `MEMBER_DEFAULT` billing rate AND a cost rate for the newly-created member at the role-matching amounts.

   File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java`

   - After `memberRepository.save(member)` on line 189 but BEFORE `return new MemberInfo(...)` on line 208, inject a new helper call:
     ```java
     memberRateSeedingService.seedDefaultRatesIfMissing(member);
     ```
   - Inject a new collaborator `MemberRateSeedingService` via the constructor.

3. Create `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberRateSeedingService.java`:
   - Inject `OrgSettingsRepository`, `VerticalProfileRegistry`, `BillingRateRepository`, `CostRateRepository`.
   - Method `seedDefaultRatesIfMissing(Member m)`:
     1. Load `OrgSettings` for the current tenant. If `vertical_profile` is null, return.
     2. Look up the profile's `rateCardDefaults` via `VerticalProfileRegistry.getProfile(orgSettings.getVerticalProfile())`. If absent, return.
     3. Map member's `orgRole.getSlug()` (one of `owner`/`admin`/`member`) to the JSON's `Owner`/`Admin`/`Member` rows (case-insensitive compare on `roleName`).
     4. If no rate row matches the role, return.
     5. If `BillingRateRepository.findMemberDefaultEarliest(m.getId()).isEmpty()`, insert a BillingRate with `memberId=m.getId()`, projectId=null, customerId=null, currency=rateCardDefaults.currency, hourlyRate=role-matched row, effectiveFrom=today, effectiveTo=null.
     6. Repeat for CostRate (member default), using the cost row amount.
   - Both operations must be idempotent — guard with "exists" checks so the seeder can't double-insert.

4. One-time backfill migration for existing consulting-za members (Zolani/Bob/Carol in tenant_2a96bc3b208b):

   File to create: `backend/src/main/resources/db/migration/tenant/V97__backfill_default_member_rates_consulting_za.sql`

   ```sql
   -- Seed member-default billing rates for every member on consulting-za tenants
   -- who has no existing billing rate (effectiveFrom = today, null effectiveTo).
   -- Uses OrgSettings.vertical_profile to identify the profile and maps the member's
   -- OrgRole.slug (owner/admin/member) to the three role-keyed rate rows.
   INSERT INTO billing_rates (id, member_id, currency, hourly_rate, effective_from, created_at, updated_at)
   SELECT gen_random_uuid(), m.id, 'ZAR',
          CASE LOWER(orl.slug)
               WHEN 'owner'  THEN 1800.00
               WHEN 'admin'  THEN 1200.00
               WHEN 'member' THEN  750.00
          END,
          CURRENT_DATE, NOW(), NOW()
   FROM members m
   JOIN org_roles orl ON orl.id = m.org_role_id
   CROSS JOIN org_settings s
   WHERE s.vertical_profile = 'consulting-za'
     AND NOT EXISTS (
       SELECT 1 FROM billing_rates br
       WHERE br.member_id = m.id AND br.project_id IS NULL AND br.customer_id IS NULL
     )
     AND LOWER(orl.slug) IN ('owner','admin','member');

   INSERT INTO cost_rates (id, member_id, currency, hourly_cost, effective_from, created_at, updated_at)
   SELECT gen_random_uuid(), m.id, 'ZAR',
          CASE LOWER(orl.slug)
               WHEN 'owner'  THEN 850.00
               WHEN 'admin'  THEN 550.00
               WHEN 'member' THEN 375.00
          END,
          CURRENT_DATE, NOW(), NOW()
   FROM members m
   JOIN org_roles orl ON orl.id = m.org_role_id
   CROSS JOIN org_settings s
   WHERE s.vertical_profile = 'consulting-za'
     AND NOT EXISTS (
       SELECT 1 FROM cost_rates cr WHERE cr.member_id = m.id
     )
     AND LOWER(orl.slug) IN ('owner','admin','member');
   ```

   (Dev should verify table and column names match `members`, `org_roles`, `org_role_id` — but these are the standard naming conventions confirmed from `Member.java`.)

5. Leave the 8 orphaned seeded `billing_rates` rows (scope ORG_DEFAULT, memberId null) in place for now — they're harmless dead data. Cleaning them up is a separate migration, out of scope.

## Design Trade-offs / What This Does NOT Do

- Does NOT change the 8-tier role-keyed seeder output (`Creative Director`, `Strategist`, etc.). That model requires a BillingRate schema change (`role_name` column) + UI change (role picker in Rates settings) + frontend rate resolver. That's 4+ hours of work. Post-QA-cycle cleanup.
- Does NOT add a UI "Role Defaults" tab surfacing role-keyed seeded rates. Same reason.
- Does NOT handle rate tiers beyond Owner/Admin/Member — the JSON-encoded 3-tier fallback is good enough for Zolani (Owner), Bob (Admin), Carol (Member). Any custom role future-proofing is separate work.
- The 3-tier rate amounts from the profile JSON (1800/1200/750) are LOWER than some of the 8-tier rate-pack amounts (Creative Director R1800, Strategist R1600, etc.). This is acceptable for unblocking QA — invoices will have non-zero values, margin > 0, utilization computable. If finance accuracy matters for the demo, a Dev + Product follow-up can tune the three-tier values.

## Scope

Backend + Migration
Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileRegistry.java` (add rateCardDefaults parsing)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java` (inject + call seeder)

Files to create:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberRateSeedingService.java`
- `backend/src/main/resources/db/migration/tenant/V97__backfill_default_member_rates_consulting_za.sql`

Migration needed: yes

## Verification

1. Restart backend. Flyway runs V97 against `tenant_2a96bc3b208b`. Expect `billing_rates` to go from 8 rows to 11 (8 orphaned + Zolani R1800 + Bob R1200 + Carol R750), and `cost_rates` to go from 0 to 3.
2. SQL check: `SELECT br.hourly_rate, m.name FROM billing_rates br JOIN members m ON br.member_id = m.id;` — expect 3 rows (Zolani/Bob/Carol) with the 3 role-default amounts.
3. Log in as Bob, open a project, click Log Time. The "No rate card found for this combination." warning banner should be GONE — replaced by a live rate-preview (`30min x R1,200.00 = R600.00`).
4. Look at an existing time entry's `rate_snapshot` column — still NULL for historical entries unless service populates retroactively. Not a concern for Cycle 1 (new entries will have the snapshot).
5. Navigate to Settings > Rates & Currency as Zolani. Rate table now shows 3 member-default rows (Zolani/Bob/Carol @ correct ZAR). GAP-C-05 is closed from a "non-empty UI rates" perspective.
6. Re-run Day 3/4/6 log-time checkpoints to confirm rates apply. Cascade unblock for Day 34 profitability, Day 36 first-invoice, Day 75 wow moment.

## Estimated Effort

M (~1.5h). Backend-only. Adds one new service class (< 60 lines), extends one registry record, wires one call-site in MemberFilter, writes one Flyway migration. No frontend change needed (UI already reads member-scoped rates).
