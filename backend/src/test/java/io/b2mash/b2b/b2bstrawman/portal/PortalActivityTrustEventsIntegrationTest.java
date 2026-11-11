package io.b2mash.b2b.b2bstrawman.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G89-F44 (D14): the portal "Firm actions" tab showed trust refund approvals but no deposit and no
 * interest. A recorded deposit and each posted interest credit now surface — and only to the client
 * whose money it is: client A sees A's deposit and A's interest, never B's. Driven through the real
 * firm trust endpoints and the real {@code GET /portal/activity?tab=FIRM}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalActivityTrustEventsIntegrationTest {

  private static final String ORG_ID = "org_pa_trust_events";
  private static final String OWNER = "user_pa_trust_owner";
  private static final String APPROVER = "user_pa_trust_approver";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectService customerProjectService;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID clientA;
  private UUID clientB;
  private UUID matterA;
  private UUID matterB;
  private String depositA;
  private String depositB;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Trust Events Firm", "legal-za").schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "pa_trust_owner@test.com", "Trust Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, APPROVER, "pa_trust_approver@test.com", "Trust Approver", "admin");

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                      clientA = clientWithMatter("Trust Events Client A", "pa-trust-a@test.com");
                      matterA = lastMatter;
                      clientB = clientWithMatter("Trust Events Client B", "pa-trust-b@test.com");
                      matterB = lastMatter;
                    }));

    String accountId = createAccountWithRate();
    depositA = deposit(accountId, clientA, matterA, "40000.00", "DEP-PA-A");
    depositB = deposit(accountId, clientB, matterB, "25000.00", "DEP-PA-B");
    postInterestRun(accountId, "2025-04-01", "2025-04-30");
  }

  @Test
  void deposit_appearsForItsClientOnly() throws Exception {
    var a = firmTab(clientA);
    var b = firmTab(clientB);

    assertThat(entityIds(a, "trust_deposit.recorded")).containsExactly(depositA);
    assertThat(entityIds(b, "trust_deposit.recorded")).containsExactly(depositB);
    assertThat(summary(a, "trust_deposit.recorded")).isEqualTo("Trust deposit received");
  }

  @Test
  void interestCredit_appearsForItsClientOnly() throws Exception {
    var a = firmTab(clientA);
    var b = firmTab(clientB);

    assertThat(matterIds(a, "trust_interest_credit.recorded")).containsExactly(matterA.toString());
    assertThat(matterIds(b, "trust_interest_credit.recorded")).containsExactly(matterB.toString());
    assertThat(summary(a, "trust_interest_credit.recorded"))
        .isEqualTo("Interest credited to your trust balance");
  }

  @Test
  void noTrustRowOfOneClientReachesTheOther() throws Exception {
    assertThat(firmTab(clientA))
        .filteredOn(row -> String.valueOf(row.get("eventType")).startsWith("trust_"))
        .extracting(row -> row.get("matterId"))
        .isNotEmpty()
        .containsOnly(matterA.toString());
  }

  // --- helpers ---

  private UUID lastMatter;

  /** An ACTIVE client with a portal contact and one linked matter (in the current tx). */
  private UUID clientWithMatter(String name, String email) {
    UUID client =
        customerRepository
            .saveAndFlush(TestCustomerFactory.createActiveCustomer(name, email, memberId))
            .getId();
    portalContactService.createContact(
        ORG_ID, client, email, name, PortalContact.ContactRole.PRIMARY);
    var project = new Project(name + " Matter", "Trust events", memberId);
    project.setCustomerId(client);
    lastMatter = projectRepository.saveAndFlush(project).getId();
    customerProjectService.linkCustomerToProject(
        client, lastMatter, memberId, new ActorContext(memberId, "owner"));
    return client;
  }

  private String createAccountWithRate() throws Exception {
    var account =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"accountName": "PA Trust Account", "bankName": "First National Bank",
                         "branchCode": "250655", "accountNumber": "62000004444",
                         "accountType": "GENERAL", "isPrimary": false,
                         "requireDualApproval": false, "openedDate": "2025-01-01"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String accountId = TestEntityHelper.extractId(account);
    mockMvc
        .perform(
            post("/api/trust-accounts/" + accountId + "/lpff-rates")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"effectiveFrom": "2025-01-01", "ratePercent": 0.0750,
                     "lpffSharePercent": 0.7500, "notes": "PA rate"}
                    """))
        .andExpect(status().isCreated());
    return accountId;
  }

  /** Records a matter deposit dated 2025-03-01; returns the trust transaction id. */
  private String deposit(String accountId, UUID client, UUID matter, String amount, String ref)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + accountId + "/transactions/deposit")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clientId": "%s", "matterId": "%s", "amount": %s, "reference": "%s",
                         "description": "Matter deposit", "transactionDate": "2025-03-01"}
                        """
                            .formatted(client, matter, amount, ref)))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractId(result);
  }

  private void postInterestRun(String accountId, String start, String end) throws Exception {
    var created =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + accountId + "/interest-runs")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"periodStart": "%s", "periodEnd": "%s"}
                        """
                            .formatted(start, end)))
            .andExpect(status().isCreated())
            .andReturn();
    String runId = TestEntityHelper.extractId(created);
    mockMvc
        .perform(
            post("/api/interest-runs/" + runId + "/calculate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/interest-runs/" + runId + "/approve")
                .with(TestJwtFactory.adminJwt(ORG_ID, APPROVER)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/interest-runs/" + runId + "/post")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk());
  }

  private List<Map<String, Object>> firmTab(UUID client) throws Exception {
    String token = portalJwtService.issueToken(client, ORG_ID);
    String body =
        mockMvc
            .perform(
                get("/portal/activity")
                    .param("tab", "FIRM")
                    .param("size", "200")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.content");
  }

  private static List<Object> entityIds(List<Map<String, Object>> rows, String eventType) {
    return rows.stream()
        .filter(row -> eventType.equals(row.get("eventType")))
        .map(row -> row.get("entityId"))
        .toList();
  }

  private static List<Object> matterIds(List<Map<String, Object>> rows, String eventType) {
    return rows.stream()
        .filter(row -> eventType.equals(row.get("eventType")))
        .map(row -> row.get("matterId"))
        .toList();
  }

  private static String summary(List<Map<String, Object>> rows, String eventType) {
    return rows.stream()
        .filter(row -> eventType.equals(row.get("eventType")))
        .map(row -> String.valueOf(row.get("summary")))
        .findFirst()
        .orElse(null);
  }
}
