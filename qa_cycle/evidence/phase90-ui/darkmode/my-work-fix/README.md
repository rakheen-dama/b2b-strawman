# /my-work dark-mode contrast fix (Phase 90 debt D-1, D-2)

Observed on the e2e mock-auth stack (:3001) as Alice. "Before" is `main` @54d150a29. "After" is a fresh e2e stack
built from `fix/my-work-dark-contrast` @2f7063cdf, so the after run has different time data: `10h 30m` and `10.5h`
instead of `0m` and `13.0h`. The probe is `obs.js` (Playwright library, 1440×1000, `kazi-theme` set
in localStorage plus the matching `colorScheme`). It measures computed colours. Contrast ratios in the main table are WCAG relative
luminance from the browser's `lab()` values, using their L component; the raw numbers are in the `*-probe.json` files.

| Element | Dark before | Dark after | Light before | Light after |
|---|---|---|---|---|
| "Time today" figure vs card | **1.00:1** (illegible) | **17.93:1** | 17.72:1 | 17.72:1 |
| Weekly total vs card | **1.00:1** (illegible) | **17.93:1** | 17.72:1 | 17.72:1 |
| Time-today track vs card | 13.71:1 (bright slab) | 1.15:1 (subtle track) | 1.29:1 | 1.12:1 |
| Day tracks ×7 vs card | 13.71:1 (bright slab) | 1.15:1 | 1.29:1 | 1.12:1 |
| Strip divider vs card | 13.71:1 | 1.25:1 | 1.29:1 | 1.27:1 |
| Selected day / hover | near-white slabs (L≈98) | subtle teal tint / slate-800 | teal-50 / slate-50 | teal-500 at 10% / slate-100 |

**Selected-day label contrast.** This is composited the way the browser paints it: the probe draws the card, then the
10%-alpha selected background, onto a 1×1 sRGB canvas and reads the pixel back. The label is read the same way. These
are the `selectedCompositeRgb`, `selectedLabelRgb` and `selectedLabelContrast` fields in the after JSONs. The before
values are recomputed with the same canvas method from the recorded before colours (`before-recompute.js` →
`before-selected-recompute.json`); the before JSONs' own `selectedLabelContrast` is the older lab-L approximation.

| Selected-day label | Before (opaque teal-50) | After (teal-500 at 10% over the card) |
|---|---|---|
| Dark | 2.56:1 (near-white slab) | **5.77:1** |
| Light | 2.56:1 | 2.40:1 (slightly lower) |

The light value is limited by the existing `text-slate-400` day label, which is under 3:1 on any light background. The
label predates this PR and is a follow-up. The small light drop (2.56 → 2.40) comes from the 10% teal-500 tint being a
little darker than teal-50.

**Trade-off in light mode:** `bg-muted` is slate-100, so the light-mode tracks are one step lighter than the old slate-200
(1.29 → 1.12 vs the card). They are still clearly visible (`after-rhythm-strip-light.png`). Using the shared token keeps
one class for both themes (ADR-371: semantic tokens over `dark:` pairs).

**"{n}d remaining"** on the next deadline (2f7063cdf, found in review) was `text-slate-600`, about 2.35:1 on the dark card.
It is now `text-muted-foreground`, which is slate-600 in light (unchanged) and slate-400 in dark. Alice has no upcoming
deadline in the e2e data, so the browser probe could not show this element (`daysRemaining: "not shown"`). It is covered
by vitest only.

0 console or page errors in either theme. The probe waits for the post-login page to settle before navigating. Without
that wait, the probe's own navigation cancels the mock-auth `userinfo` fetch, which logs "Failed to fetch". That is a
probe artefact: the mock IDP returns 200 with CORS, and the error disappears with the wait.
