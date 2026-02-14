package io.b2mash.b2b.b2bstrawman.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Minimal counter entity for generating sequential invoice numbers. RLS handles tenant isolation —
 * no TenantAware interface needed. Used only by InvoiceNumberService (Slice 81B).
 */
@Entity
@Table(name = "invoice_counters")
public class InvoiceCounter {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "next_number", nullable = false)
  private int nextNumber = 1;

  protected InvoiceCounter() {}

  public InvoiceCounter(String tenantId) {
    this.tenantId = tenantId;
    this.nextNumber = 1;
  }

  public UUID getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public int getNextNumber() {
    return nextNumber;
  }

  public void setNextNumber(int nextNumber) {
    this.nextNumber = nextNumber;
  }
}
