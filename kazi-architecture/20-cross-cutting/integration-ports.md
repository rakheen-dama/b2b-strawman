# Integration Ports

## What this concern covers

The hexagonal port pattern is the **single conduit** through which Kazi reaches any external system — a payment processor, an SMTP relay, an accounting platform, an LLM, a KYC bureau, a future e-sign vendor. Six canonical domains are enumerated as the `IntegrationDomain` enum (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationDomain.java:4`): `PAYMENT`, `EMAIL`, `KYC_VERIFICATION` (foundational, always-on), and `ACCOUNTING`, `AI`, `DOCUMENT_SIGNING` (feature-flagged, opt-in). Each domain has a port interface, one or more `@IntegrationAdapter`-annotated Spring-bean adapters, a runtime registry that resolves a `(domain, slug)` tuple to an adapter per-tenant, and a fail-closed feature gate. Per-tenant API keys are stored **BYOAK** ("bring-your-own-API-key") — encrypted at rest with AES-GCM in the per-tenant `org_secrets` table, never on the integration row itself. At the export boundary for accounting (Phase 71 / Xero), an additional fail-closed guard refuses any invoice that touches a trust account (ADR-276) — the third defence of trust-money segregation, after the module gate and the capability gate.

This page synthesises *how the pattern threads through every other module*. The mechanism's internal surface (entities, REST, registry internals) lives in [`30-modules/integration-ports.md`](../30-modules/integration-ports.md).

## The pattern, end-to-end

The path of an integration call, from a domain service to an external HTTP request:

1. **Caller is a domain service holding the port interface.** `InvoicingService` injects an `AccountingProvider` lookup; `NotificationService` injects an `EmailSender`; the AI assistant streams through `LlmChatProvider`. Callers never reference a concrete adapter class — they ask the registry for the port.
2. **`IntegrationGuardService.requireEnabled(domain)` gates the call.** `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationGuardService.java:25`. `PAYMENT` / `EMAIL` / `KYC_VERIFICATION` early-return at line 26-30 (every tenant has these by definition). `ACCOUNTING` / `AI` / `DOCUMENT_SIGNING` are checked against `OrgSettings.isAccountingEnabled() / isAiEnabled() / isDocumentSigningEnabled()` (line 38-44); on miss, `IntegrationDisabledException` → HTTP 403.
3. **`IntegrationRegistry.resolve(domain, port)` selects the adapter.** `→ integration/IntegrationRegistry.java:62`. Reads `RequestScopes.TENANT_ID` (asserts bound at line 63), looks up `OrgIntegration` for the `(tenant, domain)` row via Caffeine cache (60s TTL), reads `providerSlug`, returns the bean cast to the port interface. Three fallbacks (lines 82-101) — empty cache entry, disabled row, slug-not-registered — all default to the domain's `noop` slug. Production code never sees a `null` adapter.
4. **Adapter loads its API key from `SecretStore`.** Concrete adapter calls `secretStore.retrieve(IntegrationKeys.apiKey(domain, slug))` (`→ integration/IntegrationKeys.java:15`). `EncryptedDatabaseSecretStore` (`→ integration/secret/EncryptedDatabaseSecretStore.java:19`) decrypts AES-GCM ciphertext from `org_secrets` (per-tenant schema). Master key is environment-supplied via `integration.encryption-key`, validated to 32 bytes at `@PostConstruct` (line 42-56).
5. **(Accounting only) `TrustBoundaryGuard.evaluate(invoice)` runs.** Inside the Phase 71 sync pipeline, before any HTTP push to Xero (`→ architecture/phase71-xero-accounting-integration.md:296`). Refusal short-circuits with `BLOCKED_TRUST_BOUNDARY` and an audit event — no network call is ever made. See §5.
6. **Adapter makes the external HTTP call.** Provider-specific HTTP plumbing lives entirely inside the adapter package (`integration/accounting/xero/`, `integration/payment/payfast/`, etc.). Domain code is unchanged whether the tenant is on Xero, no-op, or a future Sage adapter.
7. **Webhook return path bypasses tenant context.** Inbound webhooks (PSP confirmations, future Xero notifications) arrive *before* any tenant scope is bound. `IntegrationRegistry.resolveBySlug(domain, slug, port)` (`→ integration/IntegrationRegistry.java:131`) is the security-sensitive entry point — caller must allowlist the slug before passing it. Tenant resolution is deferred to payload-driven lookup (provider transaction ID → `PaymentEvent` → tenant). [ADR-096](../../adr/ADR-096-webhook-tenant-identification.md) / [ADR-099](../../adr/ADR-099-webhook-tenant-identification-payments.md) frame this.

## Domains and providers (current state)

Maturity differs sharply per domain — `PAYMENT` and `EMAIL` are mature; `ACCOUNTING` is in flight (Phase 71); `DOCUMENT_SIGNING` and `KYC_VERIFICATION` ship only the `noop` adapter today.

| Domain | Always-on? | Providers (current) | Adapter location | Port |
|---|---|---|---|---|
| `PAYMENT` | yes | `noop`, `mock`, `payfast`, `stripe` | `integration/payment/` | `PaymentGateway` (ADR-098) |
| `EMAIL` | yes | `smtp` (default), Mailpit (dev) | `integration/email/` | Email-sender port |
| `KYC_VERIFICATION` | yes | `noop` only | `integration/kyc/` | `KycVerificationStatus` enum on the port |
| `ACCOUNTING` | feature-flagged | `noop`, `xero` (Phase 71, in flight) | `integration/accounting/`; Xero adapter under `integration/accounting/xero/` | `AccountingProvider` (`→ integration/accounting/AccountingProvider.java:9`) |
| `AI` | feature-flagged | `anthropic` (BYOAK; only impl today) | `assistant/provider/` (LLM chat) and `integration/ai/` (one-shot text) | `LlmChatProvider` (ADR-200) — separate from the integration-domain `AiProvider` |
| `DOCUMENT_SIGNING` | feature-flagged | `noop` only | `integration/signing/` (verify) | E-sign port |

Two things worth flagging on this table:

- **`AI` has two ports, not one** — `LlmChatProvider` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/provider/LlmChatProvider.java:12`) for streaming chat with tool use, and the integration-domain `AiProvider` for one-shot text. The split is per ADR-200 and is acknowledged in [`30-modules/ai-assistant.md`](../30-modules/ai-assistant.md). The BYOAK key still lives in `org_secrets` keyed by `IntegrationDomain.AI`.
- **`PAYMENT` is the most mature port** — four real adapters today. The accounting/Xero family is borrowing patterns from how PSP integration was built (especially the webhook-by-payload-not-header decision in ADR-096/099).

