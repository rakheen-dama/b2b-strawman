# Day 0 — Firm org onboarding (Keycloak flow) — Cycle 2026-07-12

## Session 0 — Prep & Reset

| Step | Result | Evidence |
|---|---|---|
| 0.A svc status | PASS | `svc.sh status`: backend 8080, gateway 8443, frontend 3000, portal 3002 all RUNNING+HEALTHY; docker infra (b2b-postgres/keycloak/mailpit/localstack) healthy |
| 0.B portal :3002 | PASS | `curl` → 307 (redirect to login — service live, same as prior cycle) |
| 0.C no stale tenant schema | PASS (after reset) | Stale prior-cycle Mathebula org found (org `d61bccc5…`, schema `tenant_5039f2d497cf`, KC org `cae8ba47…`). Reset per Session 0.C/0.D/0.E (same recipe as 2026-07-06 cycle): dropped schema (119 objects cascaded), deleted organizations/org_schema_mapping/subscriptions/access_requests rows, deleted KC org (204; managed members cascade-deleted) |
| 0.D no stale KC users | PASS (after reset) | thandi/bob/carol users gone (verified 0 users, 0 orgs matching in realm docteams) |
| 0.E no stale portal contacts | PASS (after reset) | contacts lived in dropped schema `tenant_5039f2d497cf` |
| 0.F Mailpit empty | PASS | DELETE /api/v1/messages → 200; total:0 |
| 0.G PayFast config | DOCUMENTED | Sandbox creds in `backend/src/main/resources/application-local.yml` lines 114-117 (merchant-id 10000100, sandbox defaults) |
| 0.H legal-za pack installed | PASS | Backend restarted post-reset (ready 18s, 0 ERROR); log: legal-za packs "already installed", pack reconciliation 3/3 tenants OK |

## Phase A — Access request & OTP

| Checkpoint | Result | Evidence |
|---|---|---|
| 0.1 landing loads | PASS | http://localhost:3000 → "Kazi — Practice management, built for Africa", 0 console errors |
| 0.2 Get Started → /request-access | PASS | URL /request-access |
| 0.3 form fields visible | PASS | Work Email, Full Name, Organisation Name, Country, Industry |
| 0.4 fill + submit | PASS | thandi@mathebula-test.local / Thandi Mathebula / Mathebula & Partners / South Africa / Legal Services |
| 0.5 OTP step | PASS | "Check Your Email" step 2 with code input, 10-min expiry timer |
| 0.6 OTP email | PASS | Mailpit msg `XRKm6jZyPaXKirVnaMZKQn`, subject "Your Kazi verification code" |
| 0.7 enter OTP → Verify | PASS | OTP 982310 accepted |
| 0.8 success card | PASS | "Request Submitted — Your access request has been submitted for review" |

## Phase B — Platform admin approval

| Checkpoint | Result | Evidence |
|---|---|---|
| 0.9 /dashboard → KC redirect | PASS | Redirected via /sign-in → KC realm docteams (gateway-bff client) |
| 0.10 padmin login | PASS | Two-step KC theme (email → password); landed on /platform-admin/access-requests |
| 0.11 access-requests page | PASS | URL confirmed |
| 0.12 request in Pending | PASS | Row: Mathebula & Partners, Legal Services, South Africa |
| 0.13 inline fields | PASS | Org Name, Email, Name, Country, Industry, Submitted ("1 minute ago"), Status PENDING all in row |
| 0.14 Approve → AlertDialog → Confirm | PASS | Dialog: "This will create a Keycloak organization, provision a tenant schema, and send an invitation to thandi@mathebula-test.local." → confirmed (via synthetic click — see harness quirk note) |
| 0.15 Approved, no error | PASS | Approved tab shows Mathebula & Partners APPROVED; backend log "Provisioned tenant schema for org c0f85aa0-30b5-4a87-ac65-7cdb1d7c7257 (slug=mathebula-partners)" |
| 0.16 profile = legal-za | PASS | DB (read-only): `tenant_5039f2d497cf.org_settings.vertical_profile = legal-za` (schema name deterministically re-derived, same as prior cycle) |
| 0.17 invitation email | PASS | Mailpit `QRNggmoovL4Z8eNwYdcYyK` "Invitation to join the Mathebula & Partners organization" |

