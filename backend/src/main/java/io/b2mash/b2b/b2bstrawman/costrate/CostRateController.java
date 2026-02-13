package io.b2mash.b2b.b2bstrawman.costrate;

import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
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
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
  private final MemberRepository memberRepository;

  public CostRateController(CostRateService costRateService, MemberRepository memberRepository) {
    this.costRateService = costRateService;
    this.memberRepository = memberRepository;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ListResponse<CostRateResponse>> listCostRates(
      @RequestParam(required = false) UUID memberId) {

    var rates = costRateService.listCostRates(memberId);
    var memberNames = resolveMemberNames(rates);
    var content = rates.stream().map(r -> CostRateResponse.from(r, memberNames)).toList();
    return ResponseEntity.ok(new ListResponse<>(content));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CostRateResponse> createCostRate(
      @Valid @RequestBody CreateCostRateRequest request) {
    UUID actorMemberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var rate =
        costRateService.createCostRate(
            request.memberId(),
            request.currency(),
            request.hourlyCost(),
            request.effectiveFrom(),
            request.effectiveTo(),
            actorMemberId,
            orgRole);

    var memberNames = resolveMemberNames(List.of(rate));
    return ResponseEntity.created(URI.create("/api/cost-rates/" + rate.getId()))
        .body(CostRateResponse.from(rate, memberNames));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CostRateResponse> updateCostRate(
      @PathVariable UUID id, @Valid @RequestBody UpdateCostRateRequest request) {
    UUID actorMemberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var rate =
        costRateService.updateCostRate(
            id,
            request.hourlyCost(),
            request.currency(),
            request.effectiveFrom(),
            request.effectiveTo(),
            actorMemberId,
            orgRole);

    var memberNames = resolveMemberNames(List.of(rate));
    return ResponseEntity.ok(CostRateResponse.from(rate, memberNames));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteCostRate(@PathVariable UUID id) {
    UUID actorMemberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    costRateService.deleteCostRate(id, actorMemberId, orgRole);
    return ResponseEntity.noContent().build();
  }

  // --- Name Resolution ---

  private Map<UUID, String> resolveMemberNames(List<CostRate> rates) {
    var memberIds =
        rates.stream().map(CostRate::getMemberId).filter(Objects::nonNull).distinct().toList();

    if (memberIds.isEmpty()) {
      return Map.of();
    }

    return memberRepository.findAllById(memberIds).stream()
        .collect(Collectors.toMap(Member::getId, Member::getName, (a, b) -> a));
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
          memberNames.get(rate.getMemberId()),
          rate.getCurrency(),
          rate.getHourlyCost(),
          rate.getEffectiveFrom(),
          rate.getEffectiveTo(),
          rate.getCreatedAt(),
          rate.getUpdatedAt());
    }
  }
}
