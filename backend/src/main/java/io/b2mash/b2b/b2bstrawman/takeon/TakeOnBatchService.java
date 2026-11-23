package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.takeon.reader.CsvTakeOnReader;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnFileReader;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnParseOptions;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnSheet;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Take-on batch writes (Phase 93 653A, §17.3.1–§17.3.2, §17.3.14): create with the first file, add
 * and remove files, pick the sheet, map columns, set the cutover date, the dry-run facade, cancel
 * and the shared purge. Every write loads the batch {@code FOR UPDATE} (404), then checks the
 * dataset capability through {@link TakeOnAccessPolicy} (403), then the state (409). Reads live in
 * {@link TakeOnBatchQueryService}. Nothing here creates a client, matter, fee note or trust row.
 */
@Service
public class TakeOnBatchService {

  // --- Wire records (pinned by TakeOnWireContractTest; never ship an unread field — P-11) ---

  public record TakeOnFieldResponse(
      String key,
      String label,
      String type,
      boolean required,
      Integer maxLength,
      List<String> allowedValues) {}

  public record TakeOnSectionResponse(
      TakeOnSection section, String label, List<TakeOnFieldResponse> fields) {}

  public record TakeOnDatasetResponse(
      TakeOnDataset dataset,
      String label,
      boolean requiresCutoverDate,
      boolean requiresTrustAccount,
      String customFieldEntityType,
      List<TakeOnSectionResponse> sections,
      long completedBatches) {}

  public record TakeOnParseOptionsResponse(
      String dateFormat, String decimalSeparator, String delimiter, String encoding) {}

  public record TakeOnMappingResponse(
      Map<String, String> targets,
      Map<String, String> customFields,
      List<String> parked,
      Map<String, String> defaults) {}

  public record TakeOnSheetNameResponse(String name, boolean hidden) {}

  public record TakeOnSourceResponse(
      UUID id,
      TakeOnSection section,
      String fileName,
      String fileFormat,
      long fileSize,
      List<TakeOnSheetNameResponse> sheetNames,
      String sheetName,
      int headerRow,
      TakeOnParseOptionsResponse parseOptions,
      TakeOnMappingResponse mapping,
      boolean mapped,
      int rowCount,
      int malformedCount,
      int blankSkipped,
      boolean purged,
      Instant uploadedAt) {}

  /** created/skipped/failed arrive with the commit (655A). */
  public record TakeOnCountsResponse(int total, int valid, int error, int duplicate, int blocks) {}

  public record TakeOnBatchResponse(
      UUID id,
      TakeOnDataset dataset,
      TakeOnBatchStatus status,
      String name,
      LocalDate cutoverDate,
      UUID trustAccountId,
      List<TakeOnSourceResponse> sources,
      TakeOnCountsResponse counts,
      String validationHash,
      Instant validatedAt,
      List<String> allowedActions,
      Instant createdAt,
      Instant cancelledAt) {}

  public record TakeOnBatchSummaryResponse(
      UUID id,
      TakeOnDataset dataset,
      TakeOnBatchStatus status,
      String name,
      LocalDate cutoverDate,
      int totalRows,
      int validRows,
      int errorRows,
      String createdByName,
      Instant createdAt) {}

  public record TakeOnUploadResponse(
      TakeOnBatchResponse batch, UUID sourceId, List<String> headers, List<String> warnings) {}

  public record TakeOnPreviewRow(int rowNumber, List<String> cells) {}

  public record TakeOnPreviewResponse(
      UUID sourceId,
      List<String> headers,
      List<TakeOnPreviewRow> rows,
      TakeOnParseOptionsResponse detectedOptions,
      List<TakeOnSheetNameResponse> sheets,
      String sheetName,
      int headerRow,
      Map<String, String> suggestedTargets,
      List<String> warnings) {}

  public record TakeOnRowMessage(String code, String field, String message) {}

  public record TakeOnRowResponse(
      UUID id,
      TakeOnSection section,
      int rowNumber,
      String sourceRef,
      TakeOnRowStatus status,
      boolean block,
      Map<String, Object> values,
      List<TakeOnRowMessage> errors,
      List<TakeOnRowMessage> warnings,
      UUID duplicateOfId) {}

