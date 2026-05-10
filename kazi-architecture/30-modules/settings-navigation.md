# Settings & Navigation

**Bounded context:** see [`10-bounded-contexts.md` § settings-navigation](../10-bounded-contexts.md).

## Purpose

Three concerns share this module because they are the runtime configuration spine of the staff frontend and have no business logic of their own:

1. **Tenant configuration aggregate.** A single-row-per-tenant `OrgSettings` entity (~40 columns) holds every cross-cutting knob — currency, branding, feature flags, terminology key, vertical profile, retention rules, time-reminder cadence, billing batch limits, pack-install status logs. Anything that needs to vary per tenant but doesn't merit its own aggregate ends up here.
2. **UI-shell navigation.** The authoritative sidebar nav tree (`NAV_GROUPS`, `UTILITY_ITEMS`, `SETTINGS_ITEMS`) lives in `frontend/lib/nav-items.ts` `→ frontend/lib/nav-items.ts:72` and is rendered by `DesktopSidebar` / `MobileSidebar`. Each entry can declare `requiredCapability` and `requiredModule` — the sidebar hides items the user lacks.
3. **Command palette + feature module toggles.** Cmd-K opens `CommandPaletteDialog`, sourced from the same nav tree. The Settings → Features page is the human surface for `OrgSettings.enabledModules` (the feature-gate vector consumed by `VerticalModuleGuard` end-to-end).

This module owns **the shell, the aggregate, and the gate vocabulary** — not the per-concern settings sub-pages. The "tax" sub-page is owned by `invoicing`; "templates" by `documents-templates`; "rates" by `invoicing` (BillingRate is a sibling aggregate, see Open Questions). Settings is referenced by virtually every other module via `OrgSettingsRepository.findFirstByOrderByCreatedAtAsc()`.

## Entities owned

- `OrgSettings` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java:34` — the aggregate. Single row per tenant schema; provisioned at tenant creation.
- `PortalRetainerMemberDisplay` enum `→ backend/.../settings/PortalRetainerMemberDisplay.java` (FIRST_NAME_ROLE / FULL_NAME / ROLE_ONLY — ADR-255).
- `PortalDigestCadence` enum `→ backend/.../settings/PortalDigestCadence.java` (WEEKLY / BIWEEKLY / OFF — ADR-258 / Epic 498A).

### OrgSettings field groups (load-bearing fields anchored)

OrgSettings has 40+ persisted columns, grouped here by the concern that reads them. **Other modules read these fields; this module owns them.**

| Group | Fields | Read by |
|-------|--------|---------|
| Branding | `defaultCurrency` (line 52), `logoS3Key` (65), `brandColor` (68), `documentFooterText` (71) | desktop sidebar (CSS var injection), invoicing (PDF render), portal |
| Feature flags | `accountingEnabled` (122), `aiEnabled` (125), `documentSigningEnabled` (128) | `IntegrationGuardService.requireEnabled(...)` per ADR-091 |
| Vertical profile | `verticalProfile` (177), `enabledModules` (181 — JSONB list), `terminologyNamespace` (184) | `VerticalModuleGuard`, `TerminologyProvider`, `ModuleGate` |
| Tax | `taxInclusive` (131), `taxRegistrationNumber` (134), `taxRegistrationLabel` (137), `taxLabel` (140) | invoicing |
| Compliance / lifecycle | `dormancyThresholdDays` (78), `dataRequestDeadlineDays` (81), `acceptanceExpiryDays` (119) | customer-lifecycle, proposals-acceptance, information-requests |
| Time + capacity | `defaultExpenseMarkupPercent` (143), `defaultWeeklyCapacityHours` (147), `timeReminderEnabled` (150), `timeReminderDays` (153), `timeReminderTime` (156), `timeReminderMinMinutes` (159) | expenses, capacity-planning, `TimeReminderScheduler` |
| Billing batch | `billingBatchAsyncThreshold` (162), `billingEmailRateLimit` (165), `defaultBillingRunCurrency` (168) | invoicing (BillingRun) |
| Project naming | `projectNamingPattern` (171) | projects |
| Onboarding | `onboardingDismissedAt` (174) | dashboard onboarding card |
| Data protection | `dataProtectionJurisdiction` (188), `retentionPolicyEnabled` (191), `defaultRetentionMonths` (194), `financialRetentionMonths` (197), `informationOfficerName` (200), `informationOfficerEmail` (203), `legalMatterRetentionYears` (213, ADR-249) | `RetentionPolicyService`, `DataSubjectRequestService` |
| Portal config | `portalRetainerMemberDisplay` (223, ADR-255), `portalDigestCadence` (232, ADR-258), `digestLastSentAt` (241), `portalNotificationDocTypes` (253, GAP-L-72) | customer-portal, `PortalDigestScheduler`, `PortalDocumentNotificationHandler` |
| Pack status logs | `fieldPackStatus` (62), `templatePackStatus` (75), `compliancePackStatus` (85), `reportPackStatus` (89), `clausePackStatus` (93), `requestPackStatus` (97), `automationPackStatus` (101), `ratePackStatus` (105), `schedulePackStatus` (109), `projectTemplatePackStatus` (113) — JSONB lists of `{packId, version, appliedAt}` | `packs` (PackInstaller idempotency check) |

`updatedAt` (line 58) is bumped by every setter. `recordTemplatePackApplication(...)` (line 361) is idempotent — removes prior entry for same packId before appending — to prevent duplicates on re-install. The same idempotency lives on `automation`, `rate`, `schedule`, and `projectTemplate` pack methods.

**Rate-card ownership (resolved):** `BillingRate` and `CostRate` do **not** live in this module. They are sibling aggregates: `BillingRate` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRate.java:14` (REST `/api/billing-rates` `→ backend/.../billingrate/BillingRateController.java:28`) and `CostRate` `→ backend/.../costrate/CostRate.java`. They appear under `/settings/rates` in the **UI** because the firm admin manages them as configuration, but the entity, repository, service, and controller belong to `invoicing` (per the glossary: `Billing Rate`, `Cost Rate` rows). OrgSettings only carries `ratePackStatus` (the install log of seeded rate packs). The `/settings/rates` route is a settings sub-page owned by `invoicing` (see Open Questions §3).

