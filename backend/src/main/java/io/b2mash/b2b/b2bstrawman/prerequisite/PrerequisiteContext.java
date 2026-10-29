package io.b2mash.b2b.b2bstrawman.prerequisite;

/** Contexts in which prerequisite checks are evaluated before allowing an action to proceed. */
public enum PrerequisiteContext {
  LIFECYCLE_ACTIVATION("Client Activation"),
  INVOICE_GENERATION("Fee note generation"),
  DOCUMENT_GENERATION("Document Generation"),
  PROJECT_CREATION("Matter Creation");

  private final String displayLabel;

  PrerequisiteContext(String displayLabel) {
    this.displayLabel = displayLabel;
  }

  public String getDisplayLabel() {
    return displayLabel;
  }
}