  public record TakeOnMappingRequest(
      Map<String, String> targets,
      Map<String, String> customFields,
      List<String> parked,
      Map<String, String> defaults,
      TakeOnParseOptionsResponse parseOptions) {}

  public record TakeOnSheetRequest(String sheetName, @Min(1) @Max(50) int headerRow) {}

  public record TakeOnPositionRequest(@NotNull LocalDate cutoverDate) {}

  /** Downloads (not JSON): the controller writes them as attachments. */
  public record TakeOnTemplateFile(byte[] bytes, String fileName) {}

  public record TakeOnSourceFile(byte[] bytes, String contentType, String fileName) {}

  // --- Service ---

  static final int MAX_NAME_LENGTH = 200;

  private final TakeOnBatchRepository batches;
  private final TakeOnSourceRepository sources;
  private final TakeOnRowRepository rows;
  private final TakeOnSignOffRepository signOffs;
  private final TakeOnAccessPolicy accessPolicy;
  private final TakeOnSourceFiles sourceFiles;
  private final TakeOnMappingValidator mappingValidator;
  private final TakeOnValidationService validationService;
  private final TakeOnBatchViews views;
  private final TakeOnAudit audit;
  private final StorageService storageService;
  private final TrustAccountRepository trustAccounts;
  private final TakeOnStorageCleanup storageCleanup;

  public TakeOnBatchService(
      TakeOnBatchRepository batches,
      TakeOnSourceRepository sources,
      TakeOnRowRepository rows,
      TakeOnSignOffRepository signOffs,
      TakeOnAccessPolicy accessPolicy,
      TakeOnSourceFiles sourceFiles,
      TakeOnMappingValidator mappingValidator,
      TakeOnValidationService validationService,
      TakeOnBatchViews views,
      TakeOnAudit audit,
      StorageService storageService,
      TrustAccountRepository trustAccounts,
      TakeOnStorageCleanup storageCleanup) {
    this.batches = batches;
    this.sources = sources;
    this.rows = rows;
    this.signOffs = signOffs;
    this.accessPolicy = accessPolicy;
    this.sourceFiles = sourceFiles;
    this.mappingValidator = mappingValidator;
    this.validationService = validationService;
    this.views = views;
    this.audit = audit;
    this.storageService = storageService;
    this.trustAccounts = trustAccounts;
    this.storageCleanup = storageCleanup;
  }

  /** {@code POST /api/take-on/batches}: a new batch with its first file (§17.3.1). */
  @Transactional
  public TakeOnUploadResponse createBatch(
      MultipartFile file,
      TakeOnDataset dataset,
      TakeOnSection section,
      UUID trustAccountId,
      String name) {
    accessPolicy.requireWrite(dataset); // before the file is read (C-23)
    TakeOnSection resolved = resolveSection(dataset, section);
    requireTrustAccount(dataset, trustAccountId);
    String batchName = batchName(name);
    var upload = readUpload(file);
    UUID memberId = RequestScopes.requireMemberId();

    var batch = batches.save(new TakeOnBatch(dataset, trustAccountId, batchName, memberId));
    var source = storeSource(batch, resolved, file.getOriginalFilename(), upload, memberId);
    audit.batchCreated(batch);
    audit.sourceUploaded(source);
    return new TakeOnUploadResponse(
        views.batch(batch), source.getId(), upload.sheet().headers(), upload.sheet().warnings());
  }

  /** {@code POST …/sources}: another trust section, or a replacement file after a removal. */
  @Transactional
  public TakeOnUploadResponse addSource(UUID batchId, MultipartFile file, TakeOnSection section) {
    var batch = lockForWrite(batchId);
    requireOpen(batch, "Only an open take-on batch can take a new or changed file.");
    var dataset = batch.getDataset();
    if (dataset.singleSource() && sources.existsByBatchIdAndSection(batchId, TakeOnSection.MAIN)) {
      throw TakeOnProblems.singleSource();
    }
    TakeOnSection resolved = resolveSection(dataset, section);
    if (sources.existsByBatchIdAndSection(batchId, resolved)) {
      throw TakeOnProblems.sectionExists(resolved);
    }
    var upload = readUpload(file);
    var source =
        storeSource(
            batch, resolved, file.getOriginalFilename(), upload, RequestScopes.requireMemberId());
    transition(batch::returnToUploaded);
    audit.sourceUploaded(source);
    return new TakeOnUploadResponse(
        views.batch(batch), source.getId(), upload.sheet().headers(), upload.sheet().warnings());
  }

