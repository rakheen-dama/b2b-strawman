package io.b2mash.b2b.b2bstrawman.costrate;

import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cost-rates")
public class CostRateController {

  private final CostRateService costRateService;

  public CostRateController(CostRateService costRateService) {
    this.costRateService = costRateService;
  }

  @GetMapping
  @RequiresCapability("FINANCIAL_VISIBILITY")
  public ResponseEntity<ListResponse<CostRateResponse>> listCostRates(
      @RequestParam(required = false) UUID memberId, ActorContext actor) {
    var rates = costRateService.listCostRates(memberId, actor);
    return ResponseEntity.ok(new ListResponse<>(costRateService.toResponses(rates)));
  }

  @PostMapping
  @RequiresCapability("FINANCIAL_VISIBILITY")
  public ResponseEntity<CostRateResponse> createCostRate(
      @Valid @RequestBody CreateCostRateRequest request, ActorContext actor) {
    var rate =
        costRateService.createCostRate(
            request.memberId(),
            request.currency(),
            request.hourlyCost(),
            request.effectiveFrom(),
            request.effectiveTo(),
            actor);
    return ResponseEntity.created(URI.create("/api/cost-rates/" + rate.getId()))
        .body(costRateService.toResponse(rate));
  }

  @PutMapping("/{id}")
  @RequiresCapability("FINANCIAL_VISIBILITY")
  public ResponseEntity<CostRateResponse> updateCostRate(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateCostRateRequest request,
      ActorContext actor) {
    var rate =
        costRateService.updateCostRate(
            id,
            request.hourlyCost(),
            request.currency(),
            request.effectiveFrom(),
            request.effectiveTo(),
            actor);
    return ResponseEntity.ok(costRateService.toResponse(rate));
  }

  @DeleteMapping("/{id}")
  @RequiresCapability("FINANCIAL_VISIBILITY")
  public ResponseEntity<Void> deleteCostRate(@PathVariable UUID id, ActorContext actor) {
    costRateService.deleteCostRate(id, actor);
    return ResponseEntity.noContent().build();
  }

  // --- DTOs ---

  public record ListResponse<T>(List<T> content) {}

  public record CreateCostRateRequest(
      @NotNull(message = "memberId is required") UUID memberId,
      @NotBlank(message = "currency is required")
          @Size(min = 3, max = 3, message = "currency must be exactly 3 characters")
          String currency,
      @NotNull(message = "hourlyCost is required")
          @Positive(message = "hourlyCost must be positive")
          BigDecimal hourlyCost,
      @NotNull(message = "effectiveFrom is required") LocalDate effectiveFrom,
      LocalDate effectiveTo) {}

  public record UpdateCostRateRequest(
      @NotBlank(message = "currency is required")
          @Size(min = 3, max = 3, message = "currency must be exactly 3 characters")
          String currency,
      @NotNull(message = "hourlyCost is required")
          @Positive(message = "hourlyCost must be positive")
          BigDecimal hourlyCost,
      @NotNull(message = "effectiveFrom is required") LocalDate effectiveFrom,
      LocalDate effectiveTo) {}

  public record CostRateResponse(
      UUID id,
      UUID memberId,
      String memberName,
      String currency,
      BigDecimal hourlyCost,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      Instant createdAt,
      Instant updatedAt) {

    public static CostRateResponse from(CostRate rate, Map<UUID, String> memberNames) {
      return new CostRateResponse(
          rate.getId(),
          rate.getMemberId(),
          memberNames.getOrDefault(rate.getMemberId(), ""),
          rate.getCurrency(),
          rate.getHourlyCost(),
          rate.getEffectiveFrom(),
          rate.getEffectiveTo(),
          rate.getCreatedAt(),
          rate.getUpdatedAt());
    }
  }
}
