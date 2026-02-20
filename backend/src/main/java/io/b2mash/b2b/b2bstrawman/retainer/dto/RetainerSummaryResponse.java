package io.b2mash.b2b.b2bstrawman.retainer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerType;
import java.math.BigDecimal;
import java.util.UUID;

public record RetainerSummaryResponse(
    boolean hasActiveRetainer,
    UUID agreementId,
    String agreementName,
    RetainerType type,
    BigDecimal allocatedHours,
    BigDecimal consumedHours,
    BigDecimal remainingHours,
    BigDecimal percentConsumed,
    @JsonProperty("isOverage") boolean isOverage) {

  /** Factory method for when no active retainer exists. */
  public static RetainerSummaryResponse noRetainer() {
    return new RetainerSummaryResponse(false, null, null, null, null, null, null, null, false);
  }
}
