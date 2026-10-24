# Session 0 — Prep & Reset (legal-za 90-day, Keycloak) — 623A mid re-walk — 2026-10-24 — HEAD 6cc2b525e

Walker 623A. Scenario `qa/testplan/demos/legal-za-90day-keycloak.md` lines 43–49 + `qa/testplan/demo-readiness-keycloak-master.md` lines 115–152 (M.1–M.9).

## Stack identity

Main checkout `/Users/rakheendama/Projects/2026/b2b-strawman` at `6cc2b525e7a3f9d17a277f7dcffd4dadb6a46499` (= origin/main), backend compiled with `./mvnw -q clean compile` (V4 on the classpath), next 16.3.8 in frontend + portal. Services via `$MAIN/compose/scripts/svc.sh start all` (see `README.md` §KC-STACK). Backend restarted once for the reset (R.0 stop → R.6 start, fresh PID 70187).

## Reset log (623.2 §R — slice-authorised infra writes)

**These writes are the slice-authorised Session 0 infrastructure reset (623.2 §R, orchestrator ruling 1), not walk state.** They ran with the backend stopped, after the V4 AFTER snapshot and step-8 browser observation were recorded. Verbatim output: `logs/session0-reset.txt`. `tenant_56399d06a773` / `org_phase86_squash_smoke` was not touched.

| Step | Command (summary — exact statements in the block below) | Result |
|---|---|---|
| R.0 | `svc.sh stop backend` | stopped PID 68172; nothing on :8080; backend java 68329 gone |
| R.1 | KC admin REST (master `admin`), `GET /admin/realms/docteams/organizations` | only org: `{"id":"2c4034d3-f76d-4323-a172-7eabd018657d","name":"Mathebula & Partners","alias":"mathebula-partners"}` |
| R.1 | `DELETE …/organizations/2c4034d3-…` | **204** |
| R.1 | per-user DELETE loop (thandi/bob/carol) | all three "already gone" — the org DELETE removed the org-managed member users with it |
| R.1 | read-back `keycloak.user_entity` (realm docteams) / `org` | users: `padmin@docteams.local` only; orgs: none. Method: admin REST (not the console UI) |
| R.2 guard | 4 NOT-IN counts (acceptance / notification pref / trust balance / trust tx) | **0 / 0 / 0 / 0**; extra guard: every org-keyed portal row had `org_id='mathebula-partners'`; deadline rows not-Mathebula 0 → no foreign rows, proceeded |
| R.2 | portal read-model DELETE transaction | acceptance_requests 2, notification_preference 1, trust_transaction 2, trust_balance 1, deadline_view 0, invoices 4, requests 0, tasks 17, documents 5, projects 5, project_summaries 0, comments 0 → COMMIT |
| R.3 | `DROP SCHEMA tenant_5039f2d497cf CASCADE` | DROP SCHEMA (102 objects) |
| R.4 | public rows transaction | subscriptions 1, org_schema_mapping 1, organizations 1, access_requests 1 → COMMIT |
| R.5 | `DELETE http://localhost:8025/api/v1/messages` | **200** (count before 0, after 0) |
| R.6 | verify | tenant schemas: `tenant_56399d06a773` only; org_schema_mapping: `org_phase86_squash_smoke` only; access_requests 0; portal_projects 0 rows; organizations 1; subscriptions 1 |
| R.6 | `svc.sh start backend` | ready 15 s, :8080 pid 70187 etime 00:13; "Running tenant migrations for 1 schemas … `tenant_56399d06a773`: 4 … up to date … 0 migrations applied … completed"; health UP |

### Statements — as executed (brief §R script; per-statement results in logs/session0-reset.txt)

The statement text below is the brief's §R script, with `<org-id>` filled in. `logs/session0-reset.txt` records each statement's **output** in order (section headers `## R.0` … `## R.6`), but it does **not** echo the command text. So each statement is included only where the log shows its own result line. The `# log:` comments give that result. Where the log shows a result whose statement text was not recorded, that is stated below the block.