## REST surface

Two controllers, ~16 endpoints; both rooted under `/api/settings`. **Note:** `OrgSettingsController` is a known thin-controller violation per `backend/CLAUDE.md` (TD-009).

### `OrgSettingsController` `→ backend/.../settings/OrgSettingsController.java:30`

| Endpoint | Method | Capability | Purpose |
|----------|--------|------------|---------|
| `/api/settings` | GET | (none — read) | Returns full `SettingsResponse` incl. presigned logo URL `→ OrgSettingsController.java:39` |
| `/api/settings` | PUT | `TEAM_OVERSIGHT` | Update currency, brandColor, footer, integration flags, projectNamingPattern (line 45) |
| `/api/settings/logo` | POST | `TEAM_OVERSIGHT` | Multipart logo upload (line 61) |
| `/api/settings/logo` | DELETE | `TEAM_OVERSIGHT` | Logo deletion (line 68) |
| `/api/settings/compliance` | PATCH | `TEAM_OVERSIGHT` | dormancyThresholdDays, dataRequestDeadlineDays (line 74) |
| `/api/settings/tax` | PATCH | `TEAM_OVERSIGHT` | tax registration / label / inclusive (line 83) |
| `/api/settings/acceptance` | PATCH | `TEAM_OVERSIGHT` | acceptanceExpiryDays (line 96) |
| `/api/settings/time-reminders` | PATCH | `TEAM_OVERSIGHT` | reminder enabled/days/time/minMinutes (line 104) |
| `/api/settings/capacity` | PATCH | `TEAM_OVERSIGHT` | defaultWeeklyCapacityHours (line 117) |
| `/api/settings/batch-billing` | PATCH | `TEAM_OVERSIGHT` | async threshold, email rate limit, default currency (line 126) |
| `/api/settings/data-protection` | PATCH | `TEAM_OVERSIGHT` (svc enforces owner-only) | jurisdiction, retention, info officer (line 139) |
| `/api/settings/vertical-profile` | PATCH | `TEAM_OVERSIGHT` (svc enforces owner-only) | switch profile — triggers reconciler (line 147) |
| `/api/settings/portal-digest-cadence` | PATCH | `TEAM_OVERSIGHT` | WEEKLY / BIWEEKLY / OFF (line 155) |
| `/api/settings/portal-retainer-member-display` | PATCH | `TEAM_OVERSIGHT` | privacy mode (line 163) |

### `ModuleSettingsController` `→ backend/.../settings/ModuleSettingsController.java:24`

| Endpoint | Method | Capability | Purpose |
|----------|--------|------------|---------|
| `/api/settings/modules` | GET | `TEAM_OVERSIGHT` | List horizontal modules with enabled state (line 34) |
| `/api/settings/modules` | PUT | `TEAM_OVERSIGHT` | Replace `enabledModules` for horizontal modules; vertical modules preserved (line 44) |

