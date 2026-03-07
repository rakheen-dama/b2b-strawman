package io.b2mash.b2b.b2bstrawman.automation.config;

import java.util.Map;

public record ActionSuccess(Map<String, Object> resultData) implements ActionResult {
  @Override
  public boolean isSuccess() {
    return true;
  }
}
