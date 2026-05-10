# Consulting (South Africa)

**Status:** filled (Phase D part 2).
**Profile type:** vertical (light overlay), South-African-localised.
**Sibling:** `consulting-generic` (no-vertical fallback) — see §9.
**Source phase doc:** `→ architecture/phase66-consulting-vertical-profile.md:1`.
**Source profile JSON:** `→ backend/src/main/resources/vertical-profiles/consulting-za.json:1`.

---

## 1. Profile ID and scope

- Profile ID: `consulting-za` `→ backend/src/main/resources/vertical-profiles/consulting-za.json:2`.
- Region: South Africa (en-ZA, ZAR, VAT 15%) `→ vertical-profiles/consulting-za.json:5,6,30`.
- Vertical type: **vertical** (light overlay). Per ADR-244 (pack-only-vertical-profiles) and phase66 §66.2, consulting introduces no backend module — every concept fits inside existing entities + custom fields `→ architecture/phase66-consulting-vertical-profile.md:42-58`. No regulatory primitive (FICA does not apply to non-regulated agencies; POPIA is org-wide via Phase 50) `→ architecture/phase66-consulting-vertical-profile.md:16,447`.
- Target firms: digital agencies, creative studios, management consultancies, professional-services firms `→ vertical-profiles/consulting-za.json:4`.

The profile is the third demo rail alongside `legal-za` and `accounting-za`, and is the worked example of ADR-244's "pack-only" shape — see [`30-modules/vertical-profiles.md`](../30-modules/vertical-profiles.md) §6.1.

---

## 2. Packs installed by this profile

Declared in `consulting-za.json` `packs` block `→ vertical-profiles/consulting-za.json:8-15`. Inventory (per phase66 §66.3):

| Pack type | Pack ID | File | Installer route |
|---|---|---|---|
| Field (Customer) | `consulting-za-customer` | `→ backend/src/main/resources/field-packs/consulting-za-customer.json` | `FieldPackSeeder` (direct, pre-Phase 65) `→ architecture/phase66-consulting-vertical-profile.md:85` |
| Field (Project) | `consulting-za-project` | `→ backend/src/main/resources/field-packs/consulting-za-project.json` | `FieldPackSeeder` (direct) |
| Rate | `consulting-za` | `→ backend/src/main/resources/rate-packs/consulting-za.json` | `RatePackSeeder` (direct) |
| Project template | `consulting-za` | `→ backend/src/main/resources/project-template-packs/consulting-za.json` | `ProjectTemplatePackSeeder` (direct) |
| Document template | `consulting-za` | `→ backend/src/main/resources/template-packs/consulting-za/pack.json` (+ 4 Tiptap JSONs) | **`TemplatePackInstaller`** (Phase 65 unified) `→ architecture/phase66-consulting-vertical-profile.md:88` |
| Automation | `automation-consulting-za` | `→ backend/src/main/resources/automation-templates/consulting-za.json` | **`AutomationPackInstaller`** (Phase 65 unified) |
| Clause | `consulting-za-clauses` | `→ backend/src/main/resources/clause-packs/consulting-za-clauses/pack.json` | `ClausePackSeeder` (direct) |
| Request | `consulting-za-creative-brief` | `→ backend/src/main/resources/request-packs/consulting-za-creative-brief.json` | `RequestPackSeeder` (direct) |
| Compliance (reference) | `generic-onboarding` | existing — **no new pack file** | `CompliancePackSeeder` (direct) `→ architecture/phase66-consulting-vertical-profile.md:77,447` |

### Field-pack content highlights

- **Customer fields** (5): `industry`, `company_size`, `primary_stakeholder`, `msa_signed`, `msa_start_date` (conditional on `msa_signed=true`) `→ architecture/phase66-consulting-vertical-profile.md:332-339`.
- **Project fields** (5): `campaign_type` (required ENUM — the connective tissue per phase66 §66.5), `channel`, `deliverable_type`, `retainer_tier` (conditional on retainer-shaped campaigns), `creative_brief_url` `→ architecture/phase66-consulting-vertical-profile.md:344-350`.

