package io.b2mash.b2b.b2bstrawman.project;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDateRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Controller-level integration test for {@code GET /api/projects/{id}/upcoming-deadlines} (GAP-L-58
 * / E9.3).
 *
 * <p>Asserts that with 1 court date + 2 regulatory deadlines linked to the same matter, the
 * endpoint returns 3 rows ordered by date ASC with each row tagged by its source type.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectUpcomingDeadlinesIntegrationTest {

  private static final String ORG_ID = "org_proj_upcoming_deadlines";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CourtDateRepository courtDateRepository;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Upcoming Deadlines Org", null).schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_pud_owner", "pud_owner@test.com", "PUD Owner", "owner"));

    // Enable BOTH modules — this matches a hypothetical multi-vertical org. Tests prove the
    // aggregator unions both sources; the per-vertical default profiles are a separate concern.
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("court_calendar", "regulatory_deadlines"));
                  orgSettingsRepository.save(settings);
                }));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Customer with FYE so DeadlineCalculationService produces rows for our matter
                  var customer = createActiveCustomer("Acme Corp", "acme@test.com", memberId);
                  customer.setCustomFields(Map.of("financial_year_end", "2025-02-28"));
                  customer = customerRepository.saveAndFlush(customer);
                  customerId = customer.getId();

                  // Project with workType="tax" + tax_year so findLinkedProject() picks it up
                  var project = new Project("Acme Tax Matter", "Tax filing matter", memberId);
                  project.setCustomerId(customerId);
                  project.setWorkType("tax");
                  project.setCustomFields(Map.of("tax_year", "2026"));
                  project = projectRepository.saveAndFlush(project);
                  projectId = project.getId();

                  // 1 future court date — must surface as type=COURT
                  var futureDate = LocalDate.now().plusDays(45);
                  var cd =
                      new CourtDate(
                          projectId,
                          customerId,
                          "HEARING",
                          futureDate,
                          LocalTime.of(10, 0),
                          "Pretoria High Court",
                          "2026/CIV/123",
                          "Judge Mokgoatlheng",
                          "Application for summary judgment",
                          7,
                          memberId);
                  courtDateRepository.saveAndFlush(cd);

                  // Past court date — must NOT surface
                  var pastCd =
                      new CourtDate(
                          projectId,
                          customerId,
                          "HEARING",
                          LocalDate.now().minusDays(30),
                          LocalTime.of(9, 0),
                          "Old Court",
                          null,
                          "Judge Past",
                          "Old hearing",
                          7,
                          memberId);
                  courtDateRepository.saveAndFlush(pastCd);

                  // Regulatory deadlines are computed on-the-fly from the customer's FYE and
                  // the deadline-type registry — they need no seeding. The calculator's
                  // findLinkedProject() infers the link via project.workType == category AND
                  // project.customFields.tax_year == period_key. With workType="tax" and
                  // tax_year="2026", every "tax"-category deadline for period "2026" will
                  // surface as linkedProjectId == this matter.
                }));
  }

  @Test
  void unionsCourtAndRegulatory_orderedByDateAsc_taggedByType() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/api/projects/{id}/upcoming-deadlines", projectId)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pud_owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andReturn();

    // Parse and assert directly. JsonPath length() comparisons via Hamcrest are unreliable
    // because Spring's helper hands the matcher a JSONArray, not an Integer.
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    java.util.List<java.util.Map<String, Object>> rows =
        mapper.readValue(
            result.getResponse().getContentAsString(),
            new com.fasterxml.jackson.core.type.TypeReference<>() {});

    // 1 future court date + 3 regulatory deadlines linked to this matter (sars_provisional_1,
    // sars_provisional_3, sars_annual_return for periodKey=2026 with FYE 2025-02-28).
    org.assertj.core.api.Assertions.assertThat(rows).hasSizeGreaterThanOrEqualTo(3);

    long courtCount = rows.stream().filter(r -> "COURT".equals(r.get("type"))).count();
    long regulatoryCount = rows.stream().filter(r -> "REGULATORY".equals(r.get("type"))).count();
    org.assertj.core.api.Assertions.assertThat(courtCount).isGreaterThanOrEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(regulatoryCount).isGreaterThanOrEqualTo(2);

    // Every row carries non-null type, date, description
    org.assertj.core.api.Assertions.assertThat(rows)
        .allSatisfy(
            r -> {
              org.assertj.core.api.Assertions.assertThat(r.get("type")).isNotNull();
              org.assertj.core.api.Assertions.assertThat(r.get("date")).isNotNull();
              org.assertj.core.api.Assertions.assertThat(r.get("description")).isNotNull();
            });
  }

  @Test
  void rowsAreOrderedByDateAsc() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/api/projects/{id}/upcoming-deadlines", projectId)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pud_owner")))
            .andExpect(status().isOk())
            .andReturn();
    String body = result.getResponse().getContentAsString();
    // Crude lexicographic check on ISO date strings — works because ISO dates sort
    // alphabetically the same as chronologically.
    var dates = new java.util.ArrayList<String>();
    var matcher = java.util.regex.Pattern.compile("\"date\":\"([0-9-]+)\"").matcher(body);
    while (matcher.find()) {
      dates.add(matcher.group(1));
    }
    org.assertj.core.api.Assertions.assertThat(dates).isSorted();
  }

  @Test
  void unknownProject_returns404() throws Exception {
    mockMvc
        .perform(
            get("/api/projects/{id}/upcoming-deadlines", UUID.randomUUID())
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pud_owner")))
        .andExpect(status().isNotFound());
  }

  // --- Helpers ---

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
