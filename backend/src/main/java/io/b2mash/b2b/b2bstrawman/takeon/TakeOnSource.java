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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One uploaded file (and chosen sheet) inside a take-on batch (§17.2.2). Single-file datasets have
 * one source (section {@code MAIN}); a trust position has one per section it uses. The raw bytes
 * live in S3 under {@link #getS3Key()} until the batch is cancelled or purged (the key is cleared
 * once the object is deleted).
 */
@Entity
@Table(name = "take_on_sources")
public class TakeOnSource {

  /** Stored in {@code take_on_sources.file_format} (CHECK {@code chk_take_on_source_format}). */
  public enum FileFormat {
    CSV,
    XLSX
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "batch_id", nullable = false, updatable = false)
  private UUID batchId;

  @Enumerated(EnumType.STRING)
  @Column(name = "section", nullable = false, length = 30)
  private TakeOnSection section;

  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Enumerated(EnumType.STRING)
  @Column(name = "file_format", nullable = false, length = 10)
  private FileFormat fileFormat;

  @Column(name = "file_size", nullable = false)
  private long fileSize;

  @Column(name = "file_sha256", nullable = false, length = 64)
  private String fileSha256;

  @Column(name = "s3_key", length = 500)
  private String s3Key;

  /** {@code [{"name": "…", "hidden": false}]} in workbook order (XLSX; CSV has one entry). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "sheet_names", columnDefinition = "jsonb")
  private List<Map<String, Object>> sheetNames;

  @Column(name = "sheet_name", length = 100)
  private String sheetName;

  @Column(name = "header_row", nullable = false)
  private int headerRow;

  /** {@code {dateFormat, decimalSeparator, delimiter, encoding}} (§17.3.1). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "parse_options", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> parseOptions;

  /** {@code {targets, customFields, parked, defaults}} — stored verbatim (the preset seed). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "column_mapping", columnDefinition = "jsonb")
  private Map<String, Object> columnMapping;

  @Column(name = "row_count", nullable = false)
  private int rowCount;

  @Column(name = "malformed_count", nullable = false)
  private int malformedCount;

  @Column(name = "blank_skipped", nullable = false)
  private int blankSkipped;

  @Column(name = "uploaded_by", nullable = false, updatable = false)
  private UUID uploadedBy;

  @Column(name = "uploaded_at", nullable = false, updatable = false)
  private Instant uploadedAt;

  @Column(name = "mapped_at")
  private Instant mappedAt;

  @Column(name = "purged_at")
  private Instant purgedAt;

  protected TakeOnSource() {}

  public TakeOnSource(
      UUID batchId,
      TakeOnSection section,
      String fileName,
      String contentType,
      FileFormat fileFormat,
      long fileSize,
      String fileSha256,
      List<Map<String, Object>> sheetNames,
      String sheetName,
      int headerRow,
      Map<String, Object> parseOptions,
      UUID uploadedBy) {
    this.batchId = Objects.requireNonNull(batchId, "batchId");
    this.section = Objects.requireNonNull(section, "section");
    this.fileName = Objects.requireNonNull(fileName, "fileName");
    this.contentType = Objects.requireNonNull(contentType, "contentType");
    this.fileFormat = Objects.requireNonNull(fileFormat, "fileFormat");
    this.fileSize = fileSize;
    this.fileSha256 = Objects.requireNonNull(fileSha256, "fileSha256");
    this.sheetNames = sheetNames;
    this.sheetName = sheetName;
    this.headerRow = headerRow;
    this.parseOptions = Objects.requireNonNull(parseOptions, "parseOptions");
    this.uploadedBy = Objects.requireNonNull(uploadedBy, "uploadedBy");
    this.uploadedAt = Instant.now();
  }

  /** The S3 object key, set once the raw bytes are stored (after a successful parse). */
  public void attachStorageKey(String s3Key) {
    this.s3Key = Objects.requireNonNull(s3Key, "s3Key");
  }

  /** Records what the reader found in the chosen sheet. */
  public void recordRead(int rowCount, int malformedCount, int blankSkipped) {
    this.rowCount = rowCount;
    this.malformedCount = malformedCount;
    this.blankSkipped = blankSkipped;
  }

  /** Picks another sheet or header row: the headers change, so the mapping is cleared. */
  public void selectSheet(String sheetName, int headerRow) {
    this.sheetName = sheetName;
    this.headerRow = headerRow;
    this.columnMapping = null;
    this.mappedAt = null;
  }

  /** Stores the column mapping verbatim and the parse options used to type the values. */
  public void applyMapping(Map<String, Object> mapping, Map<String, Object> parseOptions) {
    this.columnMapping = Objects.requireNonNull(mapping, "mapping");
    this.parseOptions = Objects.requireNonNull(parseOptions, "parseOptions");
    this.mappedAt = Instant.now();
  }

  public boolean isMapped() {
    return columnMapping != null;
  }

  /**
   * Marks the file purged (cancel or abandoned purge) and returns its S3 key so the caller can
   * delete the object after commit; null if already purged. The key stays recorded until the delete
   * succeeds ({@link TakeOnStorageCleanup}), so a failed delete is retried; every read treats a
   * purged source as gone whatever its key.
   */
  public String purge() {
    if (purgedAt != null) {
      return null;
    }
    this.purgedAt = Instant.now();
    return this.s3Key;
  }

  public boolean isPurged() {
    return purgedAt != null;
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getBatchId() {
    return batchId;
  }

  public TakeOnSection getSection() {
    return section;
  }

  public String getFileName() {
    return fileName;
  }

  public String getContentType() {
    return contentType;
  }

  public FileFormat getFileFormat() {
    return fileFormat;
  }

  public long getFileSize() {
    return fileSize;
  }

  public String getFileSha256() {
    return fileSha256;
  }

  public String getS3Key() {
    return s3Key;
  }

  public List<Map<String, Object>> getSheetNames() {
    return sheetNames;
  }

  public String getSheetName() {
    return sheetName;
  }

  public int getHeaderRow() {
    return headerRow;
  }

  public Map<String, Object> getParseOptions() {
    return parseOptions;
  }

  public Map<String, Object> getColumnMapping() {
    return columnMapping;
  }

  public int getRowCount() {
    return rowCount;
  }

  public int getMalformedCount() {
    return malformedCount;
  }

  public int getBlankSkipped() {
    return blankSkipped;
  }

  public UUID getUploadedBy() {
    return uploadedBy;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public Instant getMappedAt() {
    return mappedAt;
  }

  public Instant getPurgedAt() {
    return purgedAt;
  }
}
