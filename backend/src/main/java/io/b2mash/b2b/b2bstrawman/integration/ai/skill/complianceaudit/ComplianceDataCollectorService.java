package io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit;

import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstance;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceRepository;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.datarequest.DataSubjectRequestRepository;
import io.b2mash.b2b.b2bstrawman.datarequest.ProcessingActivity;
import io.b2mash.b2b.b2bstrawman.datarequest.ProcessingActivityRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit.ComplianceSnapshot.FicaCddSummary;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit.ComplianceSnapshot.FlaggedCustomer;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit.ComplianceSnapshot.FlaggedMatter;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit.ComplianceSnapshot.PopiaSummary;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit.ComplianceSnapshot.PrescriptionSummary;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit.ComplianceSnapshot.RetentionSummary;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit.ComplianceSnapshot.TrustAccountingSummary;
import io.b2mash.b2b.b2bstrawman.retention.RetentionPolicy;
import io.b2mash.b2b.b2bstrawman.retention.RetentionPolicyRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTracker;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates compliance data from 6+ services into a {@link ComplianceSnapshot} for AI prompt
 * assembly. Respects module guards (skips disabled modules) and manages token budget via item
 * truncation.
 *
 * <p>Tenant context is resolved from {@code RequestScopes.TENANT_ID} (already bound by filters).
 */
@Service
public class ComplianceDataCollectorService {

  private static final Logger log = LoggerFactory.getLogger(ComplianceDataCollectorService.class);

  /** Maximum flagged items per category (token budget constraint). */
  private static final int DEFAULT_FLAGGED_ITEMS_CAP = 50;

  /** Reduced cap for large firms (>500 active customers). */
  private static final int LARGE_FIRM_FLAGGED_ITEMS_CAP = 20;

  /** Threshold for "large firm" aggregation mode. */
  private static final int LARGE_FIRM_THRESHOLD = 500;

  /** Days overdue before a checklist instance is considered critically overdue. */
  private static final int CRITICALLY_OVERDUE_DAYS = 90;

  private final CustomerRepository customerRepository;
  private final ChecklistInstanceRepository checklistInstanceRepository;
  private final ChecklistInstanceItemRepository checklistInstanceItemRepository;
  private final ProcessingActivityRepository processingActivityRepository;
  private final DataSubjectRequestRepository dataSubjectRequestRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final ClientLedgerCardRepository clientLedgerCardRepository;
  private final PrescriptionTrackerRepository prescriptionTrackerRepository;
  private final RetentionPolicyRepository retentionPolicyRepository;
  private final VerticalModuleGuard moduleGuard;

  public ComplianceDataCollectorService(
      CustomerRepository customerRepository,
      ChecklistInstanceRepository checklistInstanceRepository,
      ChecklistInstanceItemRepository checklistInstanceItemRepository,
      ProcessingActivityRepository processingActivityRepository,
      DataSubjectRequestRepository dataSubjectRequestRepository,
      TrustAccountRepository trustAccountRepository,
      ClientLedgerCardRepository clientLedgerCardRepository,
      PrescriptionTrackerRepository prescriptionTrackerRepository,
      RetentionPolicyRepository retentionPolicyRepository,
      VerticalModuleGuard moduleGuard) {
    this.customerRepository = customerRepository;
    this.checklistInstanceRepository = checklistInstanceRepository;
    this.checklistInstanceItemRepository = checklistInstanceItemRepository;
    this.processingActivityRepository = processingActivityRepository;
    this.dataSubjectRequestRepository = dataSubjectRequestRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.clientLedgerCardRepository = clientLedgerCardRepository;
    this.prescriptionTrackerRepository = prescriptionTrackerRepository;
    this.retentionPolicyRepository = retentionPolicyRepository;
    this.moduleGuard = moduleGuard;
  }

