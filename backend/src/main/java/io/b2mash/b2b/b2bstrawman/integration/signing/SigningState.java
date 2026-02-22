package io.b2mash.b2b.b2bstrawman.integration.signing;

/** Tracks the lifecycle state of a document signing request. */
public enum SigningState {
  PENDING,
  SIGNED,
  DECLINED,
  EXPIRED
}
