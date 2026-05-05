package io.b2mash.b2b.b2bstrawman.assistant.invocation.payload;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Output payload for the Intake specialist. Contains proposed customer field values extracted from
 * uploaded documents (FICA packs, company certificates, trust deeds, ID documents).
 *
 * <p>Per architecture §2.4 — serialized into {@code ai_specialist_invocations.proposed_output} as
 * JSONB via Jackson polymorphism.
 */
public record IntakeExtractionPayload(
    String contextEntityType,
    UUID contextEntityId,
    Map<String, Object> proposedFields,
    String extractionPath,
    List<String> popiaFlaggedFields,
    List<String> validationFlags)
    implements OutputPayload {

  public IntakeExtractionPayload {
    proposedFields = proposedFields != null ? Map.copyOf(proposedFields) : Map.of();
    popiaFlaggedFields = popiaFlaggedFields != null ? List.copyOf(popiaFlaggedFields) : List.of();
    validationFlags = validationFlags != null ? List.copyOf(validationFlags) : List.of();
  }
}
