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
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * A rendered file of a take-on report (Phase 93 657D, §17.2.7, FD-15): the PDF or the CSV, rendered
 * from the immutable snapshot by the {@code take_on_drain} job, stored in S3 under {@link
 * TakeOnStorageKeys#reportKey} with its SHA-256 and size. One per (report, format), never
 * overwritten; a missing one is rendered again from the same snapshot.
 */
@Entity
@Immutable
@Table(name = "take_on_report_files")
public class TakeOnReportFile {

  /** The two files of a take-on report ({@code chk_take_on_report_file_format}). */
  public enum Format {
    PDF("pdf", "application/pdf"),
    CSV("csv", "text/csv;charset=UTF-8");

    private final String extension;
    private final String contentType;

    Format(String extension, String contentType) {
      this.extension = extension;
      this.contentType = contentType;
    }

    public String extension() {
      return extension;
    }

    public String contentType() {
      return contentType;
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "report_id", nullable = false, updatable = false)
  private UUID reportId;

  @Enumerated(EnumType.STRING)
  @Column(name = "format", nullable = false, length = 10, updatable = false)
  private Format format;

  @Column(name = "s3_key", nullable = false, length = 500, updatable = false)
  private String s3Key;

  @Column(name = "sha256", nullable = false, length = 64, updatable = false)
  private String sha256;

  @Column(name = "file_size", nullable = false, updatable = false)
  private long fileSize;

  @Column(name = "rendered_at", nullable = false, updatable = false)
  private Instant renderedAt;

  protected TakeOnReportFile() {}

  public TakeOnReportFile(
      UUID reportId,
      Format format,
      String s3Key,
      String sha256,
      long fileSize,
      Instant renderedAt) {
    this.reportId = Objects.requireNonNull(reportId, "reportId");
    this.format = Objects.requireNonNull(format, "format");
    this.s3Key = Objects.requireNonNull(s3Key, "s3Key");
    this.sha256 = Objects.requireNonNull(sha256, "sha256");
    this.fileSize = fileSize;
    this.renderedAt = Objects.requireNonNull(renderedAt, "renderedAt");
  }

  public UUID getId() {
    return id;
  }

  public UUID getReportId() {
    return reportId;
  }

  public Format getFormat() {
    return format;
  }

  public String getS3Key() {
    return s3Key;
  }

  public String getSha256() {
    return sha256;
  }

  public long getFileSize() {
    return fileSize;
  }

  public Instant getRenderedAt() {
    return renderedAt;
  }
}
