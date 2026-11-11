package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PR #1757 review (CodeRabbit, founder D39): an inter-client transfer posts each leg on its own
 * client's matter. TRANSFER_OUT debits the source client on {@code sourceMatterId} (linked to the
 * source); TRANSFER_IN credits the target client on {@code targetMatterId} (linked to the target).
 * Both matters are validated before either leg is written, and a transfer reversal undoes each leg
 * on its own matter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustTransferTargetMatterIntegrationTest {

  private static final String ORG_ID = "org_trust_transfer_target_matter";
  private static final String OWNER_SUB = "user_tttm_owner";

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

  @Autowired
  @Qualifier("portalJdbcClient")
  private JdbcClient portalJdbc;

  private String tenantSchema;
  private UUID ownerId;
  private UUID trustAccountId;
  private LocalDate today;
  private int counter = 0;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Transfer Target Matter Org", "legal-za")
            .schemaName();
    ownerId = UUID.fromString(TestMemberHelper.syncOwner(mockMvc, ORG_ID, "tttm"));
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
                                  "Transfer Target Matter Trust",
                                  "Test Bank",
                                  "250655",
                                  "6200-000-TTTM",
                                  TrustAccountType.GENERAL,
                                  true,
                                  false,
                                  null,
                                  LocalDate.of(2025, 1, 1),
                                  "PR #1757 transfer tests"))
                          .getId();
                    }));
  }

  @Test
  void transfer_postsEachLegOnItsOwnClientsMatter_andReachesBothPortals() throws Exception {
    UUID source = newClient("Transfer Source");
    UUID sourceMatter = newMatter(source, "Transfer Source Matter");
    UUID target = newClient("Transfer Target");
    UUID targetMatter = newMatter(target, "Transfer Target Matter");
    deposit(source, sourceMatter, "1000.00");

    String body =
        transfer(source, sourceMatter, target, targetMatter, "400.00")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat((String) JsonPath.read(body, "$[0].transactionType")).isEqualTo("TRANSFER_OUT");
    assertThat((String) JsonPath.read(body, "$[0].matterId")).isEqualTo(sourceMatter.toString());
    assertThat((String) JsonPath.read(body, "$[1].transactionType")).isEqualTo("TRANSFER_IN");
    assertThat((String) JsonPath.read(body, "$[1].matterId")).isEqualTo(targetMatter.toString());

    assertThat(matterBalance(sourceMatter)).isEqualByComparingTo("600.00");
    assertThat(matterBalance(targetMatter)).isEqualByComparingTo("400.00");
    // Each client's portal trust view equals its firm ledger card
    assertThat(portalBalance(source)).isEqualByComparingTo(cardBalance(source));
    assertThat(portalBalance(target)).isEqualByComparingTo(cardBalance(target));
    assertThat(portalBalance(target)).isEqualByComparingTo("400.00");
  }

  @Test
  void transfer_targetMatterNotLinkedToTarget_rejected_nothingWritten() throws Exception {
    UUID source = newClient("Unlinked Target Source");
    UUID sourceMatter = newMatter(source, "Unlinked Target Source Matter");
    UUID target = newClient("Unlinked Target");
    deposit(source, sourceMatter, "1000.00");

    // The target matter is the SOURCE's matter: not the target's
    transfer(source, sourceMatter, target, sourceMatter, "400.00")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Matter not linked to client"))
        .andExpect(jsonPath("$.code").value("MATTER_NOT_LINKED_TO_CLIENT"));

    assertThat(cardBalance(source)).isEqualByComparingTo("1000.00");
    assertThat(cardBalanceOrNull(target)).isNull();
    assertThat(matterBalance(sourceMatter)).isEqualByComparingTo("1000.00");
  }

  @Test
  void transfer_withoutTargetMatter_rejected() throws Exception {
    UUID source = newClient("No Target Matter Source");
    UUID sourceMatter = newMatter(source, "No Target Matter Source Matter");
    UUID target = newClient("No Target Matter Target");
    deposit(source, sourceMatter, "1000.00");

    transfer(source, sourceMatter, target, null, "400.00")
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail").value("A matter is required for client trust transactions"));
    assertThat(cardBalanceOrNull(target)).isNull();
  }

  @Test
  void transferReversal_undoesEachLegOnItsOwnMatter() throws Exception {
    UUID source = newClient("Reversal Source");
    UUID sourceMatter = newMatter(source, "Reversal Source Matter");
    UUID target = newClient("Reversal Target");
    UUID targetMatter = newMatter(target, "Reversal Target Matter");
    deposit(source, sourceMatter, "1000.00");

    String body =
        transfer(source, sourceMatter, target, targetMatter, "250.00")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID transferOutId = UUID.fromString(JsonPath.read(body, "$[0].id"));
    // reverseTransaction accepts APPROVED rows only; recordTransfer writes RECORDED legs (as in
    // TrustMatterDebitGuardIntegrationTest.reverseTransferOut_targetAlreadySpent_rejected)
    as(
        () ->
            transactionTemplate.execute(
                tx -> {
                  var out = transactionRepository.findById(transferOutId).orElseThrow();
                  out.setStatus("APPROVED");
                  return transactionRepository.save(out);
                }));

    mockMvc
        .perform(
            post("/api/trust-transactions/" + transferOutId + "/reverse")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Transferred in error\"}"))
        .andExpect(status().isCreated());

    var reversals =
        as(
            () ->
                transactionRepository.findAll().stream()
                    .filter(t -> "REVERSAL".equals(t.getTransactionType()))
                    .filter(
                        t -> source.equals(t.getCustomerId()) || target.equals(t.getCustomerId()))
                    .toList());
    assertThat(reversals).hasSize(2);
    assertThat(reversals)
        .anySatisfy(
            r -> {
              assertThat(r.getCustomerId()).isEqualTo(source);
              assertThat(r.getProjectId()).isEqualTo(sourceMatter);
            })
        .anySatisfy(
            r -> {
              assertThat(r.getCustomerId()).isEqualTo(target);
              assertThat(r.getProjectId()).isEqualTo(targetMatter);
            });
    assertThat(cardBalance(source)).isEqualByComparingTo("1000.00");
    assertThat(cardBalance(target)).isEqualByComparingTo("0.00");
    assertThat(matterBalance(sourceMatter)).isEqualByComparingTo("1000.00");
    assertThat(matterBalance(targetMatter)).isEqualByComparingTo("0.00");
  }

  // --- helpers ---

  private ResultActions transfer(
      UUID source, UUID sourceMatter, UUID target, UUID targetMatter, String amount)
      throws Exception {
    String targetMatterField =
        targetMatter == null ? "" : "\"targetMatterId\": \"%s\",".formatted(targetMatter);
    return mockMvc.perform(
        post("/api/trust-accounts/" + trustAccountId + "/transactions/transfer")
            .with(ownerJwt())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"sourceClientId": "%s", "sourceMatterId": "%s", "targetClientId": "%s", %s
                 "amount": %s, "reference": "TTTM-TRF-%d", "transactionDate": "%s"}
                """
                    .formatted(
                        source,
                        sourceMatter,
                        target,
                        targetMatterField,
                        amount,
                        ++counter,
                        today)));
  }

  private void deposit(UUID client, UUID matter, String amount) throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clientId": "%s", "matterId": "%s", "amount": %s,
                     "reference": "TTTM-DEP-%d", "transactionDate": "%s"}
                    """
                        .formatted(client, matter, amount, ++counter, today.minusDays(1))))
        .andExpect(status().isCreated());
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
                                name, "tttm_client_" + n + "@test.com", ownerId))
                        .getId()));
  }

  /** A matter opened for {@code client} (Project.customerId, the direct link). */
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
    var balance = cardBalanceOrNull(client);
    assertThat(balance).as("ledger card of %s", client).isNotNull();
    return balance;
  }

  private BigDecimal cardBalanceOrNull(UUID client) {
    return as(
        () ->
            ledgerCardRepository
                .findByTrustAccountIdAndCustomerId(trustAccountId, client)
                .map(card -> card.getBalance())
                .orElse(null));
  }

  /** The client's portal trust balance: the sum of its per-matter portal balances. */
  private BigDecimal portalBalance(UUID client) {
    return portalJdbc
        .sql(
            "SELECT COALESCE(SUM(current_balance), 0) FROM portal.portal_trust_balance"
                + " WHERE customer_id = ?")
        .params(client)
        .query(BigDecimal.class)
        .single();
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
}