Vertical modules (e.g. `trust_accounting`) are **not** togglable here — they follow `verticalProfile` selection (per controller javadoc, line 16-21). The split is deliberate: vertical modules are mandatory for their profile, horizontal modules are optional add-ons.

### Cross-link

- `GET /api/me/capabilities` — owned by `identity-access` (`MeController`); consumed by the same `CapabilityProvider` that the nav tree uses to filter sidebar items `→ A2-frontend-map.md:280`. See [`30-modules/identity-access.md`](./identity-access.md) §REST surface.

## Frontend pages / components

### Shell components (this module)

- `frontend/components/desktop-sidebar.tsx:1` — slate-950 dark sidebar, Motion animated active indicator. Reads `NAV_GROUPS` + `UTILITY_ITEMS`; injects `--brand-color` CSS var from `OrgSettings.brandColor` `→ A2-frontend-map.md:397`.
- `frontend/components/mobile-sidebar.tsx` — mobile drawer mirror.
- `frontend/components/command-palette-provider.tsx:1` — context provider, Cmd-K listener, dynamically imports the dialog `→ A2-frontend-map.md:399`.
- `frontend/components/command-palette-dialog.tsx` — the actual palette UI; sourced from the nav tree.
- `frontend/lib/nav-items.ts:72` — exports `NAV_GROUPS` (line 72), `UTILITY_ITEMS` (308), `SETTINGS_ITEMS` (the settings inner-nav), and a flattened `NAV_ITEMS` (line 493) for backwards compat. Each item carries `requiredCapability?: CapabilityName` (line 42) and `requiredModule?: string` (line 44) for hide-when-not-allowed behaviour.
- `frontend/lib/org-profile.tsx:27` — `OrgProfileProvider`, seeded from `GET /api/settings` in the org layout; carries `verticalProfile`, `enabledModules`, `terminologyNamespace` for downstream consumers `→ A6-cross-cutting.md:209`.
- `frontend/lib/terminology.tsx:24` — `TerminologyProvider`; consumes `verticalProfile` and provides `t("invoices") → "Fee Notes"` etc.
- `frontend/lib/terminology-map.ts:1` — the `TERMINOLOGY` object (`consulting-za` / `accounting-za` / `legal-za`).
- `frontend/components/module-gate.tsx:11` — `<ModuleGate module="trust_accounting">` wrapper; reads from `OrgProfileProvider` `→ A6-cross-cutting.md:205`.
- `frontend/lib/api/settings.ts:9,21,31,46,69` — typed client wrappers (`getSettings`, `isModuleEnabledServer`, `updateSettings`, `uploadLogo`, `deleteLogo`).

### Settings sub-pages (owned by other modules — listed for navigation completeness only)

The settings shell hosts a tree of sub-pages under `frontend/app/(app)/org/[slug]/settings/`. **This module owns the shell + the `general` page; the others are owned by their respective modules.** Cross-references in parentheses; do not duplicate content there.

| Path | Purpose | Owner |
|------|---------|-------|
| `settings/page.tsx` | Redirect to `/settings/general` | this module |
| `settings/layout.tsx` | Settings inner shell (renders `SETTINGS_ITEMS`) | this module |
| `settings/general/` | Org name, currency, brand color, logo, vertical profile selector (`VerticalProfileSection`) | this module |
| `settings/features/` | Module enable/disable toggles (horizontal only) | this module |
| `settings/tax/` | Tax label, registration, inclusive flag | invoicing |
| `settings/rates/` | BillingRate / CostRate management | invoicing |
| `settings/batch-billing/` | Billing-run defaults | invoicing |
| `settings/billing/` | Platform subscription (Kazi-as-tenant billing) | platform-administration |
| `settings/time-tracking/` | Time reminders, expense markup | time-entry |
| `settings/capacity/` | Default weekly capacity | capacity-planning |
| `settings/acceptance/` | Acceptance expiry days | proposals-acceptance |
| `settings/templates/` | Document template management | documents-templates |
| `settings/clauses/` | Clause library | documents-templates |
| `settings/checklists/` | Checklist templates | checklists |
| `settings/custom-fields/` | FieldDefinition / FieldGroup editor | custom-fields-tags-views |
| `settings/tags/` | Tag editor | custom-fields-tags-views |
| `settings/request-templates/` + `request-settings/` | Information-request templates + reminder defaults | information-requests |
| `settings/project-templates/` | Project template editor | project-templates |
| `settings/automations/` (incl. `ai-queue/`) | Automation rules + AI specialist queue | automation, ai-assistant |
| `settings/integrations/` | OrgIntegration adapter management | integration-ports |
| `settings/notifications/` | Notification prefs, email templates | notifications |
| `settings/email/` | Email branding + send-as config | notifications |
| `settings/data-protection/` | Retention, info officer, DSAR config | audit |
| `settings/compliance/` | Dormancy thresholds, deadlines | customer-lifecycle |
| `settings/trust-accounting/` | Trust account config (vertical-gated) | trust-accounting |
| `settings/audit-log/` | Audit log viewer | audit |
| `settings/packs/` | Installable content packs | packs |
| `settings/roles/` | Custom OrgRole editor | identity-access |
| `settings/project-naming/` | `projectNamingPattern` config | projects |

