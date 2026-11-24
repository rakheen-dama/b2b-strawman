package io.b2mash.b2b.b2bstrawman.mcp.dto;

import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceDtos.ChecklistInstanceResponse;
import io.b2mash.b2b.b2bstrawman.informationrequest.FicaStatusProjector;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.FicaStatusResponse;
import java.util.List;
import java.util.UUID;

/**
 * Compliance-gap projection for {@code list_compliance_gaps} (Epic 564B): the client's FICA status
 * plus the flattened checklist items (name/status/required) across all checklist instances. Items
 * are capped at the MCP page max and {@code truncated} is set when the flattened item count
 * exceeded the cap.
 *
 * @param customerId the client
 * @param ficaStatus FICA verification status (NOT_STARTED / IN_PROGRESS / DONE / ATTESTED /
 *     ATTESTED_REVIEW — the last two from a take-on attestation, Phase 93 655B, ADR-388)
 * @param items flattened checklist items, capped
 * @param truncated true when items were clipped to the cap
 * @param ficaOutstanding true when FICA work is owed ({@code FicaStatusProjector.isOutstanding}:
 *     NOT_STARTED, IN_PROGRESS, ATTESTED_REVIEW) — the gap flag (655B)
 * @param ficaReviewReasons why an ATTESTED_REVIEW client is due for review (UNRATED,
 *     BENEFICIAL_OWNERSHIP_OUTSTANDING, SOURCE_OF_FUNDS_OUTSTANDING, NOT_SCREENED,
 *     REVIEW_DATE_PASSED); empty otherwise, never null
 */
public record McpComplianceGapDto(
    UUID customerId,
    String ficaStatus,
    List<Item> items,
    boolean truncated,
    boolean ficaOutstanding,
    List<String> ficaReviewReasons) {

  /** One flattened checklist item. */
  public record Item(String name, String status, boolean required) {}

  /**
   * Flattens the per-customer checklist instances into a capped item list.
   *
   * @param customerId the client
   * @param fica the client's projected FICA status
   * @param instances all checklist instances (with items) for the customer
   * @param maxItems item cap
   */
  public static McpComplianceGapDto from(
      UUID customerId,
      FicaStatusResponse fica,
      List<ChecklistInstanceResponse> instances,
      int maxItems) {
    List<Item> all =
        instances.stream()
            .filter(i -> i.items() != null)
            .flatMap(i -> i.items().stream())
            .map(it -> new Item(it.name(), it.status(), it.required()))
            .toList();
    boolean truncated = all.size() > maxItems;
    List<Item> capped = truncated ? all.subList(0, maxItems) : all;
    return new McpComplianceGapDto(
        customerId,
        fica.status(),
        List.copyOf(capped),
        truncated,
        FicaStatusProjector.isOutstanding(fica.status()),
        fica.reviewReasons());
  }
}
