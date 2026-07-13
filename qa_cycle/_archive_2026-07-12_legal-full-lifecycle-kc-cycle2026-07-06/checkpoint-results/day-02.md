# Day 2 — Onboard Sipho as client, conflict check + KYC + pipeline enquiry `[FIRM]` — 2026-07-06

Actor: Bob Ndlovu (Admin). Context swap performed (cookies cleared, fresh Keycloak login as bob@mathebula-test.local). Day 1 exit check confirmed on login: firm logo (S3 branding URL) renders in Bob's sidebar → branding persists across logout/login.

| Checkpoint | Result | Evidence |
|---|---|---|
| 2.1 Clients → New Client | PASS | /org/mathebula-partners/customers → "Create Client" dialog (2-step wizard) |
| 2.2 legal promoted fields for INDIVIDUAL | PASS | Step 2 "SA Legal — Client Details": ID / Passport Number, Postal Address, Preferred Correspondence, Referred By |
| 2.3 fill client | PASS | Name "Sipho Dlamini" (single Name field — no First/Last split in product), Individual, sipho.portal@example.com, +27 82 555 0101, 12 Loveday St / Johannesburg / 2001 / South Africa (ZA), ID 8501015800088 |
| 2.4 submit → client detail | PASS | Redirected to `/customers/f6f8050d-34fd-47c0-8894-0c5617dc8251`; lifecycle badge Prospect |
| 2.5 Run Conflict Check | PASS | Via client detail "More actions" → Run Conflict Check → /conflict-check page pre-filled with customer; ID number added; check run against registry |
| 2.6 result CLEAR | PASS | "No Conflict — Checked \"Sipho Dlamini\" at 06/07/2026 01:28:19"; History (1) |
| 2.7 screenshot | PASS | `day-02-conflict-check-clear.png` |
| 2.8–2.10 KYC verification | SKIPPED (mandate exemption) | No KYC adapter configured — client detail has no "Run KYC Verification" action (only the AI "Verify with AI" FICA surface, which requires uploaded docs + BYOAK). KYC integration = documented WONT_FIX exemption per cycle mandate; noted, not counted as gap |
| 2.11 Pipeline board renders legal-za stages | PASS* | Columns: Enquiry, Conflict check, Engagement, Won, Lost (Won/Lost render as regular columns with counts, not visually collapsed — non-material presentation difference). *See infra note below: first load hit a build error from stale node_modules, fixed by `pnpm install` |
| 2.12 New Enquiry | PASS | Pick existing customer → Sipho Dlamini; Title "Dlamini v RAF — enquiry"; Value 87500; Source Referral → card in Enquiry, 10% · R 8 750,00 weighted |
| 2.13 drag Enquiry → Conflict check | PASS | dnd-kit drag executed; card in Conflict check column, probability 30% · R 26 250,00; board announcement "Draggable item 64c2e57c… dropped over droppable area 8378be03…" |
| 2.14 client Work > Deals tab | PASS | `?tab=deals`: row "Dlamini v RAF — enquiry / DEAL-0001 / Conflict check / Open / R 87 500,00 / 6 Jul 2026" |
| 2.15 screenshot | PASS | `day-02-pipeline-enquiry.png` |

## Day 2 exit checkpoints
- Client created INDIVIDUAL with legal-specific fields: PASS
- Conflict check CLEAR: PASS
- KYC: not-configured state logged (mandate WONT_FIX exemption): DONE
- DEAL-0001 open at Conflict check: PASS

## Gaps
- **LZKC-001 (Low, OPEN)** — React hydration mismatch on `/pipeline`: server/client `aria-describedby` differs on the dnd-kit DealCard (`DndDescribedBy-0` vs `DndDescribedBy-3`), console error "A tree hydrated but some attributes… didn't match". Cosmetic/console-hygiene only; drag-and-drop functions correctly. Component: `frontend/components/pipeline/DealCard.tsx` (dnd-kit SSR id counter).

## Infra note (environment, resolved in-cycle — not a product gap)
- First visit to `/pipeline` failed with dev-overlay Build Error: `Module not found: Can't resolve '@dnd-kit/core'` from `frontend/components/pipeline/DealCard.tsx:3`. Root cause: local `node_modules` stale relative to lockfile (deps ARE declared in `frontend/package.json` + `pnpm-lock.yaml`; Phase 80/Epic 579 added them; this machine never re-ran install). Fixed by `pnpm install --frozen-lockfile` at workspace root (no code/lockfile change). Pipeline rendered correctly afterwards.