Anchor for the directory listing: `frontend/app/(app)/org/[slug]/settings/` (per directory listing).

## Domain events

None emitted directly by `OrgSettingsService`. Settings mutations are audit-logged inline via `auditService.log(...)` with `AuditDeltaBuilder` for per-field diffs `→ A6-cross-cutting.md:152`. The one indirect emission path is `updateVerticalProfile(...)` which triggers the profile reconciler in `vertical-profiles` (which may emit module-related events).

## Cross-cutting touchpoints

OrgSettings is **the** cross-cutting configuration store. Every module that needs per-tenant config reads it. Key read paths:

- **Module gating (`enabledModules`).** `VerticalModuleGuard.requireModule(...)` `→ backend/.../verticals/VerticalModuleGuard.java:23` reads `OrgSettings.enabledModules` and throws on miss. Used by every legal-vertical service (`TrustAccountService.java:26`, `TrustTransactionService.java:45`, `TrustReconciliationService.java:43`, `ClientLedgerService.java:28`). Frontend mirror: `<ModuleGate>` + `requiredModule` on nav items + `isModuleEnabledServer()` `→ A6-cross-cutting.md:193,205`.
- **Integration guard (`accountingEnabled` / `aiEnabled` / `documentSigningEnabled`).** `IntegrationGuardService.requireEnabled(domain)` `→ backend/.../integration/IntegrationGuardService.java:25` reads these three booleans for ACCOUNTING / AI / DOCUMENT_SIGNING (PAYMENT/EMAIL/KYC always pass; ADR-091).
- **Terminology (`terminologyNamespace`).** Backend stores the key only (`OrgSettings.java:184`); both staff and portal frontends ship a baked-in `TERMINOLOGY` map keyed by namespace. No `GET /api/settings/terminology` endpoint exists — the backend only tells the frontend which key to use `→ A6-cross-cutting.md:218`.
- **Branding (`brandColor`, `logoS3Key`).** Desktop sidebar injects `--brand-color`; invoice/proposal PDF renderers consume both; portal renders these via `PortalBrandingController`.
- **Currency (`defaultCurrency`).** Read by invoicing for invoice/quote currency defaulting.
- **Pack idempotency (`*PackStatus` columns).** Each `*PackSeeder` (FieldPackSeeder, RatePackSeeder, ProjectTemplatePackSeeder, SchedulePackSeeder, etc.) checks `OrgSettings.is*PackApplied(packId, version)` before installing.
- **Schedulers.** `TimeReminderScheduler`, `PortalDigestScheduler`, `RetentionPolicyService` all read OrgSettings on every tick.
- **Audit.** `OrgSettingsService` uses `AuditDeltaBuilder` heavily (per-field diffs land in `audit_log.details` JSONB) `→ A6-cross-cutting.md:152`.

## Vertical specifics

The verticalisation seam **lives here**. Per `_discovery/A6-cross-cutting.md:173`: "Kazi's vertical strategy is fork-the-template, not fork-the-codebase. Verticality is a runtime configuration of three orthogonal knobs: a *vertical profile* (string identifier), a list of *enabled modules*, and a *terminology namespace*. **All three live on `OrgSettings`.**"

- **`verticalProfile`** (`OrgSettings.java:177`) — string ID (`"consulting-za"`, `"accounting-za"`, `"legal-za"`); set via `PATCH /api/settings/vertical-profile`. UI: `VerticalProfileSection` in `settings/general/page.tsx`.
- **`enabledModules`** (line 181) — JSONB list of slugs; horizontal slugs toggleable via `PUT /api/settings/modules`, vertical slugs follow profile.
- **`terminologyNamespace`** (line 184) — string key into the frontend `TERMINOLOGY` map.

