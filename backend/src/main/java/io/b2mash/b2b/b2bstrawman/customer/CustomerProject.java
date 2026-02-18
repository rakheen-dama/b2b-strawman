package io.b2mash.b2b.b2bstrawman.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_projects")
public class CustomerProject {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "linked_by")
  private UUID linkedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected CustomerProject() {}

  public CustomerProject(UUID customerId, UUID projectId, UUID linkedBy) {
    this.customerId = customerId;
    this.projectId = projectId;
    this.linkedBy = linkedBy;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getLinkedBy() {
    return linkedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