## Per-tenant configuration

The `OrgIntegration` entity (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/OrgIntegration.java:20`) is the durable per-tenant choice — one row per `(tenant, domain)`, fields `providerSlug`, `enabled`, `configJson` (jsonb, free-form provider config), `keySuffix` (last-6 chars for UI redaction). Tenant-schema-scoped — no `tenant_id` column, isolation comes from `search_path` (see [`multitenancy.md`](./multitenancy.md)).

[ADR-088](../../adr/ADR-088-integration-port-package-structure.md) frames the package layout (one sub-package per domain). [ADR-089](../../adr/ADR-089-tenant-scoped-adapter-resolution.md) frames the resolution-by-`OrgIntegration` model — every adapter is a Spring bean carrying `@IntegrationAdapter(domain=..., slug=...)`, scanned at boot, with duplicate `(domain, slug)` throwing `IllegalStateException` (`→ IntegrationRegistry.java:42`). [ADR-085](../../adr/ADR-085-auth-provider-abstraction.md) generalises the same pattern to auth providers (Clerk vs Keycloak vs mock) — sister mechanism, identical shape.

The settings UI (`frontend/app/(app)/org/[slug]/settings/integrations/page.tsx`) renders one card per domain, gated by `@RequiresCapability("TEAM_OVERSIGHT")` on every controller method (`→ integration/IntegrationController.java:30,36,42,51,59,66,73`). The card surface is uniform: pick provider slug → paste API key → click Test → toggle enabled.

## Encrypted secret storage (ADR-090)

The `SecretStore` port (`→ integration/secret/SecretStore.java:7`) abstracts read/write of API keys. `EncryptedDatabaseSecretStore` (`→ integration/secret/EncryptedDatabaseSecretStore.java:19`) is the only implementation:

- **Algorithm:** `AES/GCM/NoPadding`, 12-byte SecureRandom IV per write, 128-bit auth tag (`:23-25`, `:104-108`). Ciphertext + IV stored Base64 in `org_secrets`.
- **Master key:** environment-supplied via `integration.encryption-key` (Base64, 32 bytes). Validated at `@PostConstruct` (`:42-56`); the backend refuses to start if absent or wrong length.
- **Storage substrate:** Postgres, per-tenant `org_secrets` table — deliberately not Vault, not KMS. Small dependency footprint is the explicit trade-off.
- **Key naming:** `IntegrationKeys.apiKey(domain, slug)` (`→ integration/IntegrationKeys.java:15`) yields `{domain}:{slug}:api_key`. Single source of truth so domain code and the `keySuffix` redaction in `OrgIntegration` agree.
- **Versioning:** `OrgSecret.keyVersion` exists (currently always `1`) but no rotation job is implemented — see §9.

DSAR exports explicitly redact `org_secrets` (the *fact of* a connected integration is exported, but never its credentials). Cross-link: [`data-protection.md`](./data-protection.md), [`30-modules/integration-ports.md`](../30-modules/integration-ports.md) §6.

## The trust hard guard (ADR-276)

Independent of `IntegrationDomain.ACCOUNTING` being enabled, **`TrustBoundaryGuard.evaluate(invoice)` runs inside the Phase 71 sync pipeline before any push to Xero** (`→ architecture/phase71-xero-accounting-integration.md:296`). On refusal: the sync entry is marked `BLOCKED_TRUST_BOUNDARY`, audit event `integration.xero.push_blocked_trust` is emitted, no HTTP call is ever made.

The guard is described in the phase doc as "a regulatory safeguard mandated by the Legal Practice Act Section 86 ... deterministic Java code -- no LLM, no AI, no human bypass" (`→ architecture/phase71-xero-accounting-integration.md:785`). Three refusal conditions (`:793-795`):

1. Invoice flagged trust.
2. Any line item drawn from a trust account.
3. Customer has active trust balances.

If any DB lookup throws, the guard refuses (`:799`) — fail-closed posture. **There is no override, no setting, no admin bypass.**

The point is subtle and worth stating in cross-cutting terms: the hard guard is **not** a refusal to *create* a trust invoice in Kazi — that is the legal vertical's whole reason for existing. It is a refusal to *export* a trust invoice to a general-ledger system that has no concept of statutory trust segregation. **The line is drawn at the integration boundary, not at the domain boundary.** Co-mingling client trust money with the firm's general ledger is the precise failure mode that loses a law firm its practising certificate; the guard makes that mode unreachable at the export seam.

This is the **third defence** of trust accounting, layered on top of:

- the `trust_accounting` module gate (services self-check via `verticalModuleGuard.requireModule("trust_accounting")` — see [`multi-vertical.md`](./multi-vertical.md) and [`30-modules/vertical-profiles.md`](../30-modules/vertical-profiles.md));
- the `MANAGE_TRUST` / `APPROVE_TRUST_PAYMENT` capability gates (see [`auth-and-rbac.md`](./auth-and-rbac.md) and [`30-modules/identity-access.md`](../30-modules/identity-access.md));
- and now, the export-boundary guard described here.

Cross-link: [`30-modules/trust-accounting.md`](../30-modules/trust-accounting.md), [`30-modules/invoicing.md`](../30-modules/invoicing.md).

## Phase 71 Xero family — ADR cluster

Phase 71 (in flight per `architecture/phase71-xero-accounting-integration.md:1`) is the first non-trivial accounting adapter and the worked example for "what plugging in a real provider looks like". Eight ADRs cluster around it:

- [ADR-272](../../adr/ADR-272-xero-only-accounting-adapter-v1.md) — **Xero-only adapter for v1.** Sage / QuickBooks deferred until a second tenant asks. YAGNI on multi-provider abstraction.
- [ADR-273](../../adr/ADR-273-one-way-accounting-sync-permanent.md) — **One-way push, Kazi → Xero, permanent.** Bidirectional sync explicitly out of scope, not deferred.
- [ADR-274](../../adr/ADR-274-dedicated-accounting-sync-service-not-rule-engine.md) — **Dedicated `AccountingSyncService`**, listening for `InvoiceApprovedEvent` / `InvoiceSentEvent` and enqueueing to its own queue table. **Not** the Phase 37 automation rule engine — that is for tenant-authored automations, not platform-authored sync.
- [ADR-275](../../adr/ADR-275-oauth2-augmentation-org-integration.md) — **OAuth2 metadata augments `OrgIntegration` via a sibling table** (`AccountingXeroConnection`: `xero_tenant_id`, `access_token_expires_at`, `last_token_refresh_at`, `status`, `last_poll_at` — `phase71-xero-accounting-integration.md:43-56`). Tokens still live in `SecretStore`. A future Sage adapter would get its own connection table — `OrgIntegration` stays generic.
- [ADR-276](../../adr/ADR-276-trust-accounting-hard-guard-export.md) — **Trust hard guard at the export boundary** (described in §5).
- [ADR-277](../../adr/ADR-277-poll-over-webhooks-payment-reconciliation-v1.md) — **Polling, not webhooks, for payment reconciliation.** `last_poll_at` cursor on the connection row. Webhooks deferred for v1 — Xero webhook tenant identification would have re-opened ADR-096/099.
- [ADR-278](../../adr/ADR-278-idempotent-push-via-external-reference.md) — **Idempotent push via `external_reference` field** on the sync entry. Re-push is safe.
- [ADR-279](../../adr/ADR-279-sibling-payment-source-port.md) — **Sibling payment-source port for accounting payment pull.** `AccountingProvider` (push) and `AccountingPaymentSource` (pull) are deliberately split — interface segregation: a `noop` accounting adapter that does not pull payments shouldn't have to implement an empty `pullPayments()`. The split also matters for trust disbursements where the source-of-truth is the accounting system.

Eight ADRs is a lot for one phase, but each carves a separable design choice — the cluster is the cost of getting accounting right the first time.

## Modules affected

Every module that talks to anything outside the JVM threads through this concern. Each module page documents its specific adapter consumption pattern; this section names the touchpoints:

- [`30-modules/invoicing.md`](../30-modules/invoicing.md) — `InvoiceApprovedEvent` is the upstream trigger for accounting push (Phase 71). Invoicing itself does not call the adapter; `AccountingSyncService` is the listener.
- [`30-modules/trust-accounting.md`](../30-modules/trust-accounting.md) — owns the data the trust hard guard reads. The guard is implemented inside the integration-ports module but reads trust state owned by the trust-accounting module.
- [`30-modules/ai-assistant.md`](../30-modules/ai-assistant.md) — BYOAK consumer; `LlmChatProviderRegistry` resolves the streaming-chat adapter from `OrgIntegration`. AI is feature-flagged at `IntegrationGuardService.requireEnabled(AI)` before any chat call.
- [`30-modules/customer-portal.md`](../30-modules/customer-portal.md) — every portal email (magic link, document share, invoice notification) routes through the EMAIL adapter. `PortalEmailNotificationChannel` is the in-module emitter.
- [`30-modules/customer-lifecycle.md`](../30-modules/customer-lifecycle.md) — KYC verification calls go through the `KYC_VERIFICATION` adapter (today only `noop`, but the seam is wired for a real bureau when one is selected).
- [`30-modules/notifications.md`](../30-modules/notifications.md) — internal notifications fan out to in-app + EMAIL adapter on `AFTER_COMMIT`.

## Active ADRs

- [ADR-085](../../adr/ADR-085-auth-provider-abstraction.md) — auth provider abstraction (sister pattern; identical shape, different concern).
- [ADR-088](../../adr/ADR-088-integration-port-package-structure.md) — `integration/<domain>/` package layout.
- [ADR-089](../../adr/ADR-089-tenant-scoped-adapter-resolution.md) — tenant-scoped adapter resolution via `OrgIntegration.providerSlug` + boot-time scan.
- [ADR-090](../../adr/ADR-090-secret-storage-strategy.md) — encrypted-at-rest secret storage; AES-GCM in Postgres, no Vault dependency.
- [ADR-098](../../adr/ADR-098-payment-gateway-interface-design.md) — `PaymentGateway` port shape; the original integration-domain port that established the conventions.
- [ADR-272](../../adr/ADR-272-xero-only-accounting-adapter-v1.md) — Xero is the only v1 accounting adapter.
- [ADR-273](../../adr/ADR-273-one-way-accounting-sync-permanent.md) — one-way push, no bidirectional.
- [ADR-274](../../adr/ADR-274-dedicated-accounting-sync-service-not-rule-engine.md) — dedicated `AccountingSyncService`, not the Phase 37 rule engine.
- [ADR-275](../../adr/ADR-275-oauth2-augmentation-org-integration.md) — OAuth2 metadata augments `OrgIntegration` via a sibling table.
- [ADR-276](../../adr/ADR-276-trust-accounting-hard-guard-export.md) — trust hard guard on accounting export.
- [ADR-277](../../adr/ADR-277-poll-over-webhooks-payment-reconciliation-v1.md) — polling for payment reconciliation, v1.
- [ADR-278](../../adr/ADR-278-idempotent-push-via-external-reference.md) — idempotent push keyed by external reference.
- [ADR-279](../../adr/ADR-279-sibling-payment-source-port.md) — sibling payment-source port for accounting payment pull.
- [ADR-096](../../adr/ADR-096-webhook-tenant-identification.md) / [ADR-099](../../adr/ADR-099-webhook-tenant-identification-payments.md) — webhook tenant identification (governs `IntegrationRegistry.resolveBySlug` and the payment webhook path).
- [ADR-097](../../adr/ADR-097-rate-limiting-implementation.md) — cross-cutting frame for rate limiting (largely unimplemented at the integration layer; see §9).

## Known fragilities / open questions

1. **Webhook tenant identification (ADR-096 / ADR-099) — design tension.** `IntegrationRegistry.resolveBySlug(...)` (`→ integration/IntegrationRegistry.java:131`) exists because inbound webhooks arrive before any tenant scope is bound. Header-trust is forbidden by the multitenancy concern; the only signal is in the payload (provider transaction ID → `PaymentEvent` → tenant). The doc-comment at `:120-127` warns explicitly that callers must allowlist the slug before passing it. A regression here would let a malicious request enumerate registered adapters via error messages. Future Xero webhooks (deferred per ADR-277) will re-open this design.
2. **Rate limiting at the integration layer — likely unimplemented.** [ADR-097](../../adr/ADR-097-rate-limiting-implementation.md) is the cross-cutting frame, but per-adapter rate limits (Xero rate-limits per Xero-tenant; Anthropic rate-limits per API key; PayFast has its own throttles) are not centralised. Each adapter handles its own backoff. No shared circuit-breaker, no shared leaky bucket.
3. **Adapter versioning when external API changes — no mechanism today.** A breaking Xero API change would require a coordinated `XeroAccountingProvider` upgrade across all tenants on the same JAR; there is no per-tenant adapter-version pin. `OrgIntegration.configJson` (`→ OrgIntegration.java:38`) is the natural place to carry an `apiVersion` field if/when needed, but nothing reads it today.
4. **Single accounting provider (Xero) — generalisation cost when the second arrives.** ADR-272 explicitly defers Sage / QuickBooks; ADR-275 notes that adding a second OAuth2 provider would mean a second sibling connection table, not a generic `OAuth2Connection`. The cost of YAGNI here is that the *second* accounting provider will involve refactoring the `accounting/` package; the cost of premature abstraction would be paying for two-providerness before any tenant asks for it. The trade is consciously made.
5. **No per-tenant adapter health surface (verify).** `testConnection()` is on-demand from the settings UI. There is no scheduled health check, no per-tenant "your Xero connection is failing" notification, no platform-admin dashboard of integration failure rates. Phase 71's sync log UI is the closest substitute (per-invoice retry visibility, scoped to accounting only). Cross-link: [`observability.md`](./observability.md).
6. **Key rotation cadence not enforced.** `EncryptedDatabaseSecretStore` supports `update(...)` and `OrgSecret.keyVersion` exists (always `1` today), but there is no rotation job, no platform-mandated cadence, no expiry on `org_secrets`. Rotating the master key today means a manual re-encryption sweep. Acceptable at firm-pilot scale because the master key is environment-supplied and rotation is rare; a known gap at scale. No ADR.
7. **Webhook signature verification is per-adapter.** Each adapter handles its own HMAC / signature check (e.g. PayFast ITN signature). There is no shared `WebhookSignatureVerifier` port — drift between adapters on what "good enough" means is possible. Cross-link to ADR-096 / ADR-099.
