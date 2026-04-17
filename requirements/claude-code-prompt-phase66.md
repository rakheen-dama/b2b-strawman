# Phase 66 — `consulting-za` Vertical Profile (Pack-Only Agency/Consulting Content)

## System Context

Kazi has three vertical profiles today:

- **`legal-za`** — deep: trust accounting (Phase 60), court calendar + conflict check + LSSA tariff (Phase 55), legal compliance refinements (Phase 61), matter templates + terminology (Phase 64). Backend modules, field packs, compliance packs, template packs, clause packs, automation packs, request packs, schedule packs — all present.
- **`accounting-za`** — solid: deadline calendar + filing (Phase 51), FICA compliance pack, accounting field + template + automation + request packs, terminology overrides (`en-ZA-accounting`).
- **`consulting-generic`** — a near-empty shell. Today it has only a profile manifest + rate pack (`rate-packs/consulting-generic.json`). No field packs, no project templates, no automation rules, no document templates. The demo-readiness QA cycle note (`.claude/ideas/demo-readiness-qa-cycle-2026-04-12.md`) flagged 8 concrete gaps that make the agency demo rail (Zolani Creative) feel generic.

**Existing infrastructure relevant to this phase:**

- **Vertical profile system** (Phase 49, ADRs 180/181): `backend/src/main/resources/vertical-profiles/*.json` manifests register enabled modules, pack references, rate defaults, tax defaults, terminology key. `ProfileRegistry` + `ModuleRegistry` gate features; `OrgProfileProvider` + `ModuleGate` gate frontend surfaces.
- **Pack install pipeline** (Phase 65, ADRs 240/241/242/243): DocumentTemplate + Automation Template packs route through `PackInstaller` + `PackCatalogService` + `PackInstallService` with `PackInstall` entity tracking. Other 11 pack types (field, rate, project-template, clause, request, compliance, schedule) keep direct-seeder paths.
- **Pack directories** under `backend/src/main/resources/`: `vertical-profiles/`, `field-packs/`, `rate-packs/`, `project-template-packs/`, `automation-templates/`, `template-packs/{pack-id}/`, `clause-packs/{pack-id}/`, `request-packs/`, `compliance-packs/{pack-id}/`, `schedule-packs/`.
- **Field graduation** (Phase 63): 34 customer + 4 project + 2 task + 4 invoice slugs were promoted from custom fields to native form inputs. `campaign_type`, `channel`, `deliverable_type`, `creative_brief_url`, `retainer_tier` were **not** graduated — they stay as pack-provided custom fields because they are agency-specific, not universal.
- **Terminology system** (ADR-185): Static `TERMINOLOGY` map in `frontend/lib/terminology-map.ts` keyed by terminology ID (e.g., `en-ZA-accounting`, `en-ZA-legal`). The profile manifest's `terminologyOverrides` field references the key.
- **Retainer primitive** (Phase 17): `RetainerAgreement` entity with hour banks, period close, auto-invoicing. No retainer *templates*; every retainer is configured from scratch.
- **Proposal primitive** (Phase 32): `Proposal` entity with lifecycle (DRAFT → SENT → ACCEPTED/DECLINED/EXPIRED) and portal acceptance. Phase 31 Tiptap document model powers proposal content; no agency-specific proposal *templates* exist.
- **Resource planning & capacity** (Phase 38): `CapacityAllocation` entity + `UtilizationService` producing billable % per member across a window. No consulting-specific dashboard surface.
- **Dashboard composition** (Phase 53): Chart component library + dashboard cards; redesigned company and personal dashboards. New widgets slot in via existing `DashboardCard` primitives.
- **Reseed tool**: Pack seeders run on tenant provisioning; `scripts/reseed-packs.sh` (or equivalent `reseed-pack` admin endpoint from Phase 65) re-applies pack JSON during dev iteration.

## Objective

Deliver a South-African-flavoured agency/consulting vertical profile (`consulting-za`) as **pure pack content** plus **one small UI addition** (a team-utilization dashboard widget). The profile must be demo-ready for the Zolani Creative 90-day lifecycle script (`qa/testplan/demos/consulting-agency-90day-keycloak.md`) without adding any new backend entities, services, or domain concepts beyond what the horizontal stack already provides.

