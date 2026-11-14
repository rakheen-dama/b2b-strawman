# /my-work weekly-rhythm strip labels: light-mode contrast (fix/my-work-strip-label-contrast)

Observed on the e2e mock-auth stack (:3001) as Alice, at 1440×1000. "Before" is `main` @e8f1d4b14. "After" is a fresh
stack built from @ab4abc9a2. The probe is `labels.js`, a Playwright-library script. It composites colours as painted:
each label and every background layer behind it is drawn on a 1×1 sRGB canvas and read back, then WCAG contrast is
computed. Raw values are in the `*-labels.json` files. The selected-day figure is taken after clicking Monday. Today
was Saturday in this run.

| Label (9–10px, target 4.5:1) | Light before | Light after | Dark before | Dark after |
|---|---|---|---|---|
| Day names, unselected | 2.64:1 | **7.57:1** | 6.74:1 | 6.74:1 |
| "this week" caption | 2.64:1 | **7.57:1** | 6.74:1 | 6.74:1 |
| Selected day (10% teal tint) | 2.40:1 | **6.88:1** | 5.77:1 | 5.77:1 |
| Today's label | 3.13:1 | **5.31:1** | 5.68:1 | 5.68:1 |

The classes changed are:
- day names and caption: `text-slate-400` → `text-muted-foreground` (slate-600 in light, slate-400 in dark);
- today's label: `text-teal-600` → `text-teal-700 dark:text-teal-600`.

Dark mode is identical before and after. 0 console or page errors in either theme. Screenshots: `before-` / `after-`
`strip-light.png` and `strip-selected-light.png`, plus `after-strip-dark.png` and `after-strip-selected-dark.png`.
