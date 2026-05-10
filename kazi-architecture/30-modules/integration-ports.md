# Integration Ports

**Bounded context:** see [`10-bounded-contexts.md` § integration-ports](../10-bounded-contexts.md#integration-ports).
**Cross-cutting page:** [`20-cross-cutting/integration-ports.md`](../20-cross-cutting/integration-ports.md) — covers the pattern and how it threads through every other module. This page covers the implementation: what the registry owns, how an adapter plugs in, and which REST surface the UI talks to.

## 1. Purpose

Hexagonal port pattern for tenant-scoped adapter resolution. Every external system Kazi needs to talk to (PSP, email, accounting, AI, KYC, e-signing) is reached through a port interface implemented by one or more Spring-bean adapters, registered with a `(domain, slug)` discriminator and resolved per-tenant from `OrgIntegration`. Sensitive credentials are stored as **BYOAK** ("bring-your-own-API-key") — encrypted at rest in the per-tenant `org_secrets` table, never on the integration row itself.

Three loadbearing properties:

1. **Per-tenant choice.** Tenant A can be on Xero, Tenant B on the no-op. The choice lives on `OrgIntegration.providerSlug` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/OrgIntegration.java:30`) and is read by `IntegrationRegistry.resolve(...)` per-call, with a 60s Caffeine cache (`→ integration/IntegrationRegistry.java:29`).
2. **Boot-time fail-fast.** Two adapters claiming the same `(domain, slug)` throw at startup (`→ integration/IntegrationRegistry.java:42`). No runtime "which-one-wins" surprise.
3. **Default-slug fallback.** Every domain ships a `noop` adapter; `IntegrationDomain.getDefaultSlug()` (`→ integration/IntegrationDomain.java:19`) is what the registry falls back to when the tenant has no row, the row is disabled, or the configured slug has no registered adapter (`→ integration/IntegrationRegistry.java:80-100`). Production code never hits a `null` adapter.

[ADR-088](../../adr/ADR-088-integration-port-package-structure.md) frames the package layout (one sub-package per domain). [ADR-089](../../adr/ADR-089-tenant-scoped-adapter-resolution.md) frames the resolution-by-`OrgIntegration` model. [ADR-085](../../adr/ADR-085-auth-provider-abstraction.md) generalises the same pattern to auth providers.

## 2. Entities owned

| Entity | Anchor | Notes |
|---|---|---|
| `OrgIntegration` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/OrgIntegration.java:20` | One row per `(tenant, domain)`. Fields: `domain`, `providerSlug`, `enabled`, `configJson` (jsonb, free-form per-provider config), `keySuffix` (last-6 chars of API key, for UI redaction). Tenant-schema scoped — no `tenant_id` column. |
| `IntegrationDomain` enum | `→ integration/IntegrationDomain.java:4` | Six values: `ACCOUNTING("noop"), AI("noop"), DOCUMENT_SIGNING("noop"), EMAIL("smtp"), KYC_VERIFICATION("noop"), PAYMENT("noop")`. Each carries a `defaultSlug`. |
| `OrgSecret` | `→ integration/secret/OrgSecret.java` | AES-GCM-encrypted blob. Per-tenant `org_secrets` table. |
| `SecretStore` (port) | `→ integration/secret/SecretStore.java:7` | `store / retrieve / delete / exists`. |
| `EncryptedDatabaseSecretStore` | `→ integration/secret/EncryptedDatabaseSecretStore.java:19` | Sole implementation. `AES/GCM/NoPadding`, 12-byte IV, 128-bit tag (`:23-25`). Key from `integration.encryption-key` env var, validated to 32 bytes at `@PostConstruct` (`:42-56`). |
| `@IntegrationAdapter` | `→ integration/IntegrationAdapter.java:14` | Marker annotation: `domain()` + `slug()`. Spring beans annotated with this are auto-discovered by `IntegrationRegistry` at boot. |
| `IntegrationRegistry` | `→ integration/IntegrationRegistry.java:15` | Boot-time scan + `resolve(domain, port)` + `resolveBySlug(...)` (used by webhook controllers before tenant context is bound — `:131`) + `evict(...)`. |
| `IntegrationGuardService` | `→ integration/IntegrationGuardService.java:25` | `requireEnabled(domain)` — domain-level fail-closed gate read from `OrgSettings` flags (see §6). |
| `IntegrationKeys` | `→ integration/IntegrationKeys.java:15` | Helper that builds the SecretStore key string `{domain}:{slug}:api_key`. Single source of truth so domain code and UI key-suffix logic agree. |

Domain sub-packages (each owns its own port interface + at least one adapter):

| Sub-package | Port | Adapters |
|---|---|---|
| `integration/accounting/` | `AccountingProvider` (`→ integration/accounting/AccountingProvider.java:9`) | `NoOpAccountingProvider`, `XeroAccountingProvider` (Phase 71, in flight) |
| `integration/ai/` | (per-provider — Anthropic, OpenAI, etc.) | |
| `integration/email/` | (Email port + adapters) | |
| `integration/kyc/` | KYC verification adapters | `KycVerificationStatus` enum |
| `integration/payment/` | `PaymentGateway` (ADR-098 — interface design) | `NoOpPaymentGateway`, `MockPaymentGateway`, `PayFastPaymentGateway`, `StripePaymentGateway` |
| `integration/signing/` | E-sign port | |
| `integration/storage/` | `StorageService` port → S3 / in-memory test adapter |

## 3. REST surface

`/api/integrations` — controller `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationController.java:21`. All endpoints `@RequiresCapability("TEAM_OVERSIGHT")` (`:30, :36, :42, :51, :59, :66, :73`).

| Method | Path | Purpose | Anchor |
|---|---|---|---|
| GET | `/api/integrations` | List all `OrgIntegration` rows for the tenant. | `IntegrationController.java:31` |
| GET | `/api/integrations/providers` | Available `(domain → [slugs])` map from the registry. | `:37` |
| PUT | `/api/integrations/{domain}` | Upsert: pick `providerSlug` and free-form `configJson`. | `:43` |
| POST | `/api/integrations/{domain}/set-key` | BYOAK — store an API key in `SecretStore`. Updates `keySuffix` for UI redaction. | `:52` |
| POST | `/api/integrations/{domain}/test` | Call the resolved adapter's `testConnection()` and return a `ConnectionTestResult`. | `:60` |
| DELETE | `/api/integrations/{domain}/key` | Remove the stored API key. | `:67` |
| PATCH | `/api/integrations/{domain}/toggle` | Enable / disable without deleting config. | `:74` |

`AiSettingsController` (`→ integration/AiSettingsController.java`) exposes a sub-surface for AI-specific concerns (model list at `/api/settings/integrations/ai/models`).

## 4. Frontend pages / components

- `frontend/app/(app)/org/[slug]/settings/integrations/page.tsx` — Settings hub for the six domains. RSC; calls `listIntegrations()` and `listProviders()` from `frontend/lib/api/integrations.ts:16-21`. Renders one card per domain via `IntegrationCard`, with specialisations (`EmailIntegrationCard`, `PaymentIntegrationCard`, `KycIntegrationCard`).
- `frontend/lib/api/integrations.ts` — API client wrapping the seven endpoints above plus `getAiModels()` (`:50`).
- `frontend/components/integrations/IntegrationCard.tsx` — Generic card; reads `keySuffix` from `OrgIntegration` for "•••••• abc123" redaction, calls `setApiKey` / `deleteApiKey` / `testConnection` actions.

## 5. Domain events

None directly. The integration layer is *invoked from* other modules (`InvoiceApprovedEvent` listener pushes to Xero; AI specialist invocations call the AI adapter; portal email handlers call the email adapter), and emits **audit events** (e.g. `integration.xero.push_blocked_trust` per `architecture/phase71-xero-accounting-integration.md:296`) but does not publish domain events of its own. Sync state lives on the Phase 71 `AccountingSyncEntry` (architecture-doc §11.2; not yet in `backend/`).

## 6. Cross-cutting touchpoints

### IntegrationDomain enum and the foundational/optional split

Six domains. `IntegrationGuardService.requireEnabled(domain)` (`→ integration/IntegrationGuardService.java:25`) gates them in two tiers:

- **Foundational, always-on:** `PAYMENT`, `EMAIL`, `KYC_VERIFICATION` — early-return at line 26-30. Every tenant has these by definition; gating them would mean a tenant could disable their own ability to send a magic-link email.
- **Feature-flagged, opt-in:** `ACCOUNTING`, `AI`, `DOCUMENT_SIGNING` — checked against `OrgSettings.isAccountingEnabled() / isAiEnabled() / isDocumentSigningEnabled()` (`:38-44`). Default off (`.orElse(false)` at `:46`); on miss, throw `IntegrationDisabledException` → HTTP 403.

This split is opinionated and is the load-bearing choice: paying for Kazi includes payment/email/KYC; everything else is per-tenant opt-in.

### Adapter resolution (ADR-089)

Every adapter is a Spring bean carrying `@IntegrationAdapter(domain=..., slug=...)`. At boot, `IntegrationRegistry`'s constructor (`→ IntegrationRegistry.java:33-53`) scans `ApplicationContext.getBeansWithAnnotation(IntegrationAdapter.class)`, builds `Map<IntegrationDomain, Map<String, Object>>`, and throws `IllegalStateException` on duplicate `(domain, slug)`. At call-time, `resolve(domain, portInterface)` (`:62`) reads `RequestScopes.TENANT_ID` (asserts bound at `:63`), looks up the `OrgIntegration` via Caffeine cache keyed by `tenantSchema:DOMAIN`, picks the configured slug, and casts to the port interface. Three fallbacks (lines 82-101): empty cache entry → default slug; disabled → default slug; configured slug not registered → default slug. `resolveBySlug(...)` (`:131`) is the webhook entry-point that bypasses tenant lookup — security-sensitive, with a doc-comment warning that callers must allowlist the slug before passing it (`:120-127`).

### Secret storage (ADR-090)

`SecretStore` is the abstraction; `EncryptedDatabaseSecretStore` is the only implementation. Database-backed (not Vault, not KMS) — a deliberate small-dependency choice. `AES/GCM/NoPadding` with a 12-byte SecureRandom IV per write (`→ EncryptedDatabaseSecretStore.java:104-108`); ciphertext + IV stored Base64 in `org_secrets`; the `key_version` column carries `1` to support future rotation. The encryption key comes from `integration.encryption-key` (Base64, 32 bytes); fails to start if absent or wrong length (`:42-56`). Per-tenant — `org_secrets` lives in the tenant schema. Keys follow `{domain}:{slug}:api_key` (`IntegrationKeys.apiKey(...)`).

### Phase 71 — Xero as the worked example

Phase 71 (in flight per `architecture/phase71-xero-accounting-integration.md:1`) is the first non-trivial accounting adapter and the reference walkthrough for "what plugging in a real provider looks like". It augments rather than replaces:

- **OAuth2 augmentation, not replacement of `OrgIntegration`** ([ADR-275](../../adr/ADR-275-oauth2-augmentation-org-integration.md)). A new `AccountingXeroConnection` table (one row per `OrgIntegration`) carries Xero-specific OAuth metadata: `xero_tenant_id`, `access_token_expires_at`, `last_token_refresh_at`, `status`, `last_poll_at` (`phase71-xero-accounting-integration.md:43-56`). Refresh and access tokens still live in `SecretStore` (`:39`). Sage Pastel, if it ever lands, would get its own connection table — `OrgIntegration` stays generic.
- **Dedicated sync service, not the rule engine** ([ADR-274](../../adr/ADR-274-dedicated-accounting-sync-service-not-rule-engine.md)). `AccountingSyncService` listens for `InvoiceApprovedEvent` / `InvoiceSentEvent` and enqueues to its own queue table — Phase 37's automation rule engine is for tenant-authored automations, not platform-authored sync.
- **One-way push, permanent** ([ADR-273](../../adr/ADR-273-one-way-accounting-sync-permanent.md)). Kazi → Xero only. Bidirectional sync is explicitly out of scope, not deferred.
- **Idempotent push via external reference** ([ADR-278](../../adr/ADR-278-idempotent-push-via-external-reference.md)). The `external_reference` field on the sync entry is the idempotency key — re-push is safe.
- **Polling, not webhooks, for payment reconciliation** ([ADR-277](../../adr/ADR-277-poll-over-webhooks-payment-reconciliation-v1.md)). `last_poll_at` cursor on the connection row.
- **Sibling port for payment pull** ([ADR-279](../../adr/ADR-279-sibling-payment-source-port.md)). The accounting push port (`AccountingProvider`) and the payment-source port (`AccountingPaymentSource`) are deliberately split — interface-segregation: a noop accounting adapter that doesn't pull payments shouldn't have to implement an empty `pullPayments()`.
- **Xero-only, v1** ([ADR-272](../../adr/ADR-272-xero-only-accounting-adapter-v1.md)). YAGNI on Sage / QuickBooks until there is a second tenant asking.

### Trust-accounting hard guard (ADR-276) — the cross-vertical interlock

Independent of `IntegrationDomain.ACCOUNTING` being enabled, **`TrustBoundaryGuard.evaluate(invoice)` runs inside the sync pipeline** before any push (`architecture/phase71-xero-accounting-integration.md:296`). If it refuses, the sync entry is marked `BLOCKED_TRUST_BOUNDARY`, audit event `integration.xero.push_blocked_trust` is emitted, and no HTTP call is ever made. The guard is "deterministic Java code -- no LLM, no AI, no human bypass" (`:785`), implementing the Legal Practice Act §86 obligation that trust money is never co-mingled with the firm's general ledger. Three refusal conditions (`:793-795`): invoice flagged trust, any line item from a trust account, customer has active trust balances. Fail-closed — if any DB lookup throws, refuse (`:799`). The guard is the **third defence** of trust accounting, on top of the `trust_accounting` module gate (see [`vertical-profiles.md`](./vertical-profiles.md) and [`20-cross-cutting/multi-vertical.md`](../20-cross-cutting/multi-vertical.md)) and the `MANAGE_TRUST` / `APPROVE_TRUST_PAYMENT` capability gates ([`identity-access.md`](./identity-access.md)). Cross-link to [`trust-accounting.md`](./trust-accounting.md) and [`invoicing.md`](./invoicing.md).

The point is subtle but worth stating: the trust hard guard is **not** a refusal to *create* a trust invoice in Kazi — that's the legal vertical's whole reason for existing. It's a refusal to *export* a trust invoice to a general-ledger system that has no concept of statutory trust segregation. The line is drawn at the integration boundary, not at the domain boundary.

## 7. Vertical specifics

The trust-accounting hard guard (ADR-276 above) is legal-vertical-driven. The accounting domain itself is profile-agnostic — the integration mechanism is identical for an accounting-za firm pushing to Xero and a consulting-za firm doing the same. It is the *content* being pushed that is gated: the `TrustBoundaryGuard` only ever has work to do on a tenant whose schema contains trust-accounting data, which by construction is only legal-za tenants. See [`60-verticals/legal-vertical.md`](../60-verticals/legal-vertical.md) for the per-vertical view.

## 8. Active ADRs

- [ADR-085](../../adr/ADR-085-auth-provider-abstraction.md) — Auth provider abstraction (sister pattern; same shape, different concern).
- [ADR-088](../../adr/ADR-088-integration-port-package-structure.md) — `integration/<domain>/` package layout.
- [ADR-089](../../adr/ADR-089-tenant-scoped-adapter-resolution.md) — Tenant-scoped adapter resolution via `OrgIntegration.providerSlug` + boot-time scan.
- [ADR-090](../../adr/ADR-090-secret-storage-strategy.md) — Encrypted-at-rest secret storage; AES-GCM in Postgres, no Vault dependency.
- [ADR-098](../../adr/ADR-098-payment-gateway-interface-design.md) — `PaymentGateway` port shape.
- [ADR-272](../../adr/ADR-272-xero-only-accounting-adapter-v1.md) — Xero is the only v1 accounting adapter.
- [ADR-273](../../adr/ADR-273-one-way-accounting-sync-permanent.md) — One-way push, no bidirectional.
- [ADR-274](../../adr/ADR-274-dedicated-accounting-sync-service-not-rule-engine.md) — Dedicated `AccountingSyncService`, not Phase 37 rule engine.
- [ADR-275](../../adr/ADR-275-oauth2-augmentation-org-integration.md) — OAuth2 metadata augments `OrgIntegration` via a sibling table.
- [ADR-276](../../adr/ADR-276-trust-accounting-hard-guard-export.md) — Trust-accounting hard guard on accounting export.
- [ADR-277](../../adr/ADR-277-poll-over-webhooks-payment-reconciliation-v1.md) — Polling for payment reconciliation, v1.
- [ADR-278](../../adr/ADR-278-idempotent-push-via-external-reference.md) — Idempotent push keyed by external reference.
- [ADR-279](../../adr/ADR-279-sibling-payment-source-port.md) — Sibling payment-source port for accounting payment pull.

## 9. Key flows

- [`50-flows/payment-receipt-to-trust-allocation.md`](../50-flows/payment-receipt-to-trust-allocation.md) — Uses the PAYMENT integration domain (PSP → `PaymentEvent` → trust ledger). Worked example of the foundational, always-on case.
- **Accounting push flow** — Not yet documented. The flow is: `InvoiceApprovedEvent` → `AccountingSyncService` enqueues → worker resolves `AccountingProvider` via `IntegrationRegistry` → `TrustBoundaryGuard.evaluate` → `provider.syncInvoice(...)` → idempotent retry on failure → `AccountingSyncEntry.state` updated → audit event. Source-of-truth is `architecture/phase71-xero-accounting-integration.md` §11.5; promoting to a `50-flows/` page when Phase 71 lands.

## 10. Open questions / known fragility

1. **Webhook tenant identification** — `IntegrationRegistry.resolveBySlug(...)` (`:131`) exists because inbound webhooks (PSP confirmations, future Xero webhooks) arrive before any tenant context is bound. The mapping back to a tenant is the load-bearing decision: header-trust is forbidden, so the only signal is in the webhook payload (provider transaction ID → look up `PaymentEvent` → derive tenant). [ADR-096](../../adr/ADR-096-webhook-tenant-identification.md) and [ADR-099](../../adr/ADR-099-webhook-tenant-identification-payments.md) frame this. The `resolveBySlug` doc-comment (`:120-127`) explicitly warns that the slug must be allowlisted before being passed in — a regression here would let a malicious request enumerate registered adapters via error messages.
2. **Rate limiting at the integration layer** — [ADR-097](../../adr/ADR-097-rate-limiting-implementation.md) is the cross-cutting frame. Per-adapter rate limits (Xero rate-limits aggressively per Xero-tenant; OpenAI rate-limits per API key) are not yet centralised; each adapter handles its own backoff.
3. **Adapter versioning when external API changes** — No mechanism today. A breaking Xero API change would require a coordinated `XeroAccountingProvider` upgrade across all tenants on the same JAR; there is no per-tenant adapter-version pin. The `OrgIntegration.configJson` (`OrgIntegration.java:38`) is the natural place to carry an `apiVersion` field if/when needed.
4. **Single accounting provider today** — Generalisation cost is real but not yet paid. ADR-272 explicitly defers Sage / QuickBooks; ADR-275 notes that adding a second OAuth2 provider would mean a second sibling connection table, not a generic `OAuth2Connection`. The cost of YAGNI here is that the *second* provider will involve refactoring the `accounting/` package; the cost of premature abstraction would be paying for two-providerness before any tenant asks.
5. **Key rotation for `EncryptedDatabaseSecretStore`** — `OrgSecret.keyVersion` exists (currently always `1`) but no rotation job is implemented. Rotating the master key today means a manual re-encryption sweep; for v1 this is acceptable because the master key is environment-supplied and rotation is rare, but it's a known gap.
6. **No per-tenant adapter health surface** — `testConnection()` is on-demand from the settings UI. There is no scheduled health check, no per-tenant "your Xero connection is failing" notification, no platform-admin dashboard of integration failure rates. Phase 71's sync log UI is the closest substitute (per-invoice retry visibility).
