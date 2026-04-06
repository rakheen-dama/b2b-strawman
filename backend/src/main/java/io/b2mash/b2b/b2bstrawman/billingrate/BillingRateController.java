package io.b2mash.b2b.b2bstrawman.billingrate;

import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
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
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/billing-rates")
public class BillingRateController {

  private final BillingRateService billingRateService;

  public BillingRateController(BillingRateService billingRateService) {
    this.billingRateService = billingRateService;
  }

  @GetMapping
  public ResponseEntity<ListResponse<BillingRateResponse>> listRates(
      @RequestParam(required = false) UUID memberId,
      @RequestParam(required = false) UUID projectId,
      @RequestParam(required = false) UUID customerId) {

    var rates = billingRateService.listRates(memberId, projectId, customerId);
    var names = billingRateService.resolveNames(rates);
    var content = rates.stream().map(r -> BillingRateResponse.from(r, names)).toList();
    return ResponseEntity.ok(new ListResponse<>(content));
  }

  // ORG_MEMBER included: project leads (who are ORG_MEMBERs) need write access for
  // project-scoped rate overrides. Fine-grained permission is enforced in the service layer.
  @PostMapping
  public ResponseEntity<BillingRateResponse> createRate(
      @Valid @RequestBody CreateBillingRateRequest request, ActorContext actor) {

    var rate =
        billingRateService.createRate(
            request.memberId(),
            request.projectId(),
            request.customerId(),
            request.currency(),
            request.hourlyRate(),
            request.effectiveFrom(),
            request.effectiveTo(),
            actor);

    var names = billingRateService.resolveNames(List.of(rate));
    return ResponseEntity.created(URI.create("/api/billing-rates/" + rate.getId()))
        .body(BillingRateResponse.from(rate, names));
  }

  // ORG_MEMBER included: project leads (who are ORG_MEMBERs) need write access for
  // project-scoped rate overrides. Fine-grained permission is enforced in the service layer.
  @PutMapping("/{id}")
  public ResponseEntity<BillingRateResponse> updateRate(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateBillingRateRequest request,
      ActorContext actor) {

    var rate =
        billingRateService.updateRate(
            id,
            request.hourlyRate(),
            request.currency(),
            request.effectiveFrom(),
            request.effectiveTo(),
            actor);

    var names = billingRateService.resolveNames(List.of(rate));
    return ResponseEntity.ok(BillingRateResponse.from(rate, names));
  }

  // ORG_MEMBER included: project leads (who are ORG_MEMBERs) need write access for
  // project-scoped rate overrides. Fine-grained permission is enforced in the service layer.
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteRate(@PathVariable UUID id, ActorContext actor) {

    billingRateService.deleteRate(id, actor);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/resolve")
  public ResponseEntity<ResolvedRateResponse> resolveRate(
      @RequestParam UUID memberId,
      @RequestParam UUID projectId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

    var resolved = billingRateService.resolveRate(memberId, projectId, date);
    if (resolved.isPresent()) {
      var r = resolved.get();
      return ResponseEntity.ok(
          new ResolvedRateResponse(r.hourlyRate(), r.currency(), r.source(), r.billingRateId()));
    }
    return ResponseEntity.ok(new ResolvedRateResponse(null, null, null, null));
  }

  // --- DTOs ---

  public record ListResponse<T>(List<T> content) {}

  public record CreateBillingRateRequest(
      @NotNull(message = "memberId is required") UUID memberId,
      UUID projectId,
      UUID customerId,
      @NotBlank(message = "currency is required")
          @Size(min = 3, max = 3, message = "currency must be exactly 3 characters")
          String currency,
      @NotNull(message = "hourlyRate is required")
          @Positive(message = "hourlyRate must be positive")
          BigDecimal hourlyRate,
      @NotNull(message = "effectiveFrom is required") LocalDate effectiveFrom,
      LocalDate effectiveTo) {}

  public record UpdateBillingRateRequest(
      @NotBlank(message = "currency is required")
          @Size(min = 3, max = 3, message = "currency must be exactly 3 characters")
          String currency,
      @NotNull(message = "hourlyRate is required")
          @Positive(message = "hourlyRate must be positive")
          BigDecimal hourlyRate,
      @NotNull(message = "effectiveFrom is required") LocalDate effectiveFrom,
      LocalDate effectiveTo) {}

  public record BillingRateResponse(
      UUID id,
      UUID memberId,
      String memberName,
      UUID projectId,
      String projectName,
      UUID customerId,
      String customerName,
      String scope,
      String currency,
      BigDecimal hourlyRate,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      Instant createdAt,
      Instant updatedAt) {

    public static BillingRateResponse from(BillingRate rate, BillingRateService.NameLookup names) {
      return new BillingRateResponse(
          rate.getId(),
          rate.getMemberId(),
          rate.getMemberId() != null ? names.members().getOrDefault(rate.getMemberId(), "") : null,
          rate.getProjectId(),
          rate.getProjectId() != null ? names.projects().get(rate.getProjectId()) : null,
          rate.getCustomerId(),
          rate.getCustomerId() != null ? names.customers().get(rate.getCustomerId()) : null,
          rate.getScope(),
          rate.getCurrency(),
          rate.getHourlyRate(),
          rate.getEffectiveFrom(),
          rate.getEffectiveTo(),
          rate.getCreatedAt(),
          rate.getUpdatedAt());
    }
  }

  public record ResolvedRateResponse(
      BigDecimal hourlyRate, String currency, String source, UUID billingRateId) {}
}
