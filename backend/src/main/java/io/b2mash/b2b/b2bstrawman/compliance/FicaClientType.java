package io.b2mash.b2b.b2bstrawman.compliance;

import io.b2mash.b2b.b2bstrawman.customer.CustomerType;

/**
 * FICA's view of the client (Phase 93 655A, §17.2.5); {@code customers.customer_type} is derived
 * from it. Stored in {@code client_fica_attestations.fica_client_type} (CHECK {@code
 * chk_fica_att_client_type}).
 */
public enum FicaClientType {
  NATURAL_PERSON,
  COMPANY,
  CLOSE_CORPORATION,
  TRUST,
  PARTNERSHIP,
  OTHER;

  /** The client type Kazi records (C-20): a natural person, a trust, or a company. */
  public CustomerType customerType() {
    return switch (this) {
      case NATURAL_PERSON -> CustomerType.INDIVIDUAL;
      case TRUST -> CustomerType.TRUST;
      case COMPANY, CLOSE_CORPORATION, PARTNERSHIP, OTHER -> CustomerType.COMPANY;
    };
  }
}
