package io.b2mash.b2b.b2bstrawman.informationrequest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "request_template_items")
public class RequestTemplateItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "template_id", nullable = false)
  private UUID templateId;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "description", length = 1000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "response_type", nullable = false, length = 20)
  private ResponseType responseType;

  @Column(name = "required", nullable = false)
  private boolean required;

  @Column(name = "file_type_hints", length = 200)
  private String fileTypeHints;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected RequestTemplateItem() {}

  public RequestTemplateItem(
      UUID templateId,
      String name,
      String description,
      ResponseType responseType,
      boolean required,
      String fileTypeHints,
      int sortOrder) {
    this.templateId = templateId;
    this.name = name;
    this.description = description;
    this.responseType = responseType;
    this.required = required;
    this.fileTypeHints = fileTypeHints;
    this.sortOrder = sortOrder;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTemplateId() {
    return templateId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public ResponseType getResponseType() {
    return responseType;
  }

  public boolean isRequired() {
    return required;
  }

  public String getFileTypeHints() {
    return fileTypeHints;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