```bash
# R.0 — log: "backend: stopped"; "lsof :8080 -> []"; "backend java 68329 gone"
bash $MAIN/compose/scripts/svc.sh stop backend; lsof -nP -iTCP:8080 -sTCP:LISTEN

# R.1 — log: "token len 750"
KC=http://localhost:8180
TOKEN=$(curl -sf "$KC/realms/master/protocol/openid-connect/token" -d grant_type=password -d client_id=admin-cli -d username=admin -d password=admin | jq -r .access_token)
# log (under "orgs before:"): {"id":"2c4034d3-f76d-4323-a172-7eabd018657d","name":"Mathebula & Partners","alias":"mathebula-partners"}
curl -s "$KC/admin/realms/docteams/organizations?search=mathebula" -H "Authorization: Bearer $TOKEN" | jq -c '.[]|{id,name,alias}'
# log: "org delete 2c4034d3-f76d-4323-a172-7eabd018657d 204"
curl -s -o /dev/null -w 'org delete %{http_code}\n' -X DELETE "$KC/admin/realms/docteams/organizations/2c4034d3-f76d-4323-a172-7eabd018657d" -H "Authorization: Bearer $TOKEN"
# log: "thandi already gone" / "bob already gone" / "carol already gone" (lookup returned no id → the || branch)
for e in thandi bob carol; do
  id=$(curl -s "$KC/admin/realms/docteams/users?email=$e@mathebula-test.local&exact=true" -H "Authorization: Bearer $TOKEN" | jq -r '.[0].id // empty')
  [ -n "$id" ] && curl -s -o /dev/null -w "$e delete %{http_code}\n" -X DELETE "$KC/admin/realms/docteams/users/$id" -H "Authorization: Bearer $TOKEN" || echo "$e already gone"
done
# log (under "users after:"): padmin@docteams.local
docker exec b2b-postgres psql -U postgres -d keycloak -Atc "SELECT u.email FROM user_entity u JOIN realm r ON r.id=u.realm_id WHERE r.name='docteams' ORDER BY 1"
# log (under "orgs after:"): nothing before "(end orgs)"
docker exec b2b-postgres psql -U postgres -d keycloak -Atc "SELECT o.alias FROM org o JOIN realm r ON r.id=o.realm_id WHERE r.name='docteams'"

# R.2 guard — log: a 0 | b 0 | c 0 | d 0
P="docker exec b2b-postgres psql -U postgres -d docteams -c"; T=tenant_5039f2d497cf
$P "SELECT (SELECT count(*) FROM portal.portal_acceptance_requests WHERE portal_contact_id NOT IN (SELECT id FROM $T.portal_contacts)) a, (SELECT count(*) FROM portal.portal_notification_preference WHERE portal_contact_id NOT IN (SELECT id FROM $T.portal_contacts)) b, (SELECT count(*) FROM portal.portal_trust_balance WHERE customer_id NOT IN (SELECT id FROM $T.customers)) c, (SELECT count(*) FROM portal.portal_trust_transaction WHERE customer_id NOT IN (SELECT id FROM $T.customers)) d"

# R.2 deletes — log: BEGIN, then DELETE 2 / 1 / 2 / 1 / 0 / 4 / 0 / 17 / 5 / 5 / 0 / 0 (in this statement order), COMMIT
$P "BEGIN;
DELETE FROM portal.portal_acceptance_requests WHERE portal_contact_id IN (SELECT id FROM $T.portal_contacts);
DELETE FROM portal.portal_notification_preference WHERE portal_contact_id IN (SELECT id FROM $T.portal_contacts);
DELETE FROM portal.portal_trust_transaction WHERE customer_id IN (SELECT id FROM $T.customers);
DELETE FROM portal.portal_trust_balance WHERE customer_id IN (SELECT id FROM $T.customers);
DELETE FROM portal.portal_deadline_view WHERE customer_id IN (SELECT id FROM $T.customers);
DELETE FROM portal.portal_invoices WHERE org_id='mathebula-partners';
DELETE FROM portal.portal_requests WHERE org_id='mathebula-partners';
DELETE FROM portal.portal_tasks WHERE org_id='mathebula-partners';
DELETE FROM portal.portal_documents WHERE org_id='mathebula-partners';
DELETE FROM portal.portal_projects WHERE org_id='mathebula-partners';
DELETE FROM portal.portal_project_summaries WHERE org_id='mathebula-partners';
DELETE FROM portal.portal_comments WHERE org_id='mathebula-partners';
COMMIT;"

# R.3 — log: "NOTICE: drop cascades to 102 other objects" … "DROP SCHEMA"
$P "DROP SCHEMA tenant_5039f2d497cf CASCADE;"

# R.4 — log: BEGIN, DELETE 1 / 1 / 1 / 1, COMMIT
$P "BEGIN;
DELETE FROM public.subscriptions WHERE organization_id = (SELECT id FROM public.organizations WHERE external_org_id='mathebula-partners');
DELETE FROM public.org_schema_mapping WHERE external_org_id='mathebula-partners';
DELETE FROM public.organizations WHERE external_org_id='mathebula-partners';
DELETE FROM public.access_requests WHERE email LIKE '%@mathebula-test.local';
COMMIT;"

# R.5 — log: "count before: 0", "mailpit clear 200", "count after: 0"
curl -s -o /dev/null -w 'mailpit clear %{http_code}\n' -X DELETE http://localhost:8025/api/v1/messages

# R.6 verify — log: tenant_56399d06a773 (1 row)
$P "SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%' ORDER BY 1"
# log: org_phase86_squash_smoke | tenant_56399d06a773
$P "SELECT external_org_id, schema_name FROM public.org_schema_mapping"
# log: portal_projects "org_id | count" (0 rows). The access_requests count is NOT confirmable verbatim: the log shows a column
# header "access_requests" (value 0), so the executed SELECT aliased the count — the brief's un-aliased form below is not what ran.
$P "SELECT count(*) FROM public.access_requests"; $P "SELECT org_id, count(*) FROM portal.portal_projects GROUP BY 1"
# log: "## R.6 restart backend 2026-10-24T07:07:43Z" … "ready (15s)", "8080 pid 70187 etime 00:13", tenant_56399d06a773 "up to date … 0 migrations applied", health UP
bash $MAIN/compose/scripts/svc.sh start backend
```

