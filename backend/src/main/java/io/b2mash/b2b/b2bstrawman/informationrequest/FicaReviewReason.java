package io.b2mash.b2b.b2bstrawman.informationrequest;

/**
 * Why an active FICA attestation is due for review (Phase 93 655B, §17.3.4, FD-30). Computed by
 * {@link FicaStatusProjector} from the attestation and {@code today}; any reason projects {@code
 * ATTESTED_REVIEW}. The declaration order is the wire order of {@code reviewReasons}.
 */
public enum FicaReviewReason {
  /** The previous system recorded no risk rating — no review date can be derived. */
  UNRATED,
  BENEFICIAL_OWNERSHIP_OUTSTANDING,
  SOURCE_OF_FUNDS_OUTSTANDING,
  NOT_SCREENED,
  /** {@code next_review_date <= today}. */
  REVIEW_DATE_PASSED
}
