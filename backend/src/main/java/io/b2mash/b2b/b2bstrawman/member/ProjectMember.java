package io.b2mash.b2b.b2bstrawman.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_members")
public class ProjectMember {

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

  public Instant getCreatedAt() {
    return createdAt;
  }
}
