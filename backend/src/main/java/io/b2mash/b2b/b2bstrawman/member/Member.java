package io.b2mash.b2b.b2bstrawman.member;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAware;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAwareEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "members")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class Member implements TenantAware {

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

  @Column(name = "org_role", nullable = false, length = 50)
  private String orgRole;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Member() {}

  public Member(String clerkUserId, String email, String name, String avatarUrl, String orgRole) {
    this.clerkUserId = clerkUserId;
    this.email = email;
    this.name = name;
    this.avatarUrl = avatarUrl;
    this.orgRole = orgRole;
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

  public String getOrgRole() {
    return orgRole;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void updateFrom(String email, String name, String avatarUrl, String orgRole) {
    this.email = email;
    this.name = name;
    this.avatarUrl = avatarUrl;
    this.orgRole = orgRole;
    this.updatedAt = Instant.now();
  }
}
