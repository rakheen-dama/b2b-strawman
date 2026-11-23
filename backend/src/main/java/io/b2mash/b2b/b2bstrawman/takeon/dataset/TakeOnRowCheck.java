package io.b2mash.b2b.b2bstrawman.takeon.dataset;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One row's dry-run result while it is being built: the generic checks fill it ({@link
 * GenericTakeOnDatasetHandler#checkGeneric}), a dataset handler adds to it, and {@link #applyTo}
 * records it on the row in one {@code applyValidation} call.
 */
public final class TakeOnRowCheck {

  private final Map<String, Object> mapped;
  private final List<Map<String, Object>> errors = new ArrayList<>();
  private final List<Map<String, Object>> warnings = new ArrayList<>();
  private String sourceRef;
  private TakeOnDatasetHandler.DedupKey dedupKey;
  private boolean block;
  private UUID duplicateOfId;

  TakeOnRowCheck(Map<String, Object> mapped, String sourceRef) {
    this.mapped = mapped;
    this.sourceRef = sourceRef;
  }

  /** The typed values (mutable: a handler may add derived values). */
  public Map<String, Object> mapped() {
    return mapped;
  }

  public List<Map<String, Object>> errors() {
    return errors;
  }

  public List<Map<String, Object>> warnings() {
    return warnings;
  }

  public void addError(String code, String field, String message) {
    errors.add(message(code, field, message));
  }

  public void addWarning(String code, String field, String message) {
    warnings.add(message(code, field, message));
  }

  /** A statutory hard block (657B): recorded as an error that blocks the commit. */
  public void addBlock(String code, String field, String message) {
    addError(code, field, message);
    block = true;
  }

  public String sourceRef() {
    return sourceRef;
  }

  public void sourceRef(String sourceRef) {
    this.sourceRef = sourceRef;
  }

  public TakeOnDatasetHandler.DedupKey dedupKey() {
    return dedupKey;
  }

  public void dedupKey(TakeOnDatasetHandler.DedupKey dedupKey) {
    this.dedupKey = dedupKey;
  }

  public void duplicateOf(UUID duplicateOfId) {
    this.duplicateOfId = duplicateOfId;
  }

  /** Records the result on the row: ERROR iff any error, else VALIDATED. */
  public void applyTo(TakeOnRow row) {
    row.applyValidation(
        mapped,
        sourceRef,
        dedupKey == null ? null : dedupKey.key(),
        errors,
        warnings,
        block,
        duplicateOfId);
  }

  static Map<String, Object> message(String code, String field, String message) {
    var m = new LinkedHashMap<String, Object>();
    m.put("code", code);
    m.put("field", field);
    m.put("message", message);
    return m;
  }
}
