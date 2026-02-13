package io.b2mash.b2b.b2bstrawman.billingrate;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
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
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/billing-rates")
public class BillingRateController {

  private final BillingRateService billingRateService;
  private final MemberRepository memberRepository;
  private final ProjectRepository projectRepository;
  private final CustomerRepository customerRepository;

  public BillingRateController(
      BillingRateService billingRateService,
      MemberRepository memberRepository,
      ProjectRepository projectRepository,
      CustomerRepository customerRepository) {
    this.billingRateService = billingRateService;
    this.memberRepository = memberRepository;
    this.projectRepository = projectRepository;
    this.customerRepository = customerRepository;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ListResponse<BillingRateResponse>> listRates(
      @RequestParam(required = false) UUID memberId,
      @RequestParam(required = false) UUID projectId,
      @RequestParam(required = false) UUID customerId) {

    var rates = billingRateService.listRates(memberId, projectId, customerId);
    var names = resolveNames(rates);
    var content = rates.stream().map(r -> BillingRateResponse.from(r, names)).toList();
    return ResponseEntity.ok(new ListResponse<>(content));
  }

  // ORG_MEMBER included: project leads (who are ORG_MEMBERs) need write access for
  // project-scoped rate overrides. Fine-grained permission is enforced in the service layer.
  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<BillingRateResponse> createRate(
      @Valid @RequestBody CreateBillingRateRequest request) {
    UUID actorMemberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var rate =
        billingRateService.createRate(
            request.memberId(),
            request.projectId(),
            request.customerId(),
            request.currency(),
            request.hourlyRate(),
            request.effectiveFrom(),
            request.effectiveTo(),
            actorMemberId,
            orgRole);

    var names = resolveNames(List.of(rate));
    return ResponseEntity.created(URI.create("/api/billing-rates/" + rate.getId()))
        .body(BillingRateResponse.from(rate, names));
  }

  // ORG_MEMBER included: project leads (who are ORG_MEMBERs) need write access for
  // project-scoped rate overrides. Fine-grained permission is enforced in the service layer.
  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<BillingRateResponse> updateRate(
      @PathVariable UUID id, @Valid @RequestBody UpdateBillingRateRequest request) {
    UUID actorMemberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var rate =
        billingRateService.updateRate(
            id,
            request.hourlyRate(),
            request.currency(),
            request.effectiveFrom(),
            request.effectiveTo(),
            actorMemberId,
            orgRole);

    var names = resolveNames(List.of(rate));
    return ResponseEntity.ok(BillingRateResponse.from(rate, names));
  }

  // ORG_MEMBER included: project leads (who are ORG_MEMBERs) need write access for
  // project-scoped rate overrides. Fine-grained permission is enforced in the service layer.
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteRate(@PathVariable UUID id) {
    UUID actorMemberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    billingRateService.deleteRate(id, actorMemberId, orgRole);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/resolve")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
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

  // --- Name Resolution ---

  private NameLookup resolveNames(List<BillingRate> rates) {
    var memberIds =
        rates.stream().map(BillingRate::getMemberId).filter(Objects::nonNull).distinct().toList();
    var projectIds =
        rates.stream().map(BillingRate::getProjectId).filter(Objects::nonNull).distinct().toList();
    var customerIds =
        rates.stream().map(BillingRate::getCustomerId).filter(Objects::nonNull).distinct().toList();

    var memberNames =
        memberIds.isEmpty()
            ? Map.<UUID, String>of()
            : memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getName, (a, b) -> a));

    var projectNames =
        projectIds.isEmpty()
            ? Map.<UUID, String>of()
            : projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(p -> p.getId(), p -> p.getName(), (a, b) -> a));

    var customerNames =
        customerIds.isEmpty()
            ? Map.<UUID, String>of()
            : customerRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName(), (a, b) -> a));

    return new NameLookup(memberNames, projectNames, customerNames);
  }

  // --- DTOs ---

  record NameLookup(
      Map<UUID, String> members, Map<UUID, String> projects, Map<UUID, String> customers) {}

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

    public static BillingRateResponse from(BillingRate rate, NameLookup names) {
      return new BillingRateResponse(
          rate.getId(),
          rate.getMemberId(),
          names.members().get(rate.getMemberId()),
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
