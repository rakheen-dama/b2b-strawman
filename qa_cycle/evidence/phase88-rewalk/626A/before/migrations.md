# Migration-number evidence (OQ #9) — read-only SELECTs, captured 2026-10-27T18:46:20Z

## E2E stack (e2e-postgres, db app)

```
     schema_name     
---------------------
 tenant_7d218705360b
(1 row)

 version |         description          |        installed_on        | success 
---------+------------------------------+----------------------------+---------
 6       | phase88 audit customer index | 2026-10-27 18:22:45.251119 | t
 5       | phase88 acceptance decline   | 2026-10-27 18:22:45.2445   | t
 4       | phase88 demo ready           | 2026-10-27 18:22:45.225685 | t
 3       | normalise work types         | 2026-10-27 18:22:45.215672 | t
(4 rows)

 version |       description        |        installed_on        | success 
---------+--------------------------+----------------------------+---------
 2       | portal contact directory | 2026-10-27 18:22:26.325305 | t
 1       | baseline                 | 2026-10-27 18:22:26.159993 | t
(2 rows)

```

## Keycloak dev stack (b2b-postgres = b2mash.local:5432, db docteams)

```
     schema_name     
---------------------
 tenant_5039f2d497cf
 tenant_56399d06a773
(2 rows)

       schema        | version |          description           |        installed_on        | success 
---------------------+---------+--------------------------------+----------------------------+---------
 tenant_5039f2d497cf | 5       | phase88 acceptance decline     | 2026-10-25 00:19:35.385466 | t
 tenant_5039f2d497cf | 4       | phase88 demo ready             | 2026-10-24 09:13:25.411561 | t
 tenant_5039f2d497cf | 3       | normalise work types           | 2026-10-24 09:13:25.398636 | t
 tenant_5039f2d497cf | 2       | add project template work type | 2026-10-24 09:13:25.381194 | t
(4 rows)

       schema        | version |          description           |        installed_on        | success 
---------------------+---------+--------------------------------+----------------------------+---------
 tenant_56399d06a773 | 5       | phase88 acceptance decline     | 2026-10-25 00:19:35.320498 | t
 tenant_56399d06a773 | 4       | phase88 demo ready             | 2026-10-24 09:01:53.871463 | t
 tenant_56399d06a773 | 3       | normalise work types           | 2026-10-22 17:12:49.031763 | t
 tenant_56399d06a773 | 2       | add project template work type | 2026-10-22 17:12:49.002347 | t
(4 rows)

 version |       description        |        installed_on        | success 
---------+--------------------------+----------------------------+---------
 2       | portal contact directory | 2026-10-24 19:13:06.756576 | t
 1       | baseline                 | 2026-10-18 04:20:53.007533 | t
(2 rows)

```

## Source tree (worktree on 1c2447f12)

`backend/src/main/resources/db/migration/tenant/`: V1 baseline, V2 add_project_template_work_type, V3 normalise_work_types, V4 phase88_demo_ready, V5 phase88_acceptance_decline, V6 phase88_audit_customer_index. Global: V1, V2.

## Conclusion

| Stack | Tenant schema(s) | Max applied tenant version |
|---|---|---|
| E2E (e2e-postgres :5433, db `app`) | `tenant_7d218705360b` | **V6** (phase88 audit customer index) |
| Keycloak dev (b2b-postgres = b2mash.local:5432, db `docteams`) | `tenant_5039f2d497cf`, `tenant_56399d06a773` (both exist) | **V5** (phase88 acceptance decline). V6 is not applied yet because the dev backend has not started since V6 merged; it will apply on next start. |

The highest tenant version in source and on any persistent stack is **V6**. The next free tenant migration is **V7**. Global migrations are at V2 on both stacks, so the next free global migration is V3 if one is ever needed.
