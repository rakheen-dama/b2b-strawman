package io.b2mash.b2b.b2bstrawman.proposal;

/** Lifecycle status of a proposal. */
public enum ProposalStatus {
  /** Initial state — content is being authored. */
  DRAFT,

  /** Sent to the customer's portal contact for review. */
  SENT,

  /** Customer accepted the proposal. */
  ACCEPTED,

  /** Customer declined the proposal. */
  DECLINED,

  /** Proposal passed its expiresAt deadline without a response. */
  EXPIRED
}
