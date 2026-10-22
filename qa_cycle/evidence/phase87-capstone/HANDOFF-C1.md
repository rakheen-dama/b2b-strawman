# HANDOFF-C1 → Walker C2 (617C capstone, legal-za 90-day, Keycloak)

**Last completed step: 58.1** (doc line 474, "Verify audit log shows payment event"). Walker C1's range (doc lines 366–474) is done end to end. Walker C2 starts at **Days 61–75, step 61.1 (doc line 477, section header line 475)** and runs to the end of the doc (Day 75 closure, Day 80, Day 85 audit + override, Day 88, Day 90 sweeps, exit checkpoints E.1–E.11).

Walker C1 totals: Days 22–35 = 23 rows → 19 PASS · 3 FAIL · 1 DEFERRED; Days 36–60 = 35 rows → 21 PASS · 9 FAIL · 5 DEFERRED. Gaps G-C1…G-C12 appended to `gaps.md`. C1 used DEFERRED (with the blocking gap id) where a step could not be attempted, and no BLOCKED rows.

## Stack state (left RUNNING, 16:22Z)

Services run from the **main checkout** (HEAD `dac048f12`) with the same PIDs all walk long: backend 60278 · gateway 60449 · frontend 60581 · portal 60640, all healthy (`bash /Users/rakheendama/Projects/2026/b2b-strawman/compose/scripts/svc.sh status`). Docker dev infra up; e2e stack DOWN. `/tmp/.e2e-keycloak-mode` still exists (leave it until Part C ends). caffeinate PID 41495 alive. The main checkout still has only the dev-artefact edit to `portal/next-env.d.ts` (do not commit; restore after services stop).

Browser at handoff: logged in as **Thandi** on `/settings/firm?tab=audit-trail`. Clear cookies before switching actors.

## Still holds from HANDOFF-A / HANDOFF-B

- Org `mathebula-partners` (`http://localhost:3000/org/mathebula-partners/`), KC org `2c4034d3-f76d-4323-a172-7eabd018657d`, tenant schema `tenant_5039f2d497cf`.
- Users: Thandi (Owner, `SecureP@ss1`), Bob (Admin, `SecureP@ss2`), Carol (Member, `SecureP@ss3`), padmin/`password`. All log in with the two-step KC form. Never run `keycloak-bootstrap.sh`.
- Rates: billing 2500/1200/550, cost 1000/500/200. VAT 15%. Org `brand_color` #1B3A4B, logo uploaded. 6 matter templates. Trust account `b53ed14e-1944-4941-bb63-dfa18d8d138a` (LPFF 6.5%).

