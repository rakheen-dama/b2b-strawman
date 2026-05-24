package io.b2mash.b2b.b2bstrawman.compliance;

/** Categories for compliance audit findings. Stored as VARCHAR in the database. */
public enum FindingCategory {
  FICA_CDD,
  POPIA,
  TRUST_ACCOUNTING,
  PRESCRIPTION,
  RECORD_RETENTION
}
