package io.b2mash.b2b.b2bstrawman.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code GET /api/dashboard/trust-position} (Phase 87, Epic 614A). The composite must equal the
 * three trust reads it composes ({@code /cashbook-balance}, {@code /total-balance}, {@code
 * /pending-approvals}) and be gated exactly like them: {@code VIEW_TRUST} (403) and the {@code
 * trust_accounting} module (403 "Module not enabled"). Note the system {@code member} role holds
 * {@code VIEW_TRUST}, so the 403 case uses a custom role without it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustPositionIntegrationTest {
  private static final String ORG_ID = "org_dash_trust_position";
  private static final String OWNER = "user_dtp_owner";
  private static final String MEMBER = "user_dtp_member";

  /** Custom role holding VIEW_LEGAL only — no VIEW_TRUST. */
  private static final String NO_TRUST = "user_dtp_no_trust";

  /** Module off — every trust read (and the composite) answers 403 "Module not enabled". */
  private static final String NO_MODULE_ORG_ID = "org_dash_trust_position_nomod";

  private static final String NO_MODULE_OWNER = "user_dtp_nomod_owner";

  /** Module on, no trust account configured — the composite answers 404. */
  private static final String NO_ACCOUNT_ORG_ID = "org_dash_trust_position_noacct";

  private static final String NO_ACCOUNT_OWNER = "user_dtp_noacct_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;

  private final LocalDate today = LocalDate.now();
  private final LocalDate reconciledTo = today.minusDays(5);

  private String primaryAccountId;
  private String secondAccountId;

  @BeforeAll
  void setup() throws Exception {
    String schema =
        provisioningService.provisionTenant(ORG_ID, "Trust Position Org", null).schemaName();
    TestMemberHelper.syncMember(mockMvc, ORG_ID, OWNER, "dtp_owner@test.com", "DTP Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, MEMBER, "dtp_member@test.com", "DTP Member", "member");
    String noTrustId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, NO_TRUST, "dtp_no_trust@test.com", "DTP No Trust", "member");
    enableTrustAccounting(schema);

    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    assignRole(owner, noTrustId, createCustomRole(owner, "DTP No Trust Role", "VIEW_LEGAL"));
    primaryAccountId = createTrustAccount(owner, "Trust Primary", "62000000101", true);
    secondAccountId = createTrustAccount(owner, "Trust Secondary", "62000000102", false);
    String clientId =
        TestEntityHelper.createCustomer(mockMvc, owner, "Trust Position Client", "dtp@test.com");

    record(owner, primaryAccountId, "deposit", clientId, "10000.00", "DEP-DTP-1");
    record(owner, primaryAccountId, "payment", clientId, "2000.00", "PAY-DTP-1"); // awaiting
    record(owner, secondAccountId, "deposit", clientId, "500.00", "DEP-DTP-2");

    mockMvc
        .perform(
            post("/api/trust-accounts/" + primaryAccountId + "/reconciliations")
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"periodEnd\": \"%s\"}".formatted(reconciledTo)))
        .andExpect(status().isCreated());

    provisioningService.provisionTenant(NO_MODULE_ORG_ID, "Trust Position No Module Org", null);
    TestMemberHelper.syncMember(
        mockMvc,
        NO_MODULE_ORG_ID,
        NO_MODULE_OWNER,
        "dtp_nomod@test.com",
        "DTP NoMod Owner",
        "owner");

    String noAccountSchema =
        provisioningService
            .provisionTenant(NO_ACCOUNT_ORG_ID, "Trust Position No Account Org", null)
            .schemaName();
    TestMemberHelper.syncMember(
        mockMvc,
        NO_ACCOUNT_ORG_ID,
        NO_ACCOUNT_OWNER,
        "dtp_noacct@test.com",
        "DTP NoAcct Owner",
        "owner");
    enableTrustAccounting(noAccountSchema);
  }

  @Test
  void primaryAccountPositionEqualsItsParts() throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    String body =
        mockMvc
            .perform(get("/api/dashboard/trust-position").with(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trustAccountId").value(primaryAccountId))
            .andExpect(jsonPath("$.accountName").value("Trust Primary"))
            .andExpect(jsonPath("$.bankName").value("Test Bank"))
            .andExpect(jsonPath("$.lastReconciliationStatus").value("DRAFT"))
            .andExpect(jsonPath("$.lastReconciliationPeriodEnd").value(reconciledTo.toString()))
            .andExpect(jsonPath("$.daysSinceReconciliation").value(5))
            .andExpect(jsonPath("$.accounts", hasSize(2)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEqualsParts(owner, body, primaryAccountId);
    assertThat(pendingCount(body)).isEqualTo(1);

    // lastReconciliationBalanced matches the reconciliation the list endpoint returns.
    Object listedBalanced =
        JsonPath.read(
            read(owner, "/api/trust-accounts/" + primaryAccountId + "/reconciliations"),
            "$[0].isBalanced");
    assertThat((Object) JsonPath.read(body, "$.lastReconciliationBalanced"))
        .isEqualTo(listedBalanced);

    // accounts: both ids with the right isPrimary flag.
    assertThat(
            JsonPath.<List<Object>>read(
                body, "$.accounts[?(@.id == '%s')].isPrimary".formatted(primaryAccountId)))
        .containsExactly(true);
    assertThat(
            JsonPath.<List<Object>>read(
                body, "$.accounts[?(@.id == '%s')].isPrimary".formatted(secondAccountId)))
        .containsExactly(false);
  }

  @Test
  void requestedAccountPositionEqualsItsParts() throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    String body =
        mockMvc
            .perform(
                get("/api/dashboard/trust-position")
                    .param("accountId", secondAccountId)
                    .with(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trustAccountId").value(secondAccountId))
            .andExpect(jsonPath("$.accountName").value("Trust Secondary"))
            // Never reconciled → reconciliation fields null.
            .andExpect(jsonPath("$.lastReconciliationPeriodEnd").doesNotExist())
            .andExpect(jsonPath("$.lastReconciliationStatus").doesNotExist())
            .andExpect(jsonPath("$.lastReconciliationBalanced").doesNotExist())
            .andExpect(jsonPath("$.daysSinceReconciliation").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEqualsParts(owner, body, secondAccountId);
    assertThat(pendingCount(body)).isZero();
  }

  @Test
  void unknownAccountIdReturns404() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/trust-position")
                .param("accountId", UUID.randomUUID().toString())
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void callerWithoutViewTrustIsForbiddenLikeTheParts() throws Exception {
    var noTrust = TestJwtFactory.memberJwt(ORG_ID, NO_TRUST);
    mockMvc
        .perform(get("/api/dashboard/trust-position").with(noTrust))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/trust-accounts/" + primaryAccountId + "/cashbook-balance").with(noTrust))
        .andExpect(status().isForbidden());
  }

  @Test
  void systemMemberHoldsViewTrustSoSeesTheCompositeLikeTheParts() throws Exception {
    // The system "member" role is seeded with VIEW_TRUST (V1 baseline), so it reads both.
    var member = TestJwtFactory.memberJwt(ORG_ID, MEMBER);
    mockMvc
        .perform(get("/api/dashboard/trust-position").with(member))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trustAccountId").value(primaryAccountId));
    mockMvc
        .perform(get("/api/trust-accounts/" + primaryAccountId + "/cashbook-balance").with(member))
        .andExpect(status().isOk());
  }

  @Test
  void moduleOffIsForbiddenLikeTheParts() throws Exception {
    var owner = TestJwtFactory.ownerJwt(NO_MODULE_ORG_ID, NO_MODULE_OWNER);
    mockMvc
        .perform(get("/api/dashboard/trust-position").with(owner))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Module not enabled"))
        .andExpect(jsonPath("$.moduleId").value("trust_accounting"));
    mockMvc
        .perform(get("/api/trust-accounts/" + UUID.randomUUID() + "/cashbook-balance").with(owner))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Module not enabled"));
  }

  @Test
  void noTrustAccountConfiguredReturns404() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/trust-position")
                .with(TestJwtFactory.ownerJwt(NO_ACCOUNT_ORG_ID, NO_ACCOUNT_OWNER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("No trust account"))
        .andExpect(jsonPath("$.detail").value("No trust account has been configured"));
  }

  // --- helpers ---

  private void assertEqualsParts(JwtRequestPostProcessor jwt, String body, String accountId)
      throws Exception {
    String base = "/api/trust-accounts/" + accountId;
    BigDecimal cashbook = decimal(read(jwt, base + "/cashbook-balance"), "$.balance");
    BigDecimal ledger = decimal(read(jwt, base + "/total-balance"), "$.balance");
    int pending = JsonPath.<List<Object>>read(read(jwt, base + "/pending-approvals"), "$").size();

    assertThat(decimal(body, "$.cashbookBalance")).isEqualByComparingTo(cashbook);
    assertThat(decimal(body, "$.clientLedgerTotal")).isEqualByComparingTo(ledger);
    assertThat(decimal(body, "$.difference")).isEqualByComparingTo(cashbook.subtract(ledger));
    assertThat(pendingCount(body)).isEqualTo(pending);
  }

  private int pendingCount(String body) {
    return JsonPath.<Integer>read(body, "$.pendingApprovalCount");
  }

  private static BigDecimal decimal(String json, String path) {
    return new BigDecimal(String.valueOf((Object) JsonPath.read(json, path)));
  }

  private String read(JwtRequestPostProcessor jwt, String url) throws Exception {
    return mockMvc
        .perform(get(url).with(jwt))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String createCustomRole(JwtRequestPostProcessor owner, String name, String capability)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","description":"Test role","capabilities":["%s"]}"""
                            .formatted(name, capability)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(JwtRequestPostProcessor owner, String memberId, String roleId)
      throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + memberId + "/role")
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
  }

  private void enableTrustAccounting(String schema) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));
  }

  private String createTrustAccount(
      JwtRequestPostProcessor jwt, String name, String accountNumber, boolean primary)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "%s",
                          "bankName": "Test Bank",
                          "branchCode": "250655",
                          "accountNumber": "%s",
                          "accountType": "GENERAL",
                          "isPrimary": %s,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-15"
                        }
                        """
                            .formatted(name, accountNumber, primary)))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractId(result);
  }

  private void record(
      JwtRequestPostProcessor jwt,
      String accountId,
      String kind,
      String clientId,
      String amount,
      String reference)
      throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + accountId + "/transactions/" + kind)
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "amount": %s,
                      "reference": "%s",
                      "description": "Trust position fixture",
                      "transactionDate": "%s"
                    }
                    """
                        .formatted(clientId, amount, reference, today.minusDays(10))))
        .andExpect(status().isCreated());
  }
}
