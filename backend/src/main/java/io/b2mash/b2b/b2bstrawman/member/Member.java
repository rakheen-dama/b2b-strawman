package io.b2mash.b2b.b2bstrawman.member;

import io.b2mash.b2b.b2bstrawman.orgrole.OrgRole;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "members")
public class Member {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "clerk_user_id", nullable = false, length = 255)
  private String clerkUserId;

  @Column(name = "email", nullable = false, length = 255)
  private String email;

  @Column(name = "name", length = 255)
  private String name;

  @Column(name = "avatar_url", length = 1000)
  private String avatarUrl;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "org_role_id", nullable = false)
  private OrgRole orgRoleEntity;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @ElementCollection
  @CollectionTable(
      name = "member_capability_overrides",
      joinColumns = @JoinColumn(name = "member_id"))
  @Column(name = "override_value")
  private Set<String> capabilityOverrides = new HashSet<>();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Member() {}

  public Member(String clerkUserId, String email, String name, String avatarUrl, OrgRole orgRole) {
    this.clerkUserId = clerkUserId;
    this.email = email;
    this.name = name;
    this.avatarUrl = avatarUrl;
    this.orgRoleEntity = orgRole;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getClerkUserId() {
    return clerkUserId;
  }

  public String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  /** Returns the role slug string for backward compatibility. */
  public String getOrgRole() {
    return orgRoleEntity != null ? orgRoleEntity.getSlug() : null;
  }

  public OrgRole getOrgRoleEntity() {
    return orgRoleEntity;
  }

  public void setOrgRoleEntity(OrgRole orgRole) {
    this.orgRoleEntity = orgRole;
  }

  public UUID getOrgRoleId() {
    return orgRoleEntity != null ? orgRoleEntity.getId() : null;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Set<String> getCapabilityOverrides() {
    return capabilityOverrides;
  }

  public void setCapabilityOverrides(Set<String> capabilityOverrides) {
    this.capabilityOverrides = new HashSet<>(capabilityOverrides);
  }

  public void updateFrom(String email, String name, String avatarUrl, OrgRole orgRole) {
    if (email != null) this.email = email;
    if (name != null) this.name = name;
    if (avatarUrl != null) this.avatarUrl = avatarUrl;
    if (orgRole != null) this.orgRoleEntity = orgRole;
    this.updatedAt = Instant.now();
  }
}