  /** {@code DELETE …/sources/{sourceId}}: the file, its rows and its S3 object go. */
  @Transactional
  public TakeOnBatchResponse removeSource(UUID batchId, UUID sourceId) {
    var batch = lockForWrite(batchId);
    requireOpen(batch, "Only an open take-on batch can have a file removed.");
    var source = requireSource(batchId, sourceId);
    String key = source.getS3Key();
    rows.deleteBySourceId(sourceId); // flushes, then clears the persistence context
    sources.deleteById(sourceId);
    storageCleanup.deleteAfterCommit(key == null ? List.of() : List.of(key));

    var reloaded = reload(batchId);
    var remaining = sources.findByBatchIdOrderBySectionAsc(batchId);
    if (remaining.isEmpty() || remaining.stream().anyMatch(s -> !s.isMapped())) {
      transition(reloaded::returnToUploaded);
    } else {
      transition(reloaded::markMapped);
    }
    return views.batch(reloaded);
  }

  /**
   * {@code PUT …/sheet}: another sheet or header row — the rows are re-read, the mapping cleared.
   */
  @Transactional
  public TakeOnUploadResponse selectSheet(UUID batchId, UUID sourceId, TakeOnSheetRequest request) {
    var batch = lockForWrite(batchId);
    requireOpen(batch, "Only an open take-on batch can take a new or changed file.");
    var source = requireSource(batchId, sourceId);
    int headerRow = request.headerRow();
    if (headerRow < 1 || headerRow > TakeOnFileReader.MAX_HEADER_ROW) {
      throw new InvalidStateException(
          "Invalid header row", "The header row must be between 1 and 50.");
    }
    String sheetName;
    if (source.getFileFormat() == TakeOnSource.FileFormat.CSV) {
      sheetName = CsvTakeOnReader.SHEET_NAME;
    } else if (request.sheetName() == null || request.sheetName().isBlank()) {
      throw new InvalidStateException("Sheet required", "Choose the sheet to take on.");
    } else {
      sheetName = request.sheetName();
    }
    byte[] bytes = sourceFiles.download(source);
    TakeOnSheet sheet =
        sourceFiles.read(
            bytes,
            source.getFileFormat(),
            sheetName,
            headerRow,
            TakeOnSourceFiles.readOptions(source));

    rows.deleteBySourceId(sourceId); // flushes, then clears the persistence context
    var reloadedBatch = reload(batchId);
    var reloadedSource = sources.findById(sourceId).orElseThrow();
    saveRows(reloadedBatch, reloadedSource, sheet);
    reloadedSource.selectSheet(sheetName, headerRow);
    if (reloadedSource.getFileFormat() == TakeOnSource.FileFormat.CSV
        && reloadedSource.getParseOptions().get("delimiter") == null) {
      // Only a CSV kept with no rows at upload has no delimiter recorded (OBS F-1).
      reloadedSource.recordDetectedOptions(sheet.detected().toMap());
    }
    reloadedSource.recordRead(sheet.rows().size(), sheet.malformedCount(), sheet.blankSkipped());
    transition(reloadedBatch::returnToUploaded);
    return new TakeOnUploadResponse(
        views.batch(reloadedBatch), sourceId, sheet.headers(), sheet.warnings());
  }

  /** {@code PUT …/mapping}: stored verbatim on the source (the preset seed, RD-4). */
  @Transactional
  public TakeOnBatchResponse saveMapping(
      UUID batchId, UUID sourceId, TakeOnMappingRequest request) {
    var batch = lockForWrite(batchId);
    requireOpen(batch, "Only an open take-on batch can be mapped.");
    var source = requireSource(batchId, sourceId);
    if (source.getRowCount() == 0) {
      throw TakeOnProblems.noRows(); // nothing to map until the sheet and header row are chosen
    }
    var checked = mappingValidator.check(batch, source, request);
    source.applyMapping(checked.mapping().toMap(), checked.parseOptions().toMap());
    boolean allMapped =
        sources.findByBatchIdOrderBySectionAsc(batchId).stream().allMatch(TakeOnSource::isMapped);
    if (allMapped) {
      transition(batch::markMapped); // from VALIDATED: back to MAPPED
    }
    audit.sourceMapped(
        source,
        checked.mapping().targets().size(),
        checked.mapping().customFields().size(),
        checked.mapping().parked().size());
    return views.batch(batch);
  }

