package io.b2mash.b2b.b2bstrawman.capacity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class CapacityDtos {

  private CapacityDtos() {}

  public record CreateCapacityRequest(
      @NotNull(message = "weeklyHours is required")
          @Positive(message = "weeklyHours must be positive")
          BigDecimal weeklyHours,
      @NotNull(message = "effectiveFrom is required") LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String note) {}

  public record UpdateCapacityRequest(
      @NotNull(message = "weeklyHours is required")
          @Positive(message = "weeklyHours must be positive")
          BigDecimal weeklyHours,
      LocalDate effectiveTo,
      String note) {}

  public record MemberCapacityResponse(
      UUID id,
      UUID memberId,
      BigDecimal weeklyHours,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String note,
      Instant createdAt) {}
}
