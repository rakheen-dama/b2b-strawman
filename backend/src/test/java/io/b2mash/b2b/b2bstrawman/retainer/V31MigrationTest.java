package io.b2mash.b2b.b2bstrawman.retainer;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V31MigrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_v31_migration_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private RetainerAgreementRepository retainerAgreementRepository;
  @Autowired private RetainerPeriodRepository retainerPeriodRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "V31 Migration Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberId =
        UUID.fromString(
            syncMember(ORG_ID, "user_v31_owner", "v31_owner@test.com", "V31 Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void saveAndRetrieveRetainerAgreement() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      createActiveCustomer("Retainer Test Corp", "retainer_ag@test.com", memberId);
                  customer = customerRepository.saveAndFlush(customer);

                  var agreement =
                      new RetainerAgreement(
                          customer.getId(),
                          "Monthly Retainer",
                          RetainerType.HOUR_BANK,
                          RetainerFrequency.MONTHLY,
                          LocalDate.of(2026, 3, 1),
                          null,
                          new BigDecimal("40.00"),
                          new BigDecimal("20000.00"),
                          RolloverPolicy.FORFEIT,
                          null,
                          "Test notes",
                          memberId);
                  agreement = retainerAgreementRepository.saveAndFlush(agreement);

                  var found = retainerAgreementRepository.findById(agreement.getId()).orElseThrow();
                  assertThat(found.getCustomerId()).isEqualTo(customer.getId());
                  assertThat(found.getName()).isEqualTo("Monthly Retainer");
                  assertThat(found.getType()).isEqualTo(RetainerType.HOUR_BANK);
                  assertThat(found.getStatus()).isEqualTo(RetainerStatus.ACTIVE);
                  assertThat(found.getFrequency()).isEqualTo(RetainerFrequency.MONTHLY);
                  assertThat(found.getStartDate()).isEqualTo(LocalDate.of(2026, 3, 1));
                  assertThat(found.getAllocatedHours())
                      .isEqualByComparingTo(new BigDecimal("40.00"));
                  assertThat(found.getPeriodFee()).isEqualByComparingTo(new BigDecimal("20000.00"));
                  assertThat(found.getRolloverPolicy()).isEqualTo(RolloverPolicy.FORFEIT);
                  assertThat(found.getNotes()).isEqualTo("Test notes");
                  assertThat(found.getCreatedBy()).isEqualTo(memberId);
                  assertThat(found.getCreatedAt()).isNotNull();
                  assertThat(found.getUpdatedAt()).isNotNull();
                }));
  }

  @Test
  void saveAndRetrieveRetainerPeriod() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      createActiveCustomer(
                          "Period Test Corp", "retainer_period@test.com", memberId);
                  customer = customerRepository.saveAndFlush(customer);

                  var agreement =
                      new RetainerAgreement(
                          customer.getId(),
                          "Period Test Retainer",
                          RetainerType.HOUR_BANK,
                          RetainerFrequency.MONTHLY,
                          LocalDate.of(2026, 3, 1),
                          null,
                          new BigDecimal("40.00"),
                          new BigDecimal("20000.00"),
                          RolloverPolicy.FORFEIT,
                          null,
                          null,
                          memberId);
                  agreement = retainerAgreementRepository.saveAndFlush(agreement);

                  var period =
                      new RetainerPeriod(
                          agreement.getId(),
                          LocalDate.of(2026, 3, 1),
                          LocalDate.of(2026, 4, 1),
                          new BigDecimal("40.00"),
                          new BigDecimal("40.00"),
                          BigDecimal.ZERO);
                  period = retainerPeriodRepository.saveAndFlush(period);

                  var found = retainerPeriodRepository.findById(period.getId()).orElseThrow();
                  assertThat(found.getAgreementId()).isEqualTo(agreement.getId());
                  assertThat(found.getPeriodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
                  assertThat(found.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 4, 1));
                  assertThat(found.getStatus()).isEqualTo(PeriodStatus.OPEN);
                  assertThat(found.getAllocatedHours())
                      .isEqualByComparingTo(new BigDecimal("40.00"));
                  assertThat(found.getConsumedHours()).isEqualByComparingTo(BigDecimal.ZERO);
                  assertThat(found.getRemainingHours())
                      .isEqualByComparingTo(new BigDecimal("40.00"));
                  assertThat(found.getCreatedAt()).isNotNull();
                }));
  }

  @Test
  void uniqueConstraintOnAgreementIdAndPeriodStart() {
    // Create the agreement outside the failing transaction so it persists
    var agreementId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      createActiveCustomer(
                          "Unique Constraint Corp", "retainer_unique@test.com", memberId);
                  customer = customerRepository.saveAndFlush(customer);

                  var agreement =
                      new RetainerAgreement(
                          customer.getId(),
                          "Unique Constraint Retainer",
                          RetainerType.HOUR_BANK,
                          RetainerFrequency.MONTHLY,
                          LocalDate.of(2026, 4, 1),
                          null,
                          new BigDecimal("40.00"),
                          new BigDecimal("20000.00"),
                          RolloverPolicy.FORFEIT,
                          null,
                          null,
                          memberId);
                  agreement = retainerAgreementRepository.saveAndFlush(agreement);
                  agreementId[0] = agreement.getId();

                  // Save the first period
                  retainerPeriodRepository.saveAndFlush(
                      new RetainerPeriod(
                          agreement.getId(),
                          LocalDate.of(2026, 4, 1),
                          LocalDate.of(2026, 5, 1),
                          new BigDecimal("40.00"),
                          new BigDecimal("40.00"),
                          BigDecimal.ZERO));
                }));

    // Attempt to save a second period with the same agreementId + periodStart — must fail
    assertThatThrownBy(
            () ->
                runInTenant(
                    () ->
                        transactionTemplate.executeWithoutResult(
                            tx ->
                                retainerPeriodRepository.saveAndFlush(
                                    new RetainerPeriod(
                                        agreementId[0],
                                        LocalDate.of(2026, 4, 1), // same periodStart
                                        LocalDate.of(2026, 5, 1),
                                        new BigDecimal("40.00"),
                                        new BigDecimal("40.00"),
                                        BigDecimal.ZERO)))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void invoiceLineRetainerPeriodIdIsNullable() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      createActiveCustomer(
                          "Invoice Line Test Corp", "retainer_invoice@test.com", memberId);
                  customer = customerRepository.saveAndFlush(customer);

                  var invoice =
                      new Invoice(
                          customer.getId(),
                          "USD",
                          "Invoice Line Test Corp",
                          "retainer_invoice@test.com",
                          null,
                          "V31 Migration Test Org",
                          memberId);
                  invoice = invoiceRepository.saveAndFlush(invoice);

                  // Line with null retainerPeriodId should succeed
                  var lineWithoutRetainer =
                      new InvoiceLine(
                          invoice.getId(),
                          null,
                          null,
                          "Standard fee",
                          new BigDecimal("1.0000"),
                          new BigDecimal("1000.00"),
                          0);
                  lineWithoutRetainer = invoiceLineRepository.saveAndFlush(lineWithoutRetainer);
                  assertThat(lineWithoutRetainer.getRetainerPeriodId()).isNull();

                  // Create a retainer and period for FK
                  var agreement =
                      new RetainerAgreement(
                          customer.getId(),
                          "Invoice Link Retainer",
                          RetainerType.FIXED_FEE,
                          RetainerFrequency.MONTHLY,
                          LocalDate.of(2026, 3, 1),
                          null,
                          null,
                          new BigDecimal("5000.00"),
                          RolloverPolicy.FORFEIT,
                          null,
                          null,
                          memberId);
                  agreement = retainerAgreementRepository.saveAndFlush(agreement);

                  var period =
                      new RetainerPeriod(
                          agreement.getId(),
                          LocalDate.of(2026, 3, 1),
                          LocalDate.of(2026, 4, 1),
                          null, // FIXED_FEE has no allocatedHours
                          null,
                          BigDecimal.ZERO);
                  period = retainerPeriodRepository.saveAndFlush(period);

                  // Line with non-null retainerPeriodId (FK to retainer_periods) should succeed
                  var lineWithRetainer =
                      new InvoiceLine(
                          invoice.getId(),
                          null,
                          null,
                          "Retainer fee",
                          new BigDecimal("1.0000"),
                          new BigDecimal("5000.00"),
                          1);
                  lineWithRetainer.setRetainerPeriodId(period.getId());
                  lineWithRetainer = invoiceLineRepository.saveAndFlush(lineWithRetainer);
                  assertThat(lineWithRetainer.getRetainerPeriodId()).isEqualTo(period.getId());
                }));
  }

  // --- Helpers ---

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
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
