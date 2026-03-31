# Phase 55 Ideation — Legal Foundations
**Date**: 2026-03-31

## Decision
Build three real legal modules (court calendar, conflict check, LSSA tariff) to stress-test the multi-vertical architecture before going to production.

## Rationale
Founder wants to catch architecture issues with supporting multiple verticals early — not after real tenants are on the platform. The goal is NOT legal features per se, but proving that two real verticals coexist cleanly in one deployment. Each module tests a different integration pattern:
1. **Court calendar** — parallel vertical module (vs. accounting deadline calendar). Tests: vertical-specific entity + service + scheduled job + dashboard widget coexisting with accounting equivalents
2. **Conflict check** — vertical-only module with no accounting equivalent. Tests: module-only entity creation, module-gated API, UI that only one vertical sees
3. **LSSA tariff** — shared system extension. Tests: extending the invoice/rate system with vertical logic without breaking accounting's hourly billing

### Why Not Another Vertical?
- IT Consulting = pure config, wouldn't stress-test anything
- Accounting deeper = waiting for real user feedback before going deeper (Phase 51 decision)
- Legal foundations = right balance of new modules + architecture validation

## Key Design Preferences
- Trust accounting explicitly OUT — this is foundations only
- Advisory conflict checks (not blocking) — matches SA legal practice
- LSSA tariff as separate entity from BillingRate — loosely coupled, module-gated
- Section 6 (multi-vertical coexistence tests) is as important as the feature code

## Modules
| Module | Architecture Test |
|--------|------------------|
| `court_calendar` | Parallel vertical module (event-based dates vs. calculated dates) |
| `conflict_check` | Vertical-only module (no equivalent in accounting) |
| `lssa_tariff` | Shared system extension (InvoiceLine gets optional tariff FK) |

## Phase Roadmap (Updated)
- Phase 53: Dashboard Polish (complete)
- Phase 54 (candidate): Keycloak E2E Test Suite (specced)
- **Phase 55: Legal Foundations** (spec written)
- Phase 56 (candidate): Trust Accounting (once Phase 55 proves architecture)
