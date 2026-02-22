# Phase 21 Ideation — Integration Ports, BYOAK & Feature Flags
**Date**: 2026-02-21

## Lighthouse Domain
- SA small-to-medium law firms (unchanged)
- This phase is vertical-agnostic — every fork needs clean integration boundaries

## Decision Rationale
Founder initiated this conversation with a clear architectural goal: **prevent vendor lock-in and spaghetti code**. Not about wiring up specific vendors — about establishing the structural discipline before the codebase grows further.

Key motivations (from founder):
1. Codebase is huge — integration-specific logic mixed into domain services will make it unmaintainable
2. Ports & Adapters pattern already proven by auth abstraction (Phase 20) and PSP mock (Phase 10)
3. S3 is the worst offender — AWS SDK types in 5+ domain services
4. BYOAK is the commercial angle — orgs choose their own accounting software, provide their own API key
5. Feature flags gate entire integration domains until enough vendors are wired up
6. Stubs are fine — real vendors are future single-epic efforts

## Key Design Preferences
1. **SecretStore abstraction** — founder questioned whether DB storage is secure enough for API keys. Agreed on AES-256-GCM with env-sourced master key for v1, with abstraction allowing future Vault/KMS migration
2. **Feature flags are simple booleans** — not a generic framework, just integration domain toggles on OrgSettings
3. **Stubs-first** — all new ports get NoOp implementations. Real vendor adapters are future epics.
4. **Option A scope** (no real vendor wiring) — keeps the phase focused on architecture

## Seven Integration Ports
1. `StorageService` — seal S3 leak (system-wide, not tenant-configurable)
2. `AccountingProvider` — invoice/customer sync (Xero, QuickBooks, Sage)
3. `AiProvider` — text gen, summarization, categorization (OpenAI, Anthropic)
4. `DocumentSigningProvider` — e-signature workflow (DocuSign, SigniFlow)
5. `PaymentProvider` — already exists (Phase 10), wrap in BYOAK registry
6. `NotificationChannel` — already exists (Phase 6.5), email stub needs real adapter
7. `SecretStore` — encrypted credential storage (DB v1, Vault future)

## Phase Roadmap (updated)
- Phase 20: Auth Abstraction & E2E Testing (in progress, auth abstraction done, E2E infra remaining)
- Phase 21: Integration Ports, BYOAK & Feature Flags (requirements written)
- Phase 22+: Candidates — Customer Portal Frontend, real vendor adapters (Xero, Stripe, SendGrid as individual epics)

## Architecture Notes
- `IntegrationRegistry` does tenant-scoped adapter resolution via `OrgIntegration` entity + Caffeine cache
- `@IntegrationAdapter(domain, slug)` annotation for adapter self-registration
- `@IntegrationGuard(domain)` or service check for feature flag enforcement
- New migration for `org_integrations` + `org_secrets` tables in tenant schema
- OrgSettings extended with 3 boolean flags
- Settings UI: card grid per domain, provider dropdown, masked API key entry, connection test
