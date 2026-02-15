package io.b2mash.b2b.b2bstrawman.template;

import java.util.Map;
import java.util.UUID;

/**
 * Strategy interface for assembling template rendering context. Each implementation handles a
 * specific entity type (PROJECT, CUSTOMER, INVOICE) and produces a flat Map of variables for
 * Thymeleaf rendering.
 *
 * <p>Per ADR-058, context builders produce {@code Map<String, Object>} rather than passing JPA
 * entities directly to templates. This provides explicit control over exposed data, prevents
 * lazy-loading surprises, and allows data enrichment (date formatting, S3 URL resolution, custom
 * field flattening).
 */
public interface TemplateContextBuilder {

  /** Returns the entity type this builder handles. */
  TemplateEntityType supports();

  /**
   * Builds the template rendering context for the given entity.
   *
   * @param entityId the ID of the entity to build context for
   * @param memberId the ID of the member triggering the generation (for generatedBy)
   * @return a map of template variables ready for Thymeleaf rendering
   */
  Map<String, Object> buildContext(UUID entityId, UUID memberId);
}
