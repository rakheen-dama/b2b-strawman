package io.b2mash.b2b.b2bstrawman.automation.config;

public record ActionFailure(String errorMessage, String errorDetail) implements ActionResult {
  @Override
  public boolean isSuccess() {
    return false;
  }
}
