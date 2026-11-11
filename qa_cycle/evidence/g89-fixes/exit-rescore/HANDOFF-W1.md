# HANDOFF from W1 to W2 (written 2026-11-11 ~13:50 SAST)

## Stack

- **Build:** main `819034db5`.
- **Services:**

  | Service | Port | Holder PID | Started | Note |
  |---|---|---|---|---|
  | backend | :8080 | **60902** | 13:09:41 | restarted once, for the D-1 reset |
  | gateway | :8443 | 56786 | — | |
  | frontend | :3000 | 56847 | — | `next dev` |
  | portal | :3002 | 56896 | — | `next dev` |

- **Health:** all UP.
- **Backend ERROR lines:** 0.
- **caffeinate:** W1's (58540) was killed at the end of W1. Start your own.

## Tenant

| Item | Value |
|---|---|
| Schema | **`tenant_5039f2d497cf`** (fresh, Flyway V14; public V5) |
| Org | "Mathebula & Partners", slug `mathebula-partners` |
| KC org | `fe76c0d4-2174-4001-ae58-af2175b0fb1d` |
| `public.organizations` | `36d051c2-6c71-45c7-8e90-2c0b8759491c` |
| Tenant creation date | **11 Nov 2026 (SA)**. Every W1 entity is dated 2026-11-11. |

## Users

All registered through the KC invite links. The login is two-step (`#username` → `#password`).

| User | Email | Password | Role |
|---|---|---|---|
| Thandi Mathebula | thandi@mathebula-test.local | SecureP@ss1 | Owner |
| Bob Ndlovu | bob@mathebula-test.local | SecureP@ss2 | Admin |
| Carol Mokoena | carol@mathebula-test.local | SecureP@ss3 | Member |
| platform admin | padmin@docteams.local | password | — |

**Signed in at the end of W1:** the Playwright MCP browser has three tabs, all on 1440×900:
- default context = **Thandi**;
- isolated context `bob` = **Bob**;
- isolated context `carol` = **Carol**.

The `sipho` isolated context was closed. No portal contact is signed in. If the MCP browser was restarted, these sessions are gone.

**Tooling:** if clicks stop landing (0 click events), open a **new tab** rather than reloading. This happened once, on `/platform-admin`.

## Clients

Portal contacts were auto-provisioned (0.C, from `portal_contacts`).

| Client | id | Email / portal contact | Type | State |
|---|---|---|---|---|
| Sipho Dlamini | `2ad8854e-aca9-485b-b34d-600aea617959` | sipho.dlamini@email.co.za / `19f8c872-fc03-48af-a7c3-6c3f40cd86c6` | Individual | ACTIVE |
| Moroka Family Trust | `713a1f9a-9cd4-406e-920e-507ac449c98a` | moroka.trust@email.co.za / `f6350605-4fed-4488-8109-8849f3c0125b` | Trust (tax 9123456789) | ACTIVE |
| Khumalo Property Holdings | `3c88f00e-789e-4ed2-aa2f-f2e8036f0e87` | khumalo.holdings@email.co.za / `0fda931b-9b76-46ca-82b8-569ffd2f80fa` | Individual | ACTIVE |
| **Lerato Mthembu** | `a73eef73-348b-42b2-a733-e3ed2bd0fb7e` | **lerato.mthembu@email.co.za** / `6bf3231e-bc01-4f2c-91c3-37c116e052db` | Individual (ID 9203145800081) | ACTIVE |

Nomvula is not created; she is S2-04 / W2's.

## Matters

| Matter | id | Lead · team | State / notes |
|---|---|---|---|
| Sipho Dlamini v. Standard Bank (civil) | `c46f07d7-8525-4acb-800b-f52f3e97b1c8` | Bob · Bob, Carol | ACTIVE, LITIGATION.<br>Case JHB/CIV/2026/001, Gauteng High Court, Johannesburg.<br>9 tasks, all Open: 7 Bob, "Letter of demand" (`286e6935-…`) Carol, 2 unassigned. 74.1 must move all 9.<br>Time: Bob 1 h 30 m + Carol 2 h (both billed on INV-0001).<br>MC-1 matter comment "@Bob please review the particulars" (SHARED).<br>Doc "Particulars of Claim — draft v1.pdf".<br>Engagement letter **Accepted** by Sipho 13:22.<br>SoA 11 Nov (doc `85372981-9edc-45bf-8ec5-31ff5b6e0b64`, **PORTAL**, 5 681 B). |
| Estate Late Peter Moroka | `9181f514-1742-43c5-b446-99e1d41f220e` | Thandi · Thandi, Bob, Carol | ACTIVE, ESTATES.<br>"Obtain death certificate & will" **Done**; 8 Open.<br>Time Thandi 3 h / Bob 1 h 30 m / Carol 1 h, all billed on INV-0002.<br>No fee estimate set (15.x skipped). |
| Khumalo Property Holdings — Erf 1234 Sandton Transfer | `210401f1-06e0-4923-9f40-a3687f921d28` | Thandi · Thandi only | ACTIVE, CONVEYANCING.<br>**52 seeded matter comments** "634E seed comment #01…#52 of 52" (SHARED). Khumalo's portal will show them.<br>No conveyancing fields and no OTP. |
| Lerato Mthembu — RAF Claim (MVA 2025-11-03) | `565e5a47-302f-4e26-b86b-413161ea06d9` | Bob · Bob, Carol | ACTIVE, LITIGATION, case RAF/2026/0042.<br>Diary: **CASE_MANAGEMENT 2026-12-26** (today + 45), Gauteng High Court.<br>Time **unbilled**: Bob 4 h ("Medical records collation") + 3 h ("Expert witness statements"); Carol 2 h ("Legal research — RAF tariff"). Total 9 h, for 50.1.<br>No adverse party (23.1 skipped). **No Hearing and no prescription tracker yet: the D28 setup is W2's.** |

