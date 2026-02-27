package io.b2mash.b2b.b2bstrawman.clause;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Join entity linking a document template to a clause with ordering and required configuration. */
@Entity
@Table(name = "template_clauses")
public class TemplateClause {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "template_id", nullable = false)
  private UUID templateId;

  @Column(name = "clause_id", nullable = false)
  private UUID clauseId;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "required", nullable = false)
  private boolean required;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** JPA-required no-arg constructor. */
  protected TemplateClause() {}

  public TemplateClause(UUID templateId, UUID clauseId, int sortOrder, boolean required) {
    this.templateId = Objects.requireNonNull(templateId, "templateId must not be null");
    this.clauseId = Objects.requireNonNull(clauseId, "clauseId must not be null");
    this.sortOrder = sortOrder;
    this.required = required;
  }

  @PrePersist
  void onPrePersist() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTemplateId() {
    return templateId;
  }

  public UUID getClauseId() {
    return clauseId;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isRequired() {
    return required;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
