package io.b2mash.b2b.b2bstrawman.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateService;
import io.b2mash.b2b.b2bstrawman.costrate.CostRateService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP tests for the Profitability page CSV export ({@code GET
 * /api/reports/profitability/export/csv}). The CSV must carry exactly the rows, order and values of
 * the JSON endpoint the page renders, with numbers rounded to 2dp and nulls as empty cells.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportControllerTest {

  private static final String ORG_ID = "org_report_ctrl_csv_test";
  private static final String OWNER_SUBJECT = "user_rptcsv_owner";
  private static final String ADMIN_SUBJECT = "user_rptcsv_admin";
  private static final String MEMBER_SUBJECT = "user_rptcsv_member";

  private static final String CSV_URL = "/api/reports/profitability/export/csv";
  private static final String JSON_URL = "/api/reports/profitability";
  private static final String FROM = "2025-01-01";
  private static final String TO = "2025-12-31";

  private static final String CRLF = "\r\n";
  private static final String HEADER =
      "Matter,Client,Currency,Billable Hours,Revenue,Cost,Margin,Margin %";

  // Margin DESC, nulls last: A (180) > B (90) > C (no cost snapshot, so no margin).
  private static final String ROW_A = "High Margin Matter,Acme,ZAR,2.00,300.00,120.00,180.00,60.00";
  private static final String ROW_B =
      "\"Smith, \"\"Jnr\"\" v RAF\",'+27 Holdings,ZAR,1.00,150.00,60.00,90.00,60.00";
  private static final String ROW_C = "'=Disbursement Recovery,,ZAR,1.00,150.00,,,";

  /** Reads JSON numbers as exact decimals so the 2dp comparison is not polluted by doubles. */
  private static final JsonMapper DECIMAL_JSON =
      JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

  @Autowired private MockMvc mockMvc;
  @Autowired private ProjectService projectService;
  @Autowired private TaskService taskService;
  @Autowired private TimeEntryService timeEntryService;
  @Autowired private BillingRateService billingRateService;
  @Autowired private CostRateService costRateService;
  @Autowired private CustomerService customerService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectService customerProjectService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Report Controller CSV Test Org", null);

    var ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER_SUBJECT, "rptcsv_owner@test.com", "CSV Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, ADMIN_SUBJECT, "rptcsv_admin@test.com", "CSV Admin", "admin");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, MEMBER_SUBJECT, "rptcsv_member@test.com", "CSV Member", "member");

    var tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    var owner = new ActorContext(ownerId, "owner");

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              billingRateService.createRate(
                  ownerId,
                  null,
                  null,
                  "ZAR",
                  new BigDecimal("150.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  owner);
              // The cost rate starts after matter C's entry date, so that entry has no cost
              // snapshot and its row exercises the null cost/margin cells.
              costRateService.createCostRate(
                  ownerId, "ZAR", new BigDecimal("60.00"), LocalDate.of(2025, 1, 10), null, owner);

              var acme = activeCustomer("Acme", "rptcsv_acme@test.com", ownerId);
              var holdings = activeCustomer("+27 Holdings", "rptcsv_holdings@test.com", ownerId);

              // Matter A: 2h billable => revenue 300, cost 120, margin 180, margin % 60
              var matterA = projectService.createProject("High Margin Matter", "A", ownerId);
              customerProjectService.linkCustomerToProject(acme, matterA.getId(), ownerId, owner);
              logTime(matterA.getId(), LocalDate.of(2025, 1, 15), 120, owner);

              // Matter B: 1h billable => revenue 150, cost 60, margin 90, margin % 60
              var matterB = projectService.createProject("Smith, \"Jnr\" v RAF", "B", ownerId);
              customerProjectService.linkCustomerToProject(
                  holdings, matterB.getId(), ownerId, owner);
              logTime(matterB.getId(), LocalDate.of(2025, 1, 20), 60, owner);

              // Matter C: no client, 1h billable before the cost rate starts => cost is null
              var matterC = projectService.createProject("=Disbursement Recovery", "C", ownerId);
              logTime(matterC.getId(), LocalDate.of(2025, 1, 5), 60, owner);
            });
  }

  @Test
  void exportCsv_owner_returns200TextCsvAttachment() throws Exception {
    mockMvc
        .perform(csv(FROM, TO).with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT)))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/csv;charset=UTF-8"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    "attachment; filename=\"profitability-2025-01-01-to-2025-12-31.csv\""));
  }

  @Test
  void exportCsv_writesHeaderAndRowsInMarginOrder() throws Exception {
    var body = csvBody(csv(FROM, TO).with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT)));

    assertThat(body).isEqualTo(HEADER + CRLF + ROW_A + CRLF + ROW_B + CRLF + ROW_C + CRLF);
  }

  @Test
  void exportCsv_nullCostRow_hasEmptyCostMarginAndMarginPercentCells() throws Exception {
    var lines = lines(csvBody(csv(FROM, TO).with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT))));

    var cells = lines[3].split(",", -1);
    assertThat(cells).hasSize(8);
    assertThat(cells[0]).isEqualTo("'=Disbursement Recovery");
    assertThat(cells[1]).as("client").isEmpty();
    assertThat(cells[4]).as("revenue").isEqualTo("150.00");
    assertThat(cells[5]).as("cost").isEmpty();
    assertThat(cells[6]).as("margin").isEmpty();
    assertThat(cells[7]).as("margin %").isEmpty();
  }

  @Test
  void exportCsv_rowsMatchJsonEndpointValuesRoundedTo2dp() throws Exception {
    var jsonBody =
        mockMvc
            .perform(
                get(JSON_URL)
                    .param("from", FROM)
                    .param("to", TO)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    JsonNode matters = DECIMAL_JSON.readTree(jsonBody).get("matters");

    var lines = lines(csvBody(csv(FROM, TO).with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT))));

    assertThat(lines).hasSize(matters.size() + 1);
    for (int i = 0; i < matters.size(); i++) {
      JsonNode matter = matters.get(i);
      // Numeric cells are the trailing five columns and never contain a comma.
      var cells = lines[i + 1].split(",", -1);
      var numeric = Arrays.copyOfRange(cells, cells.length - 5, cells.length);
      assertThat(cells[cells.length - 6]).isEqualTo(matter.get("currency").asString());
      assertThat(numeric)
          .as("row %d numeric cells", i)
          .containsExactly(
              twoDp(matter.get("billableHours")),
              twoDp(matter.get("billableValue")),
              twoDp(matter.get("costValue")),
              twoDp(matter.get("margin")),
              twoDp(matter.get("marginPercent")));
    }
  }

  @Test
  void exportCsv_admin_returns200() throws Exception {
    var body = csvBody(csv(FROM, TO).with(TestJwtFactory.adminJwt(ORG_ID, ADMIN_SUBJECT)));

    assertThat(lines(body)[0]).isEqualTo(HEADER);
    assertThat(lines(body)).hasSize(4);
  }

  @Test
  void exportCsv_withoutDates_exportsAllTimeAsProfitabilityCsv() throws Exception {
    var result =
        mockMvc
            .perform(get(CSV_URL).with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT)))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/csv;charset=UTF-8"))
            .andExpect(
                header()
                    .string("Content-Disposition", "attachment; filename=\"profitability.csv\""))
            .andReturn();

    assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .isEqualTo(HEADER + CRLF + ROW_A + CRLF + ROW_B + CRLF + ROW_C + CRLF);
  }

  @Test
  void exportCsv_rangeWithNoData_returnsHeaderOnly() throws Exception {
    var body =
        csvBody(
            csv("2030-01-01", "2030-01-31").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT)));

    assertThat(body).isEqualTo(HEADER + CRLF);
  }

  @Test
  void exportCsv_withoutFinancialVisibility_returns403() throws Exception {
    mockMvc
        .perform(csv(FROM, TO).with(TestJwtFactory.jwtAs(ORG_ID, MEMBER_SUBJECT, "member")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"))
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  void exportCsv_withoutAuthentication_returns401() throws Exception {
    mockMvc.perform(csv(FROM, TO)).andExpect(status().isUnauthorized());
  }

  private UUID activeCustomer(String name, String email, UUID ownerId) {
    var customer = customerService.createCustomer(name, email, null, null, null, ownerId);
    customer.setLifecycleStatus(LifecycleStatus.ACTIVE, null);
    return customerRepository.save(customer).getId();
  }

  private void logTime(UUID matterId, LocalDate date, int minutes, ActorContext actor) {
    var task =
        taskService.createTask(matterId, "CSV task", "CSV task", "MEDIUM", "TASK", null, actor);
    timeEntryService.createTimeEntry(task.getId(), date, minutes, true, null, "CSV work", actor);
  }

  private static MockHttpServletRequestBuilder csv(String from, String to) {
    return get(CSV_URL).param("from", from).param("to", to);
  }

  private String csvBody(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc
        .perform(request)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
  }

  private static String[] lines(String body) {
    assertThat(body).endsWith(CRLF);
    return body.substring(0, body.length() - CRLF.length()).split(CRLF, -1);
  }

  private static String twoDp(JsonNode node) {
    return node == null || node.isNull()
        ? ""
        : node.decimalValue().setScale(2, RoundingMode.HALF_UP).toPlainString();
  }
}
