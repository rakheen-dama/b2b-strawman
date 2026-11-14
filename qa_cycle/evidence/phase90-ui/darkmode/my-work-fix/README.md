# /my-work dark-mode contrast fix (Phase 90 debt D-1, D-2)

Observed on the e2e mock-auth stack (:3001) as Alice. "Before" is `main` @54d150a29; "after" is the frontend rebuilt
from `fix/my-work-dark-contrast` @919037506. The probe is `obs.js` (Playwright library, 1440×1000, `kazi-theme` set
in localStorage plus the matching `colorScheme`). It measures computed colours. Contrast ratios are WCAG relative
luminance from the browser's `lab()` values; the raw numbers are in the `*-probe.json` files.

| Element | Dark before | Dark after | Light before | Light after |
|---|---|---|---|---|
| "Time today" figure (`0m`) vs card | **1.00:1** (illegible) | **17.93:1** | 17.72:1 | 17.72:1 |
| Weekly total (`13.0h`) vs card | **1.00:1** (illegible) | **17.93:1** | 17.72:1 | 17.72:1 |
| Time-today track vs card | 13.71:1 (bright slab) | 1.15:1 (subtle track) | 1.29:1 | 1.12:1 |
| Day tracks ×7 vs card | 13.71:1 (bright slab) | 1.15:1 | 1.29:1 | 1.12:1 |
| Strip divider vs card | 13.71:1 | 1.25:1 | 1.29:1 | 1.27:1 |
| Selected day / hover | near-white slabs (L≈98) | subtle teal tint / slate-800 | teal-50 / slate-50 | teal-500 at 10% / slate-100 |

The selected-day "label contrast" in `after-*-probe.json` (1.14) is a probe artefact: the new background is 10% alpha,
and the probe does not composite it. The screenshots show the label is readable.

**Trade-off in light mode:** `bg-muted` is slate-100, so the light-mode tracks are one step lighter than the old slate-200
(1.29 → 1.12 vs the card). They are still clearly visible (`after-rhythm-strip-light.png`). Using the shared token keeps
one class for both themes (ADR-371: semantic tokens over `dark:` pairs).

0 console or page errors in either theme.
