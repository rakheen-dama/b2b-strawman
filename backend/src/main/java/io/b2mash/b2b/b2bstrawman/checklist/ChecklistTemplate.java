package io.b2mash.b2b.b2bstrawman.checklist;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "checklist_templates")
public class ChecklistTemplate {

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

  @Column(name = "customer_type", nullable = false, length = 20)
  private String customerType;

  @Column(name = "source", nullable = false, length = 20)
  private String source;

  @Column(name = "pack_id", length = 100)
  private String packId;

  @Column(name = "pack_template_key", length = 100)
  private String packTemplateKey;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "auto_instantiate", nullable = false)
  private boolean autoInstantiate;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ChecklistTemplate() {}

  public ChecklistTemplate(
      String name,
      String description,
      String slug,
      String customerType,
      String source,
      boolean autoInstantiate) {
    this.name = name;
    this.description = description;
    this.slug = slug;
    this.customerType = customerType;
    this.source = source;
    this.active = true;
    this.autoInstantiate = autoInstantiate;
    this.sortOrder = 0;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

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

  public void update(String name, String description, boolean autoInstantiate) {
    this.name = name;
    this.description = description;
    this.autoInstantiate = autoInstantiate;
    this.updatedAt = Instant.now();
  }

  public void deactivate() {
    this.active = false;
    this.updatedAt = Instant.now();
  }

  // Getters
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

  public String getCustomerType() {
    return customerType;
  }

  public String getSource() {
    return source;
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

  public boolean isAutoInstantiate() {
    return autoInstantiate;
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

  // Setters for pack seeder
  public void setPackId(String packId) {
    this.packId = packId;
  }

  public void setPackTemplateKey(String packTemplateKey) {
    this.packTemplateKey = packTemplateKey;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public void setActive(boolean active) {
    this.active = active;
    this.updatedAt = Instant.now();
  }
}
