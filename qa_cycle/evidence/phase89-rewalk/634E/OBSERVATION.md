# 634E — Matter comments panel (§14.12 #6 / B7) — Observation

Stack: E2E mock-auth on :3001 (backend :8081). Browser driven with Playwright MCP (Chromium).
Matter: **Website Redesign** (`94ba0c13-5528-4a8d-97b6-ec051ae4b298`, client Acme Corp).
Users: Alice (owner) posts, Bob (admin) is the @mentioned recipient, Carol (member) checked on her own matter.
All data created through the UI. SQL was used read-only, only for the portal supplement below.

## Before (RED): e2e frontend built from main `bf8d49d3b`

| Step | Observed |
|---|---|
| Alice → Client › **Client comments** (`tab-item-customer-comments`) | Plain textarea + "Post Reply"; no visibility select, no attachments. Posted "@Bob Admin please review the transfer docs 634E-before" (comment `678dd445…`). `before/before-1440-01-client-comments.png` |
| Bob → bell → "Alice Owner commented on matter" → click | URL `…/matters/94ba0c13…?tab=comments&commentId=678dd445…` (634C link). Page landed on **Overview**: 0 `#comment-*` anchors, comment text absent, no highlight. Same at 1440 and 1024. `before/before-1440-02-bob-landing-overview.png`, `before/before-1024-02-bob-landing-overview.png` |
| Vitest at HEAD | New `matter-tabs.test.tsx` cases (`?tab=comments`, legacy `?tab=customer-comments`, "Comments" label) + renamed 22-id test: **4 failed / 15 passed**. `before/vitest-red-at-head.txt` |
| Backend grep | `before/backend-grep.txt`. PROJECT create/list exist at HEAD, so no backend change. |

## After (GREEN): e2e frontend rebuilt from branch `phase89/05E-matter-comments-panel` (`e2e-rebuild.sh frontend`, container created 2026-11-05T15:06Z)

| # | Check | 1440×900 | 1024×768 |
|---|---|---|---|
| 1 | Client group menu shows **Comments** (`tab-item-comments`); no "Client comments" | PASS: items `Clients, Information requests, Comments, Adverse parties` (`after-1440-01-client-group-menu.png`) | PASS (navigated via `tab-item-comments`) |
| 2 | Comments tab lists the existing matter comments (same data as the old tab) | PASS: the "634E-before" row posted through the old section is listed (`after-1440-02-comments-tab-alice.png`) | PASS: 3 rows listed (`after-1024-01-comments-tab-alice.png`) |
| 3 | Composer has no Visibility select and shows the slate "Shared with client" badge | PASS: Alice (owner) 0 selects, badge 1; Bob (admin) same (`after-1440-07…`) | PASS: 0 selects, badge 1 |
| 4 | Post with attachment + @mention | PASS: PDF attached, hint "Attachments on a client-visible comment are shared with the client." shown, posted `8ee1f1e8…` with chip `offer-to-purchase-634E.pdf` and "Client visible" (`after-1440-03…`, `after-1440-04…`) | PASS: posted `a45ac7ab…` with chip `fica-letter-634E-1024.pdf` (`after-1024-02…`, `after-1024-03…`) |
| 5 | Edit dialog (D2) offers no Visibility select on a matter comment | PASS: 0 selects, client-visible hint shown (`after-1440-05-edit-dialog-no-visibility.png`) | n/a (same component) |
| 6 | Bob's bell → click lands on `?tab=comments&commentId=<c>`, Client group selected, comment in viewport and highlighted, ring cleared after ~2 s | PASS: URL `…?tab=comments&commentId=8ee1f1e8…`, `tab-group-client` selected, target visible 839 ms after click, `data-highlighted="true"` + `ring-2`, fully in viewport; after +2.5 s `data-highlighted` gone, no `ring-2` (`after-1440-06-bob-bell.png`, `after-1440-07-bob-landing-highlighted.png`, `after-1440-08-ring-cleared.png`) | PASS: URL `…commentId=a45ac7ab…`, `tab-group-client` selected, `data-highlighted="true"` + `ring-2`, in viewport, cleared after +2.5 s (`after-1024-04-bob-bell.png`, `after-1024-05-bob-landing-highlighted.png`) |
| 7 | Legacy `?tab=customer-comments` opens the Comments tab | PASS: active group "Client · Comments", 3 rows (`after-1440-09-legacy-customer-comments-url.png`) | PASS: "Client · Comments" (`after-1024-06-legacy-customer-comments-url.png`) |
| 8 | No horizontal overflow (`scrollWidth − clientWidth`) | PASS: 0 | PASS: 0 |
| 9 | Member (Carol) on a matter she is on | PASS: Task Test Project i8u7 → Comments: composer, badge, 0 selects, lists the case-9 comment (`after-1440-10-comments-tab-carol-member.png`). (Carol is not on Website Redesign, which shows "This matter isn't on your list". That is the existing access rule.) | not repeated |
| 10 | Portal still shows the comment | **NOT OBSERVED** in a browser (see below) | **NOT OBSERVED** |
| 11 | (D3) a portal-authored comment shows the "Client" badge | **NOT OBSERVED** in a browser. It needs a portal-posted comment (same portal blocker). Covered by vitest `matter-comments-tab.test.tsx` › "marks portal-authored comments with a Client badge" | **NOT OBSERVED** |

Playwright `projects/project-tasks.spec.ts` against the rebuilt :3001 passed 9/9 on 3 consecutive runs. The new case 9 covers the flow: Alice posts through the panel → Carol's bell → exact `commentId` URL → highlighted + in viewport.

### Portal (#10): why NOT OBSERVED

- The portal is not part of `docker-compose.e2e.yml`. Port :3002 is held by another session's portal dev server (`b2b-strawman/portal`, `next dev --port 3002`, up ~9 h, no env file, so it targets the KC backend on :8080). It is not mine, so I did not stop it.
- The e2e backend allows CORS only from `http://localhost:3000/3001/3002` (`application-e2e.yml:22-26`). The portal's API client calls the backend from the browser (`portal/lib/api-client.ts`), so a worktree portal on another port would be blocked. Allowing it would mean changing the shared e2e stack's config, so I did not do it.
- **Read-only supplement (not a substitute for the browser):** `portal-read-model-readonly.txt`. All four PROJECT comments (`678dd445…` before, `aa791051…` probe, `8ee1f1e8…` 1440, `a45ac7ab…` 1024) are in `portal.portal_comments` for `portal_project_id = 94ba0c13…`. Both attached documents are `visibility = SHARED`. The portal reads this projection (`GET /portal/matters/{id}/comments`), and the firm-side change does not touch that path.

### Data note
One extra comment "manual probe 634E" (`aa791051…`) was posted on Website Redesign through the new UI. That happened while I diagnosed the first case-9 run, which had raced the in-flight post. The spec was then tightened to wait for the cleared draft and the posted `div#comment-<id>`.
