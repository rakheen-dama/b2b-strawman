package io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstance;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceRepository;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.datarequest.DataSubjectRequestRepository;
import io.b2mash.b2b.b2bstrawman.datarequest.ProcessingActivity;
import io.b2mash.b2b.b2bstrawman.datarequest.ProcessingActivityRepository;
import io.b2mash.b2b.b2bstrawman.retention.RetentionCheckResult;
import io.b2mash.b2b.b2bstrawman.retention.RetentionPolicyRepository;
import io.b2mash.b2b.b2bstrawman.retention.RetentionService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestIds;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ComplianceDataCollectorService}. Uses pure Mockito (no Spring context)
 * since all dependencies are mocked and no real DB transaction is needed.
 */
@ExtendWith(MockitoExtension.class)
class ComplianceDataCollectorServiceTest {

  @Mock private CustomerRepository customerRepository;
  @Mock private ChecklistInstanceRepository checklistInstanceRepository;
  @Mock private ChecklistInstanceItemRepository checklistInstanceItemRepository;
  @Mock private ProcessingActivityRepository processingActivityRepository;
  @Mock private DataSubjectRequestRepository dataSubjectRequestRepository;
  @Mock private TrustAccountRepository trustAccountRepository;
  @Mock private ClientLedgerCardRepository clientLedgerCardRepository;
  @Mock private PrescriptionTrackerRepository prescriptionTrackerRepository;
  @Mock private RetentionPolicyRepository retentionPolicyRepository;
  @Mock private RetentionService retentionService;
  @Mock private VerticalModuleGuard moduleGuard;

  private ComplianceDataCollectorService dataCollectorService;

  @BeforeEach
  void setUp() {
    dataCollectorService =
        new ComplianceDataCollectorService(
            customerRepository,
            checklistInstanceRepository,
            checklistInstanceItemRepository,
            processingActivityRepository,
            dataSubjectRequestRepository,
            trustAccountRepository,
            clientLedgerCardRepository,
            prescriptionTrackerRepository,
            retentionPolicyRepository,
            retentionService,
            moduleGuard);
  }

  @Test
  void collectSnapshot_allModulesEnabled_producesFullSnapshot() {
    // Setup: 10 active customers, 7 compliant, 3 non-compliant
    List<Customer> customers = createCustomers(10);
    when(customerRepository.findByLifecycleStatus(LifecycleStatus.ACTIVE)).thenReturn(customers);

    // Build batch instances: 7 completed, 3 in-progress
    List<ChecklistInstance> allInstances = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      allInstances.add(createInstance(customers.get(i).getId(), "COMPLETED"));
    }
    for (int i = 7; i < 10; i++) {
      allInstances.add(createInstance(customers.get(i).getId(), "IN_PROGRESS"));
    }
    when(checklistInstanceRepository.findByCustomerIdIn(anyList())).thenReturn(allInstances);
    when(checklistInstanceItemRepository.findByInstanceIdInOrderByInstanceIdAscSortOrderAsc(
            anyList()))
        .thenReturn(List.of());

    // POPIA data
    when(processingActivityRepository.findAll())
        .thenReturn(List.of(createProcessingActivity(), createProcessingActivity()));
    when(dataSubjectRequestRepository.countByStatusIn(List.of("RECEIVED", "IN_PROGRESS")))
        .thenReturn(3L);
    when(dataSubjectRequestRepository.findByStatusInAndDeadlineBefore(
            List.of("RECEIVED", "IN_PROGRESS"), LocalDate.now()))
        .thenReturn(List.of());

    // Trust accounting enabled
    when(moduleGuard.isModuleEnabled("trust_accounting")).thenReturn(true);
    TrustAccount account = createTrustAccount();
    when(trustAccountRepository.findByStatus(TrustAccountStatus.ACTIVE))
        .thenReturn(List.of(account));
    when(clientLedgerCardRepository.calculateTotalTrustBalance(account.getId()))
        .thenReturn(BigDecimal.valueOf(50000));