## Trust

- **Account:** "Mathebula & Partners Trust Account".

  | Field | Value |
  |---|---|
  | id | `b1cd0df0-d9d9-4a32-b8ae-67727297e617` |
  | Bank | Standard Bank 051001 / 0123456789 |
  | Type | GENERAL, primary |
  | Approval | single approval |
  | Opened | **2026-01-01** |

- **LPFF:** one rate, **6.50 % effective 11 Nov 2026**, share 0 %.
- **Interest-run setup still needed (W2):**
  - INT-60 needs an **ended** period (F11 guard), so add a **back-dated** LPFF rate (Batch A used 2026-10-01).
  - Also add back-dated deposits through Record Deposit › Transaction Date.
  - All W1 deposits are dated 2026-11-11, so an October run would have no balances, and a run for 1–10 Nov would start before the current rate.
- **Transactions:**

  | Ref | Type | Amount | Matter | Status | id |
  |---|---|---|---|---|---|
  | DEP/SIPHO-001 | Deposit | R 5 000 | Sipho | RECORDED | `1ee3cf50-1c0c-4a81-b585-b1f57d5012a6` |
  | DEEDS/SIPHO-001 | Payment | R 1 250 | Sipho | APPROVED | `e642d7d5-9814-47ab-a9f7-045506a5d0cf` (linked to the deeds disbursement) |
  | Initial fund for estate admin | Deposit | R 50 000 | estate | RECORDED | `2ee79e49-ce17-471c-8496-d7ac2a1bd81a` |

- **Ledgers:**

  | Ledger | Balance |
  |---|---|
  | Sipho | R 3 750,00 |
  | Moroka | R 50 000,00 |
  | Cashbook | R 53 750,00 |

- **No interest run** has been made.

## Fee notes

| Fee note | id | Client | State | Total | Contents | Mailpit |
|---|---|---|---|---|---|---|
| **INV-0001** | `69cc0501-4045-4340-a3df-51346167ca10` | Sipho | **SENT**, due 11 Dec 2026 | R 5 335,00 | Bob 1.5 h + Carol 2 h + sheriff R 750 + deeds R 1 250 | `D2GYv4mM…` |
| **INV-0002** | `2c00fc78-9966-4080-b71f-b460b138fabf` | Moroka | **SENT**, due 11 Dec 2026 | R 12 224,50 | 3 time entries + tariff 3(a) × 4 R 780, last | `KfHNFs3H…` |

INV-0002 has 4 generated "Fee Note" rows: the send attachment, 2 downloads and 1 save. One copy is saved to the Moroka documents.

## Disbursements (all on the Sipho matter)

| Disbursement | id | Amount | State |
|---|---|---|---|
| Sheriff | `dcd7d88f-b7d5-40eb-ade9-9909a5a84f39` | R 750 | Approved · **Billed** |
| Deeds (trust-linked) | `79dbd387-435f-47bc-9ed0-cab6e78d9889` | R 1 250 | Approved · **Billed** |
| **D-3(h) court filing fee** | `585014f7-2df3-418b-9a46-ee302a5c6183` | R 500, Office | Approved · **Written off** |

The D-3(h) write-off reason is "Client dispute — unable to recover from third party, firm absorbing the cost". This is the F16 input for the Day-75 closure SoA.

## Requests and documents

- **REQ-0001:** Sipho, client-level, item "Certified copy of ID document" (File upload, Required), due **2026-11-18**, **Sent** (Mailpit `MCwaSY8k…`).
- **Firm letterhead (NOTE D-W1-b):** banking details are set: First National Bank / 62123456789 / 250655 / Cheque + a payment note.
  - The VAT number, address and contact lines are still blank.
  - From now on, every fee note and receipt PDF prints a Banking block.

## Mailpit and portal tokens

- **Mailpit:** 12 messages at W1 end (`logs/mailpit-w1.txt`).
- **Portal tokens minted for sipho.dlamini@:**
  - the acceptance link at 11:22:26Z (used 11:22);
  - the REQ-0001 email at 11:28:20Z;
  - the INV-0001 email at 11:41:40Z.
- **Rate limit:** mind the 3 tokens / 5 min / contact cap and the double-exchange 401.
- **Lerato** has no portal email yet.
- **Moroka** has: trust activity 11:31Z and INV-0002 11:42Z.

## Half-done or skipped (W2 may need these)

- **D28 (W2):** on the RAF matter, add a Hearing within 7 days and a Delict prescription at about +20 days.
- **Interest (W2):** add a back-dated LPFF rate and deposits, then a past-period interest run, per Batch A. This also covers F44 (the interest-credit portal event).
- **Not walked by W1 (W2 may need them as prerequisites):**
  - 12.3 balance check (observed via SELECT only)
  - 15.x fee estimate
  - 23.1 adverse party (needed only for F38-style checks)
  - 35.1 RAF tasks
  - 33.x profitability
  - 38.2 audit labels
  - 45.9–45.10
- **Sipho tasks:** none moved; the 74.1 completion is W2's.
- **S2-03 (INT-60):** not reached.

## Candidates (from W1)

- **W1-c1, LOW, scenario mismatch:** 47.2 expects banking details, but no Day 0 step sets them (`letterhead-settings-form.tsx:72`). Needs a founder ruling. Details in `steps/W1.md`.
- **No product defects found** in the W1 rows.
