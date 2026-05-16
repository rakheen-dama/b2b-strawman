package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import io.b2mash.b2b.b2bstrawman.integration.ai.AiImageInput;
import java.util.List;
import java.util.UUID;

public record SkillExecutionRequest(
    AiSkill skill, SkillContext context, UUID invokedBy, List<AiImageInput> images) {

  public SkillExecutionRequest {
    images = images == null ? List.of() : List.copyOf(images);
  }

  public boolean hasImages() {
    return images != null && !images.isEmpty();
  }
}
