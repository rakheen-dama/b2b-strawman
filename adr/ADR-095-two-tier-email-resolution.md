# ADR-095: Two-Tier Email Resolution

**Status**: Accepted

**Context**:

DocTeams needs email delivery that works out of the box (platform SMTP via `JavaMailSender`) while allowing orgs to bring their own SendGrid API key (BYOAK) for higher volume or custom deliverability. The `IntegrationRegistry` resolves adapters per domain: it checks for an `OrgIntegration` row, and if none exists (or it is disabled), falls back to the adapter registered with slug `"noop"`. This works for ACCOUNTING, AI, DOCUMENT_SIGNING, and PAYMENT where "no integration configured" correctly means "do nothing." For EMAIL, "no integration configured" should mean "use platform SMTP" — not "do nothing."

The decision is: how should `IntegrationRegistry` resolve the platform SMTP default when no org-level EMAIL integration exists? The solution must be generic (not EMAIL-specific branching in the registry), backward-compatible (existing domains still fall back to `"noop"`), and handle local/dev environments where no SMTP server is available.

**Options Considered**:

1. **Change the hardcoded default slug from "noop" to "smtp" for all domains** — All domains would fall back to `"smtp"` when no integration is configured.
   - Pros:
     - Simplest code change (one-line change in `IntegrationRegistry`).
   - Cons:
     - Breaks existing domains: ACCOUNTING, AI, DOCUMENT_SIGNING, and PAYMENT have no `"smtp"` adapter registered, so resolution would throw `IllegalStateException`.
     - Semantically wrong: the default for non-email domains is genuinely "do nothing."
     - Would require registering dummy `"smtp"` adapters for every domain.

2. **Per-domain default slug via `IntegrationDomain` enum (chosen)** — Add a `defaultSlug` field to `IntegrationDomain`. Each domain declares its own fallback: `EMAIL("smtp")`, all others `("noop")`. `IntegrationRegistry.resolve()` uses `domain.getDefaultSlug()` instead of hardcoded `"noop"`.
   - Pros:
     - Generic mechanism — any future domain can declare a non-noop default (e.g., a built-in AI model).
     - Backward-compatible: existing domains explicitly declare `"noop"`, behavior unchanged.
     - The registry has no domain-specific branching — resolution logic stays clean.
     - The default is self-documenting: looking at the enum tells you what happens when no integration is configured.
   - Cons:
     - Minor: enum constructor changes from no-arg to single-arg (all existing values must add `("noop")`).

3. **Auto-provision an `OrgIntegration` row for every tenant with platform SMTP** — During tenant provisioning, create an `OrgIntegration(domain=EMAIL, providerSlug="smtp", enabled=true)` row.
   - Pros:
     - No changes to `IntegrationRegistry` resolution logic.
     - Explicit: every org has a visible EMAIL integration row.
   - Cons:
     - Requires a migration to backfill existing tenants.
     - Creates mandatory infrastructure (a row that must exist for email to work) — fragile if the row is accidentally deleted.
     - Conflates "platform default" with "org-configured integration" — the `OrgIntegration` table should represent org choices, not platform defaults.
     - Doesn't solve the local/dev problem (still need `NoOpEmailProvider` conditional logic).

**Decision**: Option 2 — Per-domain default slug via `IntegrationDomain` enum.

**Rationale**:

Option 2 is the cleanest separation of concerns. The `IntegrationDomain` enum is the right place to declare "what happens when no org-level integration exists" because it is the domain's inherent property, not a registry configuration. The registry stays generic — it reads `domain.getDefaultSlug()` and resolves accordingly, with no special cases.

For local/dev environments: `SmtpEmailProvider` is annotated with `@ConditionalOnProperty(name = "spring.mail.host")`, so it only registers when SMTP configuration is present. `NoOpEmailProvider` is annotated with `@ConditionalOnMissingBean(SmtpEmailProvider.class)` and also uses slug `"smtp"`. This means the `EMAIL` domain's default slug `"smtp"` always resolves to _something_ — the real SMTP adapter in production, or the no-op adapter in development. The slug `"noop"` is not used for EMAIL at all, which is conceptually correct: there is no scenario where an EMAIL domain should "do nothing" in production.

Option 3 was rejected because it introduces a mandatory database row as infrastructure — a pattern that breaks the principle that platform defaults should not require per-tenant configuration. If the row is deleted, email stops working silently. Option 1 was rejected because it is not backward-compatible and semantically incorrect for non-email domains.

**Consequences**:

- Positive:
  - Every org gets working email with zero configuration.
  - `IntegrationRegistry` remains domain-agnostic — no EMAIL-specific code.
  - Future domains with built-in providers can use the same pattern.
  - Local/dev environments automatically get `NoOpEmailProvider` without explicit profile switching.

- Negative:
  - `IntegrationDomain` enum constructor changes — all existing values must add the `("noop")` default slug argument. Low-risk, mechanical change.

- Neutral:
  - `SmtpEmailProvider` and `NoOpEmailProvider` share the `"smtp"` slug (only one is active at a time via Spring conditional annotations). This is slightly unusual but well-documented and testable.

- Related: [ADR-056](ADR-056-pdf-engine-selection.md) (Thymeleaf engine pattern reused), Phase 21 integration port design.
