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
@Table(name = "project_members")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class ProjectMember implements TenantAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "member_id", nullable = false)
  private UUID memberId;

  @Column(name = "project_role", nullable = false, length = 50)
  private String projectRole;

  @Column(name = "added_by")
  private UUID addedBy;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ProjectMember() {}

  public ProjectMember(UUID projectId, UUID memberId, String projectRole, UUID addedBy) {
    this.projectId = projectId;
    this.memberId = memberId;
    this.projectRole = projectRole;
    this.addedBy = addedBy;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public String getProjectRole() {
    return projectRole;
  }

  public void setProjectRole(String projectRole) {
    this.projectRole = projectRole;
  }

  public UUID getAddedBy() {
    return addedBy;
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
}
