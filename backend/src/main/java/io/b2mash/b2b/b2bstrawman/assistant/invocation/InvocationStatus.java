package io.b2mash.b2b.b2bstrawman.assistant.invocation;

/** Lifecycle status of an AI specialist invocation row. */
public enum InvocationStatus {
  RUNNING,
  PENDING_APPROVAL,
  APPROVED,
  REJECTED,
  AUTO_APPLIED,
  FAILED,
  EXPIRED
}
