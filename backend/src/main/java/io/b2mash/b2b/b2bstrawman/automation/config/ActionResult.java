package io.b2mash.b2b.b2bstrawman.automation.config;

public sealed interface ActionResult permits ActionSuccess, ActionFailure {
  boolean isSuccess();
}