### Rate pack — 8 SA agency roles in ZAR (2026 mid-market) `→ architecture/phase66-consulting-vertical-profile.md:354-365`

Creative Director (R1800/R850), Strategist, Art Director, Account Manager, Senior Designer/Developer, Copywriter, Designer/Developer, Producer/Junior. Manifest `rateCardDefaults` provides Owner/Admin/Member fallbacks `→ vertical-profiles/consulting-za.json:16-28`.

### Project templates — 5 `→ architecture/phase66-consulting-vertical-profile.md:373-379`

Website Build, **Social Media Retainer** (retainer-shaped), Brand Identity, SEO Campaign, **Content Marketing Retainer** (retainer-shaped). Each template seeds `campaign_type` automatically, mirroring the `matter_type` pattern legal uses (Phase 64).

### Document templates — 4

`creative-brief`, `statement-of-work`, `engagement-letter`, `monthly-retainer-report` `→ architecture/phase66-consulting-vertical-profile.md:402-407`. All consume only existing Phase 31 `VariableResolver` variables — `{{campaign_type}}` resolves through custom-field flattening, no resolver changes `→ architecture/phase66-consulting-vertical-profile.md:454-478`.

### Automations — 6

Budget 80%, budget exceeded, retainer closing (3-day warning), task blocked 7d, unbilled time 30d, proposal follow-up `→ architecture/phase66-consulting-vertical-profile.md:387-395`. All triggers reuse Phase 37/48 vocab — no new trigger types introduced `→ architecture/phase66-consulting-vertical-profile.md:396,598`.

### Clauses — 8 SOW clauses

`ip-ownership`, `revision-rounds`, `kill-fee`, `nda-mutual`, `payment-terms`, `change-requests`, `third-party-costs`, `termination` `→ architecture/phase66-consulting-vertical-profile.md:415-424`. `payment-terms` and `ip-ownership` are template-associated as required defaults on the SOW.

### Request — creative-brief questionnaire

~10 questions (brand, audience, goals, constraints, assets, tone, stakeholders, milestones) `→ architecture/phase66-consulting-vertical-profile.md:432-443`.

---

## 3. Terminology overrides

`terminologyOverrides: "en-ZA-consulting"` `→ vertical-profiles/consulting-za.json:32`. The map carries 5 effective overrides (≈18 keys after singular/plural/case expansion) `→ frontend/lib/terminology-map.ts:2-20`:

| Generic | `en-ZA-consulting` | Glossary anchor |
|---|---|---|
| Customer | **Client** | `→ glossary.md:75` ("UI label for `Customer` in legal-za, accounting-za, consulting-za") |
| Time Entry | **Time Log** | (consulting-only override; not in legal/accounting maps) |
| Rate Card | **Billing Rates** | `→ glossary.md:58` ("UI aliases: …, 'Billing Rates' (consulting)") |
| Time Tracking | Time Logs | nav label override |
| Rates & Currency | Billing Rates | settings nav override |

Plus two placeholder strings: `project.namePlaceholder` ("e.g. Q4 Strategy Engagement"), `project.referencePlaceholder` ("e.g. ENG-2026-001") `→ frontend/lib/terminology-map.ts:18,19`.

**Explicitly NOT overridden** (per phase66 §66.7):

- **Project remains Project** — accounting-za uses Engagement (`→ glossary.md:119`), legal-za uses Matter (`→ glossary.md:168`); consulting deliberately keeps the generic noun. `campaign_type` carries the agency semantic instead `→ architecture/phase66-consulting-vertical-profile.md:209-211`.
- Task → Deliverable, Invoice → (no rename), Proposal → (no rename), Member → (no rename). Phase 64 cleanup of legal terminology breakage informed the conservative choice `→ architecture/phase66-consulting-vertical-profile.md:196,209`.

