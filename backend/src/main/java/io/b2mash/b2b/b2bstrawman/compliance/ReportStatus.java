package io.b2mash.b2b.b2bstrawman.compliance;

/** Status lifecycle for compliance audit reports. Stored as VARCHAR in the database. */
public enum ReportStatus {
  DRAFT,
  PUBLISHED,
  ARCHIVED
}
