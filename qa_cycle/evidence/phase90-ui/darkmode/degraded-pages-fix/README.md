# Dark-mode degraded pages fix (Phase 90 debt D-3, D-4, D-5): branch `fix/dark-mode-degraded-pages`

This was observed on the e2e mock-auth stack (:3001) as Alice, at 1440×1000.
- **Before** is `main` @f319469b8.
- **After** is a fresh stack built from @9379f4d1f.

The probe is `probe.js`, a Playwright-library script. It visits the 12 debt pages, resolving dynamic ids through their list pages, and opens two sample dialogs. Every colour is composited as painted: the element and every background layer behind it are drawn on a 1×1 sRGB canvas and read back, then the WCAG ratio is taken. Raw values are in the `*-probe.json` files.

| Item | Measure | Dark before | Dark after | Light before → after |
|---|---|---|---|---|
| **D-3** native fields: 7 pages and 2 dialogs, 2–10 fields each | border vs card (min) | 1.15–1.29:1 | **1.72–1.92:1** (= the Input primitive) | 1.21–1.30 → unchanged |
| | field surface vs card | **1.00:1** (no fill) | 1.06–1.07:1 (the Input's `dark:bg-input/30` fill) | unchanged |
| **D-4** muted text (`/settings/matters?tab=checklists`, `/settings/fees?tab=tariffs`) | text contrast (min) | 2.37–2.62:1 | **5.84:1** | 2.51–2.67 → **6.76** |
| **D-5** Connect to Xero (`/settings/connections?tab=xero`) | text vs `#13B5EA` | 2.38:1 | **8.42:1** | 2.38 → **8.42** |

0 console or page errors in either theme, before or after.

**Not observable on this stack.** Two debt sites have no data here, so the source-scan guard covers them instead (`frontend/__tests__/dark-mode-contrast-guard.test.ts`):
- `/legal/disbursements/[id]`, "No receipt uploaded": the e2e tenant has no disbursements.
- the firm-documents "Uploaded" date on `/settings/firm?tab=general`: no org document has been uploaded.

**Scope beyond the debt list**, all authorised: the D-3 fix covers every native `<select>`/`<textarea>`, which is 62 → 82 of 82 with the dark fill, in about 40 files. Many are in dialogs the 644B walk never opened. D-4 covers all 29 lines of the pattern in 17 files.

Screenshots (`before-` / `after-`): `matters-new`, `conflict-check`, `dialog-new-disbursement` (D-3); `settings-fees--tariffs`, `settings-matters--checklists` (D-4); `settings-connections--xero` (D-5); plus `after-*-light.png` for matters-new and Xero.
