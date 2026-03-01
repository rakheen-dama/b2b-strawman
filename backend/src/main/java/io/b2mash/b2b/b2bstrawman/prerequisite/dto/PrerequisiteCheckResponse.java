package io.b2mash.b2b.b2bstrawman.prerequisite.dto;

import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteCheck;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteViolation;
import java.util.List;
import java.util.UUID;

public record PrerequisiteCheckResponse(
    boolean passed, String context, List<ViolationResponse> violations) {

  public static PrerequisiteCheckResponse from(PrerequisiteCheck check) {
    return new PrerequisiteCheckResponse(
        check.passed(),
        check.context().name(),
        check.violations().stream().map(ViolationResponse::from).toList());
  }

  public record ViolationResponse(
      String code,
      String message,
      String entityType,
      UUID entityId,
      String fieldSlug,
      String groupName,
      String resolution) {

    public static ViolationResponse from(PrerequisiteViolation v) {
      return new ViolationResponse(
          v.code(),
          v.message(),
          v.entityType(),
          v.entityId(),
          v.fieldSlug(),
          v.groupName(),
          v.resolution());
    }
  }
}
