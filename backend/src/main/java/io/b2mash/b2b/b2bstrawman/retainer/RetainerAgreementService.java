package io.b2mash.b2b.b2bstrawman.retainer;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.retainer.dto.CreateRetainerRequest;
import io.b2mash.b2b.b2bstrawman.retainer.dto.PeriodSummary;
import io.b2mash.b2b.b2bstrawman.retainer.dto.RetainerResponse;
import io.b2mash.b2b.b2bstrawman.retainer.dto.RetainerSummaryResponse;
import io.b2mash.b2b.b2bstrawman.retainer.dto.UpdateRetainerRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetainerAgreementService {

  private final RetainerAgreementRepository agreementRepository;
  private final RetainerPeriodRepository periodRepository;
  private final CustomerRepository customerRepository;
  private final AuditService auditService;
  private final NotificationService notificationService;
  private final MemberRepository memberRepository;

  public RetainerAgreementService(
      RetainerAgreementRepository agreementRepository,
      RetainerPeriodRepository periodRepository,
      CustomerRepository customerRepository,
      AuditService auditService,
      NotificationService notificationService,
      MemberRepository memberRepository) {
    this.agreementRepository = agreementRepository;
    this.periodRepository = periodRepository;
    this.customerRepository = customerRepository;
    this.auditService = auditService;
    this.notificationService = notificationService;
    this.memberRepository = memberRepository;
  }

  @Transactional
  public RetainerResponse createRetainer(CreateRetainerRequest request, UUID actorMemberId) {
    // 1. Load customer
    var customer =
        customerRepository
            .findById(request.customerId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

    // 2. Validate lifecycle status
    if (customer.getLifecycleStatus() == LifecycleStatus.OFFBOARDED
        || customer.getLifecycleStatus() == LifecycleStatus.PROSPECT) {
      throw new InvalidStateException(
          "Customer not eligible for retainer",
          "Cannot create retainer for customer with lifecycle status: "
              + customer.getLifecycleStatus());
    }

    // 3. Check no existing active/paused retainer
    agreementRepository
        .findActiveOrPausedByCustomerId(request.customerId())
        .ifPresent(
            existing -> {
              throw new ResourceConflictException(
                  "Duplicate retainer", "Customer already has an active or paused retainer");
            });

    // 4. Validate type-specific fields
    validateTypeSpecificFields(request);

    // 5. Persist agreement
    var agreement =
        new RetainerAgreement(
            request.customerId(),
            request.name(),
            request.type(),
            request.frequency(),
            request.startDate(),
            request.endDate(),
            request.allocatedHours(),
            request.periodFee(),
            request.rolloverPolicy(),
            request.rolloverCapHours(),
            request.notes(),
            actorMemberId);
    agreement = agreementRepository.save(agreement);

    // 6. Calculate first period end
    var periodEnd = agreement.getFrequency().calculateNextEnd(agreement.getStartDate());

    // 7. Create first period
    var period =
        new RetainerPeriod(
            agreement.getId(),
            agreement.getStartDate(),
            periodEnd,
            agreement.getAllocatedHours(),
            agreement.getAllocatedHours(),
            BigDecimal.ZERO);
    period = periodRepository.save(period);

    // 8. Audit events
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("retainer.created")
            .entityType("RETAINER_AGREEMENT")
            .entityId(agreement.getId())
            .details(
                Map.of(
                    "agreementId", agreement.getId().toString(),
                    "customerId", agreement.getCustomerId().toString(),
                    "customerName", customer.getName(),
                    "name", agreement.getName(),
                    "type", agreement.getType().name(),
                    "frequency", agreement.getFrequency().name(),
                    "rolloverPolicy", agreement.getRolloverPolicy().name()))
            .build());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("retainer.period.opened")
            .entityType("RETAINER_PERIOD")
            .entityId(period.getId())
            .details(
                Map.of(
                    "periodId", period.getId().toString(),
                    "agreementId", agreement.getId().toString(),
                    "periodStart", period.getPeriodStart().toString(),
                    "periodEnd", period.getPeriodEnd().toString()))
            .build());

    // 9. Build and return response
    var memberNames = resolveRetainerMemberNames(agreement);
    return RetainerResponse.from(
        agreement, customer.getName(), PeriodSummary.from(period), null, memberNames);
  }

  @Transactional
  public RetainerResponse updateRetainer(
      UUID id, UpdateRetainerRequest request, UUID actorMemberId) {
    // 1. Load agreement
    var agreement =
        agreementRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RetainerAgreement", id));

    // 2. Reject updates to terminated retainers
    if (agreement.getStatus() == RetainerStatus.TERMINATED) {
      throw new InvalidStateException(
          "Invalid retainer state", "Cannot update a terminated retainer");
    }

    // 3. If rolloverPolicy is null in update, keep the existing value
    var effectiveRolloverPolicy =
        request.rolloverPolicy() != null ? request.rolloverPolicy() : agreement.getRolloverPolicy();
    var effectiveRolloverCapHours =
        request.rolloverPolicy() != null
            ? request.rolloverCapHours()
            : agreement.getRolloverCapHours();

    // 5. Validate CARRY_CAPPED rollover cap
    if (effectiveRolloverPolicy == RolloverPolicy.CARRY_CAPPED
        && (effectiveRolloverCapHours == null
            || effectiveRolloverCapHours.compareTo(BigDecimal.ZERO) <= 0)) {
      throw new InvalidStateException(
          "Missing field", "CARRY_CAPPED rollover policy requires rolloverCapHours > 0");
    }

    // 6. Snapshot old values for audit diff
    var oldValues = snapshotValues(agreement);

    // 7. Update terms
    agreement.updateTerms(
        request.name(),
        request.allocatedHours(),
        request.periodFee(),
        effectiveRolloverPolicy,
        effectiveRolloverCapHours,
        request.endDate(),
        request.notes());

    // 8. Save agreement
    agreementRepository.save(agreement);

    // 9. Load customer for name
    var customer =
        customerRepository
            .findById(agreement.getCustomerId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Customer", agreement.getCustomerId()));

    // 10. Audit RETAINER_UPDATED with diff
    var changes = buildDiff(oldValues, agreement);
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("retainer.updated")
            .entityType("RETAINER_AGREEMENT")
            .entityId(agreement.getId())
            .details(Map.of("agreementId", agreement.getId().toString(), "changes", changes))
            .build());

    // 11. Load current open period
    var currentPeriod =
        periodRepository
            .findByAgreementIdAndStatus(id, PeriodStatus.OPEN)
            .map(PeriodSummary::from)
            .orElse(null);

    // 12. Return response
    var memberNames = resolveRetainerMemberNames(agreement);
    return RetainerResponse.from(agreement, customer.getName(), currentPeriod, null, memberNames);
  }

  @Transactional
  public RetainerResponse pauseRetainer(UUID id, UUID actorMemberId) {
    var agreement =
        agreementRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RetainerAgreement", id));

    try {
      agreement.pause();
    } catch (IllegalStateException e) {
      throw new InvalidStateException("Invalid retainer state", e.getMessage());
    }

    agreementRepository.save(agreement);

    var customer =
        customerRepository
            .findById(agreement.getCustomerId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Customer", agreement.getCustomerId()));

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("retainer.paused")
            .entityType("RETAINER_AGREEMENT")
            .entityId(agreement.getId())
            .details(
                Map.of(
                    "agreementId", agreement.getId().toString(),
                    "customerId", agreement.getCustomerId().toString(),
                    "customerName", customer.getName(),
                    "actorMemberId", actorMemberId.toString()))
            .build());

    var currentPeriod =
        periodRepository
            .findByAgreementIdAndStatus(id, PeriodStatus.OPEN)
            .map(p -> PeriodSummary.from(p))
            .orElse(null);

    var memberNames = resolveRetainerMemberNames(agreement);
    return RetainerResponse.from(agreement, customer.getName(), currentPeriod, null, memberNames);
  }

  @Transactional
  public RetainerResponse resumeRetainer(UUID id, UUID actorMemberId) {
    var agreement =
        agreementRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RetainerAgreement", id));

    try {
      agreement.resume();
    } catch (IllegalStateException e) {
      throw new InvalidStateException("Invalid retainer state", e.getMessage());
    }

    agreementRepository.save(agreement);

    var customer =
        customerRepository
            .findById(agreement.getCustomerId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Customer", agreement.getCustomerId()));

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("retainer.resumed")
            .entityType("RETAINER_AGREEMENT")
            .entityId(agreement.getId())
            .details(
                Map.of(
                    "agreementId", agreement.getId().toString(),
                    "customerId", agreement.getCustomerId().toString(),
                    "customerName", customer.getName(),
                    "actorMemberId", actorMemberId.toString()))
            .build());

    var currentPeriod =
        periodRepository
            .findByAgreementIdAndStatus(id, PeriodStatus.OPEN)
            .map(p -> PeriodSummary.from(p))
            .orElse(null);

    var memberNames = resolveRetainerMemberNames(agreement);
    return RetainerResponse.from(agreement, customer.getName(), currentPeriod, null, memberNames);
  }

  @Transactional
  public RetainerResponse terminateRetainer(UUID id, UUID actorMemberId) {
    var agreement =
        agreementRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RetainerAgreement", id));

    try {
      agreement.terminate();
    } catch (IllegalStateException e) {
      throw new InvalidStateException("Invalid retainer state", e.getMessage());
    }

    agreementRepository.save(agreement);

    var customer =
        customerRepository
            .findById(agreement.getCustomerId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Customer", agreement.getCustomerId()));

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("retainer.terminated")
            .entityType("RETAINER_AGREEMENT")
            .entityId(agreement.getId())
            .details(
                Map.of(
                    "agreementId", agreement.getId().toString(),
                    "customerId", agreement.getCustomerId().toString(),
                    "customerName", customer.getName(),
                    "actorMemberId", actorMemberId.toString()))
            .build());

    notificationService.notifyAdminsAndOwners(
        "RETAINER_TERMINATED",
        "Retainer for " + customer.getName() + " has been terminated",
        "Agreement: %s, Customer: %s".formatted(agreement.getName(), customer.getName()),
        "RETAINER_AGREEMENT",
        agreement.getId());

    var currentPeriod =
        periodRepository
            .findByAgreementIdAndStatus(id, PeriodStatus.OPEN)
            .map(p -> PeriodSummary.from(p))
            .orElse(null);

    var memberNames = resolveRetainerMemberNames(agreement);
    return RetainerResponse.from(agreement, customer.getName(), currentPeriod, null, memberNames);
  }

  @Transactional(readOnly = true)
  public List<RetainerResponse> listRetainers(RetainerStatus status, UUID customerId) {
    List<RetainerAgreement> agreements;

    if (customerId != null && status != null) {
      agreements = agreementRepository.findByCustomerIdAndStatus(customerId, status);
    } else if (customerId != null) {
      agreements = agreementRepository.findByCustomerId(customerId);
    } else if (status != null) {
      agreements = agreementRepository.findByStatus(status);
    } else {
      agreements = agreementRepository.findAll();
    }

    if (agreements.isEmpty()) {
      return List.of();
    }

    // Batch load customers
    var customerIds = agreements.stream().map(RetainerAgreement::getCustomerId).distinct().toList();
    var customerMap =
        customerRepository.findAllById(customerIds).stream()
            .collect(Collectors.toMap(Customer::getId, Function.identity()));

    // Batch load current open periods (fixes N+1)
    var agreementIds = agreements.stream().map(RetainerAgreement::getId).toList();
    var periodMap =
        periodRepository.findByAgreementIdInAndStatus(agreementIds, PeriodStatus.OPEN).stream()
            .collect(Collectors.toMap(RetainerPeriod::getAgreementId, Function.identity()));

    // Batch resolve member names for createdBy
    var memberNames = resolveRetainerMemberNames(agreements);

    return agreements.stream()
        .map(
            agreement -> {
              var customer = customerMap.get(agreement.getCustomerId());
              var customerName = customer != null ? customer.getName() : "Unknown";
              var period = periodMap.get(agreement.getId());
              var currentPeriod = period != null ? PeriodSummary.from(period) : null;
              return RetainerResponse.from(
                  agreement, customerName, currentPeriod, null, memberNames);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public RetainerResponse getRetainer(UUID id) {
    var agreement =
        agreementRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RetainerAgreement", id));

    var customer =
        customerRepository
            .findById(agreement.getCustomerId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Customer", agreement.getCustomerId()));

    var currentPeriod =
        periodRepository
            .findByAgreementIdAndStatus(id, PeriodStatus.OPEN)
            .map(PeriodSummary::from)
            .orElse(null);

    var recentPeriods =
        periodRepository
            .findByAgreementIdOrderByPeriodStartDesc(id, PageRequest.of(0, 6))
            .getContent()
            .stream()
            .map(p -> PeriodSummary.from(p))
            .toList();

    var memberNames = resolveRetainerMemberNames(agreement);
    return RetainerResponse.from(
        agreement, customer.getName(), currentPeriod, recentPeriods, memberNames);
  }

  @Transactional(readOnly = true)
  public RetainerSummaryResponse getRetainerSummary(UUID customerId) {
    // Validate customer exists in this tenant
    customerRepository
        .findById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    var agreementOpt = agreementRepository.findActiveOrPausedByCustomerId(customerId);
    if (agreementOpt.isEmpty()) {
      return RetainerSummaryResponse.noRetainer();
    }
    var agreement = agreementOpt.get();

    // Find open period — there may be none if the retainer was just created or is paused
    var periodOpt =
        periodRepository.findByAgreementIdAndStatus(agreement.getId(), PeriodStatus.OPEN);

    BigDecimal consumedHours =
        periodOpt.map(RetainerPeriod::getConsumedHours).orElse(BigDecimal.ZERO);

    if (agreement.getType() == RetainerType.FIXED_FEE) {
      return new RetainerSummaryResponse(
          true,
          agreement.getId(),
          agreement.getName(),
          agreement.getType(),
          null,
          consumedHours,
          null,
          null,
          false);
    }

    // HOUR_BANK: full summary
    BigDecimal allocatedHours = agreement.getAllocatedHours();
    BigDecimal remainingHours =
        periodOpt.map(RetainerPeriod::getRemainingHours).orElse(allocatedHours);

    BigDecimal percentConsumed = null;
    boolean isOverage = false;
    if (allocatedHours != null && allocatedHours.signum() > 0) {
      percentConsumed =
          consumedHours
              .multiply(BigDecimal.valueOf(100))
              .divide(allocatedHours, 1, RoundingMode.HALF_UP);
      isOverage = consumedHours.compareTo(allocatedHours) > 0;
    }

    return new RetainerSummaryResponse(
        true,
        agreement.getId(),
        agreement.getName(),
        agreement.getType(),
        allocatedHours,
        consumedHours,
        remainingHours,
        percentConsumed,
        isOverage);
  }

  private Map<UUID, String> resolveRetainerMemberNames(RetainerAgreement agreement) {
    if (agreement.getCreatedBy() == null) return Map.of();
    return memberRepository.findAllById(List.of(agreement.getCreatedBy())).stream()
        .collect(Collectors.toMap(Member::getId, Member::getName, (a, b) -> a));
  }

  private Map<UUID, String> resolveRetainerMemberNames(List<RetainerAgreement> agreements) {
    var ids =
        agreements.stream()
            .map(RetainerAgreement::getCreatedBy)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (ids.isEmpty()) return Map.of();
    return memberRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(Member::getId, Member::getName, (a, b) -> a));
  }

  private void validateTypeSpecificFields(CreateRetainerRequest request) {
    if (request.type() == RetainerType.HOUR_BANK) {
      if (request.allocatedHours() == null
          || request.allocatedHours().compareTo(BigDecimal.ZERO) <= 0) {
        throw new InvalidStateException(
            "Missing field", "HOUR_BANK retainer requires allocatedHours > 0");
      }
      if (request.periodFee() == null || request.periodFee().compareTo(BigDecimal.ZERO) <= 0) {
        throw new InvalidStateException(
            "Missing field", "HOUR_BANK retainer requires periodFee > 0");
      }
    }
    if (request.type() == RetainerType.FIXED_FEE) {
      if (request.periodFee() == null || request.periodFee().compareTo(BigDecimal.ZERO) <= 0) {
        throw new InvalidStateException(
            "Missing field", "FIXED_FEE retainer requires periodFee > 0");
      }
    }
    if (request.rolloverPolicy() == RolloverPolicy.CARRY_CAPPED) {
      if (request.rolloverCapHours() == null
          || request.rolloverCapHours().compareTo(BigDecimal.ZERO) <= 0) {
        throw new InvalidStateException(
            "Missing field", "CARRY_CAPPED rollover policy requires rolloverCapHours > 0");
      }
    }
  }

  private Map<String, Object> snapshotValues(RetainerAgreement agreement) {
    var values = new LinkedHashMap<String, Object>();
    values.put("name", agreement.getName());
    values.put(
        "allocatedHours",
        agreement.getAllocatedHours() != null ? agreement.getAllocatedHours().toString() : null);
    values.put(
        "periodFee", agreement.getPeriodFee() != null ? agreement.getPeriodFee().toString() : null);
    values.put("rolloverPolicy", agreement.getRolloverPolicy().name());
    values.put(
        "rolloverCapHours",
        agreement.getRolloverCapHours() != null
            ? agreement.getRolloverCapHours().toString()
            : null);
    values.put(
        "endDate", agreement.getEndDate() != null ? agreement.getEndDate().toString() : null);
    values.put("notes", agreement.getNotes());
    return values;
  }

  private Map<String, Object> buildDiff(Map<String, Object> oldValues, RetainerAgreement updated) {
    var newValues = snapshotValues(updated);

    var diff = new LinkedHashMap<String, Object>();
    for (var entry : oldValues.entrySet()) {
      var key = entry.getKey();
      var oldVal = entry.getValue();
      var newVal = newValues.get(key);
      if (!Objects.equals(oldVal, newVal)) {
        var change = new LinkedHashMap<String, Object>();
        change.put("old", oldVal);
        change.put("new", newVal);
        diff.put(key, change);
      }
    }
    return diff;
  }
}
