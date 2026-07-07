# Day 0 — Firm org onboarding (Keycloak flow) — 2026-07-06

## Session 0 — Prep & Reset

| Step | Result | Evidence |
|---|---|---|
| 0.A svc status | PASS | `svc.sh status`: backend 8080, gateway 8443, frontend 3000, portal 3002 all RUNNING+HEALTHY |
| 0.B portal :3002 | PASS | `curl` → 307 (redirect to login — service live) |
| 0.C no stale tenant schema | PASS (after reset) | Stale prior-cycle Mathebula org found (org row `5a219de1…`, schema `tenant_5039f2d497cf`, KC org `0519838f…`). Reset per Session 0.C/0.D/0.E: dropped schema, deleted org/org_schema_mapping/subscription/access_request rows, deleted KC org + 3 stale KC users via Admin REST. Backend restarted (ready 18s, 0 ERROR, pack reconciliation 3/3) |
| 0.D no stale KC users | PASS (after reset) | thandi/bob/carol@mathebula-test.local deleted (HTTP 204 ×3) |
| 0.E no stale portal contacts | PASS (after reset) | contacts lived in dropped schema `tenant_5039f2d497cf` |
| 0.F Mailpit empty | PASS | API `total:0` at start |
| 0.G PayFast config | DOCUMENTED | Sandbox creds in `backend/src/main/resources/application-local.yml` (merchant-id 10000100, sandbox defaults) |
| 0.H legal-za pack installed | PASS | backend log: legal-za packs "already installed", reconciliation 3/3 tenants OK |

## Phase A — Access request & OTP

| Checkpoint | Result | Evidence |
|---|---|---|
| 0.1 landing loads | PASS | http://localhost:3000 → "Kazi — Practice management, built for Africa", 0 console errors |
| 0.2 Get Started → /request-access | PASS | URL /request-access |
| 0.3 form fields visible | PASS | Work Email, Full Name, Organisation Name, Country, Industry |
| 0.4 fill + submit | PASS | thandi@mathebula-test.local / Thandi Mathebula / Mathebula & Partners / South Africa / Legal Services |
| 0.5 OTP step | PASS | "Check Your Email" step 2 with code input, 10-min expiry timer |
| 0.6 OTP email | PASS | Mailpit msg `fN6JrGiREoA2dn4Fw2Svos`, subject "Your Kazi verification code" |
| 0.7 enter OTP → Verify | PASS | OTP 308000 accepted |
| 0.8 success card | PASS | "Request Submitted — Your access request has been submitted for review" |

## Phase B — Platform admin approval

| Checkpoint | Result | Evidence |
|---|---|---|
| 0.9 /dashboard → KC redirect | PASS | Redirected to localhost:8180 realm docteams (gateway-bff client) |
| 0.10 padmin login | PASS | Two-step KC theme (email → password); landed on /platform-admin/access-requests |
| 0.11 access-requests page | PASS | URL confirmed |
| 0.12 request in Pending | PASS | Row: Mathebula & Partners, Legal Services, South Africa |
| 0.13 inline fields | PASS | Org Name, Email, Name, Country, Industry, Submitted ("1 minute ago"), Status PENDING all in row |
| 0.14 Approve → AlertDialog → Confirm | PASS | Dialog: "This will create a Keycloak organization, provision a tenant schema, and send an invitation…" → confirmed |
| 0.15 Approved, no error | PASS | Approved tab shows row APPROVED; backend log "Successfully provisioned tenant tenant_5039f2d497cf for org mathebula-partners" |
| 0.16 profile = legal-za | PASS | DB (read-only): `tenant_5039f2d497cf.org_settings.vertical_profile = legal-za` |
| 0.17 invitation email | PASS | Mailpit `kPnhhjRvHZAiUiheCHvYZZ` "Invitation to join the Mathebula & Partners organization" |

## Phase C — Owner Keycloak registration

| Checkpoint | Result | Evidence |
|---|---|---|
| 0.18 open invite link | PASS | /accept-invite?kcUrl=… → KC registrations page |
| 0.19 org pre-bound | PASS | Heading "Create an account to join the Mathebula & Partners organization", email pre-filled |
| 0.20 fill + register | PASS | Thandi/Mathebula/SecureP@ss1 |
| 0.21 lands on org dashboard | PASS | Final URL /org/mathebula-partners/dashboard |
| 0.22 sidebar org + user | PASS | "Mathebula & Partners" + "Thandi Mathebula / thandi@mathebula-test.local" |
| 0.23 legal terminology | PASS | Nav: Matters, Clients, Fee Notes — no Projects/Customers/Invoices labels |
| 0.24 legal modules | PASS | Matters, Trust Accounting, Court Calendar, Conflict Check all in nav (plus Tariffs, Client Ledgers, Reconciliation etc.) |
| 0.25 screenshot | PASS | `qa_cycle/checkpoint-results/day-00-firm-dashboard-legal.png` |

## Phase D — Team invites

| Checkpoint | Result | Evidence |
|---|---|---|
| 0.26 Team top-level nav | PASS | /org/mathebula-partners/team |
| 0.27 Thandi = Owner, no upgrade gate | PASS | Members table shows Owner badge; invite form has no tier/upgrade UI |
| 0.28 invite Bob Admin | PASS | Pending Invitations: bob@mathebula-test.local Admin |
| 0.29 invite Carol Member | PASS | Pending Invitations: carol@mathebula-test.local Member |
| 0.30 two invite emails | PASS | Mailpit `jNmyzE3X8n6qq7ttQpnzVh` (bob), `gXhmCYS3GxetpjFkobfsRu` (carol) |
| 0.31 Bob registers | PASS | Fresh context → registration → /org/mathebula-partners/dashboard |
| 0.32 Carol registers | PASS | Fresh context → registration → /org/mathebula-partners/dashboard |

## Day 0 exit checkpoints

- Org created via real access-request → approval → KC registration (no mock IDP): PASS
- Three KC users under realm docteams: PASS (Admin REST: thandi/bob/carol @mathebula-test.local)
- Vertical profile legal-za + terminology/nav: PASS
- No tier/upgrade/billing upsell: PASS (team page + invite flow clean)

## Observations (non-blocking)

- **QA-environment quirk (not a product bug)**: after an OAuth redirect chain (3000 → 8443 → 8180 → 3000), the Playwright/Chromium session stops delivering trusted input events to the app page (zero pointer/mouse/click events reach the document; synthetic `.click()` works). Fix: issue an explicit `page.goto(<app URL>)` after every auth redirect before interacting. Verified by capture-phase listener instrumentation; interactivity is fully normal after re-navigation, and the approve dialog/tabs work with real clicks. No product code implicated.
