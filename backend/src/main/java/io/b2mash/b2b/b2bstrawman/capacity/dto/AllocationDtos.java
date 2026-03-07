package io.b2mash.b2b.b2bstrawman.capacity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AllocationDtos {

  private AllocationDtos() {}

  public record CreateAllocationRequest(
      @NotNull(message = "memberId is required") UUID memberId,
      @NotNull(message = "projectId is required") UUID projectId,
      @NotNull(message = "weekStart is required") LocalDate weekStart,
      @NotNull(message = "allocatedHours is required") BigDecimal allocatedHours,
      String note) {}

  public record UpdateAllocationRequest(
      @NotNull(message = "allocatedHours is required") BigDecimal allocatedHours, String note) {}

  public record BulkAllocationRequest(
      @NotNull(message = "allocations is required")
          @Size(min = 1, max = 100, message = "allocations must contain 1 to 100 items")
          @Valid
          List<CreateAllocationRequest> allocations) {}

  public record AllocationResponse(
      UUID id,
      UUID memberId,
      UUID projectId,
      LocalDate weekStart,
      BigDecimal allocatedHours,
      String note,
      boolean overAllocated,
      BigDecimal overageHours,
      Instant createdAt) {}

  public record BulkAllocationResponse(List<AllocationResultItem> results) {}

  public record AllocationResultItem(AllocationResponse allocation, boolean created) {}
}
