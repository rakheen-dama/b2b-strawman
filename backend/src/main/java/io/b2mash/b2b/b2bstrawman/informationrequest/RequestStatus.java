package io.b2mash.b2b.b2bstrawman.informationrequest;

/** Lifecycle status of an information request. */
public enum RequestStatus {
  /** Initial state — content is being assembled. */
  DRAFT,

  /** Sent to the portal contact for response. */
  SENT,

  /** At least one item has been submitted by the client. */
  IN_PROGRESS,

  /** All required items accepted — request fulfilled. */
  COMPLETED,

  /** Request withdrawn by the firm. */
  CANCELLED
}
