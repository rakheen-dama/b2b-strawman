package io.b2mash.b2b.b2bstrawman.reporting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "report_definitions")
public class ReportDefinition {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "slug", nullable = false, length = 100)
  private String slug;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "category", nullable = false, length = 50)
  private String category;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "parameter_schema", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> parameterSchema;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "column_definitions", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> columnDefinitions;

  @Column(name = "template_body", nullable = false, columnDefinition = "TEXT")
  private String templateBody;

  @Column(name = "is_system", nullable = false)
  private boolean isSystem = true;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ReportDefinition() {}

  public ReportDefinition(
      String name,
      String slug,
      String category,
      Map<String, Object> parameterSchema,
      Map<String, Object> columnDefinitions,
      String templateBody) {
    this.name = name;
    this.slug = slug;
    this.category = category;
    this.parameterSchema = parameterSchema;
    this.columnDefinitions = columnDefinitions;
    this.templateBody = templateBody;
    this.isSystem = true;
    this.sortOrder = 0;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Update template body (used by seed pack upsert). */
  public void updateTemplate(String templateBody) {
    this.templateBody = templateBody;
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

  public String getCategory() {
    return category;
  }

  public Map<String, Object> getParameterSchema() {
    return parameterSchema;
  }

  public Map<String, Object> getColumnDefinitions() {
    return columnDefinitions;
  }

  public String getTemplateBody() {
    return templateBody;
  }

  public boolean isSystem() {
    return isSystem;
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

  public void setDescription(String description) {
    this.description = description;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }
}
