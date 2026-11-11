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
import jakarta.persistence.EntityManager;
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
 * G89-F28 (MEDIUM, D34; fix design D36): a client trust row without a matter never reaches the
 * portal (the read model is per client and matter), so the client's portal trust balance fell short
 * of the firm client ledger. Every new client trust posting now names a matter linked to the
 * client: a missing matter is a 400 "A matter is required for client trust transactions".
 *
 * <p>Every request goes through the firm HTTP API (the UI path). Each test seeds its own client and
 * matter so balances never bleed between tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustClientMatterRequiredIntegrationTest {

  private static final String ORG_ID = "org_trust_client_matter_required";
  private static final String OWNER_SUB = "user_tcmr_owner";
  private static final String APPROVER_SUB = "user_tcmr_approver";
  private static final String MATTER_REQUIRED =
      "A matter is required for client trust transactions";

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
  @Autowired private EntityManager entityManager;
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
            .provisionTenant(ORG_ID, "Trust Client Matter Required Org", "legal-za")
            .schemaName();
    ownerId = UUID.fromString(TestMemberHelper.syncOwner(mockMvc, ORG_ID, "tcmr"));
    // Approves placement payments (single-approval mode refuses self-approval)
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, APPROVER_SUB, "tcmr_approver@test.com", "TCMR Approver", "owner");
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
                                  "Matter Required Trust",
                                  "Test Bank",
                                  "250655",
                                  "6200-000-TCMR",
                                  TrustAccountType.GENERAL,
                                  true,
                                  false,
                                  null,
                                  LocalDate.of(2025, 1, 1),
                                  "G89-F28 tests"))
                          .getId();
                    }));
  }

  // --- the defect: a no-matter deposit is refused; a matter deposit reaches the portal ---

  @Test
  void deposit_withoutMatter_rejected400_nothingPosted() throws Exception {
    UUID client = newClient("No Matter Deposit Client");

    postTrust("deposit", depositBody(client, null, "1000.00", "TCMR-DEP-NM"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(MATTER_REQUIRED));

    assertThat(as(() -> transactionRepository.findAll()))
        .noneMatch(t -> client.equals(t.getCustomerId()));
    assertThat(cardBalanceOrNull(client)).isNull();
  }

  @Test
  void deposit_withMatter_projectsToPortal_portalBalanceEqualsFirmLedger() throws Exception {
    UUID client = newClient("Matter Deposit Client");
    UUID matter = newMatter(client, "Matter Deposit Matter");

    postTrust("deposit", depositBody(client, matter, "2500.00", "TCMR-DEP-M"))
        .andExpect(status().isCreated());
    postTrust("deposit", depositBody(client, matter, "500.00", "TCMR-DEP-M2"))
        .andExpect(status().isCreated());

    assertThat(cardBalanceOrNull(client)).isEqualByComparingTo("3000.00");
    assertThat(portalBalance(client)).isEqualByComparingTo(cardBalanceOrNull(client));
  }

  // --- every other client posting path ---

  @Test
  void payment_withoutMatter_rejected400() throws Exception {
    UUID client = fundedClient("No Matter Payment Client");
    postTrust("payment", depositBody(client, null, "100.00", "TCMR-PAY-NM"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(MATTER_REQUIRED));
  }

  @Test
  void refund_withoutMatter_rejected400() throws Exception {
    UUID client = fundedClient("No Matter Refund Client");
    postTrust("refund", depositBody(client, null, "100.00", "TCMR-REF-NM"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(MATTER_REQUIRED));
  }

  @Test
  void transfer_withoutMatter_rejected400() throws Exception {
    UUID source = fundedClient("No Matter Transfer Source");
    UUID target = newClient("No Matter Transfer Target");
    postTrust(
            "transfer",
            """
            {"sourceClientId": "%s", "targetClientId": "%s", "amount": 100.00,
             "reference": "TCMR-TRF-NM", "transactionDate": "%s"}
            """
                .formatted(source, target, today))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(MATTER_REQUIRED));
    assertThat(cardBalanceOrNull(target)).isNull();
  }

  @Test
  void feeTransfer_feeNoteWithoutSingleMatter_rejected400() throws Exception {
    UUID client = fundedClient("No Matter Fee Transfer Client");
    UUID invoiceId = sentFeeNoteWithoutMatterLines(client, "100.00");
    postTrust(
            "fee-transfer",
            """
            {"clientId": "%s", "invoiceId": "%s", "amount": 100.00, "reference": "TCMR-FT-NM"}
            """
                .formatted(client, invoiceId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(MATTER_REQUIRED));
  }

  /**
   * PR #1757 review (CodeRabbit): the fee transfer's matter comes from the fee note, and it must
   * still be a matter of the client when the transfer is recorded — not only non-null.
   */
  @Test
  void feeTransfer_feeNoteMatterNoLongerLinkedToClient_rejected400() throws Exception {
    UUID client = newClient("Unlinked Fee Note Client");
    UUID matter = newMatter(client, "Unlinked Fee Note Matter");
    postTrust("deposit", depositBody(client, matter, "1000.00", "TCMR-DEP-FTUL"))
        .andExpect(status().isCreated());
    UUID invoiceId = sentFeeNoteWithoutMatterLines(client, "100.00");
    as(
        () ->
            transactionTemplate.execute(
                tx -> {
                  entityManager
                      .createNativeQuery(
                          """
                          INSERT INTO invoice_lines (id, invoice_id, project_id, line_type,
                              description, quantity, unit_price, amount, sort_order, tax_exempt,
                              created_at, updated_at)
                          VALUES (gen_random_uuid(), :invoiceId, :projectId, 'TIME',
                              'Legal services', 1, 100.00, 100.00, 0, false, now(), now())
                          """)
                      .setParameter("invoiceId", invoiceId)
                      .setParameter("projectId", matter)
                      .executeUpdate();
                  // The matter is then unlinked from the client
                  entityManager
                      .createNativeQuery("UPDATE projects SET customer_id = NULL WHERE id = :id")
                      .setParameter("id", matter)
                      .executeUpdate();
                  return entityManager
                      .createNativeQuery("DELETE FROM customer_projects WHERE project_id = :id")
                      .setParameter("id", matter)
                      .executeUpdate();
                }));

    postTrust(
            "fee-transfer",
            """
            {"clientId": "%s", "invoiceId": "%s", "amount": 100.00, "reference": "TCMR-FT-UL"}
            """
                .formatted(client, invoiceId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Matter not linked to client"))
        .andExpect(jsonPath("$.code").value("MATTER_NOT_LINKED_TO_CLIENT"));
  }

  @Test
  void deposit_matterNotLinkedToClient_rejected400() throws Exception {
    UUID client = newClient("Unlinked Matter Client");
    UUID other = newClient("Matter Owner Client");
    UUID othersMatter = newMatter(other, "Someone Else's Matter");

    postTrust("deposit", depositBody(client, othersMatter, "1000.00", "TCMR-DEP-UNLINKED"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Matter not linked to client"));
    assertThat(cardBalanceOrNull(client)).isNull();
  }

  @Test
  void deposit_coClientLinkedThroughJoinTable_accepted() throws Exception {
    UUID owner = newClient("Join Table Matter Owner");
    UUID coClient = newClient("Join Table Co-client");
    UUID matter = newMatter(owner, "Join Table Shared Matter");
    mockMvc
        .perform(post("/api/clients/" + coClient + "/matters/" + matter).with(ownerJwt()))
        .andExpect(status().isCreated());

    postTrust("deposit", depositBody(coClient, matter, "300.00", "TCMR-DEP-CO"))
        .andExpect(status().isCreated());
    assertThat(portalBalance(coClient)).isEqualByComparingTo("300.00");
  }

  // --- investments: placement needs a matter; the withdrawal returns to it ---

  @Test
  void placeInvestment_withoutMatter_rejected400() throws Exception {
    UUID client = fundedClient("No Matter Investment Client");
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/investments")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(investmentBody(client, null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(MATTER_REQUIRED));
  }

  @Test
  void investmentWithdrawal_returnsToPlacementMatter() throws Exception {
    UUID client = newClient("Investment Matter Client");
    UUID matter = newMatter(client, "Investment Matter");
    postTrust("deposit", depositBody(client, matter, "5000.00", "TCMR-DEP-INV"))
        .andExpect(status().isCreated());

    String placed =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/investments")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(investmentBody(client, matter)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID investmentId = UUID.fromString(JsonPath.read(placed, "$.id"));
    UUID placementId = UUID.fromString(JsonPath.read(placed, "$.depositTransactionId"));
    assertThat(projectOf(placementId)).isEqualTo(matter);
    // Only a completed (approved) placement can be withdrawn (PR #1757 review)
    mockMvc
        .perform(
            post("/api/trust-transactions/" + placementId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, APPROVER_SUB)))
        .andExpect(status().isOk());

    String withdrawn =
        mockMvc
            .perform(post("/api/trust-investments/" + investmentId + "/withdraw").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID withdrawalId = UUID.fromString(JsonPath.read(withdrawn, "$.withdrawalTransactionId"));
    assertThat(projectOf(withdrawalId)).isEqualTo(matter);
  }

  // --- legacy no-matter rows (D6: no backfill) still reverse ---

  @Test
  void reversal_ofLegacyUnmatteredPayment_stillWorks() throws Exception {
    UUID client = fundedClient("Legacy Reversal Client");
    UUID legacyPaymentId =
        as(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      var payment =
                          new TrustTransaction(
                              trustAccountId,
                              "PAYMENT",
                              new BigDecimal("400.00"),
                              client,
                              null,
                              null,
                              "TCMR-PAY-LEGACY",
                              "Legacy client-level payment",
                              today.minusDays(1),
                              "APPROVED",
                              ownerId);
                      var saved = transactionRepository.save(payment);
                      var card =
                          ledgerCardRepository
                              .findByAccountAndCustomerForUpdate(trustAccountId, client)
                              .orElseThrow();
                      card.addPayment(new BigDecimal("400.00"), today.minusDays(1));
                      ledgerCardRepository.save(card);
                      return saved.getId();
                    }));

    String body =
        mockMvc
            .perform(
                post("/api/trust-transactions/" + legacyPaymentId + "/reverse")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\": \"Paid in error\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat((Object) JsonPath.read(body, "$.matterId")).isNull();
    assertThat(cardBalanceOrNull(client)).isEqualByComparingTo("1000.00");
  }

  // --- helpers ---

  private ResultActions postTrust(String path, String body) throws Exception {
    return mockMvc.perform(
        post("/api/trust-accounts/" + trustAccountId + "/transactions/" + path)
            .with(ownerJwt())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private String depositBody(UUID client, UUID matter, String amount, String reference) {
    String matterField = matter == null ? "" : "\"matterId\": \"%s\",".formatted(matter);
    return """
        {"clientId": "%s", %s "amount": %s, "reference": "%s", "description": "F28",
         "transactionDate": "%s"}
        """
        .formatted(client, matterField, amount, reference + "-" + (++counter), today);
  }

  private String investmentBody(UUID client, UUID matter) {
    String matterField = matter == null ? "" : "\"matterId\": \"%s\",".formatted(matter);
    return """
        {"clientId": "%s", %s "institution": "Test Bank", "accountNumber": "INV-%d",
         "principal": 1000.00, "interestRate": 0.07, "depositDate": "%s",
         "investmentBasis": "FIRM_DISCRETION"}
        """
        .formatted(client, matterField, ++counter, today);
  }

  /** A client with R1 000 on a matter of its own. */
  private UUID fundedClient(String name) throws Exception {
    UUID client = newClient(name);
    UUID matter = newMatter(client, name + " Matter");
    postTrust("deposit", depositBody(client, matter, "1000.00", "TCMR-FUND"))
        .andExpect(status().isCreated());
    return client;
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
                                name, "tcmr_client_" + n + "@test.com", ownerId))
                        .getId()));
  }

  /** A matter opened for {@code client} (Project.customerId, the direct link). */
  private UUID newMatter(UUID client, String name) {
    return as(
        () ->
            transactionTemplate.execute(
                tx -> {
                  var project = new Project(name, "G89-F28", ownerId);
                  project.setCustomerId(client);
                  return projectRepository.saveAndFlush(project).getId();
                }));
  }

  /** A SENT fee note with no matter lines, so a fee transfer has no matter to infer. */
  private UUID sentFeeNoteWithoutMatterLines(UUID client, String total) {
    UUID invoiceId = UUID.randomUUID();
    int n = ++counter;
    as(
        () ->
            transactionTemplate.execute(
                tx ->
                    entityManager
                        .createNativeQuery(
                            """
                            INSERT INTO invoices (id, customer_id, invoice_number, status,
                                currency, subtotal, tax_amount, total, due_date, customer_name,
                                org_name, created_by, created_at, updated_at)
                            VALUES (:id, :customerId, :number, 'SENT', 'ZAR', :total, 0, :total,
                                :dueDate, 'No Matter Fee Transfer Client', 'Test Org', :memberId,
                                now(), now())
                            """)
                        .setParameter("id", invoiceId)
                        .setParameter("customerId", client)
                        .setParameter("number", "INV-TCMR-" + n)
                        .setParameter("total", new BigDecimal(total))
                        .setParameter("dueDate", today.plusDays(30))
                        .setParameter("memberId", ownerId)
                        .executeUpdate()));
    return invoiceId;
  }

  private UUID projectOf(UUID transactionId) {
    return as(() -> transactionRepository.findById(transactionId).orElseThrow().getProjectId());
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
