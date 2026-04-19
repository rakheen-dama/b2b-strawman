# Phase 67 — Legal Depth II Curated Screenshots

This directory holds curated PNG captures of the Phase 67 **Legal Depth II** (daily operational loop) features for marketing, blog, walkthrough, and demo use on the `legal-za` vertical profile.

## Status

Placeholder. PNGs are populated manually after a clean Playwright run of `frontend/e2e/tests/legal-depth-ii/*.spec.ts`, or captured from a live `legal-za` tenant on the Keycloak dev stack.

## Capture helper limitation

The `captureScreenshot(page, name, { curated: true })` helper writes curated PNGs to `documentation/screenshots/legal-vertical/` (flat — it does not support a subdirectory option, and `assertSafeName` rejects path traversal). Workflow:

1. Run `pnpm test:e2e:legal-depth-ii` with a healthy e2e stack.
2. Specs write curated PNGs into `documentation/screenshots/legal-vertical/`.
3. Move the new PNGs into this `phase67/` subdirectory manually:
   ```bash
   cd documentation/screenshots/legal-vertical
   mv disbursement-list-view.png disbursement-approval-dialog.png trust-link-dialog.png \
      matter-closure-dialog-gate-report-failing.png matter-closure-dialog-all-green.png \
      closure-letter-preview.png statement-of-account-preview.png \
      conveyancing-matter-detail-custom-fields.png otp-document-with-clauses.png \
      otp-acceptance-request.png phase67/
   ```

This keeps the `captureScreenshot` helper unchanged (helper modification was out of scope for Epic 493A).

## Prescribed shots (mirrors the Phase 67 Day-checkpoints in `qa/testplan/demos/legal-za-90day-keycloak.md`)

1. **`disbursement-list-view.png`** — Legal → Disbursements list page (`/org/{slug}/legal/disbursements`) with mixed status badges (DRAFT / SUBMITTED / APPROVED) after Day 5.
2. **`disbursement-approval-dialog.png`** — Approve action dialog on a trust-linked disbursement (Thandi/Owner POV).
3. **`trust-link-dialog.png`** — Create Disbursement dialog with the trust-link slot populated, showing the linked `DISBURSEMENT_PAYMENT` trust transaction reference.
4. **`matter-closure-dialog-gate-report-failing.png`** — Close Matter dialog Step 1 with one or more gates in the red/fail state (trust balance > R0).
5. **`matter-closure-dialog-all-green.png`** — Close Matter dialog Step 1 with all 9 gates passing (happy path).
6. **`closure-letter-preview.png`** — Generated closure letter PDF preview (Day 75 happy path), showing Mathebula letterhead + closure reason.
7. **`statement-of-account-preview.png`** — Statement of Account dialog with HTML preview rendering fees + disbursements + trust activity + summary totals block (Day 30).
8. **`conveyancing-matter-detail-custom-fields.png`** — Matter detail view for a conveyancing matter showing the 10 conveyancing custom fields populated.
9. **`otp-document-with-clauses.png`** — Generated Offer to Purchase document preview with inserted clauses from `conveyancing-za-clauses` pack.
10. **`otp-acceptance-request.png`** — Acceptance request view (portal magic link) showing OTP preview + Accept/Decline actions.

## Convention

Mirrors `documentation/screenshots/legal-vertical/` (Phase 64) and `documentation/screenshots/consulting-vertical/`. Curated captures are non-regression — they are intended for human-readable artifacts (blog posts, sales decks, README walkthroughs), not Playwright baselines. Regression baselines live separately under `frontend/e2e/screenshots/legal-depth-ii/`.
