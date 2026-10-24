# 624C observer evidence — PR #1672 @ 54b98f25a

Date: 2026-10-24. Stack: e2e mock-auth (frontend :3001, backend :8081, Mailpit :8026). Tenant: tenant_7d218705360b.
Org external id (read-only SQL): public.organizations.external_org_id = public.org_schema_mapping.external_org_id = `e2e-test-org`.
Action: firm UI (Playwright MCP, Alice/owner) → fee note detail → "Send fee note" (Acme Corp, portal contact contact@acme.example.com).

## Fee-note email (primary before/after)

| Phase | Build | Mailpit id | Subject | To | Portal link in body |
|---|---|---|---|---|---|
| BEFORE | main (image sha256:7d8adc07…, created 14:56:19Z) | g5GaH93kYA5khashPkNyX5 | Fee Note INV-0004 from E2E Test Organization | contact@acme.example.com | `http://localhost:3002/invoices/67427168-287f-406f-8e9a-73f704839d8f` (no orgId) |
| AFTER | 624C worktree 54b98f25a (image sha256:bffc303f…, created 16:14:12Z) | bSnGf2mwMTLQPBvnMyWQ5C | Fee Note INV-0005 from E2E Test Organization | contact@acme.example.com | `http://localhost:3002/invoices/5a74e1d8-e423-49d7-880e-18fd3b5572d5?orgId=e2e-test-org` |

Backend log (after): `Marked invoice 5a74e1d8-… as sent` → `Invoice email sent for invoice=5a74e1d8-… to=contact@acme.example.com` (16:14:55Z).

## Document-ready email (extra link type)

| Phase | Build | Mailpit id | Subject | Portal link |
|---|---|---|---|---|
| BEFORE (pre-existing, from an earlier main-build run at 06:11Z, not produced by this observer) | main | kQTEAEgbujYZ5ZrpcpZ6Ez | Document ready: matter-closure-letter-ndlovu-v-raf-… | `http://localhost:3002/projects/6dfd7dda-e329-4835-92b4-9d13252cb70e` (old /projects route, no orgId) |
| AFTER (observer: matter "Website Redesign" → Generate Statement of Account → Preview & Save) | 624C | C5VMMfjxUYVqtMcVaN7fyL | Document ready: statement-of-account-website-redesign-2026-10-24.pdf | `http://localhost:3002/matters/e7770f1f-4491-431d-8ef3-c1dd9cb9f0aa?orgId=e2e-test-org` |

Backend log (after): `PortalDocumentNotificationHandler.process entered: … template=statement-of-account …` → `Portal notification sent template=portal-document-ready contact=a7bc1566-… to=contact@acme.example.com`.

## Reference (not a new observation)
Magic-link email nbrasCPgLyX5d55s5RfYcm (14:56Z, 624B-era main build): `http://localhost:3002/auth/exchange?token=<redacted>&orgId=e2e-test-org` — already carried orgId pre-624C; no new request-link was made (per-email cap).

## New build live
- `e2e-rebuild.sh backend` run from /Users/rakheendama/Projects/2026/worktree-epic-624C (`docker compose up -d --build backend` only; exit 0; no data wipe).
- Container image changed 7d8adc07… → bffc303f…; /actuator/health UP.
- `docker cp e2e-backend:/app/app.jar` + `unzip -l` → `io/b2mash/b2b/b2bstrawman/portal/PortalLinkBuilder.class` (3217 bytes, 2026-10-24 18:13 local).

## Screenshots
/tmp/624C-before-sent.png (INV-0004 Sent), /tmp/624C-after-sent.png (INV-0005 Sent). Copies under main checkout .playwright-mcp/ (gitignored).

## Verdict: OBSERVED-PASS
