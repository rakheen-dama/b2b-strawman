package io.b2mash.b2b.b2bstrawman.compliance;

/**
 * A client FICA attestation is {@code ACTIVE} until a re-attestation supersedes it (CHECK {@code
 * chk_fica_att_status}; one ACTIVE per client, {@code uq_fica_att_active_customer}).
 */
public enum FicaAttestationStatus {
  ACTIVE,
  SUPERSEDED
}
