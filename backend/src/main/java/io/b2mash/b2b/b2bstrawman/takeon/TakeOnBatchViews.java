package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnBatchResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnCountsResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnMappingResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnParseOptionsResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnSheetNameResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnSourceResponse;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnParseOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the take-on wire responses (no access checks — callers have made them). Never ships a
 * field nothing in 653A writes or reads (P-11, C-27).
 */
@Component
public class TakeOnBatchViews {

  /** What the wizard may offer next (653A; SIGN_OFF / ATTEST / COMMIT arrive in 655A, 657C). */
  public enum Action {
    ADD_SOURCE,
    REMOVE_SOURCE,
    MAP,
    VALIDATE,
    REVALIDATE,
    CANCEL
  }

  private final TakeOnBatchRepository batches;
  private final TakeOnSourceRepository sources;

  public TakeOnBatchViews(TakeOnBatchRepository batches, TakeOnSourceRepository sources) {
    this.batches = batches;
    this.sources = sources;
  }

  @Transactional(readOnly = true)
  public TakeOnBatchResponse batch(UUID batchId) {
    return batch(
        batches
            .findById(batchId)
            .orElseThrow(() -> new ResourceNotFoundException("Take-on batch", batchId)));
  }

  public TakeOnBatchResponse batch(TakeOnBatch batch) {
    var batchSources = sources.findByBatchIdOrderBySectionAsc(batch.getId());
    return new TakeOnBatchResponse(
        batch.getId(),
        batch.getDataset(),
        batch.getStatus(),
        batch.getName(),
        batch.getCutoverDate(),
        batch.getTrustAccountId(),
        batchSources.stream().map(TakeOnBatchViews::source).toList(),
        new TakeOnCountsResponse(
            batch.getTotalRows(),
            batch.getValidRows(),
            batch.getErrorRows(),
            batch.getDuplicateRows(),
            batch.getBlockCount()),
        batch.getValidationHash(),
        batch.getValidatedAt(),
        allowedActions(batch, batchSources).stream().map(Enum::name).toList(),
        batch.getCreatedAt(),
        batch.getCancelledAt());
  }

  static TakeOnSourceResponse source(TakeOnSource source) {
    var mapping = TakeOnColumnMapping.fromMap(source.getColumnMapping());
    return new TakeOnSourceResponse(
        source.getId(),
        source.getSection(),
        source.getFileName(),
        source.getFileFormat().name(),
        source.getFileSize(),
        sheetNames(source.getSheetNames()),
        source.getSheetName(),
        source.getHeaderRow(),
        parseOptions(TakeOnParseOptions.fromMap(source.getParseOptions())),
        mapping == null
            ? null
            : new TakeOnMappingResponse(
                mapping.targets(), mapping.customFields(), mapping.parked(), mapping.defaults()),
        source.isMapped(),
        source.getRowCount(),
        source.getMalformedCount(),
        source.getBlankSkipped(),
        source.isPurged(),
        source.getUploadedAt());
  }

  static TakeOnParseOptionsResponse parseOptions(TakeOnParseOptions options) {
    return new TakeOnParseOptionsResponse(
        options.dateFormat(),
        options.decimalSeparator().name(),
        options.delimiter() == null ? null : String.valueOf(options.delimiter()),
        options.encoding());
  }

  static List<TakeOnSheetNameResponse> sheetNames(List<Map<String, Object>> stored) {
    if (stored == null) {
      return List.of();
    }
    return stored.stream()
        .map(
            m ->
                new TakeOnSheetNameResponse(
                    String.valueOf(m.get("name")), Boolean.TRUE.equals(m.get("hidden"))))
        .toList();
  }

  static List<Action> allowedActions(TakeOnBatch batch, List<TakeOnSource> batchSources) {
    if (!batch.getStatus().isOpen()) {
      return List.of();
    }
    var actions = new ArrayList<Action>();
    boolean sectionLeft =
        batch.getDataset().sections().stream()
            .anyMatch(s -> batchSources.stream().noneMatch(src -> src.getSection() == s));
    if (sectionLeft) {
      actions.add(Action.ADD_SOURCE);
    }
    if (!batchSources.isEmpty()) {
      actions.add(Action.REMOVE_SOURCE);
      actions.add(Action.MAP);
    }
    switch (batch.getStatus()) {
      case MAPPED -> actions.add(Action.VALIDATE);
      case VALIDATED -> actions.add(Action.REVALIDATE);
      default -> {
        // UPLOADED: map first
      }
    }
    actions.add(Action.CANCEL);
    return List.copyOf(actions);
  }
}
