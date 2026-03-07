package io.b2mash.b2b.b2bstrawman.capacity.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class LeaveDtos {

  private LeaveDtos() {}

  public record CreateLeaveRequest(
      @NotNull(message = "startDate is required") LocalDate startDate,
      @NotNull(message = "endDate is required") LocalDate endDate,
      String note) {}

  public record UpdateLeaveRequest(
      @NotNull(message = "startDate is required") LocalDate startDate,
      @NotNull(message = "endDate is required") LocalDate endDate,
      String note) {}

  public record LeaveBlockResponse(
      UUID id,
      UUID memberId,
      LocalDate startDate,
      LocalDate endDate,
      String note,
      UUID createdBy,
      Instant createdAt) {}
}
