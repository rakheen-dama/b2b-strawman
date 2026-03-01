package io.b2mash.b2b.b2bstrawman.prerequisite;

/** Contexts in which prerequisite checks are evaluated before allowing an action to proceed. */
public enum PrerequisiteContext {
  LIFECYCLE_ACTIVATION("Customer Activation"),
  INVOICE_GENERATION("Invoice Generation"),
  PROPOSAL_SEND("Proposal Sending"),
  DOCUMENT_GENERATION("Document Generation"),
  PROJECT_CREATION("Project Creation");

  private final String displayLabel;

  PrerequisiteContext(String displayLabel) {
    this.displayLabel = displayLabel;
  }

  public String getDisplayLabel() {
    return displayLabel;
  }
}
