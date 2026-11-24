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

## F-655B-2 — a PEP/sanctions screening match is not a review reason (legal question for RG-3)

FD-30 (build default, release-gated RG-3) names five review reasons; `SCREENED_MATCH` is not one of them, so an
imported client recorded as "Screened — match" with no other gap projects `ATTESTED` ("Verified in prior system
(attested)") and never enters the "FICA review due" queue. Under FICA s21 / the RMCP a match normally drives enhanced
due diligence, so the legal reviewer at RG-3 should rule whether `SCREENED_MATCH` must be a sixth reason. Not changed
here (D-141); pinned by `FicaStatusProjectorTest.Attestation.screeningMatchAloneIsNotAReviewReason` so the ruling has a
single place to flip.
