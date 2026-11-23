package io.b2mash.b2b.b2bstrawman.takeon.dataset;

import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatch;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRow;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSource;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnParseOptions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What one dry run needs, loaded once (§17.3.2): the batch, each source's mapping and parse
 * options, the custom-field definitions of the dataset's entity type, and the dedup keys seen so
 * far. Dataset slices extend it with their lookups (existing clients, matters, accounts) through
 * the copy constructor.
 */
public class ValidationContext {

  /**
   * One source's stored mapping: {@code targets} field key → header, {@code customFields} slug →
   * header, {@code parked} headers, {@code defaults} field key → value for blank cells.
   */
  public record SourceMapping(
      TakeOnSource source,
      List<TakeOnFieldCatalogue.FieldSpec> fields,
      Map<String, String> targets,
      Map<String, String> customFields,
      List<String> parked,
      Map<String, String> defaults,
      TakeOnParseOptions options) {}

  /** A row whose dedup key an earlier row of the file already had. */
  public record InFileDuplicate(TakeOnRow row, String field, int firstRowNumber) {}

  private final TakeOnBatch batch;
  private final Map<UUID, SourceMapping> sources;
  private final EntityType customFieldEntityType;
  private final Map<String, FieldDefinition> customFieldDefinitions;
  private final Map<String, Integer> firstRowByDedupKey = new HashMap<>();
  private final List<InFileDuplicate> inFileDuplicates = new ArrayList<>();

  public ValidationContext(
      TakeOnBatch batch,
      Map<UUID, SourceMapping> sources,
      EntityType customFieldEntityType,
      Map<String, FieldDefinition> customFieldDefinitions) {
    this.batch = batch;
    this.sources = Map.copyOf(sources);
    this.customFieldEntityType = customFieldEntityType;
    this.customFieldDefinitions = Map.copyOf(customFieldDefinitions);
  }

  /**
   * The extension seam for a dataset handler (653A found F-15, chosen in 655A): a handler that
   * delegates to {@link GenericTakeOnDatasetHandler} subclasses this context and builds it from the
   * generic one with this copy constructor, adding its own lookups. Copies the loaded mappings and
   * definitions; the in-file dedup state starts empty (it is built while the rows are checked).
   */
  protected ValidationContext(ValidationContext base) {
    this(base.batch, base.sources, base.customFieldEntityType, base.customFieldDefinitions);
  }

  public TakeOnBatch batch() {
    return batch;
  }

  public SourceMapping source(UUID sourceId) {
    var mapping = sources.get(sourceId);
    if (mapping == null) {
      throw new IllegalStateException("A take-on row refers to a file that is not in its batch.");
    }
    return mapping;
  }

  public EntityType customFieldEntityType() {
    return customFieldEntityType;
  }

  public Map<String, FieldDefinition> customFieldDefinitions() {
    return customFieldDefinitions;
  }

  /**
   * Records {@code dedupKey} for {@code row}; returns the row number of the first row that had it
   * when this row is a repeat, else {@code null}.
   */
  public Integer recordDedupKey(String dedupKey, TakeOnRow row, String field) {
    Integer first = firstRowByDedupKey.putIfAbsent(dedupKey, row.getRowNumber());
    if (first != null) {
      inFileDuplicates.add(new InFileDuplicate(row, field, first));
    }
    return first;
  }

  public List<InFileDuplicate> inFileDuplicates() {
    return List.copyOf(inFileDuplicates);
  }
}
