# QA Cycle Status — Consulting Agency 90-Day Demo (Fresh Tenant, Keycloak) — 2026-04-17

## Current State

- **QA Position**: Day 0 — 0.1 (not yet started)
- **Cycle**: 1
- **Dev Stack**: READY
- **NEEDS_REBUILD**: false (GAP-C-01 fix live as of backend restart at 2026-04-17 10:16 SAST)
- **Branch**: `bugfix_cycle_consulting_2026-04-17`
- **Scenario**: `qa/testplan/demos/consulting-agency-90day-keycloak.md`
- **Focus**: Fresh tenant run — full onboarding through 90-day consulting agency lifecycle. Re-run after v1 used wrong vertical profile.
- **Auth Mode**: Keycloak (real OIDC)
- **ALL_DAYS_COMPLETE**: false

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend (kc mode) | http://localhost:3000 | UP |
| Backend (local+keycloak profile) | http://localhost:8080 | UP |
| Gateway (BFF) | http://localhost:8443 | UP |
| Portal | http://localhost:3002 | UP |
| Keycloak | http://localhost:8180 | UP |
| Mailpit UI | http://localhost:8025 | UP |
| Postgres (docteams) | localhost:5432 | UP |

## Carry-Forward Watch List (from 2026-04-14 archive)

These are architectural gaps expected to recur on this run — log fresh GAP IDs if they reproduce, then defer:

- **Retainer primitive missing** (GAP-C-07, GAP-C-09 in v1) — no native retainer entity; manual project-per-cycle workaround. HIGH severity but out-of-scope for a QA cycle.
- **Retainer invoice format** (GAP-C-08 in v1) — retainer invoices indistinguishable from project invoices (no hours consumed/remaining summary).

## Gap Tracker

| GAP_ID | Day / Checkpoint | Severity | Status | Summary | Owner | Retries |
|--------|------------------|----------|--------|---------|-------|---------|
| GAP-C-01 | D0 / 0.10 | HIGH | FIXED | `INDUSTRY_TO_PROFILE` missing Marketing/Consulting → consulting-za entries | Dev | 0 |

## Legend

- **Status**: OPEN -> SPEC_READY -> FIXED -> VERIFIED | REOPENED | STUCK | WONT_FIX | RESOLVED
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-17 — Cycle initialized. V1 (2026-04-14) archived to `_archive_2026-04-14_consulting-v1-wrong-profile/` — that run hit the industry → profile mapping bug and silently fell back to `consulting-generic`, invalidating 6 downstream profile-content gaps.
- 2026-04-17 — Orchestrator pre-diagnosed GAP-C-01 root cause: `AccessRequestApprovalService.java:29-32` has `INDUSTRY_TO_PROFILE` map with only "Accounting" and "Legal Services". "Marketing" and "Consulting" resolve to null. Fix spec written to `qa_cycle/fix-specs/GAP-C-01.md` directly (skipping Product triage since evidence is unambiguous).
- 2026-04-17 — Dev Agent: GAP-C-01 fixed via PR #1053, merged. Backend needs restart to pick up mapping change.
- 2026-04-17 10:16 SAST — Infra Agent: Dev stack READY. Docker infra (postgres, keycloak, mailpit, localstack) was already up (13h uptime, healthy). Local services: stale 10h-old backend on port 8080 was holding the port so the svc.sh restart failed silently — killed the stale java PID 41862, started fresh backend (PID 72337, up in 9.2s) which loaded `consulting-za` vertical profile and picked up PR #1053. Gateway was down (stale) — started fresh (PID 71302). Portal was down — started fresh (PID 71100). Frontend (PID 15582, 13h Next.js dev server) left running since HMR handles code changes. All health endpoints return UP / 200. No ERROR entries in backend log after "Started BackendApplication". Non-fatal warnings only (LibreOffice missing → docx4j fallback; Hibernate dialect auto-detect suggestion; CGLIB proxying notes).
