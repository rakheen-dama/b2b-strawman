package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PR #1757 review (CodeRabbit): an investment withdrawal credits the client (on the placement's
 * matter) with the money the placement PAYMENT debited — so it is only allowed once that payment
 * has actually been completed (APPROVED). While the placement awaits approval, after it is
 * rejected, or after it has been reversed, nothing was debited and a withdrawal would create trust
 * money. Conversely, once the investment is withdrawn, reversing its placement payment would credit
 * the client a second time, so that reversal is refused.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustInvestmentPlacementGuardIntegrationTest {

  private static final String ORG_ID = "org_trust_investment_placement_guard";
  private static final String OWNER_SUB = "user_tipg_owner";
  private static final String APPROVER_SUB = "user_tipg_approver";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private ClientLedgerCardRepository ledgerCardRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private Clock clock;

  private String tenantSchema;
  private UUID ownerId;
  private UUID trustAccountId;
  private LocalDate today;
  private int counter = 0;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Investment Placement Guard Org", "legal-za")
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER_SUB, "tipg_owner@test.com", "TIPG Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, APPROVER_SUB, "tipg_approver@test.com", "TIPG Approver", "owner");
    today = LocalDate.now(clock);
    trustAccountId =
        as(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                      return trustAccountRepository
                          .saveAndFlush(
                              new TrustAccount(
                                  "Placement Guard Trust",
                                  "Test Bank",
                                  "250655",
                                  "6200-000-TIPG",
                                  TrustAccountType.GENERAL,
                                  true,
                                  false,
                                  null,
                                  LocalDate.of(2025, 1, 1),
                                  "PR #1757 investment tests"))
                          .getId();
                    }));
  }

  @Test
  void withdraw_whilePlacementAwaitsApproval_rejected_nothingCredited() throws Exception {
    Placed placed = place("Awaiting Placement");

    withdraw(placed.investmentId())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Cannot withdraw investment"));

    assertThat(cardBalance(placed.client())).isEqualByComparingTo("5000.00");
    assertThat(matterBalance(placed.matter())).isEqualByComparingTo("5000.00");
  }

  @Test
  void withdraw_afterPlacementRejected_rejected() throws Exception {
    Placed placed = place("Rejected Placement");
    mockMvc
        .perform(
            post("/api/trust-transactions/" + placed.placementId() + "/reject")
                .with(approverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Not instructed\"}"))
        .andExpect(status().isOk());

    withdraw(placed.investmentId())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Cannot withdraw investment"));
    assertThat(cardBalance(placed.client())).isEqualByComparingTo("5000.00");
  }

  @Test
  void withdraw_afterPlacementApproved_creditsThePlacementMatter() throws Exception {
    Placed placed = place("Approved Placement");
    approve(placed.placementId());
    assertThat(matterBalance(placed.matter())).isEqualByComparingTo("4000.00");

    String body =
        withdraw(placed.investmentId())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("WITHDRAWN");
    assertThat(cardBalance(placed.client())).isEqualByComparingTo("5000.00");
    assertThat(matterBalance(placed.matter())).isEqualByComparingTo("5000.00");
  }

  @Test
  void reversingTheWithdrawnInvestmentsPlacement_rejected_noDoubleCredit() throws Exception {
    Placed placed = place("Reversed After Withdrawal");
    approve(placed.placementId());
    withdraw(placed.investmentId()).andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/trust-transactions/" + placed.placementId() + "/reverse")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Placed in error\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Cannot reverse transaction"));

    assertThat(cardBalance(placed.client())).isEqualByComparingTo("5000.00");
    assertThat(matterBalance(placed.matter())).isEqualByComparingTo("5000.00");
  }

  // --- helpers ---

  private record Placed(UUID client, UUID matter, UUID investmentId, UUID placementId) {}

  /** A client with R5 000 on its matter and a R1 000 investment placed from that matter. */
  private Placed place(String name) throws Exception {
    UUID client = newClient(name + " Client");
    UUID matter = newMatter(client, name + " Matter");
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clientId": "%s", "matterId": "%s", "amount": 5000.00,
                     "reference": "TIPG-DEP-%d", "transactionDate": "%s"}
                    """
                        .formatted(client, matter, ++counter, today.minusDays(5))))
        .andExpect(status().isCreated());
    String placed =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/investments")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clientId": "%s", "matterId": "%s", "institution": "Test Bank",
                         "accountNumber": "TIPG-%d", "principal": 1000.00, "interestRate": 0.07,
                         "depositDate": "%s", "investmentBasis": "FIRM_DISCRETION"}
                        """
                            .formatted(client, matter, ++counter, today.minusDays(4))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new Placed(
        client,
        matter,
        UUID.fromString(JsonPath.read(placed, "$.id")),
        UUID.fromString(JsonPath.read(placed, "$.depositTransactionId")));
  }

  private void approve(UUID transactionId) throws Exception {
    mockMvc
        .perform(post("/api/trust-transactions/" + transactionId + "/approve").with(approverJwt()))
        .andExpect(status().isOk());
  }

  private ResultActions withdraw(UUID investmentId) throws Exception {
    return mockMvc.perform(
        post("/api/trust-investments/" + investmentId + "/withdraw").with(ownerJwt()));
  }

  private UUID newClient(String name) {
    int n = ++counter;
    return as(
        () ->
            transactionTemplate.execute(
                tx ->
                    customerRepository
                        .save(
                            TestCustomerFactory.createActiveCustomer(
                                name, "tipg_client_" + n + "@test.com", ownerId))
                        .getId()));
  }

  private UUID newMatter(UUID client, String name) {
    return as(
        () ->
            transactionTemplate.execute(
                tx -> {
                  var project = new Project(name, "PR #1757", ownerId);
                  project.setCustomerId(client);
                  return projectRepository.saveAndFlush(project).getId();
                }));
  }

  private BigDecimal matterBalance(UUID matter) {
    var balance = as(() -> transactionRepository.calculateBalanceByProjectId(matter));
    return balance == null ? BigDecimal.ZERO : balance;
  }

  private BigDecimal cardBalance(UUID client) {
    return as(
        () ->
            ledgerCardRepository
                .findByTrustAccountIdAndCustomerId(trustAccountId, client)
                .orElseThrow()
                .getBalance());
  }

  private <T> T as(Supplier<T> action) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(
            RequestScopes.CAPABILITIES,
            Set.of("APPROVE_TRUST_PAYMENT", "MANAGE_TRUST", "VIEW_TRUST"))
        .call(action::get);
  }

  private static JwtRequestPostProcessor ownerJwt() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB);
  }

  private static JwtRequestPostProcessor approverJwt() {
    return TestJwtFactory.ownerJwt(ORG_ID, APPROVER_SUB);
  }
}
