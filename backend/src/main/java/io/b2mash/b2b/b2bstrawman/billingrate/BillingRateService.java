package io.b2mash.b2b.b2bstrawman.billingrate;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingRateService {

  private static final Logger log = LoggerFactory.getLogger(BillingRateService.class);
  private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 12, 31);

  private final BillingRateRepository billingRateRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final CustomerRepository customerRepository;
  private final ProjectRepository projectRepository;
  private final ProjectAccessService projectAccessService;
  private final AuditService auditService;

  public BillingRateService(
      BillingRateRepository billingRateRepository,
      CustomerProjectRepository customerProjectRepository,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      ProjectAccessService projectAccessService,
      AuditService auditService) {
    this.billingRateRepository = billingRateRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
    this.projectAccessService = projectAccessService;
    this.auditService = auditService;
  }

  /**
   * Resolved rate result containing the hourly rate, currency, resolution source, and the billing
   * rate ID that was matched.
   */
  public record ResolvedRate(
      BigDecimal hourlyRate, String currency, String source, UUID billingRateId) {}

  /**
   * Resolves the applicable billing rate for a member on a project at a given date using the
   * three-level cascade per ADR-039:
   *
   * <ol>
   *   <li>Project override (member + project, no customer)
   *   <li>Customer override (member + project's first customer, no project)
   *   <li>Member default (member only, no project or customer)
   * </ol>
   *
   * @param memberId the member whose rate to resolve
   * @param projectId the project context for resolution
   * @param date the effective date to check against
   * @return the resolved rate, or empty if no rate is configured at any level
   */
  @Transactional(readOnly = true)
  public Optional<ResolvedRate> resolveRate(UUID memberId, UUID projectId, LocalDate date) {
    // 1. Try project override
    var projectRates = billingRateRepository.findProjectOverride(memberId, projectId, date);
    if (!projectRates.isEmpty()) {
      var rate = projectRates.getFirst();
      return Optional.of(
          new ResolvedRate(
              rate.getHourlyRate(), rate.getCurrency(), "PROJECT_OVERRIDE", rate.getId()));
    }

    // 2. Try customer override via CustomerProject join
    var customerId = customerProjectRepository.findFirstCustomerByProjectId(projectId);
    if (customerId.isPresent()) {
      var customerRates =
          billingRateRepository.findCustomerOverride(memberId, customerId.get(), date);
      if (!customerRates.isEmpty()) {
        var rate = customerRates.getFirst();
        return Optional.of(
            new ResolvedRate(
                rate.getHourlyRate(), rate.getCurrency(), "CUSTOMER_OVERRIDE", rate.getId()));
      }
    }

    // 3. Try member default
    var defaultRates = billingRateRepository.findMemberDefault(memberId, date);
    if (!defaultRates.isEmpty()) {
      var rate = defaultRates.getFirst();
      return Optional.of(
          new ResolvedRate(
              rate.getHourlyRate(), rate.getCurrency(), "MEMBER_DEFAULT", rate.getId()));
    }

    return Optional.empty();
  }

  /**
   * Creates a new billing rate. Validates scope (project/customer mutual exclusivity), overlap, and
   * permissions.
   *
   * @param memberId the member this rate applies to
   * @param projectId optional project scope (null for member default or customer override)
   * @param customerId optional customer scope (null for member default or project override)
   * @param currency ISO 4217 currency code
   * @param hourlyRate the hourly rate amount (must be positive)
   * @param effectiveFrom start date of rate effectiveness
   * @param effectiveTo optional end date (null for open-ended)
   * @param actorMemberId the UUID of the member performing the action
   * @param orgRole the org role of the actor
   * @return the created BillingRate
   */
  @Transactional
  public BillingRate createRate(
      UUID memberId,
      UUID projectId,
      UUID customerId,
      String currency,
      BigDecimal hourlyRate,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      UUID actorMemberId,
      String orgRole) {

    validateScope(projectId, customerId);
    validateProjectExists(projectId);
    validateCustomerExists(customerId);
    requirePermission(projectId, actorMemberId, orgRole);

    LocalDate overlapEnd = effectiveTo != null ? effectiveTo : FAR_FUTURE;
    var overlapping =
        billingRateRepository.findOverlapping(
            memberId, projectId, customerId, effectiveFrom, overlapEnd, UUID.randomUUID());
    if (!overlapping.isEmpty()) {
      throw new ResourceConflictException(
          "Overlapping billing rate",
          "A billing rate already exists for this scope and date range");
    }

    var rate =
        new BillingRate(
            memberId, projectId, customerId, currency, hourlyRate, effectiveFrom, effectiveTo);
    rate = billingRateRepository.save(rate);

    log.info(
        "Created billing rate {} for member {} scope={}", rate.getId(), memberId, rate.getScope());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("billing_rate.created")
            .entityType("billing_rate")
            .entityId(rate.getId())
            .details(
                Map.of(
                    "member_id", memberId.toString(),
                    "scope", rate.getScope(),
                    "currency", currency,
                    "hourly_rate", hourlyRate.toString()))
            .build());

    return rate;
  }

  /**
   * Updates an existing billing rate. Validates overlap excluding the rate being updated.
   *
   * @param id the billing rate ID to update
   * @param hourlyRate the new hourly rate
   * @param currency the new currency code
   * @param effectiveFrom the new start date
   * @param effectiveTo the new end date (nullable)
   * @param actorMemberId the UUID of the member performing the action
   * @param orgRole the org role of the actor
   * @return the updated BillingRate
   */
  @Transactional
  public BillingRate updateRate(
      UUID id,
      BigDecimal hourlyRate,
      String currency,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      UUID actorMemberId,
      String orgRole) {

    var rate =
        billingRateRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("BillingRate", id));

    requirePermission(rate.getProjectId(), actorMemberId, orgRole);

    LocalDate overlapEnd = effectiveTo != null ? effectiveTo : FAR_FUTURE;
    var overlapping =
        billingRateRepository.findOverlapping(
            rate.getMemberId(),
            rate.getProjectId(),
            rate.getCustomerId(),
            effectiveFrom,
            overlapEnd,
            id);
    if (!overlapping.isEmpty()) {
      throw new ResourceConflictException(
          "Overlapping billing rate",
          "A billing rate already exists for this scope and date range");
    }

    String oldRate = rate.getHourlyRate().toString();
    String oldCurrency = rate.getCurrency();
    rate.update(hourlyRate, currency, effectiveFrom, effectiveTo);
    rate = billingRateRepository.save(rate);

    log.info("Updated billing rate {} scope={}", id, rate.getScope());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("billing_rate.updated")
            .entityType("billing_rate")
            .entityId(rate.getId())
            .details(
                Map.of(
                    "hourly_rate", Map.of("from", oldRate, "to", hourlyRate.toString()),
                    "currency", Map.of("from", oldCurrency, "to", currency)))
            .build());

    return rate;
  }

  /**
   * Deletes a billing rate by ID.
   *
   * @param id the billing rate ID to delete
   * @param actorMemberId the UUID of the member performing the action
   * @param orgRole the org role of the actor
   */
  @Transactional
  public void deleteRate(UUID id, UUID actorMemberId, String orgRole) {
    var rate =
        billingRateRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("BillingRate", id));

    requirePermission(rate.getProjectId(), actorMemberId, orgRole);

    billingRateRepository.delete(rate);

    log.info(
        "Deleted billing rate {} for member {} scope={}", id, rate.getMemberId(), rate.getScope());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("billing_rate.deleted")
            .entityType("billing_rate")
            .entityId(id)
            .details(
                Map.of(
                    "member_id", rate.getMemberId().toString(),
                    "scope", rate.getScope()))
            .build());
  }

  /**
   * Lists billing rates with optional filters.
   *
   * @param memberId optional member filter
   * @param projectId optional project filter
   * @param customerId optional customer filter
   * @return list of matching billing rates
   */
  @Transactional(readOnly = true)
  public List<BillingRate> listRates(UUID memberId, UUID projectId, UUID customerId) {
    return billingRateRepository.findByFilters(memberId, projectId, customerId);
  }

  /**
   * Validates that projectId and customerId are mutually exclusive. Compound overrides (both set)
   * are not supported per ADR-039.
   */
  private void validateScope(UUID projectId, UUID customerId) {
    if (projectId != null && customerId != null) {
      throw new InvalidStateException(
          "Invalid billing rate scope",
          "A billing rate cannot have both projectId and customerId set. Use separate rates for project and customer overrides.");
    }
  }

  /**
   * Enforces permission rules:
   *
   * <ul>
   *   <li>Admin/Owner: can manage rates for any scope
   *   <li>Project Lead: can only manage project overrides for projects they lead
   *   <li>Regular Member: cannot manage rates (403)
   * </ul>
   */
  private void requirePermission(UUID projectId, UUID actorMemberId, String orgRole) {
    if (Roles.ORG_ADMIN.equals(orgRole) || Roles.ORG_OWNER.equals(orgRole)) {
      return;
    }

    // Project leads can manage project overrides for their projects
    if (projectId != null) {
      var access = projectAccessService.checkAccess(projectId, actorMemberId, orgRole);
      if (access.canEdit()) {
        return;
      }
    }

    throw new ForbiddenException(
        "Insufficient permissions",
        "Only admins, owners, or project leads can manage billing rates");
  }

  /**
   * Validates that the project exists in the current tenant if projectId is provided. Uses
   * findOneById which respects Hibernate @Filter for tenant isolation.
   */
  private void validateProjectExists(UUID projectId) {
    if (projectId != null && projectRepository.findOneById(projectId).isEmpty()) {
      throw new ResourceNotFoundException("Project", projectId);
    }
  }

  /**
   * Validates that the customer exists in the current tenant if customerId is provided. Uses
   * findOneById which respects Hibernate @Filter for tenant isolation.
   */
  private void validateCustomerExists(UUID customerId) {
    if (customerId != null && customerRepository.findOneById(customerId).isEmpty()) {
      throw new ResourceNotFoundException("Customer", customerId);
    }
  }
}
