package io.b2mash.b2b.b2bstrawman.costrate;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
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
public class CostRateService {

  private static final Logger log = LoggerFactory.getLogger(CostRateService.class);
  private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 12, 31);

  private final CostRateRepository costRateRepository;
  private final AuditService auditService;

  /**
   * Resolved cost rate result containing the hourly cost, currency, and the cost rate ID that was
   * matched.
   */
  public record ResolvedCostRate(BigDecimal hourlyCost, String currency, UUID costRateId) {}

  public CostRateService(CostRateRepository costRateRepository, AuditService auditService) {
    this.costRateRepository = costRateRepository;
    this.auditService = auditService;
  }

  /**
   * Resolves the applicable cost rate for a member at a given date. Simple single-level lookup per
   * ADR-043 -- no project/customer hierarchy.
   *
   * @param memberId the member whose cost rate to resolve
   * @param date the effective date to check against
   * @return the resolved cost rate, or empty if no rate is configured
   */
  @Transactional(readOnly = true)
  public Optional<ResolvedCostRate> resolveCostRate(UUID memberId, LocalDate date) {
    var rates = costRateRepository.findByMemberIdAndDate(memberId, date);
    if (!rates.isEmpty()) {
      var rate = rates.getFirst();
      return Optional.of(
          new ResolvedCostRate(rate.getHourlyCost(), rate.getCurrency(), rate.getId()));
    }
    return Optional.empty();
  }

  /**
   * Creates a new cost rate. Admin/owner only.
   *
   * @param memberId the member this cost rate applies to
   * @param currency ISO 4217 currency code
   * @param hourlyCost the hourly cost amount (must be positive)
   * @param effectiveFrom start date of rate effectiveness
   * @param effectiveTo optional end date (null for open-ended)
   * @param actorMemberId the UUID of the member performing the action
   * @param orgRole the org role of the actor
   * @return the created CostRate
   */
  @Transactional
  public CostRate createCostRate(
      UUID memberId,
      String currency,
      BigDecimal hourlyCost,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      UUID actorMemberId,
      String orgRole) {

    requireAdminOrOwner(orgRole);

    LocalDate overlapEnd = effectiveTo != null ? effectiveTo : FAR_FUTURE;
    var overlapping =
        costRateRepository.findOverlapping(memberId, effectiveFrom, overlapEnd, UUID.randomUUID());
    if (!overlapping.isEmpty()) {
      throw new ResourceConflictException(
          "Overlapping cost rate", "A cost rate already exists for this member and date range");
    }

    var rate = new CostRate(memberId, currency, hourlyCost, effectiveFrom, effectiveTo);
    rate = costRateRepository.save(rate);

    log.info("Created cost rate {} for member {}", rate.getId(), memberId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("cost_rate.created")
            .entityType("cost_rate")
            .entityId(rate.getId())
            .details(
                Map.of(
                    "member_id", memberId.toString(),
                    "currency", currency,
                    "hourly_cost", hourlyCost.toString()))
            .build());

    return rate;
  }

  /**
   * Updates an existing cost rate. Validates overlap excluding the rate being updated.
   *
   * @param id the cost rate ID to update
   * @param hourlyCost the new hourly cost
   * @param currency the new currency code
   * @param effectiveFrom the new start date
   * @param effectiveTo the new end date (nullable)
   * @param actorMemberId the UUID of the member performing the action
   * @param orgRole the org role of the actor
   * @return the updated CostRate
   */
  @Transactional
  public CostRate updateCostRate(
      UUID id,
      BigDecimal hourlyCost,
      String currency,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      UUID actorMemberId,
      String orgRole) {

    requireAdminOrOwner(orgRole);

    var rate =
        costRateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("CostRate", id));

    LocalDate overlapEnd = effectiveTo != null ? effectiveTo : FAR_FUTURE;
    var overlapping =
        costRateRepository.findOverlapping(rate.getMemberId(), effectiveFrom, overlapEnd, id);
    if (!overlapping.isEmpty()) {
      throw new ResourceConflictException(
          "Overlapping cost rate", "A cost rate already exists for this member and date range");
    }

    String oldCost = rate.getHourlyCost().toString();
    String oldCurrency = rate.getCurrency();
    rate.update(hourlyCost, currency, effectiveFrom, effectiveTo);
    rate = costRateRepository.save(rate);

    log.info("Updated cost rate {}", id);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("cost_rate.updated")
            .entityType("cost_rate")
            .entityId(rate.getId())
            .details(
                Map.of(
                    "hourly_cost", Map.of("from", oldCost, "to", hourlyCost.toString()),
                    "currency", Map.of("from", oldCurrency, "to", currency)))
            .build());

    return rate;
  }

  /**
   * Deletes a cost rate by ID. Admin/owner only.
   *
   * @param id the cost rate ID to delete
   * @param actorMemberId the UUID of the member performing the action
   * @param orgRole the org role of the actor
   */
  @Transactional
  public void deleteCostRate(UUID id, UUID actorMemberId, String orgRole) {
    requireAdminOrOwner(orgRole);

    var rate =
        costRateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("CostRate", id));

    costRateRepository.delete(rate);

    log.info("Deleted cost rate {} for member {}", id, rate.getMemberId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("cost_rate.deleted")
            .entityType("cost_rate")
            .entityId(id)
            .details(Map.of("member_id", rate.getMemberId().toString()))
            .build());
  }

  /**
   * Lists cost rates, optionally filtered by member.
   *
   * @param memberId optional member filter; if null, returns all cost rates
   * @param orgRole the org role of the actor (defense-in-depth check when listing all)
   * @return list of cost rates ordered by effectiveFrom DESC
   */
  @Transactional(readOnly = true)
  public List<CostRate> listCostRates(UUID memberId, String orgRole) {
    if (memberId == null) {
      requireAdminOrOwner(orgRole);
      return costRateRepository.findAllOrderByEffectiveFromDesc();
    }
    return costRateRepository.findByMemberId(memberId);
  }

  /**
   * Enforces admin/owner-only access for cost rate management per ADR-043. Regular members and
   * project leads cannot see internal labor costs.
   */
  private void requireAdminOrOwner(String orgRole) {
    if (Roles.ORG_ADMIN.equals(orgRole) || Roles.ORG_OWNER.equals(orgRole)) {
      return;
    }
    throw new ForbiddenException(
        "Insufficient permissions", "Only admins and owners can manage cost rates");
  }
}
