package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnBatchResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnBatchSummaryResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnDatasetResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnFieldResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnPreviewResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnPreviewRow;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnRowMessage;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnRowResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnSectionResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnSourceFile;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnTemplateFile;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnDatasetHandlers;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnFieldCatalogue.FieldSpec;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Take-on reads (Phase 93 653A): the dataset catalogue and templates ({@code MANAGE_TAKE_ON} only —
 * no data), and the batch history, detail, preview, row table and raw file, each behind the
 * dataset's read capability (§17.9, X1). The history lists only datasets the caller may read.
 */
@Service
public class TakeOnBatchQueryService {

  static final int MAX_PAGE_SIZE = 200;
  static final int MAX_PREVIEW_ROWS = 50;

  private final TakeOnBatchRepository batches;
  private final TakeOnSourceRepository sources;
  private final TakeOnRowRepository rows;
  private final TakeOnAccessPolicy accessPolicy;
  private final TakeOnSourceFiles sourceFiles;
  private final TakeOnBatchViews views;
  private final TakeOnAudit audit;
  private final MemberNameResolver memberNames;
  private final TakeOnDatasetHandlers handlers;

  public TakeOnBatchQueryService(
      TakeOnBatchRepository batches,
      TakeOnSourceRepository sources,
      TakeOnRowRepository rows,
      TakeOnAccessPolicy accessPolicy,
      TakeOnSourceFiles sourceFiles,
      TakeOnBatchViews views,
      TakeOnAudit audit,
      MemberNameResolver memberNames,
      TakeOnDatasetHandlers handlers) {
    this.batches = batches;
    this.sources = sources;
    this.rows = rows;
    this.accessPolicy = accessPolicy;
    this.sourceFiles = sourceFiles;
    this.views = views;
    this.audit = audit;
    this.memberNames = memberNames;
    this.handlers = handlers;
  }

  /** A section's target fields, through the dataset's handler (the dataset seam). */
  private List<FieldSpec> fields(TakeOnDataset dataset, TakeOnSection section) {
    return handlers.forDataset(dataset).fields(dataset, section);
  }

  /** {@code GET /api/take-on/datasets}: in X3 order (clients → matters → trust → debtors). */
  @Transactional(readOnly = true)
  public List<TakeOnDatasetResponse> listDatasets() {
    return Arrays.stream(TakeOnDataset.values())
        .map(
            d ->
                new TakeOnDatasetResponse(
                    d,
                    d.label(),
                    d.requiresCutoverDate(),
                    d.requiresTrustAccount(),
                    d.customFieldEntityType().map(Enum::name).orElse(null),
                    d.sections().stream()
                        .map(
                            s ->
                                new TakeOnSectionResponse(
                                    s,
                                    s.label(),
                                    fields(d, s).stream()
                                        .map(TakeOnBatchQueryService::field)
                                        .toList()))
                        .toList(),
                    batches.countByDatasetAndStatus(d, TakeOnBatchStatus.COMPLETED)))
        .toList();
  }

  /** {@code GET /api/take-on/templates/{dataset}}: a CSV of headers and one fictional row. */
  public TakeOnTemplateFile template(TakeOnDataset dataset, TakeOnSection section) {
    TakeOnSection resolved;
    if (dataset.singleSource()) {
      resolved = TakeOnSection.MAIN;
    } else if (section != null && dataset.sections().contains(section)) {
      resolved = section;
    } else {
      throw new InvalidStateException(
          "Section required", "Choose which part of the trust position the template is for.");
    }
    return TakeOnTemplates.template(dataset, resolved, fields(dataset, resolved));
  }

  /** {@code GET /api/take-on/batches}: newest first, readable datasets only (X1). */
  @Transactional(readOnly = true)
  public Page<TakeOnBatchSummaryResponse> listBatches(
      TakeOnDataset dataset, TakeOnBatchStatus status, Pageable pageable) {
    Set<TakeOnDataset> readable = accessPolicy.readableDatasets();
    if (dataset != null) {
      readable.retainAll(Set.of(dataset));
    }
    var page = PageRequest.of(pageable.getPageNumber(), clamp(pageable.getPageSize()));
    if (readable.isEmpty()) {
      return Page.empty(page);
    }
    Page<TakeOnBatch> found =
        status == null
            ? batches.findByDatasetInOrderByCreatedAtDesc(readable, page)
            : batches.findByDatasetInAndStatusInOrderByCreatedAtDesc(
                readable, Set.of(status), page);
    var names =
        memberNames.resolveNames(
            found.getContent().stream().map(TakeOnBatch::getCreatedBy).distinct().toList());
    return found.map(
        b ->
            new TakeOnBatchSummaryResponse(
                b.getId(),
                b.getDataset(),
                b.getStatus(),
                b.getName(),
                b.getCutoverDate(),
                b.getTotalRows(),
                b.getValidRows(),
                b.getErrorRows(),
                names.get(b.getCreatedBy()),
                b.getCreatedAt()));
  }

  /** {@code GET /api/take-on/batches/{batchId}}. */
  @Transactional(readOnly = true)
  public TakeOnBatchResponse getBatch(UUID batchId) {
    return views.batch(loadForRead(batchId));
  }