Mechanism reference: ADR-185 (terminology-switching-approach) — Phase 66 only adds a key, does not change the dispatch `→ architecture/phase66-consulting-vertical-profile.md:225`.

---

## 4. Modules gated ON

Profile JSON declares three horizontal modules `→ vertical-profiles/consulting-za.json:7`:

```json
"enabledModules": ["resource_planning", "automation_builder", "information_requests"]
```

| Module slug | Category | Why ON for consulting |
|---|---|---|
| `resource_planning` | HORIZONTAL | THE consulting differentiator — capacity grid + utilization analytics. Profile-leaning per ADR-246 (the dashboard `TeamUtilizationWidget` self-gates to `consulting-za`) `→ kazi-architecture/30-modules/capacity-planning.md:9,121`. |
| `automation_builder` | HORIZONTAL | Agency operational rules (budget %, retainer-closing, blocked-task) `→ glossary.md:174`. |
| `information_requests` | HORIZONTAL | Creative-brief questionnaire arrives via this module `→ architecture/phase66-consulting-vertical-profile.md:430`. |

**Discrepancy with phase66 doc:** §66.9.1 of the phase doc shows `enabledModules: []` (pack-only purist position) `→ architecture/phase66-consulting-vertical-profile.md:299`. The shipped JSON disagrees and lists three horizontal modules. The shipped JSON wins per ADR-192 (`enabled_modules` is the runtime authority — JSON file is the seed) `→ kazi-architecture/30-modules/vertical-profiles.md:194`. The discrepancy is documented as an open question in §10.

All three are HORIZONTAL modules — flagged by §10.2 of [`vertical-profiles.md`](../30-modules/vertical-profiles.md) as the "profile-declared-and-admin-toggleable" overlap problem: the boot reconciler will re-add any of these on each restart even if an admin disables them.

---

## 5. Modules gated OFF

By omission from `enabledModules`, every other module is disabled. Notable offs:

| Module slug | Why OFF |
|---|---|
| `trust_accounting` | Legal-only (LPA s.86 statutory primitive). |
| `lssa_tariff` | Legal-only (Law Society tariffs). |
| `court_calendar` | Legal-only. |
| `conflict_check` | Legal-only. |
| `regulatory_deadlines` | Legal/accounting (no SARS-equivalent for agencies) `→ kazi-architecture/30-modules/vertical-profiles.md:112`. |
| `disbursements` | Legal-only. |
| `matter_closure` | Legal-only. |
| `retainer_agreements` | OFF in JSON, but the `RetainerAgreement` Phase 17 entity is **still used** for retainer-shaped projects — see §7. |
| `bulk_billing` | Off; admin-toggleable on if desired (HORIZONTAL). |

`period_close` and "regulatory deadlines" calendar (SARS/CIPC-style) — accounting-only, not present here.

---

## 6. Vertical-specific entities or extensions

**None.** Per phase66 §66.1 explicit non-scope: "no new backend entities, services, repositories, controllers, or endpoints" `→ architecture/phase66-consulting-vertical-profile.md:35`. No global or tenant migrations either (high-water marks unchanged) `→ architecture/phase66-consulting-vertical-profile.md:488-490`.

The vertical is purely:
- Profile JSON (1 file)
- Pack content (8 new pack files + 1 reuse)
- Terminology key (1 key in `terminology-map.ts`)
- One frontend widget — `TeamUtilizationWidget` (§7).

There is no `verticals/consulting/` Java package — by deliberate ADR-244 design.

---

## 7. Resource planning specifics

Resource planning is the consulting-leaning surface even though the underlying module is horizontal `→ kazi-architecture/30-modules/capacity-planning.md:9`.

