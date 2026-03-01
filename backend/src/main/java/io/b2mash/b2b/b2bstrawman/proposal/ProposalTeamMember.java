package io.b2mash.b2b.b2bstrawman.proposal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/** A team member assigned to a proposal (transferred to project on acceptance). */
@Entity
@Table(name = "proposal_team_members")
public class ProposalTeamMember {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "proposal_id", nullable = false)
  private UUID proposalId;

  @Column(name = "member_id", nullable = false)
  private UUID memberId;

  @Column(name = "role", length = 100)
  private String role;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  /** JPA-required no-arg constructor. */
  protected ProposalTeamMember() {}

  public ProposalTeamMember(UUID proposalId, UUID memberId, String role, int sortOrder) {
    this.proposalId = Objects.requireNonNull(proposalId, "proposalId must not be null");
    this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
    this.role = role;
    this.sortOrder = sortOrder;
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getProposalId() {
    return proposalId;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public String getRole() {
    return role;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  // --- Setters ---

  public void setRole(String role) {
    this.role = role;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }
}