The phase closes the third vertical rail so all three demo lifecycles (legal, accounting, agency) land with equivalent polish.

## Constraints & Assumptions

- **Pack-only** — no new backend entities, services, controllers, or database migrations. Every deliverable is a JSON/Markdown/TypeScript pack asset or Tiptap document content. The one exception is the dashboard widget, which is a frontend component consuming the existing Phase 38 utilization endpoint.
- **SA-focused** — `consulting-za` coexists with `consulting-generic`. The generic profile is preserved for future non-ZA forks (not touched this phase). All new pack content uses ZAR, en-ZA locale, South African cultural defaults (VAT 15%, POPIA, typical SA agency role mix and rates).
- **Non-scope modules preserved** — `consulting-za` does **not** enable the legal, trust, or accounting modules. `enabledModules` stays empty (consulting doesn't need vertical backend modules).
- **Phase 65 install pipeline is reused, not extended** — document-template + automation packs install through `PackInstaller`; other pack types use existing direct-seeder paths. No changes to pipeline code.
- **Custom fields, not graduated columns** — `campaign_type`, `channel`, `deliverable_type`, `retainer_tier`, `creative_brief_url` stay as custom fields in the project field pack. Do NOT touch the Project/Customer native schema.
- **Retainer templates** are JSON *seed* data that the owner can clone into real `RetainerAgreement` rows via existing "Save as Template" flow (Phase 16 pattern). No new entity needed — extend the existing project-template-pack JSON shape only if the retainer cloning is template-driven; otherwise provide retainer configs as documentation + a small pack-seeded snippet in the project template pack.
- **Terminology overrides are minimal** — unlike legal (which renames many core nouns), consulting is close enough to the horizontal stack that only 3–5 overrides are needed. Do not over-engineer this.
- **Zolani Creative lifecycle script already exists** (`qa/testplan/demos/consulting-agency-90day-keycloak.md`) but was written against `consulting-generic`. Update the script to target `consulting-za` so every checkpoint exercises the new pack content.
- **Explicitly out of scope for this phase**: campaign-above-project grouping, creative review rounds, agency-specific backend modules (any of these are future phases and were deliberately parked during ideation).

---

## Section 1 — Profile Manifest (`consulting-za`)

### 1.1 File

`backend/src/main/resources/vertical-profiles/consulting-za.json`

### 1.2 Required Shape (mirror `accounting-za.json`)

```json
{
  "profileId": "consulting-za",
  "name": "South African Agency & Consulting Firm",
  "description": "Configuration for SA digital agencies, creative studios, management consultancies, and professional services firms: campaign-oriented field packs, engagement templates, retainer and SOW content, automation rules for utilization and budget visibility.",
  "locale": "en-ZA",
  "currency": "ZAR",
  "enabledModules": [],
  "packs": {
    "field": ["consulting-za-customer", "consulting-za-project"],
    "template": ["consulting-za"],
    "clause": ["consulting-za-clauses"],
    "automation": ["automation-consulting-za"],
    "request": ["consulting-za-creative-brief"],
    "compliance": ["generic-onboarding"]
  },
  "rateCardDefaults": { ... see Section 4 ... },
  "taxDefaults": [
    { "name": "VAT", "rate": 15.00, "default": true }
  ],
  "terminologyOverrides": "en-ZA-consulting"
}
```

### 1.3 Registration

- Verify `ProfileRegistry` picks up the new manifest via classpath scanning. No code change expected; if the registry uses an allow-list, add `consulting-za` to it.
- Add a brief entry to any profile-picker UI enum or JSON (if one exists) so the profile is selectable during tenant setup / platform-admin provisioning.

---

## Section 2 — Field Packs

Two field packs: one for Customer, one for Project. Follow the exact shape used by `field-packs/accounting-za-customer.json` and `field-packs/accounting-za-project.json`.

### 2.1 `field-packs/consulting-za-customer.json`

| slug | label | type | required | options / notes |
|---|---|---|---|---|
| `industry` | Industry | ENUM | no | `RETAIL`, `FINANCIAL_SERVICES`, `TECH`, `HEALTHCARE`, `MANUFACTURING`, `PROFESSIONAL_SERVICES`, `HOSPITALITY`, `PUBLIC_SECTOR`, `NONPROFIT`, `OTHER` |
| `company_size` | Company Size | ENUM | no | `SOLO` (1), `SMALL` (2–10), `MEDIUM` (11–50), `LARGE` (51–250), `ENTERPRISE` (250+) |
| `primary_stakeholder` | Primary Stakeholder | STRING | no | — |
| `msa_signed` | MSA Signed | BOOLEAN | no | — |
| `msa_start_date` | MSA Start Date | DATE | no | Conditionally visible when `msa_signed = true` (use Phase 23 conditional visibility) |

### 2.2 `field-packs/consulting-za-project.json`

| slug | label | type | required | options / notes |
|---|---|---|---|---|
| `campaign_type` | Campaign / Engagement Type | ENUM | yes | `WEBSITE_BUILD`, `BRAND_IDENTITY`, `SOCIAL_MEDIA_RETAINER`, `SEO_CAMPAIGN`, `CONTENT_MARKETING`, `PAID_MEDIA`, `DESIGN_SPRINT`, `STRATEGY_CONSULTING`, `OTHER` |
| `channel` | Primary Channel | ENUM | no | `WEB`, `SOCIAL`, `EMAIL`, `PAID_SEARCH`, `PAID_SOCIAL`, `OOH`, `PRINT`, `MULTI_CHANNEL` |
| `deliverable_type` | Deliverable Type | ENUM | no | `DESIGN`, `COPY`, `CODE`, `STRATEGY_DOC`, `CAMPAIGN_ASSETS`, `VIDEO`, `AUDIO`, `MIXED` |
| `retainer_tier` | Retainer Tier | ENUM | no | `NONE`, `STARTER`, `GROWTH`, `ENTERPRISE` (conditionally visible when `campaign_type = SOCIAL_MEDIA_RETAINER` or `CONTENT_MARKETING`) |
| `creative_brief_url` | Creative Brief URL | URL | no | Link to external brief (Notion, Google Doc, etc.) |

### 2.3 Auto-Apply Groups

Per Phase 23, field packs can be configured to auto-apply their field group when the profile is active. Mark both packs as `autoApply: true` on the project and customer entity types so agency tenants see the fields without manual activation.

---

## Section 3 — Rate Pack

### 3.1 File

`backend/src/main/resources/rate-packs/consulting-za.json`

### 3.2 Structure (mirror `rate-packs/accounting-za.json`)

Default billing + cost rates for 8 typical SA agency roles, all in ZAR. Rates reflect Johannesburg/Cape Town mid-market digital agency benchmarks as of 2026.

| Role | Billing Rate (ZAR/hr) | Cost Rate (ZAR/hr) |
|---|---|---|
| Creative Director | 1800 | 850 |
| Strategist | 1600 | 750 |
| Art Director | 1400 | 650 |
| Account Manager | 1200 | 550 |
| Senior Designer / Developer | 1100 | 500 |
| Copywriter | 950 | 450 |
| Designer / Developer | 850 | 400 |
| Producer / Junior | 600 | 300 |

### 3.3 Profile Manifest Integration

Also set `rateCardDefaults` in the profile manifest (Section 1.2) with three fallback entries keyed to the generic `Owner` / `Admin` / `Member` roles (same pattern as `consulting-generic.json`), using: Owner = R1800, Admin = R1200, Member = R750 billing; cost at ~half those values. This covers tenants that haven't customized their role list yet.

---

## Section 4 — Project Template Pack

### 4.1 File

`backend/src/main/resources/project-template-packs/consulting-za.json`

### 4.2 Templates (5)

Follow the shape used by `project-template-packs/accounting-za.json`. Each template: name, description, suggested `campaign_type` value, default task list with priorities, suggested assignee role hints (Creative Director / Strategist / Designer / etc.), suggested budget hours, suggested retainer config (where applicable).

#### Template 1 — Website Design & Build

- `campaign_type`: `WEBSITE_BUILD`
- Suggested budget: 120 hours / R120,000
- Tasks:
  1. Discovery workshop & requirements (HIGH, Strategist)
  2. Sitemap & information architecture (HIGH, Strategist)
  3. Wireframes & user flows (HIGH, Art Director)
  4. Visual design system (HIGH, Senior Designer)
  5. Page design — home + key templates (HIGH, Designer)
  6. Frontend development (HIGH, Developer)
  7. CMS integration / content population (MEDIUM, Developer)
  8. QA & cross-browser testing (MEDIUM, Producer)
  9. Launch & handover (MEDIUM, Account Manager)

#### Template 2 — Social Media Management Retainer

- `campaign_type`: `SOCIAL_MEDIA_RETAINER`
- `retainer_tier`: `GROWTH` (default, overridable)
- Suggested monthly hour bank: 40 hours
- Tasks (monthly, recurring):
  1. Monthly content planning & calendar (HIGH, Strategist)
  2. Content creation — copy + visuals (HIGH, Copywriter + Designer)
  3. Scheduling & publishing (MEDIUM, Account Manager)
  4. Community management & engagement (MEDIUM, Account Manager)
  5. Paid boost management (LOW, Strategist, conditional)
  6. Monthly performance report (HIGH, Strategist)

#### Template 3 — Brand Identity

- `campaign_type`: `BRAND_IDENTITY`
- Suggested budget: 80 hours / R110,000
- Tasks:
  1. Brand discovery & strategy workshop (HIGH, Strategist)
  2. Competitive & market audit (MEDIUM, Strategist)
  3. Brand positioning & voice (HIGH, Creative Director)
  4. Logo design exploration (HIGH, Art Director)
  5. Logo refinement & finalisation (HIGH, Art Director)
  6. Visual identity system (colour, type, grid) (HIGH, Senior Designer)
  7. Brand guidelines document (HIGH, Senior Designer)
  8. Core asset production (stationery, templates) (MEDIUM, Designer)
  9. Brand rollout support (LOW, Account Manager)

#### Template 4 — SEO Campaign

- `campaign_type`: `SEO_CAMPAIGN`
- Suggested budget: 60 hours / R65,000 + monthly retainer option
- Tasks:
  1. Technical SEO audit (HIGH, Strategist)
  2. Keyword research & mapping (HIGH, Strategist)
  3. On-page optimisation (HIGH, Developer)
  4. Content brief creation (MEDIUM, Strategist)
  5. Content production (MEDIUM, Copywriter)
  6. Link-building outreach (LOW, Account Manager)
  7. Monthly ranking & traffic reporting (MEDIUM, Strategist)

#### Template 5 — Content Marketing Retainer

- `campaign_type`: `CONTENT_MARKETING`
- `retainer_tier`: `STARTER` (default)
- Suggested monthly hour bank: 25 hours
- Tasks (monthly, recurring):
  1. Editorial calendar planning (HIGH, Strategist)
  2. Article drafting (2–4 articles) (HIGH, Copywriter)
  3. Editing & SEO optimisation (MEDIUM, Strategist)
  4. Imagery / hero visuals (MEDIUM, Designer)
  5. Publishing & distribution (MEDIUM, Account Manager)
  6. Monthly reporting (MEDIUM, Strategist)

### 4.3 Template-to-Custom-Field Linking

Each template should set the `campaign_type` custom field value automatically when the template is used to create a project. Follow the same linking pattern legal uses for `matter_type` in Phase 64.

---

## Section 5 — Automation Pack

### 5.1 File

`backend/src/main/resources/automation-templates/consulting-za.json` (routes through Phase 65 `PackInstaller`)

### 5.2 Rules (6)

Mirror the shape of `automation-templates/accounting-za.json` — each rule has a name, description, trigger, conditions, actions, and variables.

| # | Rule | Trigger | Condition | Action | Variables |
|---|---|---|---|---|---|
| 1 | Budget 80% used — notify owner | `PROJECT_BUDGET_CHANGED` | `budget_used_pct >= 80 AND budget_used_pct < 100` | Notify project owner + comment on project | `{{project.name}}`, `{{budget_used_pct}}` |
| 2 | Budget exceeded — urgent notify owner + admin | `PROJECT_BUDGET_CHANGED` | `budget_used_pct >= 100` | Notify owner + admin, high priority | `{{project.name}}`, `{{budget_used_pct}}` |
| 3 | Retainer period closing in 3 days | `FIELD_DATE_APPROACHING` | `retainer.period_end - 3 days` | Notify project owner | `{{retainer.customer_name}}`, `{{retainer.period_end}}` |
| 4 | Task blocked for 7 days | `TASK_STATUS_UNCHANGED` | `status = BLOCKED AND days_since_update >= 7` | Notify assignee + project owner | `{{task.title}}`, `{{project.name}}` |
| 5 | Unbilled time older than 30 days | `TIME_ENTRY_AGE` | `is_billable = true AND is_invoiced = false AND days_old >= 30` | Notify project owner, dashboard flag | `{{project.name}}`, `{{unbilled_hours}}` |
| 6 | Proposal sent — follow-up reminder in 5 days | `PROPOSAL_SENT` | `status = SENT for 5 days` | Notify proposal owner | `{{proposal.customer_name}}`, `{{proposal.total}}` |

Triggers and action types must use existing Phase 37 automation framework verbs — do not invent new trigger types. If a specific trigger (e.g., `TIME_ENTRY_AGE`) doesn't yet exist, use the nearest available scheduled trigger and reference the Phase 48 `FIELD_DATE_APPROACHING` pattern from `automation-templates/accounting-za.json`.

---

## Section 6 — Document Template Pack

### 6.1 Directory

`backend/src/main/resources/template-packs/consulting-za/` (routes through Phase 65 `PackInstaller`)

### 6.2 Templates (4)

Each template is a Tiptap JSON document with variable placeholders, and a `manifest.json` entry (mirror `template-packs/accounting-za/`).

| Template | Purpose | Variables |
|---|---|---|
| `creative-brief.json` | Kick-off brief capturing goals, audience, deliverables, timing, budget | `{{customer.name}}`, `{{project.name}}`, `{{campaign_type}}`, `{{creative_brief_url}}`, `{{project.start_date}}`, `{{project.due_date}}`, `{{org.name}}` |
| `statement-of-work.json` | SOW: scope, deliverables, timeline, fee, out-of-scope, assumptions, sign-off block | `{{customer.name}}`, `{{project.name}}`, `{{deliverable_type}}`, `{{project.budget_total}}`, `{{project.start_date}}`, `{{project.due_date}}`, owner signature block, client signature block |
| `engagement-letter.json` | Formal engagement with scope summary + commercial terms + standard clauses (pulled from clause pack) | `{{customer.name}}`, `{{customer.address}}`, `{{project.name}}`, `{{org.name}}`, `{{org.vat_number}}`, owner + client signature blocks |
| `monthly-retainer-report.json` | Monthly retainer wrap-up: hours used, hours remaining, deliverables shipped, activity summary | `{{customer.name}}`, `{{retainer.period_start}}`, `{{retainer.period_end}}`, `{{retainer.hours_used}}`, `{{retainer.hours_remaining}}`, `{{project.name}}`, activity table |

### 6.3 Manifest

`template-packs/consulting-za/manifest.json` — same shape as `template-packs/accounting-za/manifest.json` (pack id, version, display name, list of template entries with their slugs + default names + Tiptap JSON file references).

### 6.4 Variable Availability

Confirm every variable referenced exists in the Phase 31 Tiptap variable registry (`VariableResolver` / `TemplateContextBuilder`). If a variable doesn't exist (e.g., `retainer.hours_remaining` as a first-class variable), either:
- Use a close equivalent (`{{retainer.hour_bank}} - {{retainer.hours_used}}`), or
- Flag it in the architecture doc as a small variable-registry extension.

**Do not add new variables unless strictly necessary** — prefer composition of existing variables.

---

## Section 7 — Clause Pack

### 7.1 Directory

`backend/src/main/resources/clause-packs/consulting-za-clauses/`

### 7.2 Clauses (8)

Standard agency SOW clauses, each as a Tiptap JSON document with optional variables. Mirror the shape of `clause-packs/accounting-za-clauses/`.

| Clause | Purpose |
|---|---|
| `ip-ownership.json` | IP transfer on final payment; pre-existing IP retained |
| `revision-rounds.json` | Two rounds of revisions included, additional rounds billed at hourly rate |
| `kill-fee.json` | 50% kill fee on scope if engagement cancelled post-approval |
| `nda-mutual.json` | Mutual NDA with standard exceptions |
| `payment-terms.json` | Deposit + progress payments + final payment, interest on overdue, 30-day net for retainers |
| `change-requests.json` | Written change-request process, scope-change fees |
| `third-party-costs.json` | Stock imagery, licences, hosting, production costs billed at cost + 15% |
| `termination.json` | 30-day notice either side; work-in-progress billed to termination date |

Each clause must be self-contained and insertable into any SOW / Engagement Letter via the Phase 27 clause library mechanism.

---

## Section 8 — Request Pack

### 8.1 File

`backend/src/main/resources/request-packs/consulting-za-creative-brief.json`

### 8.2 Purpose

A creative-brief questionnaire auto-assigned to new customers when an agency engagement starts. Follow the shape of `request-packs/year-end-info-request-za.json` from accounting.

### 8.3 Questions (suggested, ~10)

1. Brand & company description (long text)
2. Target audience — primary + secondary (long text)
3. Core business goals this engagement supports (long text)
4. Competitive landscape / reference brands (long text)
5. Must-have deliverables (checkbox list)
6. Known constraints or brand guidelines (file upload)
7. Existing assets or content (file upload)
8. Tone of voice preferences (short text + optional file)
9. Key stakeholders + decision-making process (structured)
10. Launch / milestone dates (date fields)

---

## Section 9 — Compliance Pack Reference

Use the existing `compliance-packs/generic-onboarding/` pack (standard customer onboarding checklist). Do not create a new compliance pack for agency — POPIA obligations already covered by Phase 50 (Data Protection). FICA does **not** apply to non-regulated agencies so no FICA pack is needed.

Reference the existing pack in the profile manifest under `packs.compliance`.

---

## Section 10 — Terminology Overrides

### 10.1 File

`frontend/lib/terminology-map.ts` — add a new key `en-ZA-consulting`.

### 10.2 Overrides (small set)

| Generic Term | Consulting Override | Rationale |
|---|---|---|
| Customer / customer | Client / client | Standard agency usage |
| Customers / customers | Clients / clients | — |
| Time Entry | Time Log | Slightly more natural for agencies |
| Time Entries | Time Logs | — |
| Rate Card | Billing Rates | "Rate card" is fine but "billing rates" reads more plainly |
| Rate Cards | Billing Rates | — |

**Do NOT** override Project → Engagement or Task → Deliverable at this stage. `campaign_type` custom-field values already carry the agency semantic, and over-overriding terminology is how legal's original map got into trouble (Phase 64). Keep this light.

### 10.3 Tests

Update `frontend/__tests__/terminology.test.ts` and `frontend/__tests__/terminology-integration.test.ts` with coverage for the new key.

---

## Section 11 — Team Utilization Dashboard Widget (The One UI Item)

### 11.1 Purpose

Surface the Phase 38 `UtilizationService` data as a compact dashboard card showing team-wide billable % for the current week/month. Visible only when the active profile is `consulting-za` (ModuleGate'd).

### 11.2 Placement

Add to the company dashboard grid (Phase 53 redesign). Widget size: 1× card equivalent (≈ 340px wide).

### 11.3 Content

- Headline: current billable % (e.g., "68% billable this week")
- Secondary: same period last week, as % delta
- Mini-chart: 4-week trend spark line using the existing Phase 53 chart primitive
- CTA link: "Team utilization →" → navigates to the existing Phase 38 utilization page

### 11.4 Data Source

Existing Phase 38 utilization endpoint (`GET /api/utilization/team?window=WEEK`). No new backend endpoint required. If the endpoint doesn't return the trend series in a single call, use its `window` parameter repeatedly or use an existing bulk endpoint — do not add a new endpoint unless unavoidable.

### 11.5 Gating

- Use `<ModuleGate profile="consulting-za">` wrapper (or the nearest equivalent — use a profile check since utilization isn't a module, it's a horizontal feature with profile-specific surfacing).
- If a profile-check helper doesn't exist, add a tiny `useProfile()` hook that reads from `OrgProfileProvider` and returns the profile ID. Keep this minimal.

### 11.6 Tests

- Component test for widget rendering (data present / data empty / loading / error states).
- Gating test — widget hidden under `legal-za` and `accounting-za` profiles.
- Snapshot / visual regression capture in the demo QA plan.

---

## Section 12 — QA Lifecycle Script Update + Screenshot Baselines

### 12.1 Update Zolani Creative Lifecycle Script

`qa/testplan/demos/consulting-agency-90day-keycloak.md` currently targets `consulting-generic`. Update it to target `consulting-za` so every checkpoint exercises the new pack content:

- Day 0: select `consulting-za` profile during provisioning; verify new field packs, rate pack, terminology overrides, utilization widget all present.
- Day 1–3: create 4 customer archetypes using the new customer field pack (industry, MSA tracking).
- Day 3–7: create 4 projects using the 5 project templates (Website Build, Social Retainer, Brand Identity, SEO Campaign, Content Retainer), confirming `campaign_type` gets set, retainer tier surfaces where applicable.
- Day 14: send first creative brief via the request pack.
- Day 30: first retainer period close → verify monthly retainer report document generates with correct retainer variables.
- Day 45: trigger automation rules — budget 80%, retainer period closing, unbilled time — confirm notifications fire.
- Day 60: second billing cycle + SOW generation with agency clauses inserted.
- Day 75+: utilization widget shows 4-week trend.

### 12.2 Screenshot Baselines

Follow the Phase 64 pattern:

- `e2e/screenshots/consulting-lifecycle/` for Playwright `toHaveScreenshot()` regression baselines.
- `documentation/screenshots/consulting-vertical/` for curated shots (dashboard with utilization widget, project with campaign_type, monthly retainer report PDF preview, SOW with agency clauses, creative brief request flow).
- No changes to the existing Playwright config beyond additive test files.

---

## Out of Scope

- **Campaigns** (a parent-of-projects grouping) — parked to a later phase; architecturally interesting and deserves its own design pass.
- **Creative review rounds** (a distinct approval loop from document acceptance) — parked.
- **Agency-specific backend module** — consulting doesn't have unique backend primitives; pack-only is enough.
- **International consulting variants** — only `consulting-za` this phase; `consulting-generic` unchanged; no UK / US / EU variants.
- **`Project` → `Engagement` terminology override** — intentionally omitted; custom-field `campaign_type` carries the semantic and avoids breakage across deeply nested components.
- **New automation triggers** — reuse Phase 37 / Phase 48 trigger set only. If a gap is discovered, log it as a follow-up, don't fix it here.
- **Variable registry expansion** — prefer composition of existing variables; any new variable should be a last resort with clear justification.
- **Graduating `campaign_type` to a native column** — it is agency-specific, not universal. Phase 63 only graduated universal fields. Keep as custom field.
- **Mobile-specific widget variant** — the utilization widget uses existing dashboard responsive breakpoints; no bespoke mobile component.

## ADR Topics

- **ADR-??? — Pack-only vertical profiles for operational-model verticals.** Legal and accounting required backend modules because they have primitives (trust ledger, filing deadlines) the horizontal stack doesn't cover. Consulting doesn't: projects + retainers + time + proposals + utilization already model it. Document the rule for future vertical profiles: if a vertical's operational model is fully expressible in existing entities, ship pack-only.
- **ADR-??? — Localized profile as derivative of a generic profile.** `consulting-za` and `consulting-generic` coexist. Document the naming pattern (`{vertical}-{country-code}` layered over `{vertical}-generic`), when to add a country-specific variant, and how pack content is partitioned (generic = universal pack content; country-specific = rates, VAT, compliance, terminology, regional seed data).
- **ADR-??? — Dashboard widget profile-gating.** Small pattern ADR: widgets that surface existing horizontal data with vertical-specific framing should be profile-gated via a `<ModuleGate>` or `useProfile()` check, not module-gated. Prevents each vertical surface from becoming a module.

## Style & Boundaries

- Follow all conventions in `frontend/CLAUDE.md` and `backend/CLAUDE.md`.
- Pack JSON must validate against existing pack loaders; add no new loader types.
- Tiptap documents follow Phase 31 conventions — do not emit legacy HTML/Thymeleaf templates.
- Variable placeholders match Phase 31 resolver syntax (`{{ dot.notation }}`).
- Terminology changes are additive only — do not modify existing `en-ZA-legal` or `en-ZA-accounting` entries.
- Widget component follows Phase 53 chart library and dashboard card conventions; reuse primitives, don't create new ones.
- Automation rules use existing triggers only; no new trigger types.
- No new database migrations; no new entities; no new services; no new repositories.
- Integration tests for pack installation use the Phase 65 `PackCatalogService` / `PackInstallService` paths.
