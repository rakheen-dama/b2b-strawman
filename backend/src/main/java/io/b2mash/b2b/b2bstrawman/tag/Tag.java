package io.b2mash.b2b.b2bstrawman.tag;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tags")
public class Tag {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false, length = 50)
  private String name;

  @Column(name = "slug", nullable = false, length = 50)
  private String slug;

  @Column(name = "color", length = 7)
  private String color;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Tag() {}

  public Tag(String name, String color) {
    this.name = name;
    this.slug = generateSlug(name);
    this.color = color;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /**
   * Generates a slug from a human-readable name. Converts to lowercase, replaces spaces and hyphens
   * with underscores, strips non-alphanumeric characters (except underscores), and validates
   * against the pattern ^[a-z][a-z0-9_]*$.
   */
  public static String generateSlug(String name) {
    if (name == null || name.isBlank()) {
      throw new InvalidStateException("Invalid name", "Name must not be blank");
    }
    String slug = name.toLowerCase().replaceAll("[\\s-]+", "_").replaceAll("[^a-z0-9_]", "");

    if (slug.isEmpty() || !Character.isLetter(slug.charAt(0))) {
      throw new InvalidStateException(
          "Invalid slug", "Generated slug must start with a letter: " + slug);
    }

    if (!slug.matches("^[a-z][a-z0-9_]*$")) {
      throw new InvalidStateException("Invalid slug", "Slug must match ^[a-z][a-z0-9_]*$: " + slug);
    }

    return slug;
  }

  /**
   * Updates mutable metadata. The slug is intentionally immutable after creation — external
   * references (URLs, API calls, saved views) may depend on it.
   */
  public void updateMetadata(String name, String color) {
    this.name = name;
    this.color = color;
    this.updatedAt = Instant.now();
  }

  /** Sets the slug explicitly (used when resolving unique slugs with suffix). */
  public void setSlug(String slug) {
    this.slug = slug;
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

  public String getColor() {
    return color;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
