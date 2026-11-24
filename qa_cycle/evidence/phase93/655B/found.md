# 655B — found outside the slice's own scope

## F-655B-1 — `GET /api/clients/{clientId}/fica-status` (and `GET /api/clients`) carry no capability check (not fixed — outside the slice)

`CustomerController.getFicaStatus` and `CustomerController.listCustomers` have no `@RequiresCapability`, while
architecture §17.4.3 and §17.10 X1 list `CUSTOMER_MANAGEMENT` for `fica-status` ("Auth (unchanged)") and for the
`ficaStatus=REVIEW` client list. Any authenticated member of the firm can read a client's FICA status and, after 655B,
its review reasons through either endpoint. The new sibling `GET /api/clients/{clientId}/fica-attestation` is gated
(`CUSTOMER_MANAGEMENT`, 403 proven in `FicaReviewQueueIntegrationTest`). Gating the two existing endpoints changes who
can open the client list — a product/permissions decision, and frontend pages that call them for members would need
their 403 handling checked. Logged as D-140.

**655B widens what the two ungated endpoints expose** (flagged by the automated security review on the builder's
commit): `GET /api/clients` rows now carry `ficaReviewReasons`, `ficaRiskRating`, `ficaNextReviewDate` and
`ficaRecordsLocation` (C-4), and `fica-status` carries `reviewReasons` and `attestationId` (§17.4.3). The full
attestation read is gated, but its risk rating and records location reach any member through the list. Options for
the orchestrator/founder (not applied — permissions are not a builder call, C-5): (a) gate `fica-status` and the
`ficaStatus=REVIEW` filter with `CUSTOMER_MANAGEMENT` as §17.4.3/X1 say; (b) redact the four C-4 fields and the two
`fica-status` additions for callers without `CUSTOMER_MANAGEMENT`, the way `CustomerService.toRow` already redacts
`trustBalance` / `outstandingFeeNoteTotal`; (c) accept as-is (the list already shows every member the FICA status).
Recommended: (b) in 655B before merge, cheap and contained to `toRow` + the controller.

**Resolved for the new detail (D-148, option (b)):** a caller without `CUSTOMER_MANAGEMENT` now gets
`ficaReviewReasons: []` and `ficaRiskRating` / `ficaNextReviewDate` / `ficaRecordsLocation` null on `GET /api/clients`,
and `reviewReasons: []` / `attestationId: null` on `fica-status`; owner/admin (and any role granted the capability)
see them. Proven for the seeded Member role and a custom role without the capability in
`FicaReviewQueueIntegrationTest.withoutClientManagement*` (both red before the change).

**Resolved for `fica-status` and the review queue (D-152, option (a) for those two reads, review round 1):**
`GET /api/clients/{clientId}/fica-status` carries `@RequiresCapability("CUSTOMER_MANAGEMENT")` and
`GET /api/clients?ficaStatus=REVIEW` returns 403 without it, as §17.4.3 / §17.10 X1 rule; the frontend hides the
client hero FICA tile and the matter FICA card for callers without the capability (a 403 renders nothing). Proven in
`FicaReviewQueueIntegrationTest.ficaStatusIs403WithoutClientManagement` / `withoutClientManagementTheReviewQueueIs403`
(seeded Member + a custom role without the capability; owner/admin 200).

**What remains out of scope** is only the pre-existing ungated client list: `GET /api/clients` (and its
`OUTSTANDING` / `DONE` filters) still answers any member, so the list's status column (`ficaStatus`, `NOT_STARTED` …
`ATTESTED_REVIEW`) reaches every member through the API, with the attestation detail blanked (D-148). In the UI the
Clients page itself already needs CUSTOMER_MANAGEMENT. Gating the list endpoint is a permissions decision for the
founder (D-140 remainder).

## F-655B-2 — a PEP/sanctions screening match is not a review reason (legal question for RG-3)

FD-30 (build default, release-gated RG-3) names five review reasons; `SCREENED_MATCH` is not one of them, so an
imported client recorded as "Screened — match" with no other gap projects `ATTESTED` ("Verified in prior system
(attested)") and never enters the "FICA review due" queue. Under FICA s21 / the RMCP a match normally drives enhanced
due diligence, so the legal reviewer at RG-3 should rule whether `SCREENED_MATCH` must be a sixth reason. Not changed
here (D-141); pinned by `FicaStatusProjectorTest.Attestation.screeningMatchAloneIsNotAReviewReason` so the ruling has a
single place to flip.

## F-655B-3 — the two new interval-validation tests do not assert the 400 body (deferred, CodeRabbit minor)

CodeRabbit (review round 1 on #1851, `OrgSettingsIntegrationTest.java:358`): the two new
`PATCH /api/settings/compliance` tests that send an out-of-range FICA review interval assert only the 400 status, not
the validation error body (which field, which bound). Deferred per the founder ruling that Minor findings are
follow-ups, not extra review rounds (memory `feedback_only_high_medium_in_pr`). Follow-up: assert the ProblemDetail
`detail` / field errors in both tests.
