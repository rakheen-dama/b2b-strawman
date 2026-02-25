package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "document_templates")
public class DocumentTemplate {

  private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*$");

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "slug", nullable = false, length = 200)
  private String slug;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 30)
  private TemplateCategory category;

  @Enumerated(EnumType.STRING)
  @Column(name = "primary_entity_type", nullable = false, length = 20)
  private TemplateEntityType primaryEntityType;

  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "css", columnDefinition = "TEXT")
  private String css;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 20)
  private TemplateSource source;

  @Column(name = "source_template_id")
  private UUID sourceTemplateId;

  @Column(name = "pack_id", length = 100)
  private String packId;

  @Column(name = "pack_template_key", length = 100)
  private String packTemplateKey;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "required_context_fields", columnDefinition = "jsonb")
  private List<Map<String, String>> requiredContextFields;

  protected DocumentTemplate() {}

  public DocumentTemplate(
      TemplateEntityType primaryEntityType,
      String name,
      String slug,
      TemplateCategory category,
      String content) {
    this.primaryEntityType = primaryEntityType;
    this.name = name;
    this.slug = slug;
    this.category = category;
    this.content = content;
    this.source = TemplateSource.ORG_CUSTOM;
    this.active = true;
    this.sortOrder = 0;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /**
   * Generates a slug from a template name. Converts to lowercase, replaces spaces and consecutive
   * hyphens with single hyphens, strips non-alphanumeric characters except hyphens, and validates
   * against the pattern ^[a-z][a-z0-9-]*$.
   *
   * @param name the template name
   * @return the generated slug
   * @throws InvalidStateException if the name is blank/null or generates an invalid slug
   */
  public static String generateSlug(String name) {
    if (name == null || name.isBlank()) {
      throw new InvalidStateException("Invalid slug", "Template name cannot be blank");
    }

    String slug =
        name.toLowerCase()
            .replaceAll("[\\s]+", "-")
            .replaceAll("[^a-z0-9-]", "")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");

    if (slug.isEmpty() || !SLUG_PATTERN.matcher(slug).matches()) {
      throw new InvalidStateException(
          "Invalid slug",
          "Generated slug '"
              + slug
              + "' from name '"
              + name
              + "' does not match pattern ^[a-z][a-z0-9-]*$");
    }

    return slug;
  }

  public void updateContent(String name, String description, String content, String css) {
    this.name = name;
    this.description = description;
    this.content = content;
    this.css = css;
    this.updatedAt = Instant.now();
  }

  public void deactivate() {
    this.active = false;
    this.updatedAt = Instant.now();
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public String getDescription() {
    return description;
  }

  public TemplateCategory getCategory() {
    return category;
  }

  public TemplateEntityType getPrimaryEntityType() {
    return primaryEntityType;
  }

  public String getContent() {
    return content;
  }

  public String getCss() {
    return css;
  }

  public TemplateSource getSource() {
    return source;
  }

  public UUID getSourceTemplateId() {
    return sourceTemplateId;
  }

  public String getPackId() {
    return packId;
  }

  public String getPackTemplateKey() {
    return packTemplateKey;
  }

  public boolean isActive() {
    return active;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  // --- Setters for mutable fields ---

  public void setCss(String css) {
    this.css = css;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }

  public void setPackId(String packId) {
    this.packId = packId;
  }

  public void setPackTemplateKey(String packTemplateKey) {
    this.packTemplateKey = packTemplateKey;
  }

  public void setSourceTemplateId(UUID sourceTemplateId) {
    this.sourceTemplateId = sourceTemplateId;
  }

  public void setSource(TemplateSource source) {
    this.source = source;
  }

  public List<Map<String, String>> getRequiredContextFields() {
    return requiredContextFields;
  }

  public void setRequiredContextFields(List<Map<String, String>> requiredContextFields) {
    this.requiredContextFields = requiredContextFields;
  }
}
