package io.b2mash.b2b.b2bstrawman.compliance;

/** Actions that can be gated by a customer's lifecycle status. */
public enum LifecycleAction {
  CREATE_PROJECT,
  CREATE_TASK,
  LOG_TIME,
  CREATE_INVOICE,
  UPLOAD_DOCUMENT,
  EDIT_CUSTOMER
}