    // Court calendar (prescription) enabled
    when(moduleGuard.isModuleEnabled("court_calendar")).thenReturn(true);
    when(prescriptionTrackerRepository.findByStatusInAndPrescriptionDateBetween(
            anyList(), any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(List.of());
    when(prescriptionTrackerRepository.findByStatusInAndPrescriptionDateLessThanEqual(
            anyList(), any(LocalDate.class)))
        .thenReturn(List.of());

    // Retention
    when(retentionService.previewPurge()).thenReturn(new RetentionCheckResult());
    when(retentionPolicyRepository.findByActive(true)).thenReturn(List.of());

    ComplianceSnapshot snapshot = dataCollectorService.collectComplianceSnapshot();

    assertThat(snapshot.totalActiveCustomers()).isEqualTo(10);
    assertThat(snapshot.ficaCdd().compliant()).isEqualTo(7);
    assertThat(snapshot.ficaCdd().nonCompliant()).isEqualTo(3);
    assertThat(snapshot.popia().registeredActivities()).isEqualTo(2);
    assertThat(snapshot.popia().pendingDsars()).isEqualTo(3);
    assertThat(snapshot.trustAccounting().moduleEnabled()).isTrue();
    assertThat(snapshot.trustAccounting().accountCount()).isEqualTo(1);
    assertThat(snapshot.trustAccounting().unreconciledItems()).isZero();
    assertThat(snapshot.prescription().moduleEnabled()).isTrue();
    assertThat(snapshot.dataCollectionNotes()).doesNotContain("not enabled");
  }

  @Test
  void collectSnapshot_modulesDisabled_skipsTrustAndPrescription() {
    when(customerRepository.findByLifecycleStatus(LifecycleStatus.ACTIVE)).thenReturn(List.of());
    when(moduleGuard.isModuleEnabled("trust_accounting")).thenReturn(false);
    when(moduleGuard.isModuleEnabled("court_calendar")).thenReturn(false);

    when(processingActivityRepository.findAll()).thenReturn(List.of());
    when(dataSubjectRequestRepository.countByStatusIn(anyList())).thenReturn(0L);
    when(dataSubjectRequestRepository.findByStatusInAndDeadlineBefore(anyList(), any()))
        .thenReturn(List.of());
    when(retentionService.previewPurge()).thenReturn(new RetentionCheckResult());
    when(retentionPolicyRepository.findByActive(true)).thenReturn(List.of());

    ComplianceSnapshot snapshot = dataCollectorService.collectComplianceSnapshot();

    assertThat(snapshot.trustAccounting().moduleEnabled()).isFalse();
    assertThat(snapshot.trustAccounting().accountCount()).isZero();
    assertThat(snapshot.prescription().moduleEnabled()).isFalse();
    assertThat(snapshot.prescription().approachingCount()).isZero();
    assertThat(snapshot.dataCollectionNotes()).contains("Trust accounting module not enabled");
    assertThat(snapshot.dataCollectionNotes()).contains("Prescription tracking module not enabled");

    // Verify guarded repos were never called
    verify(trustAccountRepository, never()).findByStatus(any());
    verify(prescriptionTrackerRepository, never())
        .findByStatusInAndPrescriptionDateBetween(anyList(), any(), any());
  }

  @Test
  void collectSnapshot_flaggedItemsCappedAt50() {
    // 60 non-compliant customers with in-progress checklists
    List<Customer> customers = createCustomers(60);
    when(customerRepository.findByLifecycleStatus(LifecycleStatus.ACTIVE)).thenReturn(customers);

    List<ChecklistInstance> allInstances =
        customers.stream().map(c -> createInstance(c.getId(), "IN_PROGRESS")).toList();
    when(checklistInstanceRepository.findByCustomerIdIn(anyList())).thenReturn(allInstances);
    when(checklistInstanceItemRepository.findByInstanceIdInOrderByInstanceIdAscSortOrderAsc(
            anyList()))
        .thenReturn(List.of());

    when(moduleGuard.isModuleEnabled("trust_accounting")).thenReturn(false);
    when(moduleGuard.isModuleEnabled("court_calendar")).thenReturn(false);
    when(processingActivityRepository.findAll()).thenReturn(List.of());
    when(dataSubjectRequestRepository.countByStatusIn(anyList())).thenReturn(0L);
    when(dataSubjectRequestRepository.findByStatusInAndDeadlineBefore(anyList(), any()))
        .thenReturn(List.of());
    when(retentionService.previewPurge()).thenReturn(new RetentionCheckResult());
    when(retentionPolicyRepository.findByActive(true)).thenReturn(List.of());

    ComplianceSnapshot snapshot = dataCollectorService.collectComplianceSnapshot();

    assertThat(snapshot.ficaCdd().nonCompliant()).isEqualTo(60);
    assertThat(snapshot.ficaCdd().flaggedCustomers()).hasSize(50);
    assertThat(snapshot.dataCollectionNotes()).contains("FICA/CDD flagged items truncated");
  }

  @Test
  void collectSnapshot_emptyFirm_producesCleanSnapshot() {
    when(customerRepository.findByLifecycleStatus(LifecycleStatus.ACTIVE)).thenReturn(List.of());
    when(moduleGuard.isModuleEnabled("trust_accounting")).thenReturn(true);
    when(moduleGuard.isModuleEnabled("court_calendar")).thenReturn(true);

    when(processingActivityRepository.findAll()).thenReturn(List.of());
    when(dataSubjectRequestRepository.countByStatusIn(anyList())).thenReturn(0L);
    when(dataSubjectRequestRepository.findByStatusInAndDeadlineBefore(anyList(), any()))
        .thenReturn(List.of());
    when(trustAccountRepository.findByStatus(TrustAccountStatus.ACTIVE)).thenReturn(List.of());
    when(prescriptionTrackerRepository.findByStatusInAndPrescriptionDateBetween(
            anyList(), any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(List.of());
    when(prescriptionTrackerRepository.findByStatusInAndPrescriptionDateLessThanEqual(
            anyList(), any(LocalDate.class)))
        .thenReturn(List.of());
    when(retentionService.previewPurge()).thenReturn(new RetentionCheckResult());
    when(retentionPolicyRepository.findByActive(true)).thenReturn(List.of());

    ComplianceSnapshot snapshot = dataCollectorService.collectComplianceSnapshot();

    assertThat(snapshot.totalActiveCustomers()).isZero();
    assertThat(snapshot.ficaCdd().compliant()).isZero();
    assertThat(snapshot.ficaCdd().nonCompliant()).isZero();
    assertThat(snapshot.ficaCdd().flaggedCustomers()).isEmpty();
    assertThat(snapshot.popia().registeredActivities()).isZero();
    assertThat(snapshot.retention().pastExpiry()).isZero();
  }

  @Test
  void collectSnapshot_largeFirm_usesAggressiveAggregation() {
    // 550 customers (>500 threshold)
    List<Customer> customers = createCustomers(550);
    when(customerRepository.findByLifecycleStatus(LifecycleStatus.ACTIVE)).thenReturn(customers);

    // All have in-progress (non-compliant) to generate flagged items
    List<ChecklistInstance> allInstances =
        customers.stream().map(c -> createInstance(c.getId(), "IN_PROGRESS")).toList();
    when(checklistInstanceRepository.findByCustomerIdIn(anyList())).thenReturn(allInstances);
    when(checklistInstanceItemRepository.findByInstanceIdInOrderByInstanceIdAscSortOrderAsc(
            anyList()))
        .thenReturn(List.of());

    when(moduleGuard.isModuleEnabled("trust_accounting")).thenReturn(false);
    when(moduleGuard.isModuleEnabled("court_calendar")).thenReturn(false);
    when(processingActivityRepository.findAll()).thenReturn(List.of());
    when(dataSubjectRequestRepository.countByStatusIn(anyList())).thenReturn(0L);
    when(dataSubjectRequestRepository.findByStatusInAndDeadlineBefore(anyList(), any()))
        .thenReturn(List.of());
    when(retentionService.previewPurge()).thenReturn(new RetentionCheckResult());
    when(retentionPolicyRepository.findByActive(true)).thenReturn(List.of());

    ComplianceSnapshot snapshot = dataCollectorService.collectComplianceSnapshot();

    assertThat(snapshot.totalActiveCustomers()).isEqualTo(550);
    // Large firm cap is 20, not 50
    assertThat(snapshot.ficaCdd().flaggedCustomers()).hasSize(20);
    assertThat(snapshot.dataCollectionNotes()).contains("Large firm mode");
    assertThat(snapshot.dataCollectionNotes()).contains("FICA/CDD flagged items truncated");
  }

  @Test
  void collectSnapshot_categorySummaryCountsAccurate() {
    // 5 customers: 2 compliant, 2 non-compliant, 1 critically overdue
    List<Customer> customers = createCustomers(5);
    when(customerRepository.findByLifecycleStatus(LifecycleStatus.ACTIVE)).thenReturn(customers);

    // Build batch instances
    List<ChecklistInstance> allInstances = new ArrayList<>();
    // Customers 0,1 = compliant (completed checklists)
    for (int i = 0; i < 2; i++) {
      allInstances.add(createInstance(customers.get(i).getId(), "COMPLETED"));
    }
    // Customer 2,3 = non-compliant (in-progress, recent)
    for (int i = 2; i < 4; i++) {
      allInstances.add(createInstance(customers.get(i).getId(), "IN_PROGRESS"));
    }
    // Customer 4 = critically overdue (in-progress, >90 days old)
    ChecklistInstance overdueInstance = createOverdueInstance(customers.get(4).getId());
    allInstances.add(overdueInstance);

    when(checklistInstanceRepository.findByCustomerIdIn(anyList())).thenReturn(allInstances);

    // Provide items for the overdue instance showing required pending item
    var overdueItem = createOverdueChecklistItem(overdueInstance.getId());
    when(checklistInstanceItemRepository.findByInstanceIdInOrderByInstanceIdAscSortOrderAsc(
            anyList()))
        .thenReturn(List.of(overdueItem));

    when(moduleGuard.isModuleEnabled("trust_accounting")).thenReturn(false);
    when(moduleGuard.isModuleEnabled("court_calendar")).thenReturn(false);
    when(processingActivityRepository.findAll()).thenReturn(List.of());
    when(dataSubjectRequestRepository.countByStatusIn(anyList())).thenReturn(0L);
    when(dataSubjectRequestRepository.findByStatusInAndDeadlineBefore(anyList(), any()))
        .thenReturn(List.of());
    when(retentionService.previewPurge()).thenReturn(new RetentionCheckResult());
    when(retentionPolicyRepository.findByActive(true)).thenReturn(List.of());

    ComplianceSnapshot snapshot = dataCollectorService.collectComplianceSnapshot();

    assertThat(snapshot.ficaCdd().compliant()).isEqualTo(2);
    assertThat(snapshot.ficaCdd().nonCompliant()).isEqualTo(3);
    assertThat(snapshot.ficaCdd().criticallyOverdue()).isEqualTo(1);
    assertThat(snapshot.ficaCdd().flaggedCustomers())
        .anyMatch(f -> f.issue().contains("overdue >90 days"));
  }

  // --- Test helpers ---

  private static final UUID ACTOR_ID = UUID.randomUUID();

  private List<Customer> createCustomers(int count) {
    return IntStream.range(0, count)
        .mapToObj(
            i ->
                TestIds.withId(
                    TestCustomerFactory.createActiveCustomer(
                        "Customer " + i, "customer" + i + "@test.com", ACTOR_ID),
                    UUID.randomUUID()))
        .toList();
  }

  private ChecklistInstance createInstance(UUID customerId, String status) {
    ChecklistInstance instance =
        new ChecklistInstance(UUID.randomUUID(), customerId, Instant.now());
    TestIds.withId(instance, UUID.randomUUID());
    if ("COMPLETED".equals(status)) {
      instance.complete(UUID.randomUUID());
    }
    return instance;
  }

  private ChecklistInstance createOverdueInstance(UUID customerId) {
    ChecklistInstance instance =
        new ChecklistInstance(
            UUID.randomUUID(), customerId, Instant.now().minus(100, ChronoUnit.DAYS));
    TestIds.withId(instance, UUID.randomUUID());
    TestIds.withField(instance, "createdAt", Instant.now().minus(100, ChronoUnit.DAYS));
    return instance;
  }

  private io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceItem createOverdueChecklistItem(
      UUID instanceId) {
    // Item defaults to PENDING status which satisfies the "required and not COMPLETED" check
    return new io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceItem(
        instanceId, UUID.randomUUID(), "FICA ID Document", "Verify identity", 1, true, false, null);
  }

  private ProcessingActivity createProcessingActivity() {
    return new ProcessingActivity(
        "Client data processing",
        "Processing client data",
        "POPIA s14",
        "Clients",
        "5 years",
        "Internal staff");
  }

  private TrustAccount createTrustAccount() {
    TrustAccount account =
        new TrustAccount(
            "Primary Trust",
            "Test Bank",
            "001",
            "123456789",
            TrustAccountType.GENERAL,
            true,
            false,
            BigDecimal.ZERO,
            LocalDate.now(),
            null);
    return TestIds.withId(account, UUID.randomUUID());
  }
}
