package io.b2mash.b2b.b2bstrawman.prerequisite;

import java.util.UUID;

/**
 * Describes a single prerequisite violation — a missing field or incomplete data that blocks an
 * action.
 *
 * @param code machine-readable violation code (e.g., "MISSING_FIELD")
 * @param message human-readable description of the violation
 * @param entityType the type of entity with the violation (e.g., "CUSTOMER")
 * @param entityId the ID of the entity with the violation
 * @param fieldSlug the slug of the missing field, or null if not field-related
 * @param groupName the group name of the field, or null if unknown
 * @param resolution suggested action to resolve the violation
 */
public record PrerequisiteViolation(
    String code,
    String message,
    String entityType,
    UUID entityId,
    String fieldSlug,
    String groupName,
    String resolution) {}
