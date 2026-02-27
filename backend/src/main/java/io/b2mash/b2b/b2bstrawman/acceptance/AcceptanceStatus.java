package io.b2mash.b2b.b2bstrawman.acceptance;

/** Lifecycle status of an acceptance request. */
public enum AcceptanceStatus {
  /** Created but email not yet sent (transient). */
  PENDING,

  /** Email sent, awaiting recipient action. */
  SENT,

  /** Recipient opened the acceptance page. */
  VIEWED,

  /** Recipient accepted the document. */
  ACCEPTED,

  /** Request passed its expiresAt deadline. */
  EXPIRED,

  /** Firm revoked the request. */
  REVOKED
}
