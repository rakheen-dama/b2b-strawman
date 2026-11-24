package io.b2mash.b2b.b2bstrawman.takeon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One data row of a take-on source (§17.2.3). {@code raw_values} holds the cells exactly as read;
 * {@code mapped_values} the typed, normalised target values after a dry run. A row matched to an
 * existing record at validation stays {@code VALIDATED} with {@code duplicate_of_id} set.
 */
@Entity
@Table(name = "take_on_rows")
public class TakeOnRow {

  private static final int FAILURE_REASON_MAX = 1000;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "batch_id", nullable = false, updatable = false)
  private UUID batchId;

  @Column(name = "source_id", nullable = false, updatable = false)
  private UUID sourceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "dataset", nullable = false, length = 30, updatable = false)
  private TakeOnDataset dataset;

  @Enumerated(EnumType.STRING)
  @Column(name = "section", nullable = false, length = 30, updatable = false)
  private TakeOnSection section;

  @Column(name = "row_number", nullable = false, updatable = false)
  private int rowNumber;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_values", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> rawValues;

  /** Headers whose XLSX cell was a number: their text is point-decimal (null for CSV). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "numeric_cells", columnDefinition = "jsonb", updatable = false)
  private List<String> numericCells;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "mapped_values", columnDefinition = "jsonb")
  private Map<String, Object> mappedValues;

  @Column(name = "source_ref", length = 200)
  private String sourceRef;

  @Column(name = "dedup_key", length = 300)
  private String dedupKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private TakeOnRowStatus status;

  /** {@code [{code, field, message}]}; any entry means {@code ERROR}. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "errors", nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> errors;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "warnings", nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> warnings;

  /** The error is a statutory hard block (CHECK {@code chk_take_on_row_block}: only on ERROR). */
  @Column(name = "block", nullable = false)
  private boolean block;

  @Column(name = "duplicate_of_id")
  private UUID duplicateOfId;

  @Column(name = "failure_reason", length = 1000)
  private String failureReason;

  @Column(name = "result_entity_type", length = 30)
  private String resultEntityType;

  @Column(name = "result_entity_id")
  private UUID resultEntityId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "result_refs", columnDefinition = "jsonb")
  private Map<String, Object> resultRefs;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TakeOnRow() {}

  public TakeOnRow(
      UUID batchId,
      UUID sourceId,
      TakeOnDataset dataset,
      TakeOnSection section,
      int rowNumber,
      Map<String, Object> rawValues) {
    this(batchId, sourceId, dataset, section, rowNumber, rawValues, Set.of());
  }

  public TakeOnRow(
      UUID batchId,
      UUID sourceId,
      TakeOnDataset dataset,
      TakeOnSection section,
      int rowNumber,
      Map<String, Object> rawValues,
      Set<String> numericHeaders) {
    this.numericCells =
        numericHeaders == null || numericHeaders.isEmpty()
            ? null
            : numericHeaders.stream().sorted().toList();
    this.batchId = Objects.requireNonNull(batchId, "batchId");
    this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
    this.dataset = Objects.requireNonNull(dataset, "dataset");
    this.section = Objects.requireNonNull(section, "section");
    this.rowNumber = rowNumber;
    this.rawValues = new LinkedHashMap<>(Objects.requireNonNull(rawValues, "rawValues"));
    this.status = TakeOnRowStatus.PENDING;
    this.errors = new ArrayList<>();
    this.warnings = new ArrayList<>();
    this.block = false;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  /** Re-validation starts from scratch. A committed (terminal) row is never reset. */
  public void resetForValidation() {
    requireNotTerminal();
    this.mappedValues = null;
    this.sourceRef = null;
    this.dedupKey = null;
    this.errors = new ArrayList<>();
    this.warnings = new ArrayList<>();
    this.block = false;
    this.duplicateOfId = null;
    this.status = TakeOnRowStatus.PENDING;
    this.updatedAt = Instant.now();
  }

  /**
   * Records the dry-run result: {@code ERROR} iff any error, else {@code VALIDATED}. A block is
   * always an error (mirrors CHECK {@code chk_take_on_row_block}).
   */
  public void applyValidation(
      Map<String, Object> mapped,
      String sourceRef,
      String dedupKey,
      List<Map<String, Object>> errors,
      List<Map<String, Object>> warnings,
      boolean block,
      UUID duplicateOfId) {
    requireNotTerminal();
    List<Map<String, Object>> errorList = errors == null ? List.of() : errors;
    if (block && errorList.isEmpty()) {
      throw new IllegalStateException("A blocked take-on row must carry the error that blocks it.");
    }
    this.mappedValues = mapped;
    this.sourceRef = sourceRef;
    this.dedupKey = dedupKey;
    this.errors = new ArrayList<>(errorList);
    this.warnings = new ArrayList<>(warnings == null ? List.of() : warnings);
    this.block = block;
    this.duplicateOfId = duplicateOfId;
    this.status = errorList.isEmpty() ? TakeOnRowStatus.VALIDATED : TakeOnRowStatus.ERROR;
    this.updatedAt = Instant.now();
  }

  /** Adds one error after the row check (the batch-level duplicate check); the row is ERROR. */
  public void addError(String code, String field, String message) {
    requireNotTerminal();
    var error = new LinkedHashMap<String, Object>();
    error.put("code", code);
    error.put("field", field);
    error.put("message", message);
    var updated = new ArrayList<>(this.errors);
    updated.add(error);
    this.errors = updated;
    this.status = TakeOnRowStatus.ERROR;
    this.updatedAt = Instant.now();
  }

  // --- Commit transitions (655A): only from VALIDATED, each terminal (never re-validated) ---

  /**
   * Adds one warning while the row commits (656A: the matter's lead could not be applied), before
   * the terminal transition. The status does not change; the row stays committable.
   */
  public void addCommitWarning(String code, String field, String message) {
    requireCommittable();
    var warning = new LinkedHashMap<String, Object>();
    warning.put("code", code);
    warning.put("field", field);
    warning.put("message", message);
    var updated = new ArrayList<>(this.warnings);
    updated.add(warning);
    this.warnings = updated;
    this.updatedAt = Instant.now();
  }

  /** The commit created the record ({@code CHECK chk_take_on_row_result}: type and id). */
  public void markCreated(String entityType, UUID entityId, Map<String, Object> refs) {
    requireCommittable();
    this.resultEntityType = Objects.requireNonNull(entityType, "entityType");
    this.resultEntityId = Objects.requireNonNull(entityId, "entityId");
    this.resultRefs = refs == null || refs.isEmpty() ? null : new LinkedHashMap<>(refs);
    this.status = TakeOnRowStatus.CREATED;
    this.updatedAt = Instant.now();
  }

  /**
   * The record already exists: nothing is created; the row points at the existing record so later
   * datasets resolve its previous-system id to it.
   */
  public void markSkippedDuplicate(String entityType, UUID existingId) {
    requireCommittable();
    this.resultEntityType = Objects.requireNonNull(entityType, "entityType");
    this.resultEntityId = Objects.requireNonNull(existingId, "existingId");
    this.status = TakeOnRowStatus.SKIPPED_DUPLICATE;
    this.updatedAt = Instant.now();
  }

  /**
   * The commit of this row failed; the others go on. {@code reason} is user-facing X3-style text
   * (never an exception message), kept to the column's 1 000 characters.
   */
  public void markFailed(String reason) {
    requireCommittable();
    Objects.requireNonNull(reason, "reason");
    this.failureReason =
        reason.length() > FAILURE_REASON_MAX ? reason.substring(0, FAILURE_REASON_MAX) : reason;
    this.status = TakeOnRowStatus.FAILED;
    this.updatedAt = Instant.now();
  }

  private void requireCommittable() {
    if (status != TakeOnRowStatus.VALIDATED) {
      throw new IllegalStateException("Only a valid take-on row can be committed.");
    }
  }

  private void requireNotTerminal() {
    if (status.isTerminal()) {
      throw new IllegalStateException("A committed take-on row cannot be validated again.");
    }
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getBatchId() {
    return batchId;
  }

  public UUID getSourceId() {
    return sourceId;
  }

  public TakeOnDataset getDataset() {
    return dataset;
  }

  public TakeOnSection getSection() {
    return section;
  }

  public int getRowNumber() {
    return rowNumber;
  }

  public Map<String, Object> getRawValues() {
    return rawValues;
  }

  /** Whether the cell under {@code header} was an XLSX number (point-decimal text). */
  public boolean isNumericCell(String header) {
    return numericCells != null && header != null && numericCells.contains(header);
  }

  public Map<String, Object> getMappedValues() {
    return mappedValues;
  }

  public String getSourceRef() {
    return sourceRef;
  }

  public String getDedupKey() {
    return dedupKey;
  }

  public TakeOnRowStatus getStatus() {
    return status;
  }

  public List<Map<String, Object>> getErrors() {
    return errors;
  }

  public List<Map<String, Object>> getWarnings() {
    return warnings;
  }

  public boolean isBlock() {
    return block;
  }

  public UUID getDuplicateOfId() {
    return duplicateOfId;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public String getResultEntityType() {
    return resultEntityType;
  }

  public UUID getResultEntityId() {
    return resultEntityId;
  }

  public Map<String, Object> getResultRefs() {
    return resultRefs;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
