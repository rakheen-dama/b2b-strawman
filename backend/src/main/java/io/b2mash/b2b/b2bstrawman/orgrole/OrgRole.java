package io.b2mash.b2b.b2bstrawman.orgrole;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "org_roles")
public class OrgRole {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "slug", nullable = false, length = 100, unique = true)
  private String slug;

  @Column(name = "description", length = 500)
  private String description;

  @Column(name = "is_system", nullable = false)
  private boolean isSystem;

  @ElementCollection
  @CollectionTable(name = "org_role_capabilities", joinColumns = @JoinColumn(name = "org_role_id"))
  @Column(name = "capability")
  private Set<String> capabilities = new HashSet<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected OrgRole() {}

  public OrgRole(String name, String slug, String description, boolean isSystem) {
    this.name = name;
    this.slug = slug;
    this.description = description;
    this.isSystem = isSystem;
  }

  @PrePersist
  void onPrePersist() {
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  @PreUpdate
  void onPreUpdate() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSlug() {
    return slug;
  }

  public void setSlug(String slug) {
    this.slug = slug;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public boolean isSystem() {
    return isSystem;
  }

  public Set<String> getCapabilities() {
    return capabilities;
  }

  public void setCapabilities(Set<String> capabilities) {
    this.capabilities = capabilities;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