- Module `resource_planning` is enabled by the profile (§4) and exposes the full Phase 38 surface: `MemberCapacity`, `ResourceAllocation`, `LeaveBlock`, allocation grid, utilization dashboard `→ kazi-architecture/30-modules/capacity-planning.md:14-17`.
- **Profile-gated dashboard widget**: `TeamUtilizationWidget` self-gates `profile === "consulting-za"` `→ frontend/components/dashboard/team-utilization-widget.tsx:18`; renders `null` for any other profile `→ frontend/components/dashboard/team-utilization-widget.tsx:31`. Per ADR-246 (profile-gated-dashboard-widgets) `→ kazi-architecture/30-modules/vertical-profiles.md:199`.
- Widget content: 4-week sparkline of `teamAverages.avgBillableUtilizationPct` from `GET /api/utilization/team` (4 sequential calls — no bulk endpoint) `→ architecture/phase66-consulting-vertical-profile.md:240-250`.
- **Depth limits**: capacity-planning is intentionally light — no waterfall scheduling, no skills matrix, no role-based allocation — see [`30-modules/capacity-planning.md`](../30-modules/capacity-planning.md) §10 open questions (utilization is billable-only; no total-utilization variant).

Retainer pattern (no new entity) — phase66 §66.6:
- Two project templates are retainer-shaped (`SOCIAL_MEDIA_RETAINER`, `CONTENT_MARKETING`).
- Owner manually creates a `RetainerAgreement` (Phase 17 entity, `→ glossary.md:140` "Hour Bank") after spinning up the project from the template — no auto-binding.
- Monthly retainer report renders against the `RetainerAgreement` at period close.
- Auto-creating the agreement on template use is **explicitly out of scope** for v1 `→ architecture/phase66-consulting-vertical-profile.md:188-190,602`.

---

## 8. Source material

- **Phase doc:** `→ architecture/phase66-consulting-vertical-profile.md:1` (full pack inventory, Tiptap variables, slice plan).
- **Profile JSON:** `→ backend/src/main/resources/vertical-profiles/consulting-za.json:1`.
- **Terminology map:** `→ frontend/lib/terminology-map.ts:2-20`.
- **Profile hook:** `→ frontend/lib/hooks/useProfile.ts:5-31`.
- **Self-gated widget:** `→ frontend/components/dashboard/team-utilization-widget.tsx:18,31`.
- **Module/registry mechanism:** [`30-modules/vertical-profiles.md`](../30-modules/vertical-profiles.md), [`30-modules/packs.md`](../30-modules/packs.md), [`30-modules/capacity-planning.md`](../30-modules/capacity-planning.md).
- **ADRs:** ADR-181, ADR-184, ADR-185, ADR-189, ADR-192, ADR-239, ADR-240, ADR-243, **ADR-244** (pack-only profiles, the canonical justification), **ADR-245** (localised-profile derivatives — `consulting-za` derives from `consulting-generic`), **ADR-246** (profile-gated dashboard widgets) `→ architecture/phase66-consulting-vertical-profile.md:609-621`.

---

## 9. Consulting-generic vs consulting-za

The frontend `useProfile()` hook lists both as known profile IDs `→ frontend/lib/hooks/useProfile.ts:6,9`. Both are **separate, registry-loaded profiles**, not a fallback relationship in code.

| | `consulting-generic` | `consulting-za` |
|---|---|---|
| File | `→ vertical-profiles/consulting-generic.json:1` | `→ vertical-profiles/consulting-za.json:1` |
| `enabledModules` | `[]` (truly empty) | `["resource_planning", "automation_builder", "information_requests"]` |
| Packs declared | None (manifest has no `packs` block) | 9 packs (§2) |
| Rate card | Owner/Admin/Member ZAR fallback only | 8 agency roles + same fallback |
| Tax | VAT 15% | VAT 15% |
| Terminology | (none — uses defaults) | `en-ZA-consulting` |
| Locale | `en-ZA` | `en-ZA` |
| Description | "General consulting, agencies, and professional services firms. ZAR defaults for South African practices." `→ vertical-profiles/consulting-generic.json:4` | "Configuration for SA digital agencies… campaign-oriented field packs, engagement templates, retainer and SOW content…" `→ vertical-profiles/consulting-za.json:4` |

