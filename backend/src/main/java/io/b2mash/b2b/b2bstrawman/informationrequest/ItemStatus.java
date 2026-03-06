package io.b2mash.b2b.b2bstrawman.informationrequest;

/** Status of an individual request item within an information request. */
public enum ItemStatus {
  /** Awaiting client response. */
  PENDING,

  /** Client has submitted a response (file or text). */
  SUBMITTED,

  /** Firm has accepted the submitted response. */
  ACCEPTED,

  /** Firm has rejected the submitted response. */
  REJECTED
}