  /** {@code PUT …/position}: the cutover date only in 653A (P-11). No audit event is defined. */
  @Transactional
  public TakeOnBatchResponse savePosition(UUID batchId, TakeOnPositionRequest request) {
    var batch = lockForWrite(batchId);
    if (!batch.getDataset().requiresCutoverDate()) {
      throw TakeOnProblems.positionNotApplicable();
    }
    if (request.cutoverDate() == null) {
      throw new InvalidStateException("Cutover date required", "Enter the cutover date.");
    }
    transition(() -> batch.setCutoverDate(request.cutoverDate()));
    return views.batch(batch);
  }

  /**
   * {@code POST …/validate}: the dry run runs in its own {@code REPEATABLE_READ} transaction; a
   * concurrent change of the batch surfaces as a serialization failure → 409 (C-21).
   */
  public TakeOnBatchResponse validate(UUID batchId) {
    try {
      validationService.validate(batchId);
    } catch (ConcurrencyFailureException e) {
      throw TakeOnProblems.concurrentChange();
    }
    return views.batch(batchId);
  }

  /** {@code POST …/cancel}: cancel and purge (§17.3.14). */
  @Transactional
  public TakeOnBatchResponse cancel(UUID batchId) {
    var batch = lockForWrite(batchId);
    requireOpen(batch, "Only an open take-on batch can be cancelled.");
    var cancelled = cancelAndPurge(batchId, RequestScopes.requireMemberId(), "USER");
    return views.batch(cancelled);
  }

  /**
   * Cancel and purge, shared with the abandoned-batch job (X4, X5). The caller holds the batch row
   * lock and has checked it is open. Live sign-offs are revoked ({@code CANCELLED}), each source
   * marked purged ({@code purged_at}), every row's values, source ref, dedup key and message texts
   * cleared (the shells stay for the audit trail); the S3 objects are deleted after commit and each
   * key forgotten only once its delete succeeded (else the purge job retries it).
   *
   * @param actor null when the purge job cancels (no member)
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public TakeOnBatch cancelAndPurge(UUID batchId, UUID actor, String reasonCode) {
    rows.purgeValues(batchId); // flushes, then clears the persistence context
    var batch = reload(batchId);
    for (var signOff : signOffs.findByBatchIdAndRevokedAtIsNull(batchId)) {
      signOff.revoke(TakeOnSignOff.RevokedReason.CANCELLED);
    }
    var keys = new LinkedHashMap<UUID, String>();
    int purged = 0;
    for (var source : sources.findByBatchIdOrderBySectionAsc(batchId)) {
      if (!source.isPurged()) {
        String key = source.purge();
        purged++;
        if (key != null) {
          keys.put(source.getId(), key);
        }
      }
    }
    transition(() -> batch.cancel(actor));
    audit.batchCancelled(batch, purged, reasonCode);
    storageCleanup.deletePurgedAfterCommit(keys);
    return batch;
  }

  // --- helpers ---

  /** A file read for upload: bytes and format (sniffed), its sheets and the chosen sheet. */
  record Upload(
      TakeOnFileSniffer.Sniffed file, List<TakeOnFileReader.SheetName> sheets, TakeOnSheet sheet) {}

  private Upload readUpload(MultipartFile multipart) {
    var file = TakeOnFileSniffer.sniff(multipart);
    var sheets = sourceFiles.sheetNames(file.bytes(), file.format());
    String sheetName = TakeOnSourceFiles.defaultSheet(sheets);
    // Header row 1 is only a guess: a file that yields no row there is kept with no rows and a
    // warning, and the wizard asks for the sheet and header row (OBS F-1).
    var sheet =
        sourceFiles.readOrNoRows(
            file.bytes(),
            file.format(),
            sheetName,
            1,
            TakeOnParseOptions.defaults(),
            sheets.size());
    return new Upload(file, sheets, sheet);
  }