Per ADR-245 (localised-profile-derivatives), `consulting-za` is the SA-localised derivative of `consulting-generic` `→ kazi-architecture/30-modules/vertical-profiles.md:198`. `consulting-generic` is documented in [`vertical-profiles.md`](../30-modules/vertical-profiles.md) §7 as "the 'no-vertical' fallback" — a tenant on `consulting-generic` gets a working SaaS but no agency-specific seed content.

In practice, `consulting-za` is the demo target; `consulting-generic` is the safe non-localised choice for tenants outside SA who don't want VAT-15-flavoured defaults applied.

---

## 10. Open questions / known fragility

1. **`enabledModules` discrepancy between phase66 doc and shipped JSON.** The phase doc says `[]` (pack-only purist) `→ architecture/phase66-consulting-vertical-profile.md:299`; the shipped JSON declares three horizontal modules `→ vertical-profiles/consulting-za.json:7`. The runtime truth is the JSON (per ADR-192). Either the doc is stale or the shipped JSON drifted into the profile-declared-horizontal overlap territory of [`vertical-profiles.md`](../30-modules/vertical-profiles.md) §10.2 — admins cannot durably disable these three on a `consulting-za` tenant because the boot reconciler will re-add them.

2. **Profile-switch fragility (universal — applies here too).** Same as legal/accounting: switching `consulting-za → consulting-generic` is **adds-only safe but reversible-dirty** per [`vertical-profiles.md`](../30-modules/vertical-profiles.md) §10.1 — packs and field definitions stay installed; `enabledModules` retains profile-added entries; terminology key reverts on next page load. `RetainerAgreement` rows persist regardless. Treat profile changes as "create a new tenant" until a `VerticalProfileDrainSeeder` exists.

3. **`retainer_agreements` module slug vs `RetainerAgreement` entity.** The slug is OFF in this profile (§5), but the Phase 17 entity is used (§7) for retainer-shaped projects. Open question: should `retainer_agreements` be ON for consulting? Some agencies run mandate-style retainers heavily; others don't. Currently the entity is reachable without the module gate — if the gate exists at the service entry points (it does for trust/legal, unclear for retainers), this is the same overlap risk as point 1. Phase doc punts on this (§66.6 manual `RetainerAgreement` creation).

4. **Resource-planning depth.** Capacity-planning module is generic and light — no waterfall scheduling, no skills matrix, no role-allocation primitive — see [`30-modules/capacity-planning.md`](../30-modules/capacity-planning.md) §10. Agencies frequently want skills-matched allocation (e.g., "Designer with Figma skill, available next sprint"); not in scope.

5. **Bulk-utilization endpoint missing.** `TeamUtilizationWidget` issues 4 sequential `/api/utilization/team` calls for the 4-week trend `→ architecture/phase66-consulting-vertical-profile.md:248`. A range-and-bucket variant is noted as out-of-scope `→ architecture/phase66-consulting-vertical-profile.md:603`.

6. **No FICA / KYC for consulting.** Compliance pack is `generic-onboarding` with no FICA `→ architecture/phase66-consulting-vertical-profile.md:447`. If an SA agency takes on regulated-client work (e.g., financial-services audits), the tenant has to switch profile or layer on a custom checklist — there is no "consulting + light FICA" profile derivative today.

7. **`retainer.hoursRemaining` template variable.** Deliberately omitted from the monthly retainer report's variable list — composition (`{{retainer.hourBank - retainer.hoursUsed}}`) is the recommended workaround `→ architecture/phase66-consulting-vertical-profile.md:472`. Not a defect, but flagged for template authors.