  /**
   * Collects a compliance snapshot aggregating data from all relevant services. Module-gated
   * categories are skipped gracefully when modules are disabled.
   *
   * @return a {@link ComplianceSnapshot} summarising firm-wide compliance posture
   */
  @Transactional(readOnly = true)
  public ComplianceSnapshot collectComplianceSnapshot() {
    var notes = new StringBuilder();

    List<Customer> activeCustomers =
        customerRepository.findByLifecycleStatus(LifecycleStatus.ACTIVE);
    int totalActive = activeCustomers.size();
    boolean isLargeFirm = totalActive > LARGE_FIRM_THRESHOLD;

    if (isLargeFirm) {
      notes.append("Large firm mode (>500 customers) — using aggressive aggregation. ");
    }

    int flaggedItemsCap = isLargeFirm ? LARGE_FIRM_FLAGGED_ITEMS_CAP : DEFAULT_FLAGGED_ITEMS_CAP;

    // 1. FICA/CDD compliance
    FicaCddSummary ficaCdd = aggregateFicaData(activeCustomers, flaggedItemsCap, notes);

    // 2. POPIA processing activities + DSARs
    PopiaSummary popia = aggregatePopiaData();

    // 3. Trust accounting (module-gated)
    TrustAccountingSummary trustAccounting;
    if (moduleGuard.isModuleEnabled("trust_accounting")) {
      trustAccounting = aggregateTrustAccountingData();
    } else {
      trustAccounting = new TrustAccountingSummary(false, 0, 0, List.of());
      notes.append("Trust accounting module not enabled — skipped. ");
    }

    // 4. Prescription tracking (module-gated)
    PrescriptionSummary prescription;
    if (moduleGuard.isModuleEnabled("court_calendar")) {
      prescription = aggregatePrescriptionData(flaggedItemsCap, notes);
    } else {
      prescription = new PrescriptionSummary(false, 0, 0, List.of());
      notes.append("Prescription tracking module not enabled — skipped. ");
    }

    // 5. Retention
    RetentionSummary retention = aggregateRetentionData();

    log.debug(
        "Compliance snapshot collected: {} active customers, large_firm={}",
        totalActive,
        isLargeFirm);

    return new ComplianceSnapshot(
        ficaCdd, popia, trustAccounting, prescription, retention, totalActive, notes.toString());
  }

  private FicaCddSummary aggregateFicaData(
      List<Customer> activeCustomers, int flaggedItemsCap, StringBuilder notes) {
    int compliant = 0;
    int nonCompliant = 0;
    int criticallyOverdue = 0;
    List<FlaggedCustomer> flagged = new ArrayList<>();

    Instant overdueThreshold = Instant.now().minus(Duration.ofDays(CRITICALLY_OVERDUE_DAYS));

    for (Customer customer : activeCustomers) {
      List<ChecklistInstance> instances =
          checklistInstanceRepository.findByCustomerId(customer.getId());

      boolean hasInProgressInstance = false;
      boolean isCompliant = false;
      boolean isCriticallyOverdue = false;

      for (ChecklistInstance instance : instances) {
        if ("COMPLETED".equals(instance.getStatus())) {
          isCompliant = true;
          break;
        }
        if ("IN_PROGRESS".equals(instance.getStatus())) {
          hasInProgressInstance = true;
          // Check if critically overdue (instance created >90 days ago with pending required items)
          if (instance.getCreatedAt().isBefore(overdueThreshold)) {
            boolean hasRequiredPending =
                checklistInstanceItemRepository.existsByInstanceIdAndRequiredAndStatusNot(
                    instance.getId(), true, "COMPLETED");
            if (hasRequiredPending) {
              isCriticallyOverdue = true;
            }
          }
        }
      }

      if (isCompliant) {
        compliant++;
      } else {
        nonCompliant++;
        if (isCriticallyOverdue) {
          criticallyOverdue++;
          flagged.add(
              new FlaggedCustomer(
                  customer.getId(), customer.getName(), "FICA checklist overdue >90 days"));
        } else if (hasInProgressInstance) {
          flagged.add(
              new FlaggedCustomer(
                  customer.getId(), customer.getName(), "FICA checklist incomplete"));
        }
      }
    }

    List<FlaggedCustomer> capped = limitFlaggedItems(flagged, flaggedItemsCap, notes, "FICA/CDD");
    return new FicaCddSummary(compliant, nonCompliant, criticallyOverdue, capped);
  }

  private PopiaSummary aggregatePopiaData() {
    List<ProcessingActivity> activities = processingActivityRepository.findAll();
    int registered = activities.size();
    // Unregistered activities: domain heuristic — expected minimum categories
    // For simplicity, report 0 unregistered (would need firm-type-specific logic)
    int unregistered = 0;

    long pendingDsars =
        dataSubjectRequestRepository.countByStatusIn(List.of("RECEIVED", "IN_PROGRESS"));
    int overdueDsars =
        dataSubjectRequestRepository
            .findByStatusInAndDeadlineBefore(List.of("RECEIVED", "IN_PROGRESS"), LocalDate.now())
            .size();

    return new PopiaSummary(registered, unregistered, (int) pendingDsars, overdueDsars);
  }

