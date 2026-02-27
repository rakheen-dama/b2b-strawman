package io.b2mash.b2b.b2bstrawman.clause.dto;

import java.util.UUID;

/**
 * Response DTO for a template-clause association enriched with clause details.
 *
 * @param id the template-clause association ID
 * @param clauseId the clause ID
 * @param title the clause title
 * @param slug the clause slug
 * @param category the clause category
 * @param description the clause description
 * @param bodyPreview first 200 characters of the clause body
 * @param required whether the clause is required on this template
 * @param sortOrder the display order within the template
 * @param active whether the clause is active
 */
public record TemplateClauseDetail(
    UUID id,
    UUID clauseId,
    String title,
    String slug,
    String category,
    String description,
    String bodyPreview,
    boolean required,
    int sortOrder,
    boolean active) {}
