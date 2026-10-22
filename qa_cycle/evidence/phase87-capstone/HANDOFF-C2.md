# HANDOFF-C2 → Part C screenshot agent (617C capstone, legal-za 90-day, Keycloak)

**Walk status:** Session 0 → Day 90 + exit checkpoints are done (last step 90.7 / E.11 e). Evidence: `index.md` (totals), `days-61-75.md`, `days-76-90.md`, `exit-checkpoints.md`, `gaps.md` (G-D1…G-D14 added). Do not re-walk steps; this note is for the deck screenshots.

## Stack state (left RUNNING, 16:54Z)

- Services from the **main checkout** (HEAD `dac048f12` = worktree HEAD), same PIDs all walk: backend 60278 · gateway 60449 · frontend 60581 · portal 60640 — all RUNNING/HEALTHY (`bash /Users/rakheendama/Projects/2026/b2b-strawman/compose/scripts/svc.sh status`). Port holders 60426 / 60574 / 60624 / 60683 (never restarted).
- Docker dev infra up (Postgres `b2b-postgres` db `docteams`, Keycloak :8180, Mailpit :8025, LocalStack). e2e stack DOWN.
- `/tmp/.e2e-keycloak-mode` still exists (needed for :3000 navigation; remove after Part C). caffeinate PID 41495 alive at 16:23Z.
- Main checkout dirty only with the dev artefact `portal/next-env.d.ts` (do not commit; restore after services stop).
- Browser (Playwright MCP): logged in as **Thandi** on `/org/mathebula-partners/team`.
- Tenant: org `mathebula-partners`, schema `tenant_5039f2d497cf`, KC org `2c4034d3-f76d-4323-a172-7eabd018657d`.

## Logins

| Actor | Where | Credentials | Notes |
|---|---|---|---|
| Thandi Mathebula (Owner) | :3000 → Keycloak two-step form (`#username` → submit → `#password` → submit) | `thandi@mathebula-test.local` / `SecureP@ss1` | best for most shots (sees everything) |
| Bob Ndlovu (Admin) | same | `bob@mathebula-test.local` / `SecureP@ss2` | lead of the RAF matter; My work has 4 tasks |
| Carol Mokoena (Member) | same | `carol@mathebula-test.local` / `SecureP@ss3` | sees only the collections matter (she is its lead); every other matter URL → "Something went wrong" (G-B3) |
| padmin | same | `padmin@docteams.local` / `password` | platform admin |
| Portal: Sipho Dlamini | :3002 — **use `http://localhost:3002/login?orgId=mathebula-partners`** (bare `/login` refuses, G-D1). Email `sipho.dlamini@email.co.za` → Send Magic Link → Mailpit `http://localhost:8025/api/v1/messages?limit=5` → open the `/auth/exchange?token=…&orgId=mathebula-partners` link in the browser | magic link | 1 token used at 16:29:58Z; the cap is 3 per contact per 5 min, so the full budget is available now. No portal session is open (cookies/localStorage were cleared). The dev login page also prints the exchange link ("Dev mode — click to sign in") |
| Other portal contacts | same flow | moroka.trust@email.co.za · khumalo.holdings@email.co.za · lerato.mthembu@email.co.za · nomvula.khoza@email.co.za | none logged in yet |

Clear cookies before each actor switch (`async (page) => { await page.context().clearCookies(); }`), and also `localStorage.clear()` on :3002.

## Richest records for the deck

