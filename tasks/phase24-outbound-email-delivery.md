# Phase 24 — Outbound Email Delivery

Phase 24 wires up real email delivery across the entire DocTeams platform. Until now, the notification system (Phase 6.5) includes an `EmailNotificationChannel` stub that logs to console instead of sending, invoice "Send" transitions status without emailing the customer, and portal magic links require manual copy-paste of the token URL. This phase closes all three gaps and adds delivery tracking, bounce handling, rate limiting, and unsubscribe support.

The design introduces a **two-tier email architecture**: every org gets working email out of the box via platform-managed SMTP (`JavaMailSender`), and orgs that want higher volume or custom deliverability can bring their own SendGrid API key (BYOAK). The `EmailProvider` port interface follows the `IntegrationPort` / `@IntegrationAdapter` / `IntegrationRegistry` pattern established in Phase 21.

**Architecture doc**: `architecture/phase24-outbound-email-delivery.md`

**ADRs**:
- [ADR-095](../adr/ADR-095-two-tier-email-resolution.md) — Two-Tier Email Resolution (per-domain default slug in `IntegrationDomain`)
- [ADR-096](../adr/ADR-096-webhook-tenant-identification.md) — Webhook Tenant Identification (tenant schema in SendGrid `unique_args`)
- [ADR-097](../adr/ADR-097-rate-limiting-implementation.md) — Rate Limiting Implementation (Caffeine cache with sliding-window counters)

**Migration**: V41 tenant — `email_delivery_log` table with 4 indexes and 2 CHECK constraints.

