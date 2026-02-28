package io.b2mash.b2b.b2bstrawman.clause;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A reusable clause in the clause library that can be attached to document templates. */
@Entity
@Table(name = "clauses")
public class Clause {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "slug", nullable = false, length = 200)
  private String slug;

  @Column(name = "description", length = 500)
  private String description;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "body", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> body;

  @Column(name = "legacy_body", columnDefinition = "TEXT")
  private String legacyBody;

  @Column(name = "category", nullable = false, length = 100)
  private String category;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 20)
  private ClauseSource source;

  @Column(name = "source_clause_id")
  private UUID sourceClauseId;

  @Column(name = "pack_id", length = 100)
  private String packId;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA-required no-arg constructor. */
  protected Clause() {}

  /**
   * Creates a new custom clause with the given required fields.
   *
   * @param title the clause title
   * @param slug the URL-friendly slug (must match ^[a-z][a-z0-9-]*$)
   * @param body the clause body as Tiptap JSON
   * @param category the clause category
   */
  public Clause(String title, String slug, Map<String, Object> body, String category) {
    this.title = Objects.requireNonNull(title, "title must not be null");
    this.slug = Objects.requireNonNull(slug, "slug must not be null");
    this.body = Objects.requireNonNull(body, "body must not be null");
    this.category = Objects.requireNonNull(category, "category must not be null");
    this.source = ClauseSource.CUSTOM;
    this.active = true;
    this.sortOrder = 0;
  }

  @PrePersist
  void onPrePersist() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onPreUpdate() {
    this.updatedAt = Instant.now();
  }

  /** Updates the mutable fields of this clause. */
  public void update(
      String title, String slug, String description, Map<String, Object> body, String category) {
    this.title = Objects.requireNonNull(title, "title must not be null");
    this.slug = Objects.requireNonNull(slug, "slug must not be null");
    this.description = description;
    this.body = Objects.requireNonNull(body, "body must not be null");
    this.category = Objects.requireNonNull(category, "category must not be null");
  }

  /** Deactivates this clause. */
  public void deactivate() {
    this.active = false;
  }

  /**
   * Creates a system clause (read-only, seeded by a pack).
   *
   * @param title the clause title
   * @param slug the URL-friendly slug
   * @param body the clause body as Tiptap JSON
   * @param category the clause category
   * @param description optional description
   * @param packId the originating pack ID
   * @param sortOrder display ordering
   * @return a new unpersisted Clause with source=SYSTEM
   */
  public static Clause createSystemClause(
      String title,
      String slug,
      Map<String, Object> body,
      String category,
      String description,
      String packId,
      int sortOrder) {
    var clause = new Clause(title, slug, body, category);
    clause.source = ClauseSource.SYSTEM;
    clause.description = description;
    clause.packId = packId;
    clause.sortOrder = sortOrder;
    return clause;
  }

  /**
   * Creates a cloned copy of the given source clause with a new slug.
   *
   * @param source the clause to clone from
   * @param newSlug the slug for the cloned clause
   * @return a new unpersisted Clause with source set to CLONED
   */
  public static Clause cloneFrom(Clause source, String newSlug) {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(newSlug, "newSlug must not be null");
    var clone = new Clause(source.getTitle(), newSlug, source.getBody(), source.getCategory());
    clone.description = source.getDescription();
    clone.source = ClauseSource.CLONED;
    clone.sourceClauseId = source.getId();
    clone.packId = source.getPackId();
    return clone;
  }

  public UUID getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getSlug() {
    return slug;
  }

  public String getDescription() {
    return description;
  }

  public Map<String, Object> getBody() {
    return body;
  }

  public String getLegacyBody() {
    return legacyBody;
  }

  public void setLegacyBody(String legacyBody) {
    this.legacyBody = legacyBody;
  }

  public String getCategory() {
    return category;
  }

  public ClauseSource getSource() {
    return source;
  }

  public UUID getSourceClauseId() {
    return sourceClauseId;
  }

  public String getPackId() {
    return packId;
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
}
