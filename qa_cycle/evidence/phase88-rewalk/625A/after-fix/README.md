# 625A fix re-observation (PR #1679, head 69357616d)

Run: 2026-10-25 00:41:43Z (`start_ts`) to 00:48Z. I am the observer only: I changed no source and wrote no SQL (SELECTs only). All actions went through the browser (Playwright MCP) on :3001 (mock auth) and :3002 (portal).

## Build info
- Worktree `/Users/rakheendama/Projects/2026/worktree-epic-625A` @ `69357616d`. `git status --short` was empty at the end.
- `e2e-rebuild.sh backend` only runs `docker compose up -d --build`. Maven runs inside the Docker builder, and `.dockerignore` excludes `target`, so it did not collide with the host verify.
  - Exit 0 (`rebuild.log`). The new image is `sha256:3360df9dd7fb…`, created 00:42:20Z. The previous image was `07937f3a39ed…`.
  - `/actuator/health` on :8081 is UP.
  - `/app/app.jar` → `AuditEventRepository.class` (00:42) contains 4 `NULLIF` clauses, which is the new client discriminator in 2 queries × (select + count).
  - As before, compose also recreated `e2e-localstack`.
- Portal: I stopped the KC portal and reused the existing worktree `.next` build (it targets :8081, and the fix has no portal changes). I started it with `pnpm run start` on :3002 (`portal-start.log`), then killed it.
- The portal has only two UI tabs ("Your actions" / "Firm actions"). **The ALL stream has no UI surface.** I observed it with an in-page GET `/portal/activity?tab=ALL` that uses the portal's own session JWT in the browser.

## Ids
| Item | Id |
|---|---|
| Estate matter | `9a27cd08-76fc-44b1-8c41-a8dd1aa1af22` |
| Sipho Ndlovu mvmzw78f31c9 (customer) | `1d7cae32-0deb-43fe-af41-a3b9452f3128` |
| Co-client: Khumalo Property Holdings mvmzw78f31c9 (existing client, ACTIVE portal contact `7861b984…`) | `218397cf-bddb-4fbd-9e9d-872bd4589c6c` |
| Link: Alice used matter → Clients tab → "Link Client" (audit `customer.linked` 00:44:05) | — |
| Deposit DEP/625A-FIX/KHU, R 10 000, Khumalo on the estate matter (Alice) | `b57003e0-f353-44d4-9058-ae575e7440fd` |
| Payment PAY/625A-FIX/KHU, R 2 500, Khumalo on the estate matter (recorded by Alice 00:45:21, **approved by Bob** 00:45:38) | `a0e634c2-f086-45d6-b3dd-bebbdbad0945`, audit `0b06d5e4-8252-40b2-b785-54ceb835f0a3` |
| Sipho's earlier approval (previous observer) | txn `341c2ff9…`, audit 00:23:37Z |
| Khumalo magic link (1 of 3 for that email) | Mailpit `hhTFYkht6MbhkJKhTw58LH` (token not recorded) |

I did not request a magic link for Sipho; the earlier portal session was still valid.

## Item → observed
| # | Item | Observed | Verdict | Evidence |
|---|---|---|---|---|
| 1 | Legit case: Sipho, Firm actions | "Trust payment approved — Bob Admin" (00:23:37Z) is still listed. 16 rows. | PASS | `01-sipho-firm-actions-legit-before-cocl.png`, `02-…before.txt` |
| 1b | Legit case: Sipho, ALL (in-page GET) | The same trust_payment.approved row is present. Total 20 (16 firm + 4 MINE). | PASS | `02-…before.txt` |
| 2 | A second client can be linked in the UI | Clients tab shows "2": Sipho + Khumalo | PASS | `03-matter-clients-two-linked.png` |
| 3 | Co-client payment recorded and approved | Status APPROVED, approved_by Bob (`c9ccb6c0…`) | PASS | `04-bob-approved-khumalo-payment.png` |
| 4 | **Leak closed, Firm tab**: Sipho does not see Khumalo's approval | After the approval (00:46Z), Sipho's Firm actions list is unchanged: newest row 00:30:26Z, exactly one trust row (his own, 22 min ago), FIRM total still 16, 0 rows after 00:44Z | **PASS** | `05-sipho-firm-actions-after-khumalo-approval-no-leak.png`, `06-sipho-api-FIRM-ALL-after.json` |
| 4b | **Leak closed, ALL**: same check | ALL total still 20, 0 rows after 00:44Z, one trust row (Sipho's) | **PASS** | `06-…json` |
| 5 | Co-client sees their own approval | Khumalo Firm actions: "Trust payment approved — Bob Admin, 1 minute ago" (00:45:38Z) | PASS | `07-khumalo-firm-actions-own-approval.png`, `08-…json` |
| 5b | Co-client does NOT see Sipho's keyed rows | Sipho's trust_payment.approved (00:23:37Z) and Sipho's keyed invoice.sent INV-0003 (00:28:39Z) are absent from Khumalo's FIRM and ALL (both total 6) | PASS | `08-…json`, `khumalo-visible-rows.txt` |

## SQL (read-only): new audit rows since start_ts (`audit-rows.txt`)
| occurred_at | event_type | actor | project_id | customer_id |
|---|---|---|---|---|
| 00:44:05 | customer.linked | Alice `2e38845d` | estate | (none) |
| 00:44:53 | trust_deposit.recorded | Alice | estate | Khumalo `218397cf…` |
| 00:45:21 | trust_payment.recorded | Alice | estate | Khumalo |
| 00:45:38 | trust_payment.approved | Bob `c9ccb6c0` | estate | Khumalo |

## Residual (not in the fix's contract; for routing)
Rows that have no `details.customer_id` keep matter-only scoping, as the fix states. On a shared matter this means the co-client still sees them. Khumalo's Firm tab shows four such rows that belong to Sipho's work on the estate matter (`khumalo-visible-rows.txt`):
- `document.generated` at 00:24:25Z and 00:25:50Z. These are Sipho's two engagement letters from today ("Engagement Letter — General", "Standard Engagement Letter"). The 23:36:40Z row on 10-24 is the same kind. They render to Khumalo as "**Document generated for you**".
  - These rows come from a **current** emitter that writes no customer_id. They are not legacy rows.
  - The generated_documents rows are PROJECT-scoped (`primary_entity_type`), so the matter-scoped visibility is arguably intended. The "for you" label is misleading on a shared matter.
- `invoice.sent` at 23:39:17Z on 10-24. This is a pre-fix legacy row with no customer_id and no backfill. It renders to Khumalo as "**Fee note sent to you**".

Only the label, actor and timestamp are shown, never the document or the amount. This matches the stated design, "legacy/unkeyed → matter-only", but it is still client-to-client metadata exposure on shared matters. It needs a product/orchestrator decision on the document.generated family and on a backfill. I am not marking it as a FAIL of this fix.

## Cleanup
- The worktree portal (next-server PID 84991) was killed and :3002 was freed. I ran `svc.sh start portal`. `svc.sh status` shows `portal 88273 yes yes 3002`, cwd `/Users/rakheendama/Projects/2026/b2b-strawman/portal`.
- `git -C worktree-epic-625A status --short` is empty.
- The e2e backend stays on the rebuilt image `3360df9dd7fb…`. Khumalo stays linked to the estate matter. The deposit and payment are real e2e data.
- Originals are also in `b2b-strawman/.playwright-mcp/625A-obsfix/`.
