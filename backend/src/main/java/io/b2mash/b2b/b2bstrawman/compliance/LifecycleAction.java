package io.b2mash.b2b.b2bstrawman.compliance;

/**
 * Lifecycle-gated actions. The label is user-visible copy: {@link CustomerLifecycleGuard} renders
 * it into the 409 detail "Cannot create {label} for client in {status} lifecycle status" (ADR-357).
 */
public enum LifecycleAction {
  CREATE_PROJECT("matter"),
  UPDATE_CUSTOM_FIELDS("custom fields"),
  CREATE_TASK("task"),
  CREATE_INVOICE("fee note"),
  CREATE_TIME_ENTRY("time entry"),
  CREATE_DOCUMENT("document"),
  CREATE_COMMENT("comment");

  private final String label;

  LifecycleAction(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
