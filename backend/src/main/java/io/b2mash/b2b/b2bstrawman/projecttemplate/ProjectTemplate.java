package io.b2mash.b2b.b2bstrawman.projecttemplate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_templates")
public class ProjectTemplate {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false, length = 300)
  private String name;

  @Column(name = "name_pattern", nullable = false, length = 300)
  private String namePattern;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "billable_default", nullable = false)
  private boolean billableDefault;

  @Column(name = "source", nullable = false, length = 20)
  private String source;

  @Column(name = "source_project_id")
  private UUID sourceProjectId;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ProjectTemplate() {}

  public ProjectTemplate(
      String name,
      String namePattern,
      String description,
      boolean billableDefault,
      String source,
      UUID sourceProjectId,
      UUID createdBy) {
    this.name = name;
    this.namePattern = namePattern;
    this.description = description;
    this.billableDefault = billableDefault;
    this.source = source;
    this.sourceProjectId = sourceProjectId;
    this.active = true;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void update(String name, String namePattern, String description, boolean billableDefault) {
    this.name = name;
    this.namePattern = namePattern;
    this.description = description;
    this.billableDefault = billableDefault;
    this.updatedAt = Instant.now();
  }

  public void activate() {
    this.active = true;
    this.updatedAt = Instant.now();
  }

  public void deactivate() {
    this.active = false;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getNamePattern() {
    return namePattern;
  }

  public String getDescription() {
    return description;
  }

  public boolean isBillableDefault() {
    return billableDefault;
  }

  public String getSource() {
    return source;
  }

  public UUID getSourceProjectId() {
    return sourceProjectId;
  }

  public boolean isActive() {
    return active;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
