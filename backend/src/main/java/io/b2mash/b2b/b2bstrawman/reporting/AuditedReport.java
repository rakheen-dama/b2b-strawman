package io.b2mash.b2b.b2bstrawman.reporting;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventRecord;

/**
 * A report produced inside its read-only transaction, with the audit row it must leave (Phase 92
 * FU-12). The transactional entry points return the row instead of logging it, because a row joined
 * to their read-only transaction is never flushed; {@link ReportRunService} writes it after the
 * entry point has returned, in a transaction of its own. Internal: never an HTTP response.
 */
public record AuditedReport<T>(T result, AuditEventRecord audit) {}
