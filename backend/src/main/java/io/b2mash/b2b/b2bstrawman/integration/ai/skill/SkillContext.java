package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import java.util.Map;
import java.util.UUID;

public record SkillContext(
    UUID entityId, String entityType, String description, Map<String, Object> additionalContext) {

  public SkillContext {
    additionalContext = additionalContext == null ? Map.of() : Map.copyOf(additionalContext);
  }
}
