package io.b2mash.b2b.b2bstrawman.integration.ai.profile;

import java.util.UUID;

public record AiFirmProfileUpdatedEvent(UUID profileId, int profileVersion, UUID updatedBy) {

  public static AiFirmProfileUpdatedEvent of(AiFirmProfile profile) {
    return new AiFirmProfileUpdatedEvent(
        profile.getId(), profile.getProfileVersion(), profile.getUpdatedBy());
  }
}
