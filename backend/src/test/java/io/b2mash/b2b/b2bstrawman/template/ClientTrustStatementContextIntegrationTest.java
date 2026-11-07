package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G89 632A / D11: the Client Trust Statement (legal-za, CUSTOMER) printed "[Transaction details to
 * be inserted]" — the generic client context had no trust data. The client context now carries a
 * {@code trust} block (account name, opening / closing balance and the client's ledger lines, all
 * history to today) when the trust accounting module is on, on the account the client has activity
 * on (the Statement of Account's selection), and the v13 template prints it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientTrustStatementContextIntegrationTest {

  private static final String ORG_ID = "org_client_trust_statement_ctx_test";
  private static final String OWNER = "user_cts_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private CustomerContextBuilder customerContextBuilder;
  @Autowired private PdfRenderingService pdfRenderingService;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private TrustTransactionService trustTransactionService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;

  private String schema;
  private UUID memberId;
  private UUID clientId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Trust Statement Attorneys", "legal-za");
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "cts_owner@test.com", "CTS Owner", "owner"));
    clientId =
        inTenantTx(
            () -> {
              UUID id =
                  customerRepository
                      .save(
                          TestCustomerFactory.createActiveCustomer(
                              "Ndlovu Family Trust", "ndlovu@test.com", memberId))
                      .getId();
              UUID accountId =
                  trustAccountService
                      .createTrustAccount(
                          new CreateTrustAccountRequest(
                              "Ndlovu Attorneys Trust Account",
                              "First National Bank",
                              "250655",
                              "6200000001",
                              "GENERAL",
                              true,
                              false,
                              null,
                              LocalDate.of(2026, 1, 1),
                              "Client trust statement test"))
                      .id();
              trustTransactionService.recordDeposit(
                  accountId,
                  new RecordDepositRequest(
                      id,
                      null,
                      new BigDecimal("1000.00"),
                      "DEP-CTS-001",
                      "Deposit on instruction",
                      LocalDate.of(2026, 2, 10)));
              trustTransactionService.recordDeposit(
                  accountId,
                  new RecordDepositRequest(
                      id,
                      null,
                      new BigDecimal("500.00"),
                      "DEP-CTS-002",
                      "Top-up for transfer costs",
                      LocalDate.of(2026, 3, 5)));
              return id;
            });
  }

  @Test
  @SuppressWarnings("unchecked")
  void rendersBalanceAndLines() throws Exception {
    Map<String, Object> context =
        inTenantTx(() -> customerContextBuilder.buildContext(clientId, memberId));

    assertThat(context).containsKey("trust");
    var trust = (Map<String, Object>) context.get("trust");
    assertThat(trust.get("account_name")).isEqualTo("Ndlovu Attorneys Trust Account");
    assertThat((BigDecimal) trust.get("opening_balance")).isEqualByComparingTo("0");
    assertThat((BigDecimal) trust.get("closing_balance")).isEqualByComparingTo("1500.00");
    var lines = (List<Map<String, Object>>) trust.get("lines");
    assertThat(lines)
        .extracting(l -> l.get("reference"))
        .containsExactly("DEP-CTS-001", "DEP-CTS-002");
    assertThat((BigDecimal) lines.getLast().get("runningBalance")).isEqualByComparingTo("1500.00");

    UUID ctsId =
        inTenantTx(
            () ->
                documentTemplateRepository
                    .findPackSeeded("legal-za", "client-trust-statement")
                    .getFirst()
                    .getId());
    var result =
        mockMvc
            .perform(
                post("/api/templates/" + ctsId + "/preview")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"entityId\":\"%s\"}".formatted(clientId)))
            .andExpect(status().isOk())
            .andReturn();
    String html = JsonPath.read(result.getResponse().getContentAsString(), "$.html");
    assertThat(html)
        .doesNotContain("to be inserted")
        .contains("DEP-CTS-001", "DEP-CTS-002", "Ndlovu Attorneys Trust Account")
        .contains(
            VariableFormatter.format(
                new BigDecimal("1500.00"), "currency", (Locale) context.get("_locale")));
  }

  @Test
  void trustModuleDisabled_noTrustBlock() {
    // trust_accounting is a vertical module (not toggleable over the API): switch it off directly.
    List<String> enabled = inTenantTx(() -> List.copyOf(orgSettings().getEnabledModules()));
    setEnabledModules(enabled.stream().filter(m -> !"trust_accounting".equals(m)).toList());
    try {
      Map<String, Object> context =
          inTenantTx(() -> customerContextBuilder.buildContext(clientId, memberId));
      assertThat(context).doesNotContainKey("trust");
    } finally {
      setEnabledModules(enabled);
    }
  }

  /**
   * Security review (PR #1749): trust balances and lines need the trust_accounting module AND
   * VIEW_TRUST (the matter list's rule, ProjectService). The generic generate / preview path does
   * not require VIEW_TRUST, so without it the block is withheld and the statement says so.
   */
  @Test
  void memberWithoutViewTrust_noTrustBlock() {
    Set<String> noTrust = Set.of("TEAM_OVERSIGHT", "VIEW_LEGAL");
    Map<String, Object> context =
        inTenantTx(noTrust, () -> customerContextBuilder.buildContext(clientId, memberId));
    assertThat(context).doesNotContainKey("trust");

    String html =
        inTenantTx(
            noTrust, () -> pdfRenderingService.previewHtml(ctsTemplateId(), clientId, memberId));
    assertThat(html)
        .contains("Trust details are not available")
        .doesNotContain("DEP-CTS-001")
        .doesNotContain("Ndlovu Attorneys Trust Account");
  }

  /** Positive control: VIEW_TRUST alone (no other trust capability) sees the block. */
  @Test
  @SuppressWarnings("unchecked")
  void memberWithViewTrust_seesTrustBlock() {
    Map<String, Object> context =
        inTenantTx(
            Set.of("VIEW_TRUST"), () -> customerContextBuilder.buildContext(clientId, memberId));
    var trust = (Map<String, Object>) context.get("trust");
    assertThat(trust).isNotNull();
    assertThat((List<Map<String, Object>>) trust.get("lines"))
        .extracting(l -> l.get("reference"))
        .containsExactly("DEP-CTS-001", "DEP-CTS-002");
  }

  private UUID ctsTemplateId() {
    return inTenantTx(
        () ->
            documentTemplateRepository
                .findPackSeeded("legal-za", "client-trust-statement")
                .getFirst()
                .getId());
  }

  private OrgSettings orgSettings() {
    return orgSettingsService.getOrCreateForCurrentTenant();
  }

  private void setEnabledModules(List<String> modules) {
    inTenantTx(
        () -> {
          var settings = orgSettings();
          settings.setEnabledModules(modules);
          return orgSettingsRepository.save(settings);
        });
  }

  private <T> T inTenantTx(Supplier<T> body) {
    return inTenantTx("owner", Set.of("APPROVE_TRUST_PAYMENT", "MANAGE_TRUST", "VIEW_TRUST"), body);
  }

  /** A member scope holding exactly {@code capabilities}. */
  private <T> T inTenantTx(Set<String> capabilities, Supplier<T> body) {
    return inTenantTx("member", capabilities, body);
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