  /** {@code GET …/preview}: re-reads the stored file (C-6) — headers, first rows, suggestions. */
  @Transactional(readOnly = true)
  public TakeOnPreviewResponse preview(UUID batchId, UUID sourceId, int limit) {
    var batch = loadForRead(batchId);
    var source = requireSource(batchId, sourceId);
    int count = Math.max(1, Math.min(MAX_PREVIEW_ROWS, limit));
    var sheet = sourceFiles.reread(source);
    var previewRows =
        sheet.rows().stream()
            .limit(count)
            .map(
                r ->
                    new TakeOnPreviewRow(
                        r.rowNumber(),
                        sheet.headers().stream()
                            .map(h -> Objects.requireNonNullElse(r.values().get(h), ""))
                            .toList()))
            .toList();
    return new TakeOnPreviewResponse(
        sourceId,
        sheet.headers(),
        previewRows,
        TakeOnBatchViews.parseOptions(sheet.detected()),
        TakeOnBatchViews.sheetNames(source.getSheetNames()),
        source.getSheetName(),
        source.getHeaderRow(),
        suggestTargets(fields(batch.getDataset(), source.getSection()), sheet.headers()),
        sheet.warnings());
  }

  /** {@code GET …/rows}: section then row number; filters by status, section, blocks only. */
  @Transactional(readOnly = true)
  public Page<TakeOnRowResponse> listRows(
      UUID batchId,
      TakeOnRowStatus status,
      TakeOnSection section,
      boolean blocksOnly,
      Pageable pageable) {
    loadForRead(batchId);
    var page =
        PageRequest.of(
            pageable.getPageNumber(),
            clamp(pageable.getPageSize()),
            Sort.by("section").ascending().and(Sort.by("rowNumber").ascending()));
    return rows.findByBatchIdAndStatusInAndSectionInAndBlockIn(
            batchId,
            status == null ? EnumSet.allOf(TakeOnRowStatus.class) : Set.of(status),
            section == null ? EnumSet.allOf(TakeOnSection.class) : Set.of(section),
            blocksOnly ? Set.of(Boolean.TRUE) : Set.of(Boolean.TRUE, Boolean.FALSE),
            page)
        .map(TakeOnBatchQueryService::row);
  }

  /** {@code GET …/file}: the raw file, streamed through the backend and audited. */
  @Transactional
  public TakeOnSourceFile downloadFile(UUID batchId, UUID sourceId) {
    loadForRead(batchId);
    var source = requireSource(batchId, sourceId);
    byte[] bytes = sourceFiles.download(source);
    audit.fileDownloaded(source);
    return new TakeOnSourceFile(bytes, source.getContentType(), source.getFileName());
  }

  // --- helpers ---

  private TakeOnBatch loadForRead(UUID batchId) {
    var batch =
        batches
            .findById(batchId)
            .orElseThrow(() -> new ResourceNotFoundException("Take-on batch", batchId));
    accessPolicy.requireRead(batch.getDataset());
    return batch;
  }

  private TakeOnSource requireSource(UUID batchId, UUID sourceId) {
    return sources
        .findByIdAndBatchId(sourceId, batchId)
        .orElseThrow(() -> new ResourceNotFoundException("Take-on file", sourceId));
  }

  private static int clamp(int size) {
    return Math.max(1, Math.min(MAX_PAGE_SIZE, size));
  }

  private static TakeOnFieldResponse field(FieldSpec f) {
    return new TakeOnFieldResponse(
        f.key(), f.label(), f.type().name(), f.required(), f.maxLength(), f.values());
  }

  @SuppressWarnings("unchecked")
  private static TakeOnRowResponse row(TakeOnRow r) {
    Map<String, Object> values =
        r.getMappedValues() != null ? r.getMappedValues() : r.getRawValues();
    return new TakeOnRowResponse(
        r.getId(),
        r.getSection(),
        r.getRowNumber(),
        r.getSourceRef(),
        r.getStatus(),
        r.isBlock(),
        values,
        messages(r.getErrors()),
        messages(r.getWarnings()),
        r.getDuplicateOfId());
  }

  private static List<TakeOnRowMessage> messages(List<Map<String, Object>> stored) {
    if (stored == null) {
      return List.of();
    }
    return stored.stream()
        .map(
            m ->
                new TakeOnRowMessage(
                    str(m.get("code")), str(m.get("field")), str(m.get("message"))))
        .toList();
  }

  private static String str(Object value) {
    return value == null ? null : value.toString();
  }

  /**
   * {@code fieldKey → header}: a header matches a field's key, label or header alias
   * case-insensitively, ignoring spaces and punctuation; each header is suggested once, the first
   * field in catalogue order winning.
   */
  static Map<String, String> suggestTargets(List<FieldSpec> fields, List<String> headers) {
    var suggested = new LinkedHashMap<String, String>();
    var used = new HashSet<String>();
    for (FieldSpec field : fields) {
      var names = new HashSet<String>();
      names.add(normalise(field.key()));
      names.add(normalise(field.label()));
      field.headerAliases().forEach(a -> names.add(normalise(a)));
      for (String header : headers) {
        if (!used.contains(header) && names.contains(normalise(header))) {
          suggested.put(field.key(), header);
          used.add(header);
          break;
        }
      }
    }
    return suggested;
  }

  private static String normalise(String s) {
    return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }
}
