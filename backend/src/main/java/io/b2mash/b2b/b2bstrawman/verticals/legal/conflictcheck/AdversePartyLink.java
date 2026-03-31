package io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "adverse_party_links",
    uniqueConstraints = @UniqueConstraint(columnNames = {"adverse_party_id", "project_id"}))
public class AdversePartyLink {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "adverse_party_id", nullable = false)
  private UUID adversePartyId;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "relationship", nullable = false, length = 30)
  private String relationship;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AdversePartyLink() {}

  public AdversePartyLink(
      UUID adversePartyId,
      UUID projectId,
      UUID customerId,
      String relationship,
      String description) {
    this.adversePartyId = adversePartyId;
    this.projectId = projectId;
    this.customerId = customerId;
    this.relationship = relationship;
    this.description = description;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getAdversePartyId() {
    return adversePartyId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getRelationship() {
    return relationship;
  }

  public String getDescription() {
    return description;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
