package io.b2mash.b2b.b2bstrawman.retainer;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.retainer.dto.CreateRetainerRequest;
import io.b2mash.b2b.b2bstrawman.retainer.dto.PeriodSummary;
import io.b2mash.b2b.b2bstrawman.retainer.dto.RetainerResponse;
import io.b2mash.b2b.b2bstrawman.retainer.dto.UpdateRetainerRequest;
import java.math.BigDecimal;
import java.util.HashMap;
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

  public RetainerAgreementService(
      RetainerAgreementRepository agreementRepository,
      RetainerPeriodRepository periodRepository,
      CustomerRepository customerRepository,
      AuditService auditService) {
    this.agreementRepository = agreementRepository;
    this.periodRepository = periodRepository;
    this.customerRepository = customerRepository;
    this.auditService = auditService;
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
    return RetainerResponse.from(agreement, customer.getName(), PeriodSummary.from(period), null);
  }

  @Transactional
  public RetainerResponse updateRetainer(
      UUID id, UpdateRetainerRequest request, UUID actorMemberId) {
    // 1. Load agreement
    var agreement =
        agreementRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RetainerAgreement", id));

    // 2. Validate CARRY_CAPPED rollover cap
    if (request.rolloverPolicy() == RolloverPolicy.CARRY_CAPPED
        && (request.rolloverCapHours() == null
            || request.rolloverCapHours().compareTo(BigDecimal.ZERO) <= 0)) {
      throw new InvalidStateException(
          "Missing field", "CARRY_CAPPED rollover policy requires rolloverCapHours > 0");
    }

    // 3. Snapshot old values for audit diff
    var oldValues = new HashMap<String, String>();
    oldValues.put("name", agreement.getName());
    oldValues.put("allocatedHours", Objects.toString(agreement.getAllocatedHours(), "null"));
    oldValues.put("periodFee", Objects.toString(agreement.getPeriodFee(), "null"));
    oldValues.put("rolloverPolicy", agreement.getRolloverPolicy().name());
    oldValues.put("rolloverCapHours", Objects.toString(agreement.getRolloverCapHours(), "null"));
    oldValues.put("endDate", Objects.toString(agreement.getEndDate(), "null"));
    oldValues.put("notes", Objects.toString(agreement.getNotes(), "null"));

    // 4. Update terms
    agreement.updateTerms(
        request.name(),
        request.allocatedHours(),
        request.periodFee(),
        request.rolloverPolicy(),
        request.rolloverCapHours(),
        request.endDate(),
        request.notes());

    // 5. Save agreement
    agreementRepository.save(agreement);

    // 6. Load customer for name
    var customer =
        customerRepository
            .findById(agreement.getCustomerId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Customer", agreement.getCustomerId()));

    // 7. Audit RETAINER_UPDATED with diff
    var changes = buildDiff(oldValues, agreement);
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("retainer.updated")
            .entityType("RETAINER_AGREEMENT")
            .entityId(agreement.getId())
            .details(Map.of("agreementId", agreement.getId().toString(), "changes", changes))
            .build());

    // 8. Load current open period
    var currentPeriod =
        periodRepository
            .findByAgreementIdAndStatus(id, PeriodStatus.OPEN)
            .map(PeriodSummary::from)
            .orElse(null);

    // 9. Return response
    return RetainerResponse.from(agreement, customer.getName(), currentPeriod, null);
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
                    "customerName", customer.getName()))
            .build());

    var currentPeriod =
        periodRepository
            .findByAgreementIdAndStatus(id, PeriodStatus.OPEN)
            .map(PeriodSummary::from)
            .orElse(null);

    return RetainerResponse.from(agreement, customer.getName(), currentPeriod, null);
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
                    "customerName", customer.getName()))
            .build());

    var currentPeriod =
        periodRepository
            .findByAgreementIdAndStatus(id, PeriodStatus.OPEN)
            .map(PeriodSummary::from)
            .orElse(null);

    return RetainerResponse.from(agreement, customer.getName(), currentPeriod, null);
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
                    "customerName", customer.getName()))
            .build());

    var currentPeriod =
        periodRepository
            .findByAgreementIdAndStatus(id, PeriodStatus.OPEN)
            .map(PeriodSummary::from)
            .orElse(null);

    return RetainerResponse.from(agreement, customer.getName(), currentPeriod, null);
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

    return agreements.stream()
        .map(
            agreement -> {
              var customer = customerMap.get(agreement.getCustomerId());
              var customerName = customer != null ? customer.getName() : "Unknown";
              var currentPeriod =
                  periodRepository
                      .findByAgreementIdAndStatus(agreement.getId(), PeriodStatus.OPEN)
                      .map(PeriodSummary::from)
                      .orElse(null);
              return RetainerResponse.from(agreement, customerName, currentPeriod, null);
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
            .map(PeriodSummary::from)
            .toList();

    return RetainerResponse.from(agreement, customer.getName(), currentPeriod, recentPeriods);
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

  private Map<String, Object> buildDiff(Map<String, String> oldValues, RetainerAgreement updated) {
    var newValues = new HashMap<String, String>();
    newValues.put("name", updated.getName());
    newValues.put("allocatedHours", Objects.toString(updated.getAllocatedHours(), "null"));
    newValues.put("periodFee", Objects.toString(updated.getPeriodFee(), "null"));
    newValues.put("rolloverPolicy", updated.getRolloverPolicy().name());
    newValues.put("rolloverCapHours", Objects.toString(updated.getRolloverCapHours(), "null"));
    newValues.put("endDate", Objects.toString(updated.getEndDate(), "null"));
    newValues.put("notes", Objects.toString(updated.getNotes(), "null"));

    var diff = new LinkedHashMap<String, Object>();
    for (var entry : oldValues.entrySet()) {
      var key = entry.getKey();
      var oldVal = entry.getValue();
      var newVal = newValues.get(key);
      if (!Objects.equals(oldVal, newVal)) {
        diff.put(key, Map.of("old", oldVal, "new", newVal));
      }
    }
    return diff;
  }
}
