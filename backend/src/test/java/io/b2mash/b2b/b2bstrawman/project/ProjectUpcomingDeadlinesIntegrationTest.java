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
 * Controller-level integration test for {@code GET /api/matters/{id}/upcoming-deadlines} (GAP-L-58
 * / E9.3).
 *
 * <p>Asserts that with two future court dates and one past court date on a matter, the endpoint
 * returns exactly the two future rows, ordered by date ASC and tagged {@code COURT}. The regulatory
 * deadlines source ({@code regulatory_deadlines} module) was removed in Phase 86 (Epic 604A).
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

    // Enable court_calendar — the only remaining source of matter deadlines.
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("court_calendar"));
                  orgSettingsRepository.save(settings);
                }));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer = createActiveCustomer("Acme Corp", "acme@test.com", memberId);
                  customer = customerRepository.saveAndFlush(customer);
                  customerId = customer.getId();

                  var project = new Project("Acme v Beta", "Litigation matter", memberId);
                  project.setCustomerId(customerId);
                  project = projectRepository.saveAndFlush(project);
                  projectId = project.getId();

                  // 2 future court dates (saved later-first) — both must surface as type=COURT,
                  // ordered by date ASC.
                  var laterCd =
                      new CourtDate(
                          projectId,
                          customerId,
                          "TRIAL",
                          LocalDate.now().plusDays(90),
                          LocalTime.of(10, 0),
                          "Johannesburg High Court",
                          "2026/CIV/124",
                          "Judge Later",
                          "Trial",
                          7,
                          memberId);
                  courtDateRepository.saveAndFlush(laterCd);

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
                }));
  }

  @Test
  void returnsFutureCourtDatesOnly_taggedCourt() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/api/matters/{id}/upcoming-deadlines", projectId)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pud_owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andReturn();

    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    java.util.List<java.util.Map<String, Object>> rows =
        mapper.readValue(
            result.getResponse().getContentAsString(),
            new com.fasterxml.jackson.core.type.TypeReference<>() {});

    // Scoped to this matter's own court dates: 2 future rows; the past one is filtered out.
    org.assertj.core.api.Assertions.assertThat(rows).hasSize(2);
    org.assertj.core.api.Assertions.assertThat(rows)
        .allSatisfy(
            r -> {
              org.assertj.core.api.Assertions.assertThat(r.get("type")).isEqualTo("COURT");
              org.assertj.core.api.Assertions.assertThat(r.get("date")).isNotNull();
              org.assertj.core.api.Assertions.assertThat(r.get("description")).isNotNull();
            });
    org.assertj.core.api.Assertions.assertThat(rows)
        .extracting(r -> r.get("type"))
        .doesNotContain("REGULATORY");
    org.assertj.core.api.Assertions.assertThat(rows.get(0).get("description").toString())
        .contains("Pretoria High Court");
    org.assertj.core.api.Assertions.assertThat(rows.get(1).get("description").toString())
        .contains("Johannesburg High Court");
  }

  @Test
  void rowsAreOrderedByDateAsc() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/api/matters/{id}/upcoming-deadlines", projectId)
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
            get("/api/matters/{id}/upcoming-deadlines", UUID.randomUUID())
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
