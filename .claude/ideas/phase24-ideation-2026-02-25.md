# Phase 24 Ideation — Outbound Email Delivery
**Date**: 2026-02-25

## Lighthouse Domain
- SA small-to-medium law firms (unchanged)
- Email delivery is vertical-agnostic — every fork needs transactional email

## Decision Rationale
Founder asked specifically about "email integration". Narrowed to **outbound delivery** (not inbound capture or email-to-app threading). The platform has 20+ notification types, invoice sending, portal magic links, and document sharing — all stubbed. This is the single biggest gap between "demo" and "usable product."

Key design decisions:
1. **Two-tier architecture**: Platform SMTP (JavaMail) as default zero-config, SendGrid BYOAK as upgrade — founder's suggestion, avoids forcing API key setup on small firms
2. **Medium delivery tracking**: Bounce/failure webhooks for debugging, no open/click tracking — bounces matter for invoices/magic links
3. **Provider-agnostic port**: `EmailProvider` interface with `SmtpEmailProvider` (default) and `SendGridEmailProvider` (BYOAK)
4. **Thymeleaf for email templates**: Reuses Phase 12 rendering infrastructure, classpath resources not DB-stored
5. **Sender**: `noreply@{platform-domain}` for v1, no custom domains

## Key Design Preferences
1. **Zero-config email is non-negotiable** — orgs must get working email out of the box
2. **BYOAK is the power-user path** — higher rate limits, custom sender identity
3. **Rate limits are tier-aware** — platform SMTP: 50/hr, BYOAK: 200/hr
4. **Fire-and-forget delivery** — email failures never block the triggering operation

## Shelved Ideas
- **Custom sender domains / DNS verification** — v2, requires DKIM/SPF/DMARC setup flow
- **Inbound email capture** — separate phase, much larger scope
- **Open/click tracking** — noise for practice management
- **Email threading (In-Reply-To headers)** — nice-to-have, not v1

## Phase Roadmap (updated)
- Phase 23: Custom Field Maturity & Data Integrity (complete)
- Phase 24: Outbound Email Delivery (requirements written)
- Phase 25+: Candidates — Accounting sync (Xero/Sage), AI features, E-signatures, Inbound email
