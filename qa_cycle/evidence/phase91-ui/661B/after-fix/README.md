# 661B: post-fix pixel-identity re-check, head `d62c5dc19`

| | |
|---|---|
| Head | `phase91/61B-light-scheme` @ **`d62c5dc19`** (PR #1863). The fix under test is `--button-accent-foreground: var(--color-white)` in `packages/ui/styles/globals.css` (`:root` and `.dark`) and in the firm light block of `frontend/app/globals.css`. Before the fix the value was `oklch(100% 0 0)` |
| Stack | The e2e stack from `../after/` (built from `6e3e0566f`, same data). Only the **frontend** was rebuilt from this head (`SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh frontend`). The served CSS chunks contain `--button-accent-foreground:var(--color-white)` (3 matches) |
| Portal | This worktree's portal on `next dev --port 3012` with `NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081` and the Origin shim, as in `../after/` |
| Script | `scripts/capture-after-fix.cjs`, a new file. Helpers are in `../walk/scripts/common.cjs`, copied from `../after/scripts/capture-after.cjs`, which was not edited. The capture method is the same as `../after/`: the same fee notes (first DRAFT `62c9aa43-…` for "Approve", APPROVED `b46f2343-…` for "Send fee note"), the same clip, and the same search of the button crop in the before tile |
| Before tiles | `/tmp/661b-before-raw/*.png`, the raw before run, kept out of the tree under EVIDENCE-BUDGET as in `../after/`, plus the committed `../before/marketing-1024-light.png` |
| Date / node | 2026-11-25, nvm node `v20.11.1` |

Run from the worktree root:

```
BEFORE_RAW=/tmp/661b-before-raw node qa_cycle/evidence/phase91-ui/661B/after-fix/scripts/capture-after-fix.cjs qa_cycle/evidence/phase91-ui/661B/after-fix
```

## 1. Pixel identity vs before (the crops that failed d/e in `../after/diffs.json`)

| Crop | `../after/` (`6e3e0566f`) | **after-fix (`d62c5dc19`)** |
|---|---|---|
| Dark accent button "Approve" (fee-note DRAFT header, 79×32) | 126 / 2 528 px, Δ1 | **0 / 2 528**, max Δ 0 |
| Dark accent button "Send fee note" (APPROVED header, 116×32) | 161 / 3 712 px, Δ1 | **0 / 3 712**, max Δ 0 |
| Portal `/login` 1024 light, full tile | 214 / 786 432 px, Δ1 (label "Send Magic Link") | **0 / 786 432**, max Δ 0 |
| Portal `/login` "Send Magic Link" (crop) | — | **0 / 17 864** |
| Portal `/home` 1024 light, full tile | 0 | **0 / 786 432** |
| Marketing `/` 1024 light, full tile | 394 / 786 432 px, Δ1 (accent labels) | **0 / 786 432**, max Δ 0 |
| Marketing `/` first accent button "Book a Demo" (crop) | — | **0 / 4 800** |

Every compared crop is **bit-identical** to before (`diffs.json`, `pixel-identity.png` = before | after-fix | diff). The Δ1 glyph-edge drift recorded as `../after/` defect 1 is gone. That run's counterfactual predicted this result, and the run here observed it.

## 2. Computed label colours (`probes.json`)

| Where | Element | Computed `color` | Fill | Ratio |
|---|---|---|---|---|
| Firm app, **dark** | "Approve", "Send fee note" (`[data-variant=accent]`) | **`rgb(255, 255, 255)`** (was `lab(100 0 0)`) | `#00a38f` (teal-600, unchanged) | 3.165 (K-21, unchanged) |
| Portal `/login` | "Send Magic Link" | **`rgb(255, 255, 255)`** | `#00a38f` | — |
| Marketing `/` | "Book a Demo" ×3, "Get in touch" | **`rgb(255, 255, 255)`** | `#00a38f` | — |
| Firm app, **light** | "Approve", "Send fee note" | **`rgb(255, 255, 255)`** | **`#00776a`** (Kazi teal-700) | **5.459** |

`--button-accent-foreground` computes to `#fff` on every accent element.

## 3. Navigation suite

`cd frontend && E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 /opt/homebrew/bin/pnpm test:e2e:navigation` on the rebuilt stack gave **17 passed**, 0 failed (6.0 s), exit 0. The run left no tracked change, so the `git checkout` of `frontend/test-results` and `documentation/screenshots` changed nothing.

## 4. Console

0 hydration warnings. There is 1 error: marketing `/` "Failed to load resource: 404". It is the same one before and after (`console.json`).

## Files

| File | Content |
|---|---|
| `pixel-identity.png` | Before / after-fix / diff crops: dark Approve, dark Send fee note, marketing "Book a Demo", portal "Send Magic Link" |
| `tiles.png` | The as-rendered tiles: fee-note headers in dark and light, marketing `/`, portal `/login` and `/home` |
| `diffs.json`, `probes.json`, `console.json`, `notes.json` | Single-line JSON |
| `scripts/capture-after-fix.cjs` | As executed |