What the log cannot confirm:
- **Logging wrappers.** Several output lines were produced by logging wrappers around the brief's statements, not by the bare statements: `token len 750`, `lsof :8080 -> []`, `org delete 2c4034d3-… 204` (the brief's `-w` format prints no id), and `count before/after` around R.5. The operations themselves (the REST calls, SQL `SELECT`/`DELETE`/`DROP`, Mailpit `DELETE`) match the brief one-for-one, and so do their results. The exact wrapper text is not recorded.
- **R.1 search query string.** The org GET result is logged (the jq `{id,name,alias}` shape), but the log does not record whether the `?search=mathebula` filter was in the URL. The realm held only this one org (orgs-after read-back: none), so the result is the same either way.
- **Extra read-backs whose statement text was not recorded.** "users before:" / "orgs before:" (the R.1 read-back SELECTs, run before the deletes as well), the R.2 "extra guard: org_id values in org-keyed portal tables" queries (8 result sets plus `deadline_rows_not_mathebula 0`), the R.6 `organizations 1` / `subscriptions 1` counts, the port-holder `ps` line and the post-start `jq` migration extract. The log shows their outputs only, so they are **not** in the block above.
- **R.6 access_requests count.** The value 0 is logged, but under the header `access_requests`. The executed text therefore differs from the brief's `SELECT count(*) FROM public.access_requests` (it had a column alias), and the exact text was not recorded.
- **R.6 `N1=$(wc -l …)`.** The offset is not logged. It is moot anyway: `svc.sh start` truncates the log (README § KC-STACK infra note).

## Scenario steps

| Step | Result | Evidence (what was observed) | Mailpit ID | Screenshot | Gap |
|---|---|---|---|---|---|
| 0.A | PASS | No schema named `tenant_mathebula*` exists (`information_schema.schemata`); the Mathebula tenant is `tenant_5039f2d497cf` (deterministic slug hash) → **dropped** (R.3, 102 objects). After reset only `tenant_56399d06a773` remained. The doc's wording (`tenant_mathebula*`) does not match how schemas are named | — | — | G88-M1 (doc) |
| 0.B | PASS | thandi@/bob@/carol@mathebula-test.local removed from realm `docteams` (via the org DELETE 204; per-user loop found them already gone); read-back: `padmin@docteams.local` only | — | — | — |
| M.1 | PASS | Dev infra already up: b2b-keycloak / b2b-postgres / b2b-mailpit / b2b-localstack healthy; `dev-up.sh` not needed | — | — | — |
| M.2 | N/A | Not run by rule (realm + padmin exist; bootstrap would reset member passwords) | — | — | — |
| M.3 | PASS | `svc.sh start all` from `$MAIN` — four services ready (README §KC-STACK); backend restarted once for the reset (fresh PID 70187) | — | — | — |
| M.4 | PASS | 8080 `{"status":"UP"}`, 8443 `{"status":"UP"}`, 3000 → 200, 3002/login → 200, `:8180/realms/docteams` → KC-OK (`logs/kc-stack-svc-status.txt`) | — | — | — |
| M.5 | PASS | Mailpit total 0 before the reset | — | — | — |
| M.6 | PASS | Ran with `-d docteams` (the doc's `-d app` fails: database "app" does not exist) and schema `tenant_5039f2d497cf` (the doc's example `tenant_mathebula_partners` does not exist). Dropped; smoke tenant kept | — | — | G88-M1 (doc) |
| M.7 | PASS | Admin REST read-back (not the console UI): realm `docteams` users = `padmin@docteams.local` only; orgs none | — | — | — |
| M.8 | PASS | `DELETE /api/v1/messages` → 200, total 0 | — | — | — |
| M.9 | PASS | Playwright `context.clearCookies()` + `localStorage/sessionStorage.clear()` before Phase A | — | — | — |
| CP-1 All four services healthy | PASS | as M.4 | — | — | — |
| CP-2 padmin only KC user | PASS | as M.7 | — | — | — |
| CP-3 No leftover tenant schemas for the vertical | PASS | `tenant_%` = `tenant_56399d06a773` only (smoke, not legal-za Mathebula) | — | — | — |
| CP-4 Mailpit empty | PASS | total 0 | — | — | — |
| CP-5 Clean browser session | PASS | cookies + storage cleared | — | — | — |

