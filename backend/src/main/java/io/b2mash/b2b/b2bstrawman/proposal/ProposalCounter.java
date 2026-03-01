package io.b2mash.b2b.b2bstrawman.proposal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Singleton counter entity for generating sequential proposal numbers. Each dedicated tenant schema
 * has exactly one row. Used by ProposalNumberService (ADR-128).
 */
@Entity
@Table(name = "proposal_counters")
public class ProposalCounter {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "next_number", nullable = false)
  private int nextNumber = 1;

  @Column(name = "singleton", nullable = false)
  private boolean singleton = true;

  protected ProposalCounter() {}

  public ProposalCounter(int nextNumber) {
    this.nextNumber = nextNumber;
  }

  public UUID getId() {
    return id;
  }

  public int getNextNumber() {
    return nextNumber;
  }

  public void setNextNumber(int nextNumber) {
    this.nextNumber = nextNumber;
  }
}
