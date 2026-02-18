package io.b2mash.b2b.b2bstrawman.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Singleton counter entity for generating sequential invoice numbers. Each dedicated tenant schema
 * has exactly one row. Used only by InvoiceNumberService (Slice 81B).
 */
@Entity
@Table(name = "invoice_counters")
public class InvoiceCounter {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "next_number", nullable = false)
  private int nextNumber = 1;

  @Column(name = "singleton", nullable = false)
  private boolean singleton = true;

  protected InvoiceCounter() {}

  public InvoiceCounter(int nextNumber) {
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