## Day 0 seeding (not scored)

Browser: Playwright MCP, 1440×900. All state created through the UI. Read-only verification: `logs/day0-seeded-state.txt`, `logs/day0-provisioning.txt`.

| Phase | Steps run | Outcome | Created ids / Mailpit ids | Notes |
|---|---|---|---|---|
| A Access request & OTP | 0.1–0.8 | OK. Landing → Get Started → `/request-access` (Work Email, Full Name, Organisation Name, Country, Industry) → "Check Your Email" → OTP 825505 → "Request Submitted … submitted for review" | OTP `bgiRhuB2ThwbjxduUrkufv` ("Your Kazi verification code") | Industry list = Legal Services, Other |
| B Platform admin approval | 0.9–0.18 | OK. padmin first login was **single-step** (Email+Password on one form) → `/platform-admin/access-requests`; Pending row Mathebula & Partners / Legal Services / South Africa (inline row fields, no detail view — G-A1) → Approve → AlertDialog "Approve Access Request" → Approve → Pending empty, row under Approved. 0.17 `org_settings.vertical_profile = legal-za`, currency ZAR (read-only SELECT) | invitation `82XSyceTe6H7gNexZ4nDMc` ("Invitation to join the Mathebula & Partners organization") | **Tooling incident (not product):** the first Approve clicks did nothing. Diagnosis: no `pointerdown`/`click`/`mousemove`/`keydown` events reached the document at all (capture listeners empty) while `document.hasFocus()` was true and the button was hydrated with an `onClick`; a DOM `.click()` opened the dialog instantly. The Playwright MCP page's input pipeline was stuck. `browser_close` + re-navigate fixed it; the next real click opened the dialog in 66 ms. No product gap. Provisioning: backend log "Could not find Keycloak user for email thandi@… to set as org creator" / "Cannot set password — no Keycloak user found" (WARN, expected: Thandi registers later in 0.19) |
| C Owner registration | 0.19–0.25 | OK. Invite link (HTML-escaped in Mailpit `Text`, decoded) in a cleared context → KC page "Create an account to join the Mathebula & Partners organization", email pre-filled → Thandi / Mathebula / SecureP@ss1 → `/org/mathebula-partners/dashboard`. Sidebar: org "Mathebula & Partners", footer "Thandi Mathebula"; zones Today · Matters · Clients (open) · Fees · Trust · Firm (collapsed) | — | `screens/0.25-dashboard-thandi.png` | Home shows "Getting started with Kazi 0 of 6 complete" |
| D Team invites | 0.26–0.36 | OK. `/team` "Invite a team member" inline form (Email + Role + Send Invite): bob@ Admin, carol@ Member → Pending Invitations lists both. Bob / Carol registered via their KC links → each landed on `/org/mathebula-partners/dashboard` (footer "Bob Ndlovu" / "Carol Mokoena"); cookies cleared after each | Bob `bpNFVYg9uSpdZ3ynmm4Z3G`, Carol `LrRQahuUAwUYjQ99hBPLxU` | No "Upgrade to Pro" gate |
| E General settings & branding | 0.37–0.40 | OK. `/settings/firm?tab=general`: Default Currency "ZAR — South African Rand"; Brand Color → `#1B3A4B`; Upload Logo → `mathebula-logo-623a.png` (128×128); Save Settings; after reload Brand Color `#1B3A4B`, "Organization logo" img (LocalStack `…/tenant_5039f2d497cf/branding/logo.png`) and sidebar "Mathebula & Partners logo" | — | — | DB: brand_color `#1B3A4B`, has_logo t |
| F Rates & tax | 0.41–0.45 | OK. `/settings/fees?tab=tariffs` (heading "Rates & Currency"): rates not pre-seeded ("Not set"); Add Rate ×6 → Billing Thandi R 2 500,00 · Bob R 1 200,00 · Carol R 550,00; Cost Thandi R 1 000,00 · Bob R 500,00 · Carol R 200,00 (ZAR, effective 24 Oct 2026, Ongoing). Tax tab: "VAT — Standard 15.00% Default Active" pre-seeded. Fee runs tab: "Default payment terms" = **30** (left as is) | — | — | Cost-rate dialog labels the field "Hourly Cost" (not "Hourly Rate") |
| G Custom fields | — | not run — verification only | | | |
| H Templates & automations | — | not run — verification only | | | |
| I Progressive disclosure | — | not run — verification only | | | |
| J Trust account | 0.61–0.64 | OK. Trust zone Overview "No Trust Accounts" → Add Account: Mathebula & Partners Trust Account / Standard Bank / 051001 / 1234567890 / General / primary ✓ / dual approval off → Create. Trust › Interest › LPFF Rate History › Add Rate: effective 2026-10-24, Rate 6.5 %, LPFF Share 0 % → "1 rate configured … 6.50% 0.00%". Overview: Trust Balance R 0,00, Active Clients 0, Pending Approvals 0, "No transactions recorded yet" | trust account `1a2cfeee-0562-47e6-88d3-8a79dd7ba746` | |
| K Billing page | — | not run — verification only | | | |