  private TrustAccountingSummary aggregateTrustAccountingData() {
    List<TrustAccount> activeAccounts =
        trustAccountRepository.findByStatus(TrustAccountStatus.ACTIVE);
    int accountCount = activeAccounts.size();

    // Check for negative balances (boundary violations) on client ledger cards
    List<String> violations = new ArrayList<>();
    int unreconciledItems = 0;

    for (TrustAccount account : activeAccounts) {
      BigDecimal totalBalance =
          clientLedgerCardRepository.calculateTotalTrustBalance(account.getId());
      if (totalBalance.compareTo(BigDecimal.ZERO) < 0) {
        violations.add(
            "Account '"
                + account.getAccountName()
                + "' has negative aggregate client balance: "
                + totalBalance);
        unreconciledItems++;
      }
    }

    return new TrustAccountingSummary(true, accountCount, unreconciledItems, violations);
  }

  private PrescriptionSummary aggregatePrescriptionData(int flaggedItemsCap, StringBuilder notes) {
    LocalDate now = LocalDate.now();

    // Approaching: prescriptions expiring within 90 days
    List<PrescriptionTracker> approaching =
        prescriptionTrackerRepository.findByStatusInAndPrescriptionDateBetween(
            List.of("RUNNING", "WARNED"), now, now.plusDays(90));

    // Expired: prescriptions already past their date
    List<PrescriptionTracker> expired =
        prescriptionTrackerRepository.findByStatusInAndPrescriptionDateLessThanEqual(
            List.of("RUNNING", "WARNED"), now);

    List<FlaggedMatter> flagged = new ArrayList<>();
    for (PrescriptionTracker tracker : expired) {
      flagged.add(
          new FlaggedMatter(
              tracker.getId(),
              "Project " + tracker.getProjectId(),
              tracker.getPrescriptionDate().toString(),
              "Prescription EXPIRED — immediate attention required"));
    }
    for (PrescriptionTracker tracker : approaching) {
      flagged.add(
          new FlaggedMatter(
              tracker.getId(),
              "Project " + tracker.getProjectId(),
              tracker.getPrescriptionDate().toString(),
              "Prescription approaching within 90 days"));
    }

    List<FlaggedMatter> capped = limitFlaggedItems(flagged, flaggedItemsCap, notes, "Prescription");
    return new PrescriptionSummary(true, approaching.size(), expired.size(), capped);
  }

  private RetentionSummary aggregateRetentionData() {
    List<RetentionPolicy> policies = retentionPolicyRepository.findByActive(true);
    int approaching = 0;
    int pastExpiry = 0;

    Instant now = Instant.now();
    for (RetentionPolicy policy : policies) {
      if (policy.getRetentionDays() <= 0) {
        continue;
      }
      // Past expiry: records older than retention period
      Instant expiredCutoff = now.minus(Duration.ofDays(policy.getRetentionDays()));
      // Approaching: records within 30 days of retention expiry
      Instant approachingCutoff = now.minus(Duration.ofDays(policy.getRetentionDays() - 30));

      // Use customer count as a proxy (most impactful record type)
      if ("CUSTOMER".equals(policy.getRecordType())) {
        List<UUID> expiredIds =
            customerRepository.findIdsByLifecycleStatusAndOffboardedAtBefore(
                LifecycleStatus.OFFBOARDED, expiredCutoff);
        pastExpiry += expiredIds.size();

        if (policy.getRetentionDays() > 30) {
          List<UUID> approachingIds =
              customerRepository.findIdsByLifecycleStatusAndOffboardedAtBefore(
                  LifecycleStatus.OFFBOARDED, approachingCutoff);
          // Approaching = those in warning window but not yet expired
          approaching += Math.max(0, approachingIds.size() - expiredIds.size());
        }
      }
    }

    return new RetentionSummary(approaching, pastExpiry);
  }

  /**
   * Truncates a list of flagged items to the given cap, appending a note if truncation occurred.
   */
  <T> List<T> limitFlaggedItems(List<T> items, int max, StringBuilder notes, String category) {
    if (items.size() <= max) {
      return List.copyOf(items);
    }
    notes
        .append(category)
        .append(" flagged items truncated from ")
        .append(items.size())
        .append(" to ")
        .append(max)
        .append(". ");
    return List.copyOf(items.subList(0, max));
  }
}