`updateVerticalProfile(...)` (line 877) atomically updates all three columns and bumps `updatedAt`. The reconciler logic (which packs to install / data to migrate) lives in `vertical-profiles` — see [`30-modules/vertical-profiles.md`](./vertical-profiles.md). UI: `settings/general/` (profile select), `settings/features/` (module toggles), `settings/trust-accounting/` (only shown when `trust_accounting` enabled, per `_discovery/A2-frontend-map.md:493`).

## Active ADRs

- **ADR-076** `→ adr/ADR-076-separate-portal-app.md` — module identity / portal-app boundary (cited by `10-bounded-contexts.md:355`).
- **ADR-091** `→ adr/ADR-091-feature-flag-scope.md` — `accountingEnabled` / `aiEnabled` / `documentSigningEnabled` are coarse domain-level booleans on OrgSettings; granular operation flags rejected.
- **ADR-092** `→ adr/ADR-092-auto-apply-strategy.md` — `FieldGroup.autoApply` retroactive-apply policy; relevant because the `field_pack_status` JSONB on OrgSettings is the install ledger that strategy reasons about.
- **ADR-249** `→ adr/ADR-249-retention-clock-starts-on-closure.md` — `legalMatterRetentionYears` field semantics.
- **ADR-255** `→ adr/ADR-255-portal-retainer-member-display.md` — `portalRetainerMemberDisplay` enum.
- **ADR-258** `→ adr/ADR-258-portal-notification-no-double-send.md` — `portalDigestCadence` + `digestLastSentAt` skip-window logic.
- **ADR-039** / **ADR-073** — rate-resolution-hierarchy / standard-billing-rate-for-overage. Listed because rate cards appear under `/settings/rates`, but the rate aggregates themselves are owned by `invoicing` — these ADRs cross-link to that module.

## Key flows

No dedicated flow page. Settings mutations are simple CRUD on a single aggregate, gated by `TEAM_OVERSIGHT` (or owner-only for vertical-profile + data-protection). The one non-trivial sequence is **vertical-profile change → reconciler → pack reinstall**, which is documented in [`30-modules/vertical-profiles.md`](./vertical-profiles.md) and `_discovery/A6-cross-cutting.md` §6 — pointer added when `50-flows/` is written.

## Open questions / known fragility

1. **OrgSettings as a god-object.** ~40 columns across 12 distinct concerns. It is the most-referenced shared entity in the backend (~30+ consumer files via grep on `orgSettings`). Migration cost is high (every column change is a Flyway tenant migration), and the entity ignores the "feature package" convention. A future refactor could split by concern (`OrgBranding`, `OrgTaxConfig`, `OrgRetentionConfig`, `OrgPortalConfig`, etc.) but no ADR proposes this. Track as drift risk; do not split casually — the single-row-per-tenant pattern is what makes the read paths cheap.

2. **Rate-card ownership glossary drift.** Glossary line 313 (`Rate Card`) already correctly disambiguates — "Rate Card" is a UI label, the type is `BillingRate`, owned by `invoicing`. Backend confirms: dedicated `billingrate/` and `costrate/` packages, controllers `/api/billing-rates` and `/api/cost-rates`. **No drift.** This module owns `ratePackStatus` (install log) only. Update path: if anyone misreads "Rate Pack" as belonging here, redirect to `invoicing` + the glossary disambiguation.

3. **Settings sub-pages owned by other modules.** The shell + `SETTINGS_ITEMS` nav tree are owned here; the per-concern pages are owned by their respective modules (table in §Frontend). Each module's page should describe its `settings/<concern>/` UI; this page should not duplicate. When new settings sub-pages land, the rule is: nav entry goes in `SETTINGS_ITEMS` (this module), page implementation goes in the owning module's page. Drift risk: contributors adding sub-pages without registering them in `SETTINGS_ITEMS` (silent navigation gap) or adding nav entries without backing pages (404).

4. **Two controllers both rooted under `/api/settings`.** `OrgSettingsController` and `ModuleSettingsController` (which adds `/modules`). Splitting the module endpoints out is reasonable (different concern, different lifecycle) but means readers must know to check both files. Document via this module page; do not consolidate.

5. **`OrgSettingsController` thin-controller violation.** Listed in `backend/CLAUDE.md` as a known TD-009 case predating ArchUnit enforcement. New endpoints must follow the pure-delegation rule even though existing methods do not. Do not pattern-match off this controller.