| Kind | Name | ID | State at handoff |
|---|---|---|---|
| Client | Sipho Dlamini (INDIVIDUAL) | `ce83c576-9bd9-4c7a-80b9-39100ade80d7` | ACTIVE; no tax number |
| Matter | Sipho Dlamini v. Standard Bank (civil) | `458bc331-d51e-4a94-99d8-32ecad3de00e` | ACTIVE; **0 members**; all time billed (INV-0001 PAID); sheriff disbursement still UNBILLED; SoA generated |
| Client | Moroka Family Trust (TRUST) | `24b98ee9-f65e-47bb-ba8e-55acae27ec34` | ACTIVE; trust balance R 50 000,00 |
| Matter | Estate Late Peter Moroka (ESTATES) | `bb2ce7c7-17d1-4121-8ac1-ee907863a7be` | ACTIVE; lead/only member Thandi; 18 h all billed on INV-0002 |
| Trust deposit | Moroka / estate matter | `aff6ea56-411a-4226-a3e2-8f5b9178387b` | DEPOSIT R 50 000,00 RECORDED (no withdrawals yet; the R12,000 withdrawal is in C2's range) |
| Client | Khumalo Property Holdings | `e1229036-88c4-49ae-9ade-9d773d0f72fc` | ACTIVE |
| Matter | Khumalo … Erf 1234 Sandton Transfer (CONVEYANCING) | `0d9bf92c-d6b0-4993-bcf6-5dcbf2ffe547` | ACTIVE; lead Thandi; OTP acceptance `ce2f21b7-…` VIEWED, not accepted |

## Created by Walker C1

| Kind | Name | ID | State |
|---|---|---|---|
| Client | **Lerato Mthembu** (INDIVIDUAL) | `32d5764d-0965-4cf6-9daf-7e51c190ee71` | ACTIVE; email `lerato.mthembu@email.co.za` (walker test value); ID 9203150123087; 3 Church Square, Pretoria, ZA; FICA checklist `3b1ab7d4-…` COMPLETED 9/9; no tax number |
| Matter | **Lerato Mthembu — RAF Claim (MVA 2025-11-03)** (LITIGATION) | `a31bde33-836a-4555-a2ec-a8d3e380ae0d` | ACTIVE; lead/only member **Bob**; case_number RAF/2026/0042, court_name Gauteng High Court; 4/9 tasks DONE; 7 h Bob (all on the RAF draft fee note); 2 medical-report PDFs; comment "Ready for summons @Bob" `f89e2210-…` |
| Adverse party | Road Accident Fund (OTHER) | `f4d005ab-7605-4771-aec2-fc8453c976eb` | Linked to the RAF matter as Opposing Party (`787625d9-…`) |
| Court date | CMC, Gauteng High Court | `3ed25641-0d09-4fa7-823d-2ae2975651fc` | CASE_MANAGEMENT **2026-12-06 10:00**, SCHEDULED (Diary week 30 Nov–6 Dec) |
| Statement of Account | Sipho matter | generated_document `19a6ac83-7399-4adf-998f-e3b05f827f05` | Saved to documents; closing balance owing R 2 820,00 |
| Fee note **INV-0001** | Sipho | `00939152-e774-464f-874f-c18c3a92fa1a` | **PAID** (R 2 070,00; reference "EFT — Sipho Dlamini — INV-0001") |
| Fee note **INV-0002** | Moroka estate | `3f8888c9-4b2a-4891-af9c-eb1a2a13b858` | **SENT**, unpaid, R 42 182,00 (incl. LSSA tariff 3(a) × 4 = R 780) |
| Fee note (draft) | Lerato RAF | `b9d00357-f68b-4dbf-b185-ea2b8b9d698f` | **DRAFT**, R 9 660,00, no number yet |
| Disbursement | Sheriff fee, Sipho | `a5aafdc4-20be-4bc8-a940-e77172002cca` | APPROVED / **UNBILLED** (write-off impossible in the UI, G-C9) |

## Portal

| Contact | Email | ID |
|---|---|---|
| Sipho Dlamini | sipho.dlamini@email.co.za | `ac43d200-cffd-4a30-91b8-f0796db96383` |
| Moroka Family Trust | moroka.trust@email.co.za | `7d49d818-3e9e-4cce-94b4-cc1326734ad6` |
| Khumalo Property Holdings | khumalo.holdings@email.co.za | `ca957639-a8ad-4172-b725-c16181572c70` |
| Lerato Mthembu (new, auto-created) | lerato.mthembu@email.co.za | `7eba0301-da7b-4048-8df1-e2bcfed1a230` |

**No portal login of any kind was done by Walker C1.** `magic_link_tokens` count = **0**, so Sipho's 3-per-5-min magic-link budget is fully available for **68.1**. Sipho now has a PAID fee note (INV-0001), an SoA document and the accepted engagement letter to view in the portal. The SoA email link points at `http://localhost:3002/projects/<matterId>` (legacy path, G-C5).

## Mailpit (dev :8025): total 11. Do NOT delete.

New in C1:
| ID | To | Subject |
|---|---|---|
| `H73QLPFcGTG7iuZdomittW` | sipho.dlamini@ | Document ready: statement-of-account-sipho-dlamini-v-standard-bank-civil-2026-10-22.pdf (sent automatically on SoA save, G-C5) |
| `UW24krGpDaMZqBUcWuyipR` | sipho.dlamini@ | Fee Note INV-0001 from Mathebula & Partners (attachment INV-0001.pdf) |
| `gc8Rz8JiYAbVVt8XSdbisH` | moroka.trust@ | Fee Note INV-0002 from Mathebula & Partners |

Earlier: `NTJgYA4eAEgkt8R3YE4NZP`, `A7HfxBpigZkmFBiGzL42iw` (Sipho engagement letter), `FrRPJuefbwR9sXbWDHWXBF` (Moroka trust activity), `j6q5Ymg3wDEu4SwYyDiC9f` (Khumalo OTP), plus 4 Walker A messages (OTP + 3 KC invitations). Recording a payment sends **no** receipt email.

## Gotchas (HANDOFF-B gotchas 1–12 still apply; additions)

1. **G-B3 still bites:** Carol (Member) sees "0 matters" and gets "Something went wrong" on every matter URL, including the RAF matter (30.1). Any Carol step in C2 (e.g. Day 80 utilisation for Carol) will FAIL for the same reason.
2. **Fee notes:** Fees zone › Fee notes › "New fee note" opens a client picker, then routes to the client's Fees › Fee notes tab; click "New fee note" there again → "Fetch Unbilled Time" → select → "Validate & Create Draft" → "Create Draft". Approve gives APPROVED + number INV-000n; **"Send fee note" is a separate click** (G-C8). There is no matter "Billing" tab.
3. **Generate Document on a fee note:** opening the preview does not persist anything; "Save to Documents" does. Sending a fee note also auto-generates an unsaved PDF row, so each sent note shows two "Fee Note" rows (G-B6).
4. **Record Payment** is an inline form with only "Payment Reference". It always pays the full balance (G-C11).
5. **Statement of Account dialog:** "Preview & Save" saves and **emails the client at once** (G-C5). The preview pane then grows without bound and freezes layout (G-C4). Close it with Escape and use the row "Download" in Fees › Statements of account.
6. **Adverse parties:** "Link to Matter" in the registry crashes the page (G-C2). Link from the matter: Client group › Adverse parties › Link Adverse Party.
7. **Diary:** "Go to date" + Enter jumps the week (`?from=`). List view is per calendar month, so the 6 Dec event shows under December only.
8. **Profitability** lives at `/reports/profitability` (old `/profitability` 308s there). At handoff it shows Moroka 18 h R 35 900, RAF 7 h R 8 400, Sipho 1.5 h R 1 800; Team Utilization Bob 15.5 h, Thandi 11 h.
9. **Audit trail** is `/settings/firm?tab=audit-trail` ("Audit log", 180+ events). It has filters From / To / Actor ID / Event type / Entity type / Entity ID / Severity and an Export button, which C2 needs for Day 85. Event column shows label + key, e.g. "Invoice / invoice.paid".
10. Screenshots: pass an absolute path under `/Users/rakheendama/Projects/2026/b2b-strawman/.playwright-mcp/` (a bare filename lands in the main checkout root). Then `cp` into `screens/`.
11. **Mention picker** does not exist: "@Bob" is plain text, but the assignee still gets a `COMMENT_ADDED` notification.

## Exact last step

58.1: as Thandi, Firm › Audit trail showed "22 Oct 2026, 18:21 · INFO · Invoice / invoice.paid · Thandi Mathebula · invoice 00939152" (screens/58.1-audit-invoice-paid.png). Evidence files: `days-22-35.md`, `days-36-60.md`, `gaps.md` (G-C1…G-C12), `index.md` (C1 rows filled), `logs/walkC1-*`.
