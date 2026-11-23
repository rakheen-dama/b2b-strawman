package io.b2mash.b2b.b2bstrawman.takeon.dataset;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatch;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDataset;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRow;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSection;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSource;
import java.util.List;
import java.util.Map;

/**
 * Dataset-polymorphic dry run (§17.3.2, §17.8.1). 653A ships only {@link
 * GenericTakeOnDatasetHandler} (generic checks); each dataset slice adds its own handler bean (655A
 * clients, 656A matters, 657B trust, 658A debtors), and with it {@code commitRow} / {@code
 * commitAll} — not declared here until a slice needs them.
 *
 * <p>A dataset handler reuses the generic checks through {@link
 * GenericTakeOnDatasetHandler#checkGeneric}, adds its own errors, warnings, block or duplicate to
 * the returned {@link TakeOnRowCheck}, and records the row once ({@link
 * GenericTakeOnDatasetHandler#finish}).
 *
 * <p>Every method runs inside the validating {@code REPEATABLE_READ} transaction: lookups made in
 * {@link #loadContext} see the same snapshot as the counts (§17.10 X2). A handler never creates or
 * changes a client, matter, fee note or trust row.
 */
public interface TakeOnDatasetHandler {

  /** A row's lookup-free dedup key (C-25) and the field it came from. */
  record DedupKey(String field, String key) {}

  /** The dataset served; {@code null} for the generic handler, which serves any. */
  TakeOnDataset dataset();

  /** The target fields of a section (the field catalogue, plus any dataset-specific ones). */
  List<TakeOnFieldCatalogue.FieldSpec> fields(TakeOnDataset dataset, TakeOnSection section);

  /** Loads what the row checks need once per run: mappings, parse options, custom fields. */
  ValidationContext loadContext(TakeOnBatch batch, List<TakeOnSource> sources);

  /** Types one row's values and records its errors, warnings, source ref and dedup key. */
  void mapAndValidate(TakeOnRow row, ValidationContext context);

  /** The row's dedup key from its typed values, or {@code null} when it has none. */
  DedupKey dedupKey(TakeOnDataset dataset, Map<String, Object> mapped);

  /** Checks across rows (in-file duplicates; later: control totals, the trust equation). */
  void validateBatchLevel(TakeOnBatch batch, ValidationContext context);

  /** The batch summary stored with the validation ({@code take_on_batches.summary}). */
  Map<String, Object> summary(ValidationContext context);
}