**Dependencies on prior phases**: Phase 6 (Audit), Phase 6.5 (Notifications), Phase 7 (Portal), Phase 10 (Invoicing), Phase 12 (Document Templates / Thymeleaf), Phase 21 (Integration Ports).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 167 | EmailProvider Port + SMTP Adapter | Backend | -- | M | 167A, 167B | **Done** (PRs #348, #349) |
| 168 | Email Template Rendering | Backend | -- | M | 168A, 168B | |
| 169 | EmailNotificationChannel + Delivery Log + Migration | Backend | 167, 168 | L | 169A, 169B | |
| 170 | Invoice Delivery + Portal Magic Link Email | Backend | 169 | M | 170A, 170B | |
| 171 | SendGrid BYOAK + Bounce Webhooks | Backend | 169 | M | 171A, 171B | |
| 172 | Unsubscribe + Admin Endpoints | Backend | 169 | M | 172A, 172B | |
| 173 | Frontend — Email Toggle + Integration Card + Delivery Log | Frontend | 172 | M | 173A, 173B | |

---

## Dependency Graph

```
[E167A EmailProvider Port + VOs]  [E168A EmailTemplateRenderer + Base Template]
        |                                       |
[E167B SmtpEmailProvider + NoOp]  [E168B Per-Type Email Templates]
        |                                       |
        +-------------------+-------------------+
                            |
              [E169A Delivery Log Entity + Migration + RateLimiter]
                            |
              [E169B EmailNotificationChannel Production Wiring]
                            |
          +-----------------+--------------------+
          |                 |                    |
  [E170A InvoiceEmail]  [E171A SendGridProvider] [E172A Unsubscribe]
          |                 |                    |
  [E170B PortalEmail]   [E171B Webhook Handler]  [E172B Admin Endpoints]
                                                 |
                                         [E173A Email Toggle + Integration Card]
                                                 |
                                         [E173B Delivery Log Page]
```

**Parallel opportunities**:
- Epics 167 and 168 are fully independent — can start in parallel immediately.
- After 169B completes: Epics 170, 171, and 172 can start in parallel.
- 173A depends on 172B (admin endpoints must exist for stats display). 173B depends on 173A.

---

## Implementation Order

### Stage 0: Foundation (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a (parallel) | 167 | 167A | EmailProvider port interface + value objects + IntegrationDomain.EMAIL + defaultSlug refactor | **Done** (PR #348) |
| 0b (parallel) | 168 | 168A | EmailTemplateRenderer service + base.html layout template + test-email.html |
| 0c (parallel) | 167 | 167B | SmtpEmailProvider adapter + NoOpEmailProvider + Maven dependency + application properties | **Done** (PR #349) |
| 0d (parallel) | 168 | 168B | All 10 per-type email templates (8 notification groups + portal-magic-link + invoice-delivery) |

### Stage 1: Core enabling slice

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 1a | 169 | 169A | V41 migration + EmailDeliveryLog entity + repository + EmailDeliveryLogService + EmailRateLimiter |
| 1b | 169 | 169B | EmailNotificationChannel production wiring (replace stub) + notification type-to-template mapping + integration tests |

### Stage 2: Parallel feature tracks (after Stage 1)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 2a (parallel) | 170 | 170A | InvoiceEmailService + @TransactionalEventListener on InvoiceSentEvent + PDF attachment |
| 2b (parallel) | 170 | 170B | PortalEmailService + MagicLinkService integration + magic link template wiring |
| 2c (parallel) | 171 | 171A | SendGridEmailProvider adapter + sendgrid-java dependency + @ConditionalOnClass |
| 2d (parallel) | 172 | 172A | UnsubscribeService (HMAC tokens) + UnsubscribeController + confirmation HTML page |

### Stage 3: Webhook + Admin (after respective Stage 2 slices)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 3a (parallel) | 171 | 171B | EmailWebhookController + signature verification + delivery log updates + bounce notifications + audit events |
| 3b (parallel) | 172 | 172B | EmailAdminController (delivery-log, stats, test endpoints) + List-Unsubscribe headers in notification emails |

### Stage 4: Frontend

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 4a | 173 | 173A | Notification preferences email toggle + EmailIntegrationCard + server actions + API types |
| 4b | 173 | 173B | Email settings page + DeliveryLogTable + delivery stats + rate limit display |

### Timeline

```
Stage 0: [167A] // [168A] // [167B] // [168B]          (parallel, immediate)
Stage 1: [169A] --> [169B]                               (sequential, after Stage 0)
Stage 2: [170A] // [170B] // [171A] // [172A]            (parallel, after 169B)
Stage 3: [171B] // [172B]                                (parallel, after respective Stage 2)
Stage 4: [173A] --> [173B]                               (sequential, after 172B)
```

**Critical path**: 167A → 167B → 169A → 169B → 172A → 172B → 173A → 173B

---

## Epic 167: EmailProvider Port + SMTP Adapter

**Goal**: Establish the email provider abstraction layer with the `EmailProvider` port interface, all value objects, the `EMAIL` domain in `IntegrationDomain` with per-domain default slug refactoring, and the platform SMTP adapter (`SmtpEmailProvider`) and local/dev fallback (`NoOpEmailProvider`).

**References**: Architecture doc Sections 24.2.2 (value objects), 24.2.3 (EmailDeliveryStatus enum), 24.2.4 (IntegrationDomain extension), 24.3.1 (EmailProvider port, SmtpEmailProvider, NoOpEmailProvider, resolution logic).

**Dependencies**: None — this is the foundation epic.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **167A** | 167.1–167.6 | `EmailProvider` port interface + `EmailMessage`, `EmailAttachment`, `SendResult`, `RenderedEmail` value objects + `EmailDeliveryStatus` enum + `IntegrationDomain.EMAIL` with `defaultSlug` field + `IntegrationRegistry.resolve()` refactor to use `domain.getDefaultSlug()` + integration tests for backward compatibility. ~6 new files, ~2 modified files. Backend only. | **Done** (PR #348) |
| **167B** | 167.7–167.13 | `SmtpEmailProvider` with `@ConditionalOnProperty` + `NoOpEmailProvider` with `@ConditionalOnMissingBean` + `spring-boot-starter-mail` Maven dependency + `docteams.email.*` application properties + GreenMail test dependency + integration tests. ~3 new files, ~2 modified files. Backend only. | **Done** (PR #349) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 167.1 | Create `EmailProvider` port interface | 167A | | New file: `integration/email/EmailProvider.java`. Methods: `String providerId()`, `SendResult sendEmail(EmailMessage message)`, `SendResult sendEmailWithAttachment(EmailMessage message, EmailAttachment attachment)`, `ConnectionTestResult testConnection()`. Pattern: existing `AccountingProvider` interface in `integration/accounting/`. |
| 167.2 | Create value objects: `EmailMessage`, `EmailAttachment`, `SendResult`, `RenderedEmail` | 167A | | New files (4 records): `integration/email/EmailMessage.java`, `integration/email/EmailAttachment.java`, `integration/email/SendResult.java`, `integration/email/RenderedEmail.java`. All Java records. `EmailMessage` includes `withTracking()` factory method enforcing required metadata keys (`referenceType`, `referenceId`, `tenantSchema`). See architecture doc Section 24.2.2. |
| 167.3 | Create `EmailDeliveryStatus` enum | 167A | | New file: `integration/email/EmailDeliveryStatus.java`. Values: `SENT`, `DELIVERED`, `BOUNCED`, `FAILED`, `RATE_LIMITED`. |
| 167.4 | Add `EMAIL` to `IntegrationDomain` with `defaultSlug` field | 167A | | Modify: `integration/IntegrationDomain.java`. Add `defaultSlug` field to enum constructor. Change existing values: `ACCOUNTING("noop")`, `AI("noop")`, `DOCUMENT_SIGNING("noop")`, `PAYMENT("noop")`. Add `EMAIL("smtp")`. Add `getDefaultSlug()` getter. See ADR-095. |
| 167.5 | Refactor `IntegrationRegistry.resolve()` to use `domain.getDefaultSlug()` | 167A | 167.4 | Modify: `integration/IntegrationRegistry.java`. Replace hardcoded `"noop"` fallback with `domain.getDefaultSlug()`. One-line change in the resolution logic. |
| 167.6 | Write integration tests for IntegrationDomain defaultSlug and EMAIL resolution | 167A | 167.5 | New file: `EmailProviderResolutionIntegrationTest.java`. Tests: (1) resolve_EMAIL_returns_smtp_when_no_org_integration, (2) resolve_ACCOUNTING_still_returns_noop, (3) resolve_AI_still_returns_noop, (4) defaultSlug_EMAIL_is_smtp, (5) defaultSlug_ACCOUNTING_is_noop. ~5 tests. |
| 167.7 | Add `spring-boot-starter-mail` Maven dependency | 167B | | Modify: `backend/pom.xml`. Add `spring-boot-starter-mail`. |
| 167.8 | Add GreenMail test dependency | 167B | | Modify: `backend/pom.xml`. Add `greenmail-junit5:2.1.2` (scope: test). GreenMail is an in-memory SMTP server for integration testing. |
| 167.9 | Add `docteams.email.*` application properties | 167B | | Modify: `application.yml` and `application-local.yml`. Add `spring.mail.*`, `docteams.email.sender-address`, `docteams.email.unsubscribe-secret`, `docteams.email.rate-limit.*`, `docteams.email.sendgrid.webhook-verification-key`, `docteams.app.base-url`. See architecture doc Section 24.8. |
| 167.10 | Implement `SmtpEmailProvider` | 167B | 167.1, 167.7 | New file: `integration/email/SmtpEmailProvider.java`. `@IntegrationAdapter(domain = EMAIL, slug = "smtp")`, `@ConditionalOnProperty(name = "spring.mail.host")`. Inject `JavaMailSender`. Build `MimeMessage` via `MimeMessageHelper`. Extract `Message-ID` header for `providerMessageId`. Handle `MailException`. Pattern: mirror `NoOpAccountingProvider` structure. |
| 167.11 | Implement `NoOpEmailProvider` | 167B | 167.1 | New file: `integration/email/NoOpEmailProvider.java`. `@IntegrationAdapter(domain = EMAIL, slug = "smtp")`, `@ConditionalOnMissingBean(SmtpEmailProvider.class)`. Logs email details at INFO level. Returns `SendResult(true, "NOOP-" + UUID.randomUUID(), null)`. |
| 167.12 | Write integration tests for `SmtpEmailProvider` with GreenMail | 167B | 167.10, 167.8 | New file: `SmtpEmailProviderIntegrationTest.java`. Tests: (1) sendEmail_delivers_to_greenmail, (2) sendEmail_returns_message_id, (3) sendEmailWithAttachment_includes_pdf, (4) sendEmail_failure_returns_error_result, (5) testConnection_succeeds_with_greenmail. ~5 tests. |
| 167.13 | Write unit tests for `NoOpEmailProvider` | 167B | 167.11 | New file: `NoOpEmailProviderTest.java`. Tests: (1) sendEmail_returns_success_with_noop_id, (2) sendEmailWithAttachment_returns_success, (3) testConnection_returns_success. ~3 tests. |

### Key Files

**Slice 167A — Create:**
- `backend/src/main/java/.../integration/email/EmailProvider.java`
- `backend/src/main/java/.../integration/email/EmailMessage.java`
- `backend/src/main/java/.../integration/email/EmailAttachment.java`
- `backend/src/main/java/.../integration/email/SendResult.java`
- `backend/src/main/java/.../integration/email/RenderedEmail.java`
- `backend/src/main/java/.../integration/email/EmailDeliveryStatus.java`
- `backend/src/test/java/.../integration/email/EmailProviderResolutionIntegrationTest.java`

**Slice 167A — Modify:**
- `backend/src/main/java/.../integration/IntegrationDomain.java`
- `backend/src/main/java/.../integration/IntegrationRegistry.java`

**Slice 167B — Create:**
- `backend/src/main/java/.../integration/email/SmtpEmailProvider.java`
- `backend/src/main/java/.../integration/email/NoOpEmailProvider.java`
- `backend/src/test/java/.../integration/email/SmtpEmailProviderIntegrationTest.java`
- `backend/src/test/java/.../integration/email/NoOpEmailProviderTest.java`

**Slice 167B — Modify:**
- `backend/pom.xml`
- `backend/src/main/resources/application.yml`

**Read for context:**
- `integration/IntegrationRegistry.java` — Resolution logic to refactor
- `integration/accounting/` — Pattern for adapter structure
- `integration/IntegrationAdapter.java` — Annotation to use

### Architecture Decisions

- **Per-domain default slug (ADR-095)**: `IntegrationDomain` enum gains a `defaultSlug` field. `EMAIL("smtp")` means the registry returns `SmtpEmailProvider` when no org-level EMAIL integration exists. All other domains keep `("noop")`.
- **SmtpEmailProvider and NoOpEmailProvider share slug `"smtp"`**: Only one is active at a time via Spring conditional annotations. In production with SMTP config, `SmtpEmailProvider` registers. In local/dev without SMTP config, `NoOpEmailProvider` registers under the same slug.
- **GreenMail for SMTP integration tests**: In-memory SMTP server. Tests configure `spring.mail.host` to point at GreenMail's dynamic port.

---

## Epic 168: Email Template Rendering

**Goal**: Build the Thymeleaf email rendering pipeline using classpath templates. Create the base branded layout and all 12 email templates. Replace the `TemplateRenderer` stub with a production `EmailTemplateRenderer`.

**References**: Architecture doc Sections 24.3.2 (template rendering), template hierarchy, template context variables.

**Dependencies**: None — can run in parallel with Epic 167.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **168A** | 168.1–168.6 | `EmailTemplateRenderer` service with `ClassLoaderTemplateResolver` + `base.html` branded layout + `test-email.html` template + `toPlainText()` utility + unit tests. ~3 new Java files, ~2 new template files. Backend only. | |
| **168B** | 168.7–168.12 | All 10 per-type email templates (8 notification groups + `portal-magic-link.html` + `invoice-delivery.html`) + rendering tests. ~10 new template files, ~1 new test file. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 168.1 | Implement `EmailTemplateRenderer` service | 168A | | New file: `notification/template/EmailTemplateRenderer.java`. Replace the existing `TemplateRenderer` stub. Use `ClassLoaderTemplateResolver` pointing to `templates/email/` with suffix `.html`. Reuse `LenientStandardDialect` from Phase 12. Method: `RenderedEmail render(String templateName, Map<String, Object> context)`. Pattern: `PdfRenderingService` Thymeleaf setup but with classpath resolver. |
| 168.2 | Implement `toPlainText()` utility | 168A | 168.1 | In `EmailTemplateRenderer.java` (private method). Strips HTML tags using Jsoup or simple regex. Preserves link text. Collapses whitespace. Generates `plainTextBody` from rendered HTML. |
| 168.3 | Create `EmailContextBuilder` helper | 168A | | New file: `notification/template/EmailContextBuilder.java`. Builds common base context: `orgName`, `orgLogoUrl` (presigned S3 URL from `OrgSettings.logoS3Key`), `brandColor` (default `#2563EB`), `footerText`, `recipientName`, `unsubscribeUrl`, `appUrl`. Inject `OrgSettingsService` and `StorageService`. Pattern: `TemplateContextBuilder` from Phase 12. |
| 168.4 | Create `base.html` base layout template | 168A | | New file: `templates/email/base.html`. Responsive HTML email using table-based layout. Header: org logo + org name. Content area: `th:replace` fragment. Footer: org name, `footerText`, unsubscribe link (conditional). Brand color accent. Inline CSS. |
| 168.5 | Create `test-email.html` template | 168A | | New file: `templates/email/test-email.html`. Extends `base.html`. Simple body: "This is a test email from DocTeams." with org branding. Used by the admin test email endpoint. |
| 168.6 | Write unit tests for `EmailTemplateRenderer` | 168A | 168.1, 168.4, 168.5 | New file: `EmailTemplateRendererTest.java`. Tests: (1) render_test_email_contains_org_name, (2) render_with_brand_color_applies_to_header, (3) render_missing_variable_does_not_throw, (4) toPlainText_strips_html_tags, (5) toPlainText_preserves_link_text, (6) render_base_layout_includes_footer. ~6 tests. |
| 168.7 | Create notification task template | 168B | 168A | New file: `templates/email/notification-task.html`. Extends `base.html`. Context: `actorName`, `taskName`, `projectName`, `action`, `taskUrl`. For TASK_ASSIGNED, TASK_CLAIMED, TASK_UPDATED. |
| 168.8 | Create notification comment + document + member templates | 168B | 168A | New files (3): `notification-comment.html`, `notification-document.html`, `notification-member.html`. Each extends `base.html`. Context variables per architecture doc Section 24.3.2. |
| 168.9 | Create notification budget + invoice + schedule + retainer templates | 168B | 168A | New files (4): `notification-budget.html`, `notification-invoice.html`, `notification-schedule.html`, `notification-retainer.html`. Each extends `base.html`. |
| 168.10 | Create `portal-magic-link.html` template | 168B | 168A | New file: `templates/email/portal-magic-link.html`. Context: `magicLinkUrl`, `expiryMinutes` (15), `contactName`. No unsubscribe link. Prominent "Access Portal" button. |
| 168.11 | Create `invoice-delivery.html` template | 168B | 168A | New file: `templates/email/invoice-delivery.html`. Context: `invoiceNumber`, `amount`, `currency`, `dueDate`, `customerName`, `portalUrl`. No unsubscribe link. "View Invoice" button. |
| 168.12 | Write rendering tests for all per-type templates | 168B | 168.7-168.11 | New file: `EmailTemplateRenderingIntegrationTest.java`. Render each of 10 templates with test context. Assert: HTML non-empty, subject populated, key variables appear, plain-text generated. ~10-12 tests. |

### Key Files

**Slice 168A — Create:**
- `backend/src/main/java/.../notification/template/EmailTemplateRenderer.java`
- `backend/src/main/java/.../notification/template/EmailContextBuilder.java`
- `backend/src/main/resources/templates/email/base.html`
- `backend/src/main/resources/templates/email/test-email.html`
- `backend/src/test/java/.../notification/template/EmailTemplateRendererTest.java`

**Slice 168A — Modify:**
- `notification/template/TemplateRenderer.java` — Replace stub (or delete and replace with `EmailTemplateRenderer`)

**Slice 168B — Create:**
- `backend/src/main/resources/templates/email/notification-task.html`
- `backend/src/main/resources/templates/email/notification-comment.html`
- `backend/src/main/resources/templates/email/notification-document.html`
- `backend/src/main/resources/templates/email/notification-member.html`
- `backend/src/main/resources/templates/email/notification-budget.html`
- `backend/src/main/resources/templates/email/notification-invoice.html`
- `backend/src/main/resources/templates/email/notification-schedule.html`
- `backend/src/main/resources/templates/email/notification-retainer.html`
- `backend/src/main/resources/templates/email/portal-magic-link.html`
- `backend/src/main/resources/templates/email/invoice-delivery.html`
- `backend/src/test/java/.../notification/template/EmailTemplateRenderingIntegrationTest.java`

**Read for context:**
- `template/` package — Phase 12 Thymeleaf pattern (`PdfRenderingService`, `LenientStandardDialect`)
- `notification/template/EmailTemplate.java` — Existing enum (type-to-template mapping)
- `settings/OrgSettingsService.java` — Branding fields

### Architecture Decisions

- **Classpath templates, not database**: Email templates are developer-authored classpath resources. NOT user-customizable. `TemplateSecurityValidator` is not needed.
- **ClassLoaderTemplateResolver**: Different from Phase 12's `StringTemplateResolver`. Email templates are file-based, not string-based.
- **LenientStandardDialect reused**: Missing context variables render as empty strings, not exceptions.
- **Inline CSS**: Email clients strip `<style>` tags. All CSS is inline.

---

## Epic 169: EmailNotificationChannel + Delivery Log + Migration

**Goal**: Create the `EmailDeliveryLog` entity and V41 migration, implement the `EmailRateLimiter`, and replace the `EmailNotificationChannel` stub with a production implementation that resolves `EmailProvider`, renders templates, sends emails, and logs delivery status.

**References**: Architecture doc Sections 24.2.1 (EmailDeliveryLog), 24.3.3 (notification flow), 24.3.6 (delivery tracking), 24.3.7 (rate limiting), 24.7 (V41 migration).

**Dependencies**: Epic 167 (EmailProvider port), Epic 168 (EmailTemplateRenderer).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **169A** | 169.1–169.8 | V41 migration + `EmailDeliveryLog` entity + `EmailDeliveryLogRepository` + `EmailDeliveryLogService` + `EmailRateLimiter` (Caffeine-based) + integration tests. ~6 new files. Backend only. | |
| **169B** | 169.9–169.14 | Replace `EmailNotificationChannel` stub with production implementation + notification type-to-template mapping + rate limiter integration + end-to-end tests. ~1 modified file, ~1 new test file. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 169.1 | Create V41 tenant migration | 169A | | New file: `db/migration/tenant/V41__create_email_delivery_log.sql`. Creates `email_delivery_log` table, 4 indexes (partial on `provider_message_id`, composite on `reference_type, reference_id`, composite on `status, created_at`, on `created_at`), 2 CHECK constraints. Exact SQL in architecture doc Section 24.7. |
| 169.2 | Create `EmailDeliveryLog` entity | 169A | 169.1 | New file: `integration/email/EmailDeliveryLog.java`. `@Entity`, `@Table(name = "email_delivery_log")`. Fields: `id` (UUID PK), `recipientEmail`, `templateName`, `referenceType`, `referenceId`, `status`, `providerMessageId`, `providerSlug`, `errorMessage`, `createdAt`, `updatedAt`. `@PrePersist`/`@PreUpdate` for timestamps. `updateDeliveryStatus()` method. Pattern: standard entity (no multitenancy boilerplate per Phase 13). |
| 169.3 | Create `EmailDeliveryLogRepository` | 169A | 169.2 | New file: `integration/email/EmailDeliveryLogRepository.java`. `JpaRepository<EmailDeliveryLog, UUID>`. Methods: `findByProviderMessageId()`, `findByStatusAndCreatedAtBetween()`, `countByStatusAndCreatedAtAfter()`, `countByCreatedAtAfter()`. |
| 169.4 | Create `EmailDeliveryLogService` | 169A | 169.3 | New file: `integration/email/EmailDeliveryLogService.java`. Methods: `record(...)` — creates and saves log entry. `updateStatus(providerMessageId, newStatus, errorMessage)`. `findByFilters(status, from, to, pageable)`. `getStats()` — returns `EmailDeliveryStats`. |
| 169.5 | Create `EmailDeliveryStats` response record | 169A | | New file: `integration/email/EmailDeliveryStats.java`. Fields: `sent24h`, `bounced7d`, `failed7d`, `rateLimited7d`, `currentHourUsage`, `hourlyLimit`, `providerSlug`. |
| 169.6 | Implement `EmailRateLimiter` | 169A | | New file: `integration/email/EmailRateLimiter.java`. `@Service`. Caffeine cache with `expireAfterWrite(1, HOURS)`. Methods: `boolean tryAcquire(tenantSchema, providerSlug)` — per-tenant + platform aggregate. `RateLimitStatus getStatus(tenantSchema, providerSlug)`. Configurable limits from `docteams.email.rate-limit.*`. See ADR-097. |
| 169.7 | Write integration tests for `EmailDeliveryLogService` | 169A | 169.4 | New file: `EmailDeliveryLogServiceIntegrationTest.java`. Tests: record_creates_entry, updateStatus_updates_existing, findByFilters_with_status, findByFilters_with_date_range, getStats_returns_correct_counts. ~5 tests. |
| 169.8 | Write unit tests for `EmailRateLimiter` | 169A | 169.6 | New file: `EmailRateLimiterTest.java`. Tests: tryAcquire_succeeds_within_limit, tryAcquire_fails_when_tenant_limit_exceeded, tryAcquire_fails_when_platform_aggregate_exceeded, byoak_has_higher_limit_than_smtp, counters_reset_after_cache_expiry. ~5 tests. |
| 169.9 | Replace `EmailNotificationChannel` stub with production implementation | 169B | 167A, 167B, 168A | Modify: `notification/channel/EmailNotificationChannel.java`. Remove `@Profile`. Inject `IntegrationRegistry`, `EmailTemplateRenderer`, `EmailContextBuilder`, `EmailDeliveryLogService`, `EmailRateLimiter`, `OrgSettingsService`. In `deliver()`: (1) resolve EmailProvider, (2) map notification type to template, (3) build context, (4) render, (5) check rate limit, (6) send, (7) record delivery. Catch all exceptions. |
| 169.10 | Implement notification type-to-template mapping | 169B | 169.9 | In `EmailNotificationChannel.java`. Map each of 20 notification types to template name: `TASK_ASSIGNED/TASK_CLAIMED/TASK_UPDATED` → `notification-task`, `COMMENT_ADDED` → `notification-comment`, etc. |
| 169.11 | Build notification-specific template context | 169B | 169.9 | In `EmailNotificationChannel.java` or `EmailContextBuilder`. Populate type-specific variables from `Notification` fields. URLs from `docteams.app.base-url` + entity path. |
| 169.12 | Add `EmailMessage` metadata for tracking | 169B | 169.9 | In `deliver()`: construct `EmailMessage` with metadata: `referenceType=NOTIFICATION`, `referenceId=notification.id`, `tenantSchema`. Unsubscribe URL metadata key (null for now, wired in Epic 172). |
| 169.13 | Wire rate limiter into `EmailNotificationChannel` | 169B | 169.6, 169.9 | Before sending, call `emailRateLimiter.tryAcquire()`. If false, record `RATE_LIMITED` status and return. |
| 169.14 | Write end-to-end integration tests | 169B | 169.9 | New file: `EmailNotificationChannelIntegrationTest.java`. Tests: deliver_sends_email_via_greenmail, deliver_records_delivery_log, deliver_rate_limited_records_status, deliver_failure_records_failed, deliver_maps_task_assigned_to_template, deliver_maps_comment_to_template, deliver_email_contains_branding. ~7 tests. |

### Key Files

**Slice 169A — Create:**
- `backend/src/main/resources/db/migration/tenant/V41__create_email_delivery_log.sql`
- `backend/src/main/java/.../integration/email/EmailDeliveryLog.java`
- `backend/src/main/java/.../integration/email/EmailDeliveryLogRepository.java`
- `backend/src/main/java/.../integration/email/EmailDeliveryLogService.java`
- `backend/src/main/java/.../integration/email/EmailDeliveryStats.java`
- `backend/src/main/java/.../integration/email/EmailRateLimiter.java`
- `backend/src/test/java/.../integration/email/EmailDeliveryLogServiceIntegrationTest.java`
- `backend/src/test/java/.../integration/email/EmailRateLimiterTest.java`

**Slice 169B — Create:**
- `backend/src/test/java/.../notification/channel/EmailNotificationChannelIntegrationTest.java`

**Slice 169B — Modify:**
- `backend/src/main/java/.../notification/channel/EmailNotificationChannel.java` — Full rewrite from stub

**Read for context:**
- `notification/channel/NotificationDispatcher.java` — Dispatch flow
- `notification/channel/InAppNotificationChannel.java` — Peer channel pattern
- `notification/NotificationService.java` — Notification creation
- `notification/template/EmailTemplate.java` — Existing type-to-template mapping

### Architecture Decisions

- **V41 migration is standalone**: Only creates `email_delivery_log`. No ALTER to existing tables.
- **Rate limiter is Caffeine-based (ADR-097)**: Simple, fast. Counters reset on JVM restart (acceptable).
- **EmailNotificationChannel catches all exceptions**: Never propagates to `NotificationDispatcher`. In-app notifications unaffected by email failures.
- **All 20 notification types become email-capable**: After this slice, any type with `emailEnabled = true` sends branded HTML email.

---

## Epic 170: Invoice Delivery + Portal Magic Link Email

**Goal**: Implement the two non-notification email flows: invoice delivery with PDF attachment and portal magic link emails.

**References**: Architecture doc Sections 24.3.4 (invoice delivery), 24.3.5 (portal magic link).

**Dependencies**: Epic 169 (delivery log, EmailProvider, EmailTemplateRenderer).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **170A** | 170.1–170.6 | `InvoiceEmailService` + `@TransactionalEventListener` on `InvoiceSentEvent` + PDF attachment + delivery log + integration tests. ~2 new files, ~1 modified file. Backend only. | |
| **170B** | 170.7–170.12 | `PortalEmailService` + `MagicLinkService` integration + delivery log + integration tests. ~2 new files, ~1 modified file. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 170.1 | Create `InvoiceEmailService` | 170A | | New file: `invoice/InvoiceEmailService.java`. `@Service`. Method: `sendInvoiceEmail(Invoice invoice, byte[] pdfBytes)` — resolves EmailProvider, renders `invoice-delivery` template, constructs `EmailAttachment` (PDF), calls `sendEmailWithAttachment()`, records delivery with `referenceType=INVOICE`. Recipient: `invoice.getCustomer().getEmail()`. |
| 170.2 | Create `@TransactionalEventListener` for `InvoiceSentEvent` | 170A | 170.1 | New file: `invoice/InvoiceEmailEventListener.java`. `@TransactionalEventListener(phase = AFTER_COMMIT)` on `InvoiceSentEvent`. Loads invoice, generates PDF via `PdfRenderingService`, calls `InvoiceEmailService`. All exceptions caught. Pattern: existing `NotificationEventHandler`. |
| 170.3 | Publish `InvoiceSentEvent` from `InvoiceService.send()` | 170A | | Modify: `invoice/InvoiceService.java`. After SENT transition, publish `InvoiceSentEvent` via `ApplicationEventPublisher`. Create event record if it doesn't exist. |
| 170.4 | Write integration tests for invoice email | 170A | 170.1, 170.2 | New file: `InvoiceEmailServiceIntegrationTest.java`. Tests: sends_email_with_pdf_attachment, records_delivery_log, failure_does_not_throw, rate_limited_records_status, event_listener_triggers. ~5 tests. |
| 170.5 | Verify PDF attachment filename format | 170A | 170.1 | Attachment filename: `INV-{invoiceNumber}.pdf`. Content type: `application/pdf`. Verify in test via GreenMail. |
| 170.6 | Verify email failure does not block SENT transition | 170A | 170.2 | Integration test: simulate failure, assert invoice still SENT, assert delivery log has FAILED entry. |
| 170.7 | Create `PortalEmailService` | 170B | | New file: `portal/PortalEmailService.java`. `@Service`. Method: `sendMagicLinkEmail(PortalContact contact, String magicLinkUrl)` — resolves EmailProvider, renders `portal-magic-link` template, sends, records delivery with `referenceType=MAGIC_LINK`. No unsubscribe link. |
| 170.8 | Integrate `PortalEmailService` into `MagicLinkService` | 170B | 170.7 | Modify: `portal/MagicLinkService.java`. After `generateToken()`, call `portalEmailService.sendMagicLinkEmail()`. Token still returned. If email fails, log warning and continue. |
| 170.9 | Construct magic link URL | 170B | 170.8 | In `MagicLinkService`: construct URL from `docteams.app.base-url` + `/portal/auth?token={rawToken}`. |
| 170.10 | Write integration tests for portal email | 170B | 170.7, 170.8 | New file: `PortalEmailServiceIntegrationTest.java`. Tests: sends_email_to_contact, records_delivery_log, failure_does_not_throw, generateToken_triggers_email, token_returned_on_email_failure. ~5 tests. |
| 170.11 | Verify no unsubscribe link in magic link email | 170B | 170.7 | Integration test: verify rendered HTML has no unsubscribe link. |
| 170.12 | Verify delivery log referenceId points to MagicLinkToken | 170B | 170.10 | Integration test: verify `referenceId` matches `MagicLinkToken.id`. |

### Key Files

**Slice 170A — Create:**
- `backend/src/main/java/.../invoice/InvoiceEmailService.java`
- `backend/src/main/java/.../invoice/InvoiceEmailEventListener.java`
- `backend/src/test/java/.../invoice/InvoiceEmailServiceIntegrationTest.java`

**Slice 170A — Modify:**
- `backend/src/main/java/.../invoice/InvoiceService.java` — Publish `InvoiceSentEvent`

**Slice 170B — Create:**
- `backend/src/main/java/.../portal/PortalEmailService.java`
- `backend/src/test/java/.../portal/PortalEmailServiceIntegrationTest.java`

**Slice 170B — Modify:**
- `backend/src/main/java/.../portal/MagicLinkService.java` — Call PortalEmailService

**Read for context:**
- `invoice/InvoiceService.java` — SENT transition
- `portal/MagicLinkService.java` — Token generation
- `notification/NotificationEventHandler.java` — Event listener pattern

### Architecture Decisions

- **`@TransactionalEventListener(phase = AFTER_COMMIT)` for invoice email**: Email sent after invoice committed. If email fails, invoice remains SENT.
- **Magic link email is fire-and-forget**: Token still returned regardless of email success.
- **No unsubscribe on transactional emails**: Invoice and magic link emails are transactional.

---

## Epic 171: SendGrid BYOAK + Bounce Webhooks

**Goal**: Implement the SendGrid email provider adapter for BYOAK orgs and the webhook endpoint for processing delivery status events.

**References**: Architecture doc Sections 24.3.1 (SendGridEmailProvider), 24.3.6 (bounce webhook), 24.6 (webhook security).

**Dependencies**: Epic 169 (delivery log entity and service).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **171A** | 171.1–171.6 | `SendGridEmailProvider` adapter + `sendgrid-java` dependency + `@ConditionalOnClass` + `unique_args` metadata + unit tests. ~2 new files, ~1 modified file. Backend only. | |
| **171B** | 171.7–171.13 | `EmailWebhookController` + signature verification + tenant context from `unique_args` + delivery log updates + bounce notifications + audit events + integration tests. ~2 new files, ~1 modified file. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 171.1 | Add `sendgrid-java` Maven dependency | 171A | | Modify: `backend/pom.xml`. Add `sendgrid-java:4.10.3` with `<optional>true</optional>`. |
| 171.2 | Implement `SendGridEmailProvider` | 171A | 171.1 | New file: `integration/email/SendGridEmailProvider.java`. `@IntegrationAdapter(domain = EMAIL, slug = "sendgrid")`, `@ConditionalOnClass(name = "com.sendgrid.SendGrid")`. Inject `SecretStore`, `IntegrationService`. Retrieves API key from `SecretStore`. Builds `Mail` object with `Personalization`. Adds `tenantSchema` to `unique_args` (ADR-096). Returns `sg_message_id`. |
| 171.3 | Implement `sendEmailWithAttachment()` for SendGrid | 171A | 171.2 | Uses `Attachments` class. Base64-encodes `byte[]` content. Sets type and filename. |
| 171.4 | Implement `testConnection()` for SendGrid | 171A | 171.2 | Sends lightweight API request to validate key. Returns `ConnectionTestResult`. |
| 171.5 | Write unit tests for `SendGridEmailProvider` | 171A | 171.2 | New file: `SendGridEmailProviderTest.java`. Tests: constructs_correct_mail_object, includes_tenant_schema_in_unique_args, returns_sg_message_id, handles_api_error, includes_base64_attachment. ~5 tests. |
| 171.6 | Verify `unique_args` includes required metadata | 171A | 171.2 | Unit test: verify `Personalization.customArgs` contains `tenantSchema`, `referenceType`, `referenceId`. |
| 171.7 | Create `EmailWebhookController` | 171B | | New file: `integration/email/EmailWebhookController.java`. `@RestController`. `POST /api/webhooks/email/{provider}`. Not tenant-scoped. Returns 200/401. Exclude from Spring Security. |
| 171.8 | Implement SendGrid webhook signature verification | 171B | 171.7 | Use SendGrid Event Webhook Verification library. Verify using platform or BYOAK key. Extract tenant from `unique_args` to determine which key. |
| 171.9 | Process events and update delivery log | 171B | 171.8 | Parse event array. For `delivered`/`bounce`/`dropped`/`deferred`: extract `sg_message_id`, `tenantSchema` from `unique_args`, set tenant context via `ScopedValue`, call `updateStatus()`. |
| 171.10 | Create admin notification for bounced invoice emails | 171B | 171.9 | If `referenceType == INVOICE` and BOUNCED: create in-app notification for admins via `NotificationService`. |
| 171.11 | Publish audit events for delivery failures | 171B | 171.9 | For BOUNCED/FAILED: publish `email.delivery.bounced`/`email.delivery.failed` audit event via `AuditService`. |
| 171.12 | Add webhook endpoint to Security permit list | 171B | 171.7 | Modify: `security/SecurityConfig.java`. Add `/api/webhooks/email/**` to permit list. |
| 171.13 | Write integration tests for webhook | 171B | 171.9 | New file: `EmailWebhookControllerIntegrationTest.java`. Tests: bounce_updates_log, delivered_updates_log, invalid_signature_401, missing_tenant_skipped, invoice_bounce_creates_notification, bounce_creates_audit_event. ~6 tests. |

### Key Files

**Slice 171A — Create:**
- `backend/src/main/java/.../integration/email/SendGridEmailProvider.java`
- `backend/src/test/java/.../integration/email/SendGridEmailProviderTest.java`

**Slice 171A — Modify:**
- `backend/pom.xml` — Add `sendgrid-java`

**Slice 171B — Create:**
- `backend/src/main/java/.../integration/email/EmailWebhookController.java`
- `backend/src/test/java/.../integration/email/EmailWebhookControllerIntegrationTest.java`

**Slice 171B — Modify:**
- `backend/src/main/java/.../security/SecurityConfig.java` — Permit webhook path

**Read for context:**
- `integration/IntegrationRegistry.java`, `integration/secret/SecretStore.java`, `notification/NotificationService.java`, `audit/AuditService.java`

### Architecture Decisions

- **Tenant identification via `unique_args` (ADR-096)**: Every SendGrid email includes tenant schema in custom metadata. Webhook handler extracts it to set correct tenant context.
- **Webhook signature verification is mandatory**: Invalid signatures return 401 immediately.
- **`sendgrid-java` is optional**: `@ConditionalOnClass` ensures adapter only registers when SDK is on classpath.

---

## Epic 172: Unsubscribe + Admin Endpoints

**Goal**: HMAC-signed unsubscribe tokens for one-click email unsubscribe, admin email management endpoints, and `List-Unsubscribe` headers.

**References**: Architecture doc Sections 24.3.8 (unsubscribe), 24.4 (admin endpoints), 24.6 (HMAC tokens).

**Dependencies**: Epic 169 (delivery log, EmailNotificationChannel).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **172A** | 172.1–172.7 | `UnsubscribeService` (HMAC tokens) + `UnsubscribeController` + HTML confirmation page + integration tests. ~3 new files, ~1 modified file. Backend only. | |
| **172B** | 172.8–172.14 | `EmailAdminController` (delivery-log, stats, test endpoints) + `List-Unsubscribe` headers + audit event for test email + integration tests. ~2 new files, ~2 modified files. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 172.1 | Create `UnsubscribeService` | 172A | | New file: `integration/email/UnsubscribeService.java`. `@Service`. Methods: `generateToken(memberId, notificationType, tenantSchema)` — HMAC-SHA256 signed token. `verifyToken(token)` — verifies HMAC, returns `UnsubscribePayload`. Secret from `docteams.email.unsubscribe-secret`. Token format: `base64url(payload):base64url(hmac)`. |
| 172.2 | Create `UnsubscribePayload` record | 172A | | `record UnsubscribePayload(UUID memberId, String notificationType, String tenantSchema) {}`. |
| 172.3 | Create `UnsubscribeController` | 172A | 172.1 | New file: `integration/email/UnsubscribeController.java`. `GET /api/email/unsubscribe?token={token}`. Not authenticated. Verifies token, sets tenant context, updates `NotificationPreference.emailEnabled = false`. Returns HTML confirmation. |
| 172.4 | Create unsubscribe confirmation HTML | 172A | 172.3 | Inline HTML string from controller. Minimal styling. "You have been unsubscribed from {type} emails." |
| 172.5 | Add unsubscribe endpoint to Security permit list | 172A | 172.3 | Modify: `security/SecurityConfig.java`. Add `/api/email/unsubscribe` to permit list. |
| 172.6 | Write unit tests for `UnsubscribeService` | 172A | 172.1 | New file: `UnsubscribeServiceTest.java`. Tests: generates_valid_token, verifies_correct_payload, rejects_tampered_token, rejects_truncated_token, different_inputs_different_tokens. ~5 tests. |
| 172.7 | Write integration tests for `UnsubscribeController` | 172A | 172.3 | New file: `UnsubscribeControllerIntegrationTest.java`. Tests: valid_token_sets_emailEnabled_false, returns_html_confirmation, invalid_token_returns_400, idempotent, no_auth_required. ~5 tests. |
| 172.8 | Create `EmailAdminController` | 172B | | New file: `integration/email/EmailAdminController.java`. `@RestController`, `@RequestMapping("/api/email")`. `@PreAuthorize("hasAnyRole('org:admin','org:owner')")`. Endpoints: `GET /delivery-log`, `GET /stats`, `POST /test`. |
| 172.9 | Implement `GET /api/email/delivery-log` | 172B | 172.8 | Paginated, filterable by status/date. Returns `Page<EmailDeliveryLogResponse>`. Create `EmailDeliveryLogResponse` record. |
| 172.10 | Implement `GET /api/email/stats` | 172B | 172.8 | Delegates to `EmailDeliveryLogService.getStats()`. Includes `currentHourUsage` and `hourlyLimit` from `EmailRateLimiter.getStatus()`. |
| 172.11 | Implement `POST /api/email/test` | 172B | 172.8 | Resolves current user's email. Renders `test-email.html`. Sends email. Records with `referenceType=TEST`. Publishes `email.test.sent` audit event. |
| 172.12 | Add `List-Unsubscribe` headers to notification emails | 172B | 172.1, 169B | Modify: `EmailNotificationChannel.java`. Generate unsubscribe URL via `UnsubscribeService.generateToken()`. Add to `EmailMessage.metadata`: `List-Unsubscribe` and `List-Unsubscribe-Post` headers. |
| 172.13 | Update `SmtpEmailProvider` to set `List-Unsubscribe` headers | 172B | 172.12 | Modify: `SmtpEmailProvider.java`. Read from `message.metadata()` and set as `MimeMessage` headers. |
| 172.14 | Write integration tests for admin endpoints | 172B | 172.8 | New file: `EmailAdminControllerIntegrationTest.java`. Tests: delivery_log_paginated, filters_by_status, filters_by_date, stats_correct_counts, test_email_sends, test_email_records_log, requires_admin_role. ~7 tests. |

### Key Files

**Slice 172A — Create:**
- `backend/src/main/java/.../integration/email/UnsubscribeService.java`
- `backend/src/main/java/.../integration/email/UnsubscribeController.java`
- `backend/src/test/java/.../integration/email/UnsubscribeServiceTest.java`
- `backend/src/test/java/.../integration/email/UnsubscribeControllerIntegrationTest.java`

**Slice 172A — Modify:**
- `backend/src/main/java/.../security/SecurityConfig.java`

**Slice 172B — Create:**
- `backend/src/main/java/.../integration/email/EmailAdminController.java`
- `backend/src/main/java/.../integration/email/EmailDeliveryLogResponse.java`
- `backend/src/test/java/.../integration/email/EmailAdminControllerIntegrationTest.java`

**Slice 172B — Modify:**
- `backend/src/main/java/.../notification/channel/EmailNotificationChannel.java`
- `backend/src/main/java/.../integration/email/SmtpEmailProvider.java`

**Read for context:**
- `notification/NotificationPreferenceRepository.java`, `audit/AuditService.java`, `security/SecurityConfig.java`

### Architecture Decisions

- **HMAC-signed unsubscribe tokens**: No session required. No expiry. Token encodes `memberId:notificationType:tenantSchema`.
- **`List-Unsubscribe` and `List-Unsubscribe-Post` headers**: RFC 8058 one-click unsubscribe. Only in notification emails.
- **Admin endpoints ADMIN/OWNER only**: Delivery log contains sensitive recipient data.

---

## Epic 173: Frontend — Email Toggle + Integration Card + Delivery Log

**Goal**: Enable email toggle in notification preferences, add EMAIL integration card, create delivery log viewer.

**References**: Architecture doc Section 24.8 (frontend changes), requirements Sections 8a–8c.

**Dependencies**: Epic 172 (admin endpoints).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **173A** | 173.1–173.8 | Notification preferences email toggle + `EmailIntegrationCard` + server actions + API types. ~4 modified files, ~3 new files. Frontend only. | |
| **173B** | 173.9–173.14 | Email settings page + `DeliveryLogTable` + stats + rate limit display. ~4 new files, ~1 modified file. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 173.1 | Enable email toggle in notification preferences | 173A | | Modify: `notification-preferences-form.tsx`. Remove "Coming soon" disabled state. Enable `emailEnabled` toggle for all types. |
| 173.2 | Add missing notification types to preferences UI | 173A | 173.1 | Modify: `notification-preferences-form.tsx`. Add: BUDGET_ALERT, INVOICE_APPROVED, INVOICE_SENT, INVOICE_PAID, INVOICE_VOIDED, DOCUMENT_GENERATED, RECURRING_PROJECT_CREATED, SCHEDULE_SKIPPED, SCHEDULE_COMPLETED, and all 5 RETAINER types. Group by category. |
| 173.3 | Create email server actions | 173A | | New file: `frontend/lib/actions/email.ts`. Actions: `getDeliveryLog()`, `getEmailStats()`, `sendTestEmail()`. Pattern: existing `notifications.ts` actions. |
| 173.4 | Create email API types | 173A | | New file: `frontend/lib/api/email.ts`. Types: `EmailDeliveryLogEntry`, `EmailDeliveryStats`, `DeliveryLogParams`. |
| 173.5 | Add `EMAIL` to `DOMAIN_CONFIG` | 173A | | Modify: `integrations/page.tsx`. Add EMAIL to `DOMAIN_CONFIG` array. |
| 173.6 | Create `EmailIntegrationCard` component | 173A | 173.5 | New file: `components/integrations/EmailIntegrationCard.tsx`. Two-tier UX: (1) "Platform Email — Active" badge. (2) Expand for BYOAK config. Pattern: existing `IntegrationCard.tsx`. Stats summary + rate limit tier. |
| 173.7 | Write frontend tests for email toggle | 173A | 173.1 | Tests: email_toggle_enabled, saves_preference, all_types_displayed, missing_types_visible. ~4 tests. |
| 173.8 | Write frontend tests for `EmailIntegrationCard` | 173A | 173.6 | New file: `EmailIntegrationCard.test.tsx`. Tests: shows_platform_badge, expand_shows_form, api_key_password_type, test_connection_calls_action. ~4 tests. |
| 173.9 | Create email settings page | 173B | 173A | New file: `settings/email/page.tsx`. Two tabs: Overview (stats + rate limit) and Delivery Log. Route: `/settings/email`. ADMIN/OWNER only. Add to settings navigation. |
| 173.10 | Create `DeliveryLogTable` component | 173B | 173.3 | New file: `components/email/DeliveryLogTable.tsx`. Columns: Date, Recipient, Template, Status (color badge), Provider Message ID, Error. Pattern: existing data tables. |
| 173.11 | Add status filter to delivery log | 173B | 173.10 | Dropdown filter: All, SENT, DELIVERED, BOUNCED, FAILED, RATE_LIMITED. |
| 173.12 | Add date range filter | 173B | 173.10 | Date range picker (from/to). Pattern: existing date filters in reporting. |
| 173.13 | Add delivery stats summary | 173B | 173.9 | Overview tab: sent 24h, bounced 7d, failed 7d, rate limited 7d, current hour usage, provider. |
| 173.14 | Write frontend tests for delivery log and settings | 173B | 173.10 | Tests: table_renders, status_filter_works, stats_display, rate_limit_tier, admin_required. ~5 tests. |

### Key Files

**Slice 173A — Create:**
- `frontend/lib/actions/email.ts`
- `frontend/lib/api/email.ts`
- `frontend/components/integrations/EmailIntegrationCard.tsx`
- `frontend/__tests__/integrations/EmailIntegrationCard.test.tsx`

**Slice 173A — Modify:**
- `frontend/components/notifications/notification-preferences-form.tsx`
- `frontend/app/(app)/org/[slug]/settings/integrations/page.tsx`

**Slice 173B — Create:**
- `frontend/app/(app)/org/[slug]/settings/email/page.tsx`
- `frontend/components/email/DeliveryLogTable.tsx`
- `frontend/__tests__/email/EmailSettingsPage.test.tsx`

**Slice 173B — Modify:**
- Settings navigation (layout or sidebar) — Add email route

**Read for context:**
- `notification-preferences-form.tsx`, `IntegrationCard.tsx`, `integrations/page.tsx`, `integrations/actions.ts`

### Architecture Decisions

- **Two-tier UX**: Default shows "Platform Email — Active" with no action required. BYOAK revealed on expand.
- **Dedicated email settings page**: Delivery log justifies `/settings/email` rather than cramming into integration card.
- **All 20 notification types visible**: Grouped by category. Previously hidden types now shown with both toggles.

---

## Summary

| Metric | Count |
|--------|-------|
| Epics | 7 (167–173) |
| Slices | 14 (167A–173B) |
| New backend files | ~30 (Java + templates + migration) |
| Modified backend files | ~8 |
| New frontend files | ~7 |
| Modified frontend files | ~4 |
| New template files | 12 (in `templates/email/`) |
| Integration tests | ~85-95 |
| Frontend tests | ~17 |
| Migration | V41 (`email_delivery_log`) |
