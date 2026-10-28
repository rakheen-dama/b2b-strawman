# 627B — tenant migration state (read-only)

Queried 2026-10-28 ~06:11Z. Method: `docker exec <container> psql -U postgres -d <db>`; for every `tenant_%` schema
`select max(version::int) from <schema>.flyway_schema_history where version ~ '^[0-9]+$'` plus full history string.

## Source tree (main checkout HEAD f469434d7 == origin/main f469434d7)
Tenant migrations: V1__baseline, V2__add_project_template_work_type, V3__normalise_work_types, V4__phase88_demo_ready,
V5__phase88_acceptance_decline, V6__phase88_audit_customer_index, V7__phase88_letterhead. Global: V1, V2.
No open/remote branch for 627 found.

## Raw output
```
### b2b-postgres db=docteams  (queried 2026-10-28T06:11:26Z)
tenant schemas: 2
       schema        | max_v |       last_installed       |              history               
---------------------+-------+----------------------------+------------------------------------
 tenant_5039f2d497cf |     5 | 2026-10-25 00:19:35.385466 | 1:true,2:true,3:true,4:true,5:true
 tenant_56399d06a773 |     5 | 2026-10-25 00:19:35.320498 | 1:true,2:true,3:true,4:true,5:true
(2 rows)

public flyway max:
 public_max_v |            max             
--------------+----------------------------
            2 | 2026-10-24 19:13:06.756576
(1 row)

### e2e-postgres db=app  (queried 2026-10-28T06:11:27Z)
tenant schemas: 1
       schema        | max_v |       last_installed       |                     history                      
---------------------+-------+----------------------------+--------------------------------------------------
 tenant_7d218705360b |     7 | 2026-10-28 05:41:15.319129 | 1:true,2:true,3:true,4:true,5:true,6:true,7:true
(1 row)

public flyway max:
 public_max_v |            max             
--------------+----------------------------
            2 | 2026-10-28 05:40:45.528297
(1 row)

```

## Summary

| DB | Container / db | Tenant schemas | Max tenant version | Last tenant migration installed_on |
|----|----------------|----------------|--------------------|------------------------------------|
| Keycloak dev (persistent) | `b2b-postgres` (published on host :5432; accessed via docker exec), db `docteams`, creds postgres/postgres per `backend/src/main/resources/application-local.yml` | 2 (`tenant_5039f2d497cf`, `tenant_56399d06a773`) | **V5** | 2026-10-25 00:19:35 |
| E2E mock-auth | `e2e-postgres` (:5433), db `app` | 1 (`tenant_7d218705360b`, org `e2e-test-org`) | **V7** | 2026-10-28 05:41:15 |

Note: the dev DB is two versions behind the source tree (V6 + V7 pending; they apply on next dev-backend start —
this is normal lag, not a conflict). No DB on either stack holds a version > V7, and the source tree tops out at V7.

## Conclusion
**Next free tenant version = V8.** (Matches project memory "626A ... tenant V7 → next free V8".)
