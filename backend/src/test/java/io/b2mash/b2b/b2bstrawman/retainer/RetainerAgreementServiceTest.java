package io.b2mash.b2b.b2bstrawman.retainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.retainer.dto.CreateRetainerRequest;
import io.b2mash.b2b.b2bstrawman.retainer.dto.UpdateRetainerRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetainerAgreementServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_retainer_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private RetainerAgreementService retainerAgreementService;
  @Autowired private RetainerAgreementRepository retainerAgreementRepository;
  @Autowired private RetainerPeriodRepository retainerPeriodRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Retainer Service Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberId =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_retainer_owner",
                "retainer_owner@test.com",
                "Retainer Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  /** Creates a fresh ACTIVE customer within the tenant and returns its ID. */
  private UUID createCustomer(String name, String email) {
    var ref = new AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer = new Customer(name, email, null, null, null, memberId);
                  customer = customerRepository.save(customer);
                  ref.set(customer.getId());
                }));
    return ref.get();
  }

  /** Creates a customer with a specific lifecycle status within the tenant and returns its ID. */
  private UUID createCustomerWithStatus(String name, String email, LifecycleStatus status) {
    var ref = new AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      new Customer(name, email, null, null, null, memberId, null, status);
                  customer = customerRepository.save(customer);
                  ref.set(customer.getId());
                }));
    return ref.get();
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  @Test
  void createHourBankRetainer_createsAgreementAndFirstPeriod() {
    var customerId = createCustomer("HourBank Corp", "hourbank@test.com");

    runInTenant(
        () -> {
          var request =
              new CreateRetainerRequest(
                  customerId,
                  null,
                  "Monthly Hour Bank",
                  RetainerType.HOUR_BANK,
                  RetainerFrequency.MONTHLY,
                  LocalDate.of(2026, 3, 1),
                  null,
                  new BigDecimal("40"),
                  new BigDecimal("20000"),
                  RolloverPolicy.FORFEIT,
                  null,
                  null);

          var response = retainerAgreementService.createRetainer(request, memberId);

          assertThat(response.status()).isEqualTo(RetainerStatus.ACTIVE);
          assertThat(response.type()).isEqualTo(RetainerType.HOUR_BANK);
          assertThat(response.allocatedHours()).isEqualByComparingTo("40");
          assertThat(response.periodFee()).isEqualByComparingTo("20000");
          assertThat(response.customerName()).isEqualTo("HourBank Corp");

          // Verify first period
          assertThat(response.currentPeriod()).isNotNull();
          var period = response.currentPeriod();
          assertThat(period.periodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
          assertThat(period.periodEnd()).isEqualTo(LocalDate.of(2026, 4, 1));
          assertThat(period.status()).isEqualTo(PeriodStatus.OPEN);
          assertThat(period.allocatedHours()).isEqualByComparingTo("40");
          assertThat(period.remainingHours()).isEqualByComparingTo("40");
          assertThat(period.consumedHours()).isEqualByComparingTo("0");
          assertThat(period.rolloverHoursIn()).isEqualByComparingTo("0");
        });
  }

  @Test
  void createFixedFeeRetainer_periodHasNullAllocatedHours() {
    var customerId = createCustomer("FixedFee Corp", "fixedfee@test.com");

    runInTenant(
        () -> {
          var request =
              new CreateRetainerRequest(
                  customerId,
                  null,
                  "Monthly Fixed Fee",
                  RetainerType.FIXED_FEE,
                  RetainerFrequency.MONTHLY,
                  LocalDate.of(2026, 3, 1),
                  null,
                  null,
                  new BigDecimal("5000"),
                  RolloverPolicy.FORFEIT,
                  null,
                  null);

          var response = retainerAgreementService.createRetainer(request, memberId);

          assertThat(response.type()).isEqualTo(RetainerType.FIXED_FEE);
          assertThat(response.currentPeriod()).isNotNull();
          assertThat(response.currentPeriod().allocatedHours()).isNull();
          assertThat(response.currentPeriod().remainingHours()).isEqualByComparingTo("0");
          assertThat(response.currentPeriod().consumedHours()).isEqualByComparingTo("0");
        });
  }

  @Test
  void createRetainer_duplicateActiveRetainer_throws409() {
    var customerId = createCustomer("Duplicate Corp", "duplicate@test.com");

    runInTenant(
        () -> {
          var request =
              new CreateRetainerRequest(
                  customerId,
                  null,
                  "First Retainer",
                  RetainerType.HOUR_BANK,
                  RetainerFrequency.MONTHLY,
                  LocalDate.of(2026, 3, 1),
                  null,
                  new BigDecimal("40"),
                  new BigDecimal("20000"),
                  RolloverPolicy.FORFEIT,
                  null,
                  null);

          retainerAgreementService.createRetainer(request, memberId);

          var duplicateRequest =
              new CreateRetainerRequest(
                  customerId,
                  null,
                  "Second Retainer",
                  RetainerType.HOUR_BANK,
                  RetainerFrequency.MONTHLY,
                  LocalDate.of(2026, 4, 1),
                  null,
                  new BigDecimal("20"),
                  new BigDecimal("10000"),
                  RolloverPolicy.FORFEIT,
                  null,
                  null);

          assertThatThrownBy(
                  () -> retainerAgreementService.createRetainer(duplicateRequest, memberId))
              .isInstanceOf(ResourceConflictException.class);
        });
  }

  @Test
  void createRetainer_offboardedCustomer_throws400() {
    var customerId =
        createCustomerWithStatus(
            "Offboarded Corp", "offboarded@test.com", LifecycleStatus.OFFBOARDED);

    runInTenant(
        () -> {
          var request =
              new CreateRetainerRequest(
                  customerId,
                  null,
                  "Bad Retainer",
                  RetainerType.HOUR_BANK,
                  RetainerFrequency.MONTHLY,
                  LocalDate.of(2026, 3, 1),
                  null,
                  new BigDecimal("40"),
                  new BigDecimal("20000"),
                  RolloverPolicy.FORFEIT,
                  null,
                  null);

          assertThatThrownBy(() -> retainerAgreementService.createRetainer(request, memberId))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void createRetainer_prospectCustomer_throws400() {
    var customerId =
        createCustomerWithStatus("Prospect Corp", "prospect@test.com", LifecycleStatus.PROSPECT);

    runInTenant(
        () -> {
          var request =
              new CreateRetainerRequest(
                  customerId,
                  null,
                  "Bad Retainer",
                  RetainerType.HOUR_BANK,
                  RetainerFrequency.MONTHLY,
                  LocalDate.of(2026, 3, 1),
                  null,
                  new BigDecimal("40"),
                  new BigDecimal("20000"),
                  RolloverPolicy.FORFEIT,
                  null,
                  null);

          assertThatThrownBy(() -> retainerAgreementService.createRetainer(request, memberId))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void createHourBankRetainer_missingAllocatedHours_throws400() {
    var customerId = createCustomer("MissingHours Corp", "missinghours@test.com");

    runInTenant(
        () -> {
          var request =
              new CreateRetainerRequest(
                  customerId,
                  null,
                  "Bad Retainer",
                  RetainerType.HOUR_BANK,
                  RetainerFrequency.MONTHLY,
                  LocalDate.of(2026, 3, 1),
                  null,
                  null,
                  new BigDecimal("20000"),
                  RolloverPolicy.FORFEIT,
                  null,
                  null);

          assertThatThrownBy(() -> retainerAgreementService.createRetainer(request, memberId))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void createRetainer_carryCappedMissingCapHours_throws400() {
    var customerId = createCustomer("MissingCap Corp", "missingcap@test.com");

    runInTenant(
        () -> {
          var request =
              new CreateRetainerRequest(
                  customerId,
                  null,
                  "Bad Retainer",
                  RetainerType.HOUR_BANK,
                  RetainerFrequency.MONTHLY,
                  LocalDate.of(2026, 3, 1),
                  null,
                  new BigDecimal("40"),
                  new BigDecimal("20000"),
                  RolloverPolicy.CARRY_CAPPED,
                  null,
                  null);

          assertThatThrownBy(() -> retainerAgreementService.createRetainer(request, memberId))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void updateRetainer_changesTermsButNotCurrentPeriod() {
    var customerId = createCustomer("Update Corp", "update@test.com");

    runInTenant(
        () -> {
          var createRequest =
              new CreateRetainerRequest(
                  customerId,
                  null,
                  "Original Name",
                  RetainerType.HOUR_BANK,
                  RetainerFrequency.MONTHLY,
                  LocalDate.of(2026, 3, 1),
                  null,
                  new BigDecimal("40"),
                  new BigDecimal("20000"),
                  RolloverPolicy.FORFEIT,
                  null,
                  null);

          var created = retainerAgreementService.createRetainer(createRequest, memberId);

          var updateRequest =
              new UpdateRetainerRequest(
                  "Updated Name",
                  new BigDecimal("50"),
                  new BigDecimal("25000"),
                  RolloverPolicy.FORFEIT,
                  null,
                  null,
                  "Updated notes");

          var updated =
              retainerAgreementService.updateRetainer(created.id(), updateRequest, memberId);

          assertThat(updated.name()).isEqualTo("Updated Name");
          assertThat(updated.allocatedHours()).isEqualByComparingTo("50");
          assertThat(updated.periodFee()).isEqualByComparingTo("25000");
          assertThat(updated.notes()).isEqualTo("Updated notes");

          // Current period should remain unchanged
          assertThat(updated.currentPeriod()).isNotNull();
          assertThat(updated.currentPeriod().allocatedHours()).isEqualByComparingTo("40");
        });
  }

  @Test
  void pauseRetainer_activeRetainerBecomesPaused() {
    var customerId = createCustomer("Pause Corp", "pause@test.com");

    runInTenant(
        () -> {
          var request =
              new CreateRetainerRequest(
                  customerId,
                  null,
                  "Pausable Retainer",
                  RetainerType.HOUR_BANK,
                  RetainerFrequency.MONTHLY,
                  LocalDate.of(2026, 3, 1),
                  null,
                  new BigDecimal("40"),
                  new BigDecimal("20000"),
                  RolloverPolicy.FORFEIT,
                  null,
                  null);

          var created = retainerAgreementService.createRetainer(request, memberId);

          var paused = retainerAgreementService.pauseRetainer(created.id(), memberId);

          assertThat(paused.status()).isEqualTo(RetainerStatus.PAUSED);
        });
  }

  @Test
  void pauseRetainer_alreadyPaused_throws400() {
    var customerId = createCustomer("DoublePause Corp", "doublepause@test.com");

    runInTenant(
        () -> {
          var request =
              new CreateRetainerRequest(
                  customerId,
                  null,
                  "Double Pause Retainer",
                  RetainerType.HOUR_BANK,
                  RetainerFrequency.MONTHLY,
                  LocalDate.of(2026, 3, 1),
                  null,
                  new BigDecimal("40"),
                  new BigDecimal("20000"),
                  RolloverPolicy.FORFEIT,
                  null,
                  null);

          var created = retainerAgreementService.createRetainer(request, memberId);
          retainerAgreementService.pauseRetainer(created.id(), memberId);

          assertThatThrownBy(() -> retainerAgreementService.pauseRetainer(created.id(), memberId))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void resumeRetainer_pausedRetainerBecomesActive() {
    var customerId = createCustomer("Resume Corp", "resume@test.com");

    runInTenant(
        () -> {
          var request =
              new CreateRetainerRequest(
                  customerId,
                  null,
                  "Resumable Retainer",
                  RetainerType.HOUR_BANK,
                  RetainerFrequency.MONTHLY,
                  LocalDate.of(2026, 3, 1),
                  null,
                  new BigDecimal("40"),
                  new BigDecimal("20000"),
                  RolloverPolicy.FORFEIT,
                  null,
                  null);

          var created = retainerAgreementService.createRetainer(request, memberId);
          retainerAgreementService.pauseRetainer(created.id(), memberId);

          var resumed = retainerAgreementService.resumeRetainer(created.id(), memberId);

          assertThat(resumed.status()).isEqualTo(RetainerStatus.ACTIVE);
        });
  }

  @Test
  void terminateRetainer_activeRetainerBecomesTerminated() {
    var customerId = createCustomer("Terminate Corp", "terminate@test.com");

    runInTenant(
        () -> {
          var request =
              new CreateRetainerRequest(
                  customerId,
                  null,
                  "Terminable Retainer",
                  RetainerType.HOUR_BANK,
                  RetainerFrequency.MONTHLY,
                  LocalDate.of(2026, 3, 1),
                  null,
                  new BigDecimal("40"),
                  new BigDecimal("20000"),
                  RolloverPolicy.FORFEIT,
                  null,
                  null);

          var created = retainerAgreementService.createRetainer(request, memberId);

          var terminated = retainerAgreementService.terminateRetainer(created.id(), memberId);

          assertThat(terminated.status()).isEqualTo(RetainerStatus.TERMINATED);
        });
  }

  @Test
  void getRetainer_includesCurrentPeriodAndRecentPeriods() {
    var customerId = createCustomer("Get Corp", "get@test.com");

    runInTenant(
        () -> {
          var request =
              new CreateRetainerRequest(
                  customerId,
                  null,
                  "Get Detail Retainer",
                  RetainerType.HOUR_BANK,
                  RetainerFrequency.MONTHLY,
                  LocalDate.of(2026, 3, 1),
                  null,
                  new BigDecimal("40"),
                  new BigDecimal("20000"),
                  RolloverPolicy.FORFEIT,
                  null,
                  null);

          var created = retainerAgreementService.createRetainer(request, memberId);

          var detail = retainerAgreementService.getRetainer(created.id());

          assertThat(detail.currentPeriod()).isNotNull();
          assertThat(detail.recentPeriods()).hasSize(1);
          assertThat(detail.customerName()).isEqualTo("Get Corp");
        });
  }
}