Note: two expected WARNs at approval time ("Could not find Keycloak user for email thandi@… to set as org creator" / "Cannot set password") — user registers later via invite; same behaviour as prior cycle, resolved at registration.

## Phase C — Owner Keycloak registration

| Checkpoint | Result | Evidence |
|---|---|---|
| 0.18 open invite link | PASS | /accept-invite?kcUrl=… → KC registrations page |
| 0.19 org pre-bound | PASS | Heading "Create an account to join the Mathebula & Partners organization", email pre-filled |
| 0.20 fill + register | PASS | Thandi/Mathebula/SecureP@ss1 (form submitted via requestSubmit — harness quirk) |
| 0.21 lands on org dashboard | PASS | /accept-invite/complete → final URL /org/mathebula-partners/dashboard |
| 0.22 sidebar org + user | PASS | "Mathebula & Partners" + "Thandi Mathebula / thandi@mathebula-test.local" |
| 0.23 legal terminology | PASS | Nav: Matters, Clients, Fee Notes — no Projects/Customers/Invoices labels |
| 0.24 legal modules | PASS | Matters, Trust Accounting, Court Calendar, Conflict Check all in nav (plus Tariffs, Client Ledgers, Reconciliation, Engagement Letters, Mandates, AI Intake/Reviews) |
| 0.25 screenshot | PASS | `qa_cycle/checkpoint-results/day-00-firm-dashboard-legal.png` |

## Phase D — Team invites

| Checkpoint | Result | Evidence |
|---|---|---|
| 0.26 Team top-level nav | PASS | /org/mathebula-partners/team |
| 0.27 Thandi = Owner, no upgrade gate | PASS | Members table shows Owner badge; invite form has no tier/upgrade UI |
| 0.28 invite Bob Admin | PASS | Toast "Invitation sent to bob@mathebula-test.local."; Pending Invitations: bob Admin |
| 0.29 invite Carol Member | PASS | Toast "Invitation sent to carol@mathebula-test.local."; Pending Invitations: carol Member |
| 0.30 two invite emails | PASS | Mailpit `RLBcxRHkHfYftakYiwb9Av` (bob), `eGDGA7cM7j8xi8aYs7zwHF` (carol) |
| 0.31 Bob registers | PASS | Fresh context (cookies cleared) → registration → /org/mathebula-partners/dashboard |
| 0.32 Carol registers | PASS | Fresh context (cookies cleared) → registration → /org/mathebula-partners/dashboard |

## Day 0 exit checkpoints

- Org created via real access-request → approval → KC registration (no mock IDP): PASS
- Three KC users under realm docteams: PASS (Admin REST exact-email lookup: thandi/bob/carol @mathebula-test.local, all enabled, correct names)
- JIT member sync: PASS (read-only DB check: tenant members table has Thandi=Owner, Bob=Admin, Carol=Member)
- Vertical profile legal-za + terminology/nav: PASS
- No tier/upgrade/billing upsell: PASS (team page + invite flow clean)

## Observations (non-blocking)

- **QA-harness quirk (known, not a product bug; worse than prior cycle)**: trusted Playwright pointer/keyboard events do not reach the page in this session — not only after OAuth redirect chains (as documented 2026-07-06) but also after plain `page.goto`. Prior cycle's re-goto workaround was NOT sufficient this run. Synthetic events work everywhere (element.click(), form.requestSubmit(), dispatched PointerEvents for radix tabs, native-setter + input/change events for controlled inputs). All checkpoint interactions verified via product-visible outcomes (toasts, provisioning logs, emails, DB state), so results are unaffected. No product code implicated — same pages respond normally to real user input.
- KC Admin REST `users?search=` does not match on `@domain` substring; `email=…&exact=true` used instead (evidence-gathering detail only).
