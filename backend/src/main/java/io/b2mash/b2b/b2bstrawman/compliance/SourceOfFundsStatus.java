package io.b2mash.b2b.b2bstrawman.compliance;

/**
 * Source-of-funds work where the firm's RMCP requires it (CHECK {@code chk_fica_att_sof}); blank in
 * a take-on file is {@code NOT_REQUIRED}.
 */
public enum SourceOfFundsStatus {
  DONE,
  NOT_REQUIRED,
  OUTSTANDING
}
