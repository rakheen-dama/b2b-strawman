package io.b2mash.b2b.b2bstrawman.verticals.legal.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.GenerateStatementRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.StatementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Security review (PR #1749): the Statement of Account prints the client's trust activity only for
 * a member who may see trust money — the trust_accounting module AND VIEW_TRUST, the matter list's
 * rule. Generating a statement needs GENERATE_STATEMENT_OF_ACCOUNT only, so without VIEW_TRUST the
 * trust section is withheld ("Trust details are not available") instead of printing the ledger.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatementTrustVisibilityIntegrationTest {

  private static final String ORG_ID = "org_statement_trust_visibility_test";
  private static final String OWNER = "user_soa_trust_owner";
  private static final String DEPOSIT_REF = "DEP-SOA-VIS-001";
  private static final LocalDate PERIOD_START = LocalDate.of(2026, 1, 1);
  private static final LocalDate PERIOD_END = LocalDate.of(2026, 3, 31);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private TrustTransactionService trustTransactionService;
  @Autowired private StatementOfAccountContextBuilder contextBuilder;
  @Autowired private StatementService statementService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private ObjectMapper objectMapper;

  private String schema;
  private UUID memberId;
  private UUID matterId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Statement Trust Visibility Attorneys", "legal-za");
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "soa_trust_owner@test.com", "SoA Trust Owner", "owner"));
    UUID clientId =
        inTenantTx(
            "owner",
            Set.of("MANAGE_TRUST", "VIEW_TRUST"),
            () ->
                customerRepository
                    .save(
                        TestCustomerFactory.createActiveCustomer(
                            "Khumalo Holdings", "khumalo@test.com", memberId))
                    .getId());
    var created =
        mockMvc
            .perform(
                post("/api/matters")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Khumalo transfer","clientId":"%s","workType":"CONVEYANCING"}"""
                            .formatted(clientId)))
            .andExpect(status().isCreated())
            .andReturn();
    matterId = UUID.fromString(TestEntityHelper.extractId(created));
    inTenantTx(
        "owner",
        Set.of("MANAGE_TRUST", "VIEW_TRUST"),
        () -> {
          UUID accountId =
              trustAccountService
                  .createTrustAccount(
                      new CreateTrustAccountRequest(
                          "Khumalo Trust Account",
                          "Standard Bank",
                          "051001",
                          "6200000002",
                          "GENERAL",
                          true,
                          false,
                          null,
                          LocalDate.of(2026, 1, 1),
                          "SoA trust visibility test"))
                  .id();
          return trustTransactionService.recordDeposit(
              accountId,
              new RecordDepositRequest(
                  clientId,
                  matterId,
                  new BigDecimal("2500.00"),
                  DEPOSIT_REF,
                  "Transfer costs deposit",
                  LocalDate.of(2026, 2, 10)));
        });
  }

  @Test
  @SuppressWarnings("unchecked")
  void soaTrustBlock_memberWithoutViewTrust_omitted() {
    Set<String> noTrust = Set.of("GENERATE_STATEMENT_OF_ACCOUNT", "VIEW_LEGAL");

    Map<String, Object> context =
        inTenantTx(
            "member", noTrust, () -> contextBuilder.build(matterId, PERIOD_START, PERIOD_END));
    var trust = (Map<String, Object>) context.get("trust");
    assertThat((List<?>) trust.get("deposits")).isEmpty();
    assertThat((List<?>) trust.get("payments")).isEmpty();
    assertThat(String.valueOf(trust)).doesNotContain(DEPOSIT_REF).doesNotContain("2500");

    String html = generateHtml("member", noTrust);
    assertThat(html)
        .contains("Trust details are not available")
        .doesNotContain(DEPOSIT_REF)
        .doesNotContain("Trust Activity");
  }

  /** Positive control: a member holding VIEW_TRUST gets the trust activity. */
  @Test
  void soaTrustBlock_memberWithViewTrust_included() {
    String html =
        generateHtml("member", Set.of("GENERATE_STATEMENT_OF_ACCOUNT", "VIEW_LEGAL", "VIEW_TRUST"));
    assertThat(html).contains(DEPOSIT_REF).doesNotContain("Trust details are not available");
  }

  /** Positive control over HTTP: the owner (every capability, resolved by MemberFilter). */
  @Test
  void soaTrustBlock_owner_includedOverHttp() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/matters/" + matterId + "/statements")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"periodStart":"%s","periodEnd":"%s"}"""
                            .formatted(PERIOD_START, PERIOD_END)))
            .andExpect(status().isCreated())
            .andReturn();
    String html = JsonPath.read(result.getResponse().getContentAsString(), "$.htmlPreview");
    assertThat(html).contains(DEPOSIT_REF).doesNotContain("Trust details are not available");
  }

  /**
   * CodeRabbit #5: a statement generated without VIEW_TRUST must record its trust figure as
   * withheld, not as a zero the Statements table would show everyone as "no trust money held".
   */
  @Test
  @SuppressWarnings("unchecked")
  void withheldGeneration_summaryRecordsWithheld_notZero() {
    UUID statementId =
        generate("member", Set.of("GENERATE_STATEMENT_OF_ACCOUNT", "VIEW_LEGAL")).id();

    Map<String, Object> summary =
        inTenantTx(
            "owner",
            Set.of("VIEW_LEGAL", "VIEW_TRUST"),
            () ->
                (Map<String, Object>)
                    generatedDocumentRepository
                        .findById(statementId)
                        .orElseThrow()
                        .getContextSnapshot()
                        .get("summary"));
    assertThat(summary.get("trust_withheld")).isEqualTo(true);
    assertThat(summary).doesNotContainKey("trust_balance_held");

    // Even a reader who may see trust money gets the withheld state, never a fabricated zero.
    Map<String, Object> read = readSummary(statementId, Set.of("VIEW_LEGAL", "VIEW_TRUST"));
    assertThat(read.get("trustWithheld")).isEqualTo(true);
    assertThat(read.get("trustBalanceHeld")).isNull();
  }

  /**
   * G89-F51 (CodeRabbit #4): the list and get endpoints (VIEW_LEGAL) must not reveal a saved trust
   * balance to a reader without VIEW_TRUST; the stored snapshot is left as generated.
   */
  @Test
  void nonViewTrustReader_listAndGet_trustBalanceWithheld() {
    UUID statementId =
        generate("member", Set.of("GENERATE_STATEMENT_OF_ACCOUNT", "VIEW_LEGAL", "VIEW_TRUST"))
            .id();
    Set<String> legalOnly = Set.of("VIEW_LEGAL");

    Map<String, Object> got = readSummary(statementId, legalOnly);
    assertThat(got.get("trustWithheld")).isEqualTo(true);
    assertThat(got.get("trustBalanceHeld")).isNull();

    Map<String, Object> listed = listedSummary(statementId, legalOnly);
    assertThat(listed.get("trustWithheld")).isEqualTo(true);
    assertThat(listed.get("trustBalanceHeld")).isNull();
  }

  /** Control: a VIEW_TRUST reader sees the saved balance (R2 500 deposit, not withheld). */
  @Test
  void viewTrustReader_seesBalance() {
    UUID statementId =
        generate("member", Set.of("GENERATE_STATEMENT_OF_ACCOUNT", "VIEW_LEGAL", "VIEW_TRUST"))
            .id();
    Set<String> withTrust = Set.of("VIEW_LEGAL", "VIEW_TRUST");

    for (Map<String, Object> summary :
        List.of(readSummary(statementId, withTrust), listedSummary(statementId, withTrust))) {
      assertThat(summary.get("trustWithheld")).isNotEqualTo(true);
      assertThat(new BigDecimal(String.valueOf(summary.get("trustBalanceHeld"))))
          .isEqualByComparingTo("2500.00");
    }
  }

  /** The {@code summary} of GET /statements/{id}, as the wire JSON (a map). */
  @SuppressWarnings("unchecked")
  private Map<String, Object> readSummary(UUID statementId, Set<String> capabilities) {
    var response =
        inTenantTx("member", capabilities, () -> statementService.getById(matterId, statementId));
    return (Map<String, Object>) objectMapper.convertValue(response, Map.class).get("summary");
  }

  /** The {@code summary} of this statement's row in GET /statements, as the wire JSON (a map). */
  @SuppressWarnings("unchecked")
  private Map<String, Object> listedSummary(UUID statementId, Set<String> capabilities) {
    var page =
        inTenantTx(
            "member",
            capabilities,
            () -> statementService.list(matterId, PageRequest.of(0, 100)).getContent());
    var row = page.stream().filter(s -> s.id().equals(statementId)).findFirst().orElseThrow();
    return (Map<String, Object>) objectMapper.convertValue(row, Map.class).get("summary");
  }

  private StatementResponse generate(String orgRole, Set<String> capabilities) {
    return inTenantTx(
        orgRole,
        capabilities,
        () ->
            statementService.generate(
                matterId,
                new GenerateStatementRequest(PERIOD_START, PERIOD_END, null, false, false),
                memberId));
  }

  private String generateHtml(String orgRole, Set<String> capabilities) {
    return inTenantTx(
        orgRole,
        capabilities,
        () ->
            statementService
                .generate(
                    matterId,
                    new GenerateStatementRequest(PERIOD_START, PERIOD_END, null, false, false),
                    memberId)
                .htmlPreview());
  }

  private <T> T inTenantTx(String orgRole, Set<String> capabilities, Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, orgRole)
        .where(RequestScopes.CAPABILITIES, capabilities)
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }
}
