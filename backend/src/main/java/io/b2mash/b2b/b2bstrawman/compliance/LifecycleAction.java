package io.b2mash.b2b.b2bstrawman.compliance;

public enum LifecycleAction {
  CREATE_PROJECT("project"),
  CREATE_TASK("task"),
  CREATE_INVOICE("invoice"),
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
