# G89 exit re-score: targeted Keycloak re-walk on a fresh tenant (evidence index)

- **Brief:** `tasks/g89-fixes/exit-rescore-brief.md`.
- **Recipe:** 636B (`qa_cycle/evidence/phase89-rewalk/README.md`, `HANDOFF.md`). D-1 is option **A**.
- **Status vocabulary:** PASS / FAIL / PARTIAL / DEFERRED / NOTE / SKIP.
- **Sessions:**
  - **W1:** stack, fresh tenant, Days 0–45, 634E(1). Steps in `steps/W1.md`, handoff in `HANDOFF-W1.md`.
  - **W2:** Days 46–90 and portal. Pending.

## Build and stack (W1, 2026-11-11 13:07 SAST)

| Item | Value |
|---|---|
| Build | main `819034db5c101c617b52e1f153ee16e608f582ed`. origin/main `8ca82c935` is the same code plus the brief doc commit. `$MAIN` status: only untracked Phase 90 ideation and requirements files. |
| Services at identity (svc PID / port holder / start) | backend 56461 / **56639** (13:04:34)<br>gateway 56661 / **56786** (13:04:49)<br>frontend 56804 / **56847** (13:04:54)<br>portal 56853 / **56896** (13:04:59)<br>KC :8180 Docker (since 16 Oct). All cwd under `$MAIN`. |
| Backend after the D-1 reset | stopped 13:08:56; restarted by `svc.sh start backend`. :8080 holder **60902** (started 13:09:41), health UP, "Running tenant migrations for 1 schemas". |
| Health | 8080 UP · 8443 UP · 3000 200 · 3002/login 200 · KC realm `docteams` 200 · backend ERROR lines 0 (before and after W1) |
| Compiled migrations | tenant ends `V14__g89_contains_trust_data.sql`; global ends `V5__portal_strip_matter_type_suffix.sql` (target/classes 13:04) |
| Power | AC, 92 %; `caffeinate -dims` PID 58540 (W1) |
| Mailpit | 33 → cleared at the reset → 12 at W1 end (`logs/mailpit-w1.txt`) |

Detail: `logs/stack-identity.txt`, `logs/session0-reset.txt`.

## Tenant (fresh, D-1 option A)

| Item | Value |
|---|---|
| Org | "Mathebula & Partners", slug `mathebula-partners` |
| Schema | **`tenant_5039f2d497cf`**. The name is deterministic, so the old schema was dropped and re-created under the same name. |
| KC org id | **`fe76c0d4-2174-4001-ae58-af2175b0fb1d`** (the old org `2cc9bcfd-…` was deleted) |
| `public.organizations.id` | `36d051c2-6c71-45c7-8e90-2c0b8759491c` (created 11:12:16Z) |
| Flyway | tenant **V14** (14 rows, installed 13:12:16–13:12:18 SAST) · public **V5** · `org_phase86_squash_smoke` (`tenant_56399d06a773`) V14, untouched |
| Profile / packs | `vertical_profile = legal-za`; "Trust report pack seeded … 7 definitions" (`report_definitions` total 10) |
| Old tenant backup | Scratch: `/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/backup/g89-w1-tenant-backup-202611111308.sql` (842 153 B) and `…-portal-rows.csv.txt` (24 376 B). The scratch area is session-local, so copy these out if they must be kept. |

## W1 results

PASS 7 · FAIL 0 · PARTIAL 0 · DEFERRED 0. The rows:
- S1-02
- S1-29
- S1-30
- S1-65
- S1-67
- S1-76
- 634E(1)

There is one new candidate, W1-c1 (LOW, scenario mismatch: 47.2 banking details). See `steps/W1.md`.

## Files

| Path | What |
|---|---|
| `steps/W1.md` | The W1 row table (scored + NOTE) and candidates |
| `HANDOFF-W1.md` | Tenant ids, users, matters, trust, fee notes and open items for W2 |
| `screenshots/` | 31 frames (W1) |
| `logs/` | stack, reset, provisioning, read-only SELECTs, PDFs + extracted text, Mailpit list, console log, `harness/` (the Playwright snippets used) |
