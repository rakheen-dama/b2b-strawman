package io.b2mash.b2b.b2bstrawman.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Audit-details builder for matter-scoped events (ADR-349). {@code project_id} in details is a
 * publisher obligation: the matter Activity feed ({@link AuditEventRepository#findByProjectId}),
 * Home recent activity and the portal Firm-actions trail all correlate on {@code
 * details->>'project_id'}, so a publisher that omits it silently drops its rows from all three
 * (G-B8, G-D9). Enforced by {@code MatterScopedPublisherGuardTest}.
 *
 * <p>Returns a fresh mutable map so a null id cannot hit {@code Map.of}'s null rejection: an
 * unknown matter is written as {@code ""} (the {@code trust_fee_transfer.recorded} / {@code
 * invoice.sent} convention — every reader compares against a UUID string, so {@code ""} never
 * matches); an unknown client is omitted.
 */
public final class AuditDetails {

  /** ADR-342: stored audit-details key, unchanged under the matter/client wire — do not rename. */
  public static final String PROJECT_ID = "project_id";

  /** ADR-342: stored audit-details key, unchanged under the matter/client wire — do not rename. */
  public static final String CUSTOMER_ID = "customer_id";

  private AuditDetails() {}

  /** {@code project_id} (or {@code ""}) and, when known, {@code customer_id}; add more keys. */
  public static LinkedHashMap<String, Object> matterScoped(UUID projectId, UUID customerId) {
    var details = new LinkedHashMap<String, Object>();
    details.put(PROJECT_ID, projectId != null ? projectId.toString() : "");
    if (customerId != null) {
      details.put(CUSTOMER_ID, customerId.toString());
    }
    return details;
  }

  /**
   * As {@link #matterScoped(UUID, UUID)}, followed by {@code rest} (typically a {@code Map.of(...)}
   * of the event's own keys). {@code rest} must not carry the correlation keys — they have one
   * writer.
   */
  public static LinkedHashMap<String, Object> matterScoped(
      UUID projectId, UUID customerId, Map<String, ?> rest) {
    if (rest.containsKey(PROJECT_ID) || rest.containsKey(CUSTOMER_ID)) {
      throw new IllegalArgumentException(
          "rest must not contain "
              + PROJECT_ID
              + "/"
              + CUSTOMER_ID
              + " — matterScoped writes them");
    }
    var details = matterScoped(projectId, customerId);
    details.putAll(rest);
    return details;
  }
}
