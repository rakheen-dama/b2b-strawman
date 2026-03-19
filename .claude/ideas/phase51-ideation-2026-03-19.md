# Phase 51 Ideation — Accounting Practice Management Essentials
**Date**: 2026-03-19

## Lighthouse Domain
SA small accounting firm. First real vertical feature phase (beyond config/packs). Tests whether the modular monolith + module guard approach delivers genuine vertical value.

## Decision Rationale
Founder asked about payments — redirected after realizing Phase 25 already built Stripe/PayFast adapters. Then asked "anything left for common functions, or can we zero in on a vertical?" Assessment: foundation is remarkably complete (50 phases). PSP, Portal Frontend, Recurring Work — all done. Past the inflection point where generic work has diminishing returns.

### Why Accounting First (Confirmed Again)
- Smallest fork gap from foundation
- Phase 47 QA + Phase 48 gap closure already validated the accounting workflow
- Phase 49 module guard infrastructure is ready for real modules
- IT Consulting needs nothing (pure config). Legal needs trust accounting (massive). Accounting is the sweet spot.

### Option A vs B
- **Option A (chosen)**: "Accounting-Ready" polish — deadline calendar, post-schedule automation, onboarding seeding. ~4-5 epics. Makes existing infrastructure shine.
- **Option B (deferred)**: Deeper domain logic — SARS eFiling prep, service category reports, client financial summary. Too much before real users give feedback.

### Accounting Sync (Xero/Sage) Decision
Repeatedly deferred throughout 50 phases. Key quote from Phase 34: "Accounting sync is an integration (moves data elsewhere), not a capability (changes what the product does). High maintenance, narrow audience." Phase 21 built the `AccountingProvider` port and NoOp stub — adding Xero is a single-epic effort when needed. Not blocking for launch.

## Founder Preferences (Confirmed)
- Go vertical, AI assistant later
- Option A (minimum viable accounting features, not full vertical module)
- Deadline calendar as the headline feature
- Post-schedule automation to close the recurring engagement gap
- Profile-based onboarding to reduce setup friction

## Phase Roadmap (Updated)
- Phase 50: Data Protection Compliance (in progress, 4/8 epics done)
- **Phase 51: Accounting Practice Management Essentials** (spec written)
- Phase 52 (candidate): Legal trust accounting OR accounting Xero sync
- Phase 45 (parked): AI Assistant — revisit after first vertical ships
