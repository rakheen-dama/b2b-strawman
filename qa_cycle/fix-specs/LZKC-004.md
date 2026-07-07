# Fix Spec: LZKC-004 — Client-facing "proposal" vocabulary in email + seeded letter body

## Problem
Day 7/8: client-facing engagement-letter surfaces mix vocabulary — email subject "…New proposal PROP-0001 for your review", email body chrome all says "Proposal", and the seeded letter body says "This proposal expires…" / "This proposal is subject to…", while portal chrome correctly says "Engagement Letter".

## Root Cause (verified)
Backend email terminology exists ONLY for invoice nouns:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/template/EmailTerminology.java:19-28` — legal-za maps Invoice→Fee Note only; no Proposal entries.
- `notification/template/EmailContextBuilder.java:87-95` — injects `invoiceTerm*` convenience keys only; no `proposalTerm`.
- `invoice/InvoiceEmailService.java:74-77` — the working precedent ("Fee Note INV-0001 from …").

The proposal path was never wired in:
1. Subject: `proposal/ProposalSentEmailHandler.java:118-121` — hardcodes `"%s: New proposal %s for your review"`.
2. Body chrome: `backend/src/main/resources/templates/email/portal-new-proposal.html` lines 5, 16, 24, 30, 36 — literal "Proposal"/"proposal" strings, no terminology variables.
3. Seeded body: `proposal/ProposalContentSeeder.java:110` (`"This proposal expires on …"`) and `:118` (`"This proposal is subject to our standard terms…"`) — seeder receives no verticalProfile; content is persisted Tiptap JSON (test pins the literal at `ProposalContentSeederTest.java:192`).

## Fix
Mirror the invoice pattern:
1. `EmailTerminology.java`: add `Proposal/proposal/Proposals/proposals → Engagement Letter/engagement letter/…` to the legal-za map.
2. `EmailContextBuilder.java` (~87-95): add `proposalTerm`, `proposalTermLower` (plural variants if used) alongside the invoice keys.
3. `ProposalSentEmailHandler.java:118-121`: build subject from `proposalTerm` — note article: "New engagement letter PROP-0001 for your review" needs no a/an, keep phrasing article-free.
4. `templates/email/portal-new-proposal.html`: replace literal "Proposal"/"proposal" at lines 5, 16, 24, 30, 36 with the context variables.
5. Seeder: thread the tenant's verticalProfile (or resolved term) into `ProposalContentSeeder.buildDefaultContent` and use it in the two strings; update `ProposalContentSeederTest`. **Forward-only**: existing proposals keep their persisted body — flag to orchestrator; no backfill proposed (Low).

## Scope
Backend only
Files to modify: `EmailTerminology.java`, `EmailContextBuilder.java`, `ProposalSentEmailHandler.java`, `templates/email/portal-new-proposal.html`, `ProposalContentSeeder.java`, `ProposalContentSeederTest.java`
Files to create: none
Migration needed: no

## Verification
Send a scratch engagement letter on the legal-za tenant → Mailpit subject/body say "engagement letter" (no "proposal"); new letter body reads "This engagement letter expires…". Regression: consulting/accounting tenant emails still say "proposal". GreenMail test asserting subject per profile.

## Estimated Effort
M (30 min – 2 hr)