| Shot | Target | URL / ID | Why |
|---|---|---|---|
| **Matters table** | `/org/mathebula-partners/matters` | default tab "Open matters" shows 3 ACTIVE matters (RAF with "Court date: 06 Dec 2026", Khumalo conveyancing, Nomvula collections COL-2026-001); the two CLOSED matters (Sipho, Moroka estate) appear under the status filter "Closed" / "All" | 5 matters total. Note G-D11: "Fee earner" and "Unbilled" cells show "—" |
| **Clients table** | `/org/mathebula-partners/clients` | 5 clients: Khumalo Property Holdings, Lerato Mthembu, Moroka Family Trust (trust balance R 38 000,00), Nomvula Khoza, Sipho Dlamini | Note G-D11: FICA "Not started" and Outstanding "—" are wrong |
| **Matter detail — Fees group** | **Estate Late Peter Moroka** `bb2ce7c7-17d1-4121-8ac1-ee907863a7be` (client Moroka Family Trust `24b98ee9-f65e-47bb-ba8e-55acae27ec34`) | `/org/mathebula-partners/matters/bb2ce7c7-17d1-4121-8ac1-ee907863a7be` then Fees group (Time recording · Disbursements · Fee estimate · Tariffs · Financials · Statements of account · Trust); direct `?tab=budget` / `?tab=time` / `?tab=financials` | Richest: 18 h by Thandi+Bob, fee estimate 40 h / R 80 000 (45% consumed), INV-0002 SENT R 42 182 incl. LSSA tariff lines, trust R 50 000 in / R 12 000 out (balance R 38 000), closure SoA. Status badge now **Closed** (closed under owner override). For an ACTIVE-looking alternative use the RAF matter `a31bde33-836a-4555-a2ec-a8d3e380ae0d` (7 h Bob, draft fee note R 9 660, CMC court date 6 Dec, adverse party RAF) |
| Matter detail (other) | Sipho Dlamini v. Standard Bank (civil) `458bc331-d51e-4a94-99d8-32ecad3de00e` | CLOSED, closure history card, closure letter + 2 SoAs in Documents | closure story |
| Collections matter | `a4c1324e-1f43-4e50-b280-04f09b58bd35` | ACTIVE, 4 entries across 3 members, INV-0003 SENT R 7 058,13 | "team" story |
| Profitability | `/org/mathebula-partners/reports/profitability` | 4 matters, 4 clients, 3 members | — |
| Diary | `/org/mathebula-partners/diary` → "Go to date" 2026-12-06 (Enter) | CMC Gauteng High Court 6 Dec 10:00 | — |
| Trust | `/org/mathebula-partners/trust-accounting/client-ledgers` | Moroka R 38 000,00 | — |
| Fee notes | `/org/mathebula-partners/invoices` | INV-0001 PAID, INV-0002 SENT, INV-0003 SENT, INV-0004 PAID, RAF DRAFT | — |
| Audit trail | `/org/mathebula-partners/settings/firm?tab=audit-trail` | 248 events at 16:55Z; filter Event type `matter.closure.override_used` (CRITICAL, justification) | filters commit on blur (Tab), not Enter |

Other IDs: Lerato `32d5764d-0965-4cf6-9daf-7e51c190ee71`; Khumalo client `e1229036-88c4-49ae-9ade-9d773d0f72fc`, matter `0d9bf92c-d6b0-4993-bcf6-5dcbf2ffe547`; Nomvula `f12280ca-9882-4446-abb0-13086c42138d`; trust payment `41453484-aa0b-4a44-96b5-d69c13de2cc0`; INV-0003 `878bedc7-9812-4826-ab9b-5c23e666a878`; INV-0004 `064290fd-d3ce-461a-9a9b-0ccfaf8da1b0`.

## Mailpit (dev :8025): total 19. Do NOT delete.

New in C2: `VGqw24J5xahLSAeGwwWWoY` (INV-0003 → nomvula), `DrtLDuWa2eMixynRnBhGyW` (portal link → sipho), `ZhHCcRbbXMXr3CGjcr8ttW` (trust activity → moroka), `2RF5u2hh6pKWaoAc5yRe5L` (INV-0004 → sipho), `3atFgK9dyn87CLK4ADps5x` + `3hoK9FjjckhjrbWFZc5X3a` (closure letter + SoA → sipho), `kSbsCaKQuQm4doVfXAB6jp` + `2fqmU4AZBtQQrJM9HqieYc` (closure letter + SoA → moroka). Earlier 11 IDs: see HANDOFF-C1.md.

## Gotchas (HANDOFF-B/C1 gotchas still apply)

1. **Matter tabs:** the "Activity" group tab opens a menu (Activity / Audit trail); click the menu item. A `?tab=` in the URL pins the panel (G-B14) — navigate to `?tab=<target>` directly.
2. **Close Matter** is in the matter header (`close-matter-btn`), only on ACTIVE matters (G-D2). Do not close/complete anything else unless a shot needs it — it emails the client (G-D14).
3. Screenshots: pass an absolute path under `/Users/rakheendama/Projects/2026/b2b-strawman/.playwright-mcp/`, then `cp` into the deck dir; wait for content (Home/Profitability need ~2–3 s).
4. Home breadcrumb still says "Dashboard" (G-A2); sidebar org name in brand colour is low-contrast (G-A3) — visible in any full-window shot.
5. Portal `/accept/<token>` cannot show the PDF (G-B7); do not use it as a deck shot.
6. Carol's matter visit generates `security.access_denied` audit rows (G-D13) — harmless.
