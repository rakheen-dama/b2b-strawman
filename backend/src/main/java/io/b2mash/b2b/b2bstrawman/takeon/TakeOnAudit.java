package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.audit.SafeAuditDetails;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The {@code takeon.*} audit events (§17.6.4: six from 653A, six from 655A), each with exactly its
 * documented params, built through {@link SafeAuditDetails} — ids, enums and counts only, never a
 * name, file name, header or value. Logged in the caller's transaction.
 */
@Component
public class TakeOnAudit {

  static final String BATCH = "take_on_batch";
  static final String SOURCE = "take_on_source";

  private final AuditService auditService;

  public TakeOnAudit(AuditService auditService) {
    this.auditService = auditService;
  }

  public void batchCreated(TakeOnBatch batch) {
    log(
        "takeon.batch.created",
        BATCH,
        batch.getId(),
        SafeAuditDetails.builder()
            .put("batch_id", batch.getId())
            .put("dataset", batch.getDataset())
            .build());
  }

  public void sourceUploaded(TakeOnSource source) {
    log(
        "takeon.source.uploaded",
        SOURCE,
        source.getId(),
        SafeAuditDetails.builder()
            .put("batch_id", source.getBatchId())
            .put("source_id", source.getId())
            .put("section", source.getSection())
            .put("file_format", source.getFileFormat())
            .put("file_size_kb", (source.getFileSize() + 1023) / 1024)
            .put("row_count", source.getRowCount())
            .put("malformed_count", source.getMalformedCount())
            .build());
  }

  public void sourceMapped(
      TakeOnSource source, int mappedFieldCount, int customFieldCount, int parkedColumnCount) {
    log(
        "takeon.source.mapped",
        SOURCE,
        source.getId(),
        SafeAuditDetails.builder()
            .put("batch_id", source.getBatchId())
            .put("source_id", source.getId())
            .put("mapped_field_count", mappedFieldCount)
            .put("custom_field_count", customFieldCount)
            .put("parked_column_count", parkedColumnCount)
            .build());
  }

  /** Counts only. {@code eq1_holds}/{@code eq2_holds} arrive with the trust equation (657B). */
  public void batchValidated(TakeOnBatch batch) {
    log(
        "takeon.batch.validated",
        BATCH,
        batch.getId(),
        SafeAuditDetails.builder()
            .put("batch_id", batch.getId())
            .put("dataset", batch.getDataset())
            .put("total_rows", batch.getTotalRows())
            .put("valid_rows", batch.getValidRows())
            .put("error_rows", batch.getErrorRows())
            .put("duplicate_rows", batch.getDuplicateRows())
            .put("block_count", batch.getBlockCount())
            .build());
  }

  /** {@code reasonCode}: {@code USER} (cancel) or {@code ABANDONED} (30-day purge). */
  public void batchCancelled(TakeOnBatch batch, int purgedSources, String reasonCode) {
    log(
        "takeon.batch.cancelled",
        BATCH,
        batch.getId(),
        SafeAuditDetails.builder()
            .put("batch_id", batch.getId())
            .put("dataset", batch.getDataset())
            .put("purged_sources", purgedSources)
            .token("reason_code", reasonCode)
            .build());
  }

  public void fileDownloaded(TakeOnSource source) {
    log(
        "takeon.file.downloaded",
        SOURCE,
        source.getId(),
        SafeAuditDetails.builder()
            .put("batch_id", source.getBatchId())
            .put("source_id", source.getId())
            .build());
  }

  // --- 655A: attestation, commit, portal access (§17.6.4) ---

  /** {@code rowCount}: the rows the attestation covers (valid rows that are not duplicates). */
  public void batchAttested(TakeOnBatch batch, TakeOnSignOff signOff, int rowCount) {
    log(
        "takeon.batch.attested",
        BATCH,
        batch.getId(),
        SafeAuditDetails.builder()
            .put("batch_id", batch.getId())
            .put("sign_off_id", signOff.getId())
            .token("statement_version", signOff.getStatementVersion())
            .put("row_count", rowCount)
            .build());
  }

  public void signOffRevoked(TakeOnBatch batch, TakeOnSignOff signOff) {
    log(
        "takeon.batch.sign_off_revoked",
        BATCH,
        batch.getId(),
        SafeAuditDetails.builder()
            .put("batch_id", batch.getId())
            .put("sign_off_id", signOff.getId())
            .put("kind", signOff.getKind())
            .put("reason_code", signOff.getRevokedReason())
            .build());
  }

  /** {@code sign_off_id} is omitted for datasets that need no sign-off (C-9). */
  public void commitRequested(TakeOnBatch batch, UUID signOffId) {
    var details =
        SafeAuditDetails.builder()
            .put("batch_id", batch.getId())
            .put("dataset", batch.getDataset());
    if (signOffId != null) {
      details.put("sign_off_id", signOffId);
    }
    log("takeon.batch.commit_requested", BATCH, batch.getId(), details.build());
  }

  public void committed(TakeOnBatch batch) {
    log(
        "takeon.batch.committed",
        BATCH,
        batch.getId(),
        SafeAuditDetails.builder()
            .put("batch_id", batch.getId())
            .put("dataset", batch.getDataset())
            .put("created_rows", batch.getCreatedRows())
            .put("skipped_rows", batch.getSkippedRows())
            .put("failed_rows", batch.getFailedRows())
            .build());
  }

  /** {@code reasonCode}: a structural token, e.g. {@code ATTESTATION_MISSING}. */
  public void commitFailed(TakeOnBatch batch, String reasonCode) {
    log(
        "takeon.batch.commit_failed",
        BATCH,
        batch.getId(),
        SafeAuditDetails.builder()
            .put("batch_id", batch.getId())
            .put("dataset", batch.getDataset())
            .token("reason_code", reasonCode)
            .build());
  }

  public void portalContactsCreated(TakeOnBatch batch, int created, int skipped) {
    log(
        "takeon.portal_contacts.created",
        BATCH,
        batch.getId(),
        SafeAuditDetails.builder()
            .put("batch_id", batch.getId())
            .put("created_count", created)
            .put("skipped_count", skipped)
            .build());
  }

  private void log(String eventType, String entityType, UUID entityId, Map<String, Object> d) {
    auditService.log(
        AuditEventBuilder.builder()
            .eventType(eventType)
            .entityType(entityType)
            .entityId(entityId)
            .details(d)
            .build());
  }
}
