package io.b2mash.b2b.b2bstrawman.crm.dto;

import io.b2mash.b2b.b2bstrawman.crm.Deal;
import io.b2mash.b2b.b2bstrawman.crm.DealStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Deal read-model view (Phase 80, §11.4). The derived fields {@code effectiveProbabilityPct},
 * {@code weightedValue} and {@code stageName} are resolved in {@code DealService} (they depend on
 * the deal's pipeline stage), NOT on the entity — the controller never performs this
 * transformation.
 *
 * @param effectiveProbabilityPct WON ⇒ 100, LOST ⇒ 0, else the deal's override or the stage default
 * @param weightedValue {@code valueAmount * effectiveProbabilityPct / 100}
 * @param stageName resolved name of the deal's current pipeline stage
 */
public record DealResponse(
    UUID id,
    String dealNumber,
    UUID customerId,
    String title,
    UUID stageId,
    String stageName,
    DealStatus status,
    BigDecimal valueAmount,
    String valueCurrency,
    Integer probabilityPct,
    int effectiveProbabilityPct,
    BigDecimal weightedValue,
    LocalDate expectedCloseDate,
    UUID ownerId,
    String source,
    Instant wonAt,
    Instant lostAt,
    String lostReason,
    Map<String, Object> customFields,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt) {

  public static DealResponse from(Deal deal, int effectiveProbabilityPct, String stageName) {
    return new DealResponse(
        deal.getId(),
        deal.getDealNumber(),
        deal.getCustomerId(),
        deal.getTitle(),
        deal.getStageId(),
        stageName,
        deal.getStatus(),
        deal.getValueAmount(),
        deal.getValueCurrency(),
        deal.getProbabilityPct(),
        effectiveProbabilityPct,
        deal.weightedValue(effectiveProbabilityPct),
        deal.getExpectedCloseDate(),
        deal.getOwnerId(),
        deal.getSource(),
        deal.getWonAt(),
        deal.getLostAt(),
        deal.getLostReason(),
        deal.getCustomFields(),
        deal.getCreatedBy(),
        deal.getCreatedAt(),
        deal.getUpdatedAt());
  }
}