  private TakeOnSource storeSource(
      TakeOnBatch batch, TakeOnSection section, String originalName, Upload upload, UUID memberId) {
    var file = upload.file();
    String fileName = TakeOnStorageKeys.sanitize(originalName, file.format());
    var source =
        sources.save(
            new TakeOnSource(
                batch.getId(),
                section,
                fileName,
                file.contentType(),
                file.format(),
                file.bytes().length,
                TakeOnHashes.sha256(file.bytes()),
                TakeOnSourceFiles.sheetNamesJson(upload.sheets()),
                upload.sheet().sheetName(),
                1,
                upload.sheet().detected().toMap(),
                memberId));
    saveRows(batch, source, upload.sheet());
    source.recordRead(
        upload.sheet().rows().size(),
        upload.sheet().malformedCount(),
        upload.sheet().blankSkipped());
    // S3 last (the bank-statement order): a refused upload leaves no object behind.
    String key =
        TakeOnStorageKeys.sourceKey(RequestScopes.requireTenantId(), batch.getId(), fileName);
    storageService.upload(key, file.bytes(), file.contentType());
    storageCleanup.deleteOnRollback(key); // a later failure must not leave the file behind
    source.attachStorageKey(key);
    return source;
  }

  private void saveRows(TakeOnBatch batch, TakeOnSource source, TakeOnSheet sheet) {
    var toSave = new ArrayList<TakeOnRow>(sheet.rows().size());
    for (var raw : sheet.rows()) {
      toSave.add(
          new TakeOnRow(
              batch.getId(),
              source.getId(),
              batch.getDataset(),
              source.getSection(),
              raw.rowNumber(),
              new LinkedHashMap<>(raw.values()),
              raw.numericHeaders()));
    }
    rows.saveAll(toSave);
  }

  private TakeOnBatch lockForWrite(UUID batchId) {
    var batch =
        batches
            .findByIdForUpdate(batchId)
            .orElseThrow(() -> new ResourceNotFoundException("Take-on batch", batchId));
    accessPolicy.requireWrite(batch.getDataset());
    return batch;
  }

  private TakeOnBatch reload(UUID batchId) {
    return batches
        .findById(batchId)
        .orElseThrow(() -> new ResourceNotFoundException("Take-on batch", batchId));
  }

  private TakeOnSource requireSource(UUID batchId, UUID sourceId) {
    return sources
        .findByIdAndBatchId(sourceId, batchId)
        .orElseThrow(() -> new ResourceNotFoundException("Take-on file", sourceId));
  }

  private static void requireOpen(TakeOnBatch batch, String message) {
    if (!batch.getStatus().isOpen()) {
      throw TakeOnProblems.wrongState(message);
    }
  }

  /** Entity transitions throw IllegalStateException with X3 text; here they become a 409 (C-2). */
  private static void transition(Runnable change) {
    try {
      change.run();
    } catch (IllegalStateException e) {
      throw TakeOnProblems.wrongState(e.getMessage());
    }
  }

  private static TakeOnSection resolveSection(TakeOnDataset dataset, TakeOnSection section) {
    if (dataset.singleSource()) {
      if (section != null && section != TakeOnSection.MAIN) {
        throw new InvalidStateException(
            "Section not applicable", "This take-on takes one file, without a section.");
      }
      return TakeOnSection.MAIN;
    }
    if (section == null || !dataset.sections().contains(section)) {
      throw new InvalidStateException(
          "Section required", "Choose which part of the trust position this file holds.");
    }
    return section;
  }

  private void requireTrustAccount(TakeOnDataset dataset, UUID trustAccountId) {
    if (!dataset.requiresTrustAccount()) {
      if (trustAccountId != null) {
        throw new InvalidStateException(
            "Trust account not applicable", "A trust account applies only to a trust take-on.");
      }
      return;
    }
    if (trustAccountId == null) {
      throw new InvalidStateException(
          "Trust account required", "Choose the trust banking account to take on.");
    }
    var account =
        trustAccounts
            .findById(trustAccountId)
            .orElseThrow(() -> new ResourceNotFoundException("Trust account", trustAccountId));
    if (account.getAccountType() == TrustAccountType.INVESTMENT) {
      throw TakeOnProblems.notABankingAccount();
    }
  }

  private static String batchName(String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    String trimmed = name.strip();
    if (trimmed.length() > MAX_NAME_LENGTH) {
      throw new InvalidStateException(
          "Name too long", "The take-on batch name is longer than 200 characters.");
    }
    return trimmed;
  }
}