### Re-provisioned tenant — V4 evidence #2 (`logs/day0-provisioning.txt`)

```
07:13:24.260Z TenantProvisioningService  Provisioning tenant schema tenant_5039f2d497cf for org mathebula-partners on shard primary
07:13:24.324Z DbMigrate  Current version of schema "tenant_5039f2d497cf": << Empty Schema >>
07:13:24.421Z DbMigrate  Migrating schema "tenant_5039f2d497cf" to version "1 - baseline"
07:13:25.391Z DbMigrate  Migrating schema "tenant_5039f2d497cf" to version "2 - add project template work type"
07:13:25.404Z DbMigrate  Migrating schema "tenant_5039f2d497cf" to version "3 - normalise work types"
07:13:25.417Z DbMigrate  Migrating schema "tenant_5039f2d497cf" to version "4 - phase88 demo ready"
07:13:25.433Z DbMigrate  Successfully applied 4 migrations to schema "tenant_5039f2d497cf", now at version v4 (execution time 00:00.980s)
07:13:25.918Z TenantProvisioningService  Successfully provisioned tenant tenant_5039f2d497cf for org mathebula-partners
```
`org_schema_mapping`: `mathebula-partners → tenant_5039f2d497cf` (+ smoke). `flyway_schema_history`: V1 baseline 09:13:24.320 · V2 09:13:25.381 · V3 09:13:25.398 · **V4 phase88 demo ready 2026-10-24 09:13:25.411561 (SAST)**, all success.


## Handoff for 623B

- Org slug `mathebula-partners`; KC org id `a21f5935-c745-4454-8dde-aa6b758af449`; `public.organizations.id` `72abce40-ec2c-498e-a4f8-e195d329f47b`; tenant `tenant_5039f2d497cf` (V1–V4).
- Members (read-only SELECT, `logs/day0-seeded-state.txt`): bob@ admin `04a52be7-6661-4411-9251-dfe75a7a6ad6`; carol@ member `133de597-3305-4941-baf9-09cc0e46ba97`; thandi@ owner `6b188912-21c9-43fc-bc64-1781ea557a02`. Passwords SecureP@ss1/2/3.
- Trust account `1a2cfeee-0562-47e6-88d3-8a79dd7ba746` (primary, GENERAL, Standard Bank 051001/1234567890); LPFF 6.5 % from 2026-10-24.
- Mailpit: OTP `bgiRhuB2ThwbjxduUrkufv`; invitations Thandi `82XSyceTe6H7gNexZ4nDMc`, Bob `bpNFVYg9uSpdZ3ynmm4Z3G`, Carol `LrRQahuUAwUYjQ99hBPLxU`.
- Browser: Playwright MCP persistent profile; last signed-in actor Bob (clear cookies at the next actor switch).
- Dev artefacts in `$MAIN` (not committed): `.svc/logs`, `.playwright-mcp/` (screens `623a-*`, fixtures `Sipho Dlamini — certified FICA pack.pdf`, `Particulars of Claim — draft v1.pdf`, `mathebula-logo-623a.png`), possibly `portal/next-env.d.ts`.
- Stack left running; caffeinate PID 67665; `/tmp/.e2e-keycloak-mode` present.
