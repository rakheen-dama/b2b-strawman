package io.b2mash.b2b.b2bstrawman.compliance;

/** Severity levels for compliance audit findings. Stored as VARCHAR in the database. */
public enum FindingSeverity {
  CRITICAL,
  HIGH,
  MEDIUM,
  LOW,
  INFO
}
