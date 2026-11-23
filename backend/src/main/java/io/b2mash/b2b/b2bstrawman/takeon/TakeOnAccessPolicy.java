package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.orgrole.CapabilityAuthorizationService;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The dataset's own capability on top of {@code MANAGE_TAKE_ON} (§17.9, §17.10 X1): the take-on
 * screens never show data more widely than the screens that normally show it. A custom role with
 * {@code MANAGE_TAKE_ON} but not {@code VIEW_TRUST} cannot read trust balances from the row table.
 *
 * <p>Services call it <b>after</b> loading the batch, so a missing batch is a 404 before a 403.
 * Refusals are {@code AccessDeniedException} → 403 "Access denied".
 */
@Component
public class TakeOnAccessPolicy {

  private final CapabilityAuthorizationService capabilities;

  public TakeOnAccessPolicy(CapabilityAuthorizationService capabilities) {
    this.capabilities = capabilities;
  }

  /** Read rows, the raw file and (later) the result CSV. */
  public static String readCapability(TakeOnDataset dataset) {
    return switch (dataset) {
      case CLIENTS -> "CUSTOMER_MANAGEMENT";
      case MATTERS -> "PROJECT_MANAGEMENT";
      case TRUST_POSITION -> "VIEW_TRUST";
      case DEBTOR_BALANCES -> "INVOICING";
    };
  }

  /**
   * Upload, map, validate, commit, cancel: the dataset's write capability <b>and</b> its read
   * capability. A trust write needs {@code MANAGE_TRUST} plus {@code VIEW_TRUST} — the upload step
   * lists the trust accounts ({@code GET /api/trust-accounts}, {@code VIEW_TRUST}) and every write
   * returns the batch, so a writer must be a reader (decision D-44; never looser than §17.9).
   */
  public static List<String> writeCapabilities(TakeOnDataset dataset) {
    return dataset == TakeOnDataset.TRUST_POSITION
        ? List.of("MANAGE_TRUST", readCapability(dataset))
        : List.of(readCapability(dataset));
  }

  public void requireRead(TakeOnDataset dataset) {
    capabilities.requireCapability(readCapability(dataset));
  }

  public void requireWrite(TakeOnDataset dataset) {
    writeCapabilities(dataset).forEach(capabilities::requireCapability);
  }

  /** The datasets whose batches the caller may see in the history. */
  public Set<TakeOnDataset> readableDatasets() {
    return Arrays.stream(TakeOnDataset.values())
        .filter(d -> capabilities.hasCapability(readCapability(d)))
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(TakeOnDataset.class)));
  }
}
