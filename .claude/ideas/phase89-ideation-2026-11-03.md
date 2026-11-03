# Phase 89 ideation — 2026-11-03 (post-Phase 88 closure triage)

Builds on `phase88-ideation-2026-10-22.md`. Input: Phase 88 closure (`tasks/phase88-demo-ready-root-cause-closure.md` § Closure report), authoritative register `qa_cycle/evidence/phase88-rewalk/full/gaps.md` (63 open: 3 HIGH / 15 MED / 45 LOW + F36 hypothesis).

## Lighthouse domain
SA small/medium law firms (legal-za only since Phase 86). Next milestone is design-partner firms live (Phase 84 take-on) → the bar shifts from "the demo script walks" to "a live firm, its auditor and its clients see no defect".

## Key insight from the triage
- 45 of the 59 FAIL rows outside §5 were **scenario mismatches waiting on the founder**, not code. One authorisation pass cleared them.
- The walk's severity scale is demo-centric. Several MEDIUMs are **go-live blockers**: Section 35 trust reports never seeded (F40), interest run unapprovable (F39), POPIA export/anonymise broken (F43/F44), conflict check crash (F38), ledger running balance wrong (F23).
- Recurring defect classes: time-of-day (UTC server vs SAST browser), name/date-derived storage keys, seeders/listeners that exist but are never wired, FE/BE DTO drift on rarely-walked screens, raw enums on client-facing pages.

## Decisions (all recommendations accepted)
- Sequence: 3 HIGH fix PRs standalone now (P1 clock F48+F49, P2 S3 keys F33, P3 OTP parties F12) → Phase 89 → Phase 84.
- Exit bar: 0 HIGH + 0 MEDIUM, LOW tracked; targeted Keycloak re-walk (closed-gap steps + amended steps) + 3 suites, not a full 90-day walk.
- Clock: product-wide `Africa/Johannesburg` + ban zone-less `now()`; no per-firm TZ setting (SA-only, no DST).
- OTP: explicit `seller_name` / `purchaser_name` matter fields; attorney sign-off on clause wording.
- Build: portal payment receipt (F32), portal deadline sync for court dates + prescriptions (§5b row 19, no mark-read).
- Amend (§6 authorised): 7 design-proven mismatches; F37 (10 rows); drop F34 final digest; add info-request step (F28); portal profile read-only (§5b row 20); retire committed visual baselines (P-E.2b/P-E.10).
- G-D10b: hide audit event key visually, keep it searchable; ADR-357 unamended.
- F47: add a `typecheck` script rather than amend E.11c.
- Phase 87 rulings ratified: 615D All-clients default; OQ #18 spec retirement. 611A.2 Mandate badge → Phase 84.

## Phase roadmap
P1–P3 HIGH PRs → Phase 89 (E1 trust, E2 POPIA+conflict, E3 documents+receipt, E4 portal tasks/deadlines/copy, E5 firm MEDIUMs, E6 scenario+tooling, E7 exit+deck re-shoot) → Phase 84 take-on → LOW backlog cleanup as filler.

## Domain notes
- Section 35 (Legal Practice Act) trust reports + LPFF interest are auditor-facing; treat any trust or report defect as go-live-blocking regardless of the demo scale.
- Conveyancing: transferring attorney is often appointed by the seller — never infer a contract party from "the firm's client".
- POPIA data-subject export/anonymisation must work before a live firm holds real personal data.
