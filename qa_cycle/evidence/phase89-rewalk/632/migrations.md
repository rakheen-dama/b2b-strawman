# Epic 632 — Keycloak tenant migration state (632.17 input to OQ #9)

Recorded 2026-11-05 02:19 SAST, after the post-merge KC-STACK restart on main `51f3e7719` (backend PID 83650; `./mvnw -q clean compile` first).

| Tenant schema | Org | Latest tenant migration | legal-za pack |
|---|---|---|---|
| `tenant_56399d06a773` | `org_phase86_squash_smoke` | **10 — phase89 go live** (installed 2026-11-05 02:19:20) | v12 |
| `tenant_5039f2d497cf` | `mathebula-partners` | **10 — phase89 go live** | v12 |

`public` schema stays at global version 2. Boot log lines: `../632C/kc-boot-log.txt`; raw read-only SELECT output: `../632C/kc-migrations.txt`.
