package io.b2mash.b2b.b2bstrawman.deadline;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.deadline.FilingStatusService.BatchUpdateRequest;
import io.b2mash.b2b.b2bstrawman.deadline.FilingStatusService.CreateFilingStatusRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FilingStatusServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_filing_status_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private FilingStatusService filingStatusService;
  @Autowired private FilingStatusRepository filingStatusRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private VerticalModuleRegistry verticalModuleRegistry;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Filing Status Test Org", null);
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_filing_owner", "filing_owner@test.com", "Filing Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.saveAndFlush(
                          createActiveCustomer("Filing Test Corp", "filing@test.com", memberId));
                  customerId = customer.getId();
                }));
  }

  @Test
  void upsert_createsNewFilingStatusRecord_withStatusFiled() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreateFilingStatusRequest(
                          customerId, "paye-monthly", "2026-01", "filed", "Filed on time", null);
                  var response = filingStatusService.upsert(request, memberId);

                  assertThat(response.id()).isNotNull();
                  assertThat(response.customerId()).isEqualTo(customerId);
                  assertThat(response.status()).isEqualTo("filed");
                  assertThat(response.filedAt()).isNotNull();
                }));
  }

  @Test
  void upsert_updatesExistingRecord_onSameCustomerSlugPeriod() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request1 =
                      new CreateFilingStatusRequest(
                          customerId, "vat-return", "2026-01", "filed", "Initial filing", null);
                  filingStatusService.upsert(request1, memberId);

                  var request2 =
                      new CreateFilingStatusRequest(
                          customerId, "vat-return", "2026-01", "not_applicable", "Corrected", null);
                  filingStatusService.upsert(request2, memberId);

                  var results =
                      filingStatusRepository.findByCustomerIdAndDeadlineTypeSlugAndPeriodKey(
                          customerId, "vat-return", "2026-01");
                  assertThat(results).isPresent();
                  assertThat(results.get().getStatus()).isEqualTo("not_applicable");
                }));
  }

  @Test
  void batchUpsert_createsMultipleRecords() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var items =
                      List.of(
                          new CreateFilingStatusRequest(
                              customerId, "it12-annual", "2025", "filed", null, null),
                          new CreateFilingStatusRequest(
                              customerId, "it12-annual", "2024", "filed", null, null),
                          new CreateFilingStatusRequest(
                              customerId,
                              "it12-annual",
                              "2023",
                              "not_applicable",
                              "Not required",
                              null));
                  var batch = new BatchUpdateRequest(items);
                  var results = filingStatusService.batchUpsert(batch, memberId);

                  assertThat(results).hasSize(3);
                }));
  }

  @Test
  void list_returnsAllFilingStatusesForCustomer() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  filingStatusService.upsert(
                      new CreateFilingStatusRequest(
                          customerId, "uif-monthly", "2026-01", "filed", null, null),
                      memberId);
                  filingStatusService.upsert(
                      new CreateFilingStatusRequest(
                          customerId, "uif-monthly", "2026-02", "filed", null, null),
                      memberId);

                  var results = filingStatusService.list(customerId, null, null);
                  assertThat(results).hasSizeGreaterThanOrEqualTo(2);
                }));
  }

  @Test
  void list_filtersByStatus() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  filingStatusService.upsert(
                      new CreateFilingStatusRequest(
                          customerId, "sdl-monthly", "2026-01", "filed", null, null),
                      memberId);
                  filingStatusService.upsert(
                      new CreateFilingStatusRequest(
                          customerId, "sdl-monthly", "2026-02", "not_applicable", "Exempt", null),
                      memberId);

                  var filedResults = filingStatusService.list(customerId, "sdl-monthly", "filed");
                  assertThat(filedResults).allMatch(r -> "filed".equals(r.status()));

                  var naResults =
                      filingStatusService.list(customerId, "sdl-monthly", "not_applicable");
                  assertThat(naResults).allMatch(r -> "not_applicable".equals(r.status()));
                }));
  }

  @Test
  void upsert_createsAuditEvent_filingStatusUpdated() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreateFilingStatusRequest(
                          customerId, "emp201-monthly", "2026-03", "filed", "Audit test", null);
                  var response = filingStatusService.upsert(request, memberId);

                  var page =
                      auditEventRepository.findByFilter(
                          "filing_status",
                          response.id(),
                          null,
                          "filing_status.updated",
                          null,
                          null,
                          Pageable.ofSize(10));
                  assertThat(page.getContent()).isNotEmpty();
                }));
  }

  @Test
  void verticalModuleRegistry_regulatoryDeadlines_isRegisteredAndActive() {
    var module = verticalModuleRegistry.getModule("regulatory_deadlines");
    assertThat(module).isPresent();
    assertThat(module.get().status()).isEqualTo("active");
    assertThat(module.get().defaultEnabledFor()).contains("accounting-za");
  }

  @Test
  void orgSettings_recordRatePackApplication_isIdempotent() {
    var settings = new OrgSettings("ZAR");
    settings.recordRatePackApplication("rate-pack-accounting-za", 1);
    assertThat(settings.isRatePackApplied("rate-pack-accounting-za", 1)).isTrue();
    // Call again -- idempotent (isRatePackApplied still returns true)
    settings.recordRatePackApplication("rate-pack-accounting-za", 1);
    assertThat(settings.isRatePackApplied("rate-pack-accounting-za", 1)).isTrue();
    // Verify true idempotency: list still has exactly 1 entry, not 2
    assertThat(settings.getRatePackStatus()).hasSize(1);
    // Different version is not applied
    assertThat(settings.isRatePackApplied("rate-pack-accounting-za", 2)).isFalse();
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
    return com.jayway.jsonpath.JsonPath.read(
        result.getResponse().getContentAsString(), "$.memberId");
  }
}
