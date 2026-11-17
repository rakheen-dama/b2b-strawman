package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
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
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 92 646E: {@code GET /api/trust-transaction-types} — the registry as data (§16.4.1), the one
 * source of every type filter. Capability VIEW_TRUST; module {@code trust_accounting}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustTransactionTypeControllerTest {

  private static final String ORG_ID = "org_trust_txn_types";
  private static final String OWNER_SUBJECT = "user_trust_txn_types_owner";
  private static final String MEMBER_SUBJECT = "user_trust_txn_types_member";

  /** A member on a custom role holding VIEW_LEGAL only — no VIEW_TRUST. */
  private static final String NO_TRUST_SUBJECT = "user_trust_txn_types_no_trust";

  /** Module off — its own tenant, never given {@code trust_accounting}. */
  private static final String NO_MODULE_ORG_ID = "org_trust_txn_types_nomod";

  private static final String NO_MODULE_OWNER_SUBJECT = "user_trust_txn_types_nomod_owner";

  private static final String URL = "/api/trust-transaction-types";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;

  @BeforeAll
  void setup() throws Exception {
    var tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Transaction Types Test Org", null)
            .schemaName();
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        OWNER_SUBJECT,
        "trust_txn_types_owner@test.com",
        "Trust Types Owner",
        "owner");
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        MEMBER_SUBJECT,
        "trust_txn_types_member@test.com",
        "Trust Types Member",
        "member");
    var noTrustMemberId =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            NO_TRUST_SUBJECT,
            "trust_txn_types_no_trust@test.com",
            "Trust Types No Trust",
            "member");
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));
    assignRole(noTrustMemberId, createCustomRole("Trust Types No Trust Role", "VIEW_LEGAL"));

    provisioningService.provisionTenant(NO_MODULE_ORG_ID, "Trust Types No Module Org", null);
    TestMemberHelper.syncMember(
        mockMvc,
        NO_MODULE_ORG_ID,
        NO_MODULE_OWNER_SUBJECT,
        "trust_txn_types_nomod@test.com",
        "Trust Types NoMod Owner",
        "owner");
  }

  @Test
  void owner_readsTheRegistryAsData_inDeclarationOrder() throws Exception {
    mockMvc
        .perform(get(URL).with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(TrustTransactionType.values().length))
        .andExpect(jsonPath("$.length()").value(25))
        .andExpect(jsonPath("$[0].code").value("DEPOSIT"))
        .andExpect(jsonPath("$[0].label").value("Deposit"))
        .andExpect(jsonPath("$[0].direction").value("IN"))
        .andExpect(jsonPath("$[0].ledger").value("CLIENT"))
        .andExpect(jsonPath("$[0].origin").value("FIRM"))
        .andExpect(jsonPath("$[0].requiresApproval").value(false))
        .andExpect(jsonPath("$[0].reversal").value("NEEDS_APPROVAL"))
        .andExpect(jsonPath("$[0].clientVisible").value(true))
        .andExpect(jsonPath("$[?(@.code == 'PAYMENT')].requiresApproval").value(true))
        .andExpect(jsonPath("$[?(@.code == 'PAYMENT')].direction").value("OUT"))
        .andExpect(jsonPath("$[?(@.code == 'PAYMENT')].reversal").value("IMMEDIATE"))
        .andExpect(jsonPath("$[?(@.code == 'TRANSFER_IN')].direction").value("IN"))
        .andExpect(jsonPath("$[?(@.code == 'REVERSAL')].label").value("Reversal"))
        .andExpect(jsonPath("$[?(@.code == 'REVERSAL')].direction").value("NEUTRAL"))
        // A filter expression yields a list: [null] for a present-but-null field
        .andExpect(jsonPath("$[?(@.code == 'REVERSAL')].ledger").value(contains(nullValue())))
        .andExpect(jsonPath("$[?(@.code == 'REVERSAL')].origin").value(contains(nullValue())))
        .andExpect(jsonPath("$[?(@.code == 'FUND_INTEREST')].ledger").value("NON_CLIENT"))
        .andExpect(jsonPath("$[?(@.code == 'FUND_INTEREST')].origin").value("BANK"))
        .andExpect(jsonPath("$[?(@.code == 'FUND_INTEREST')].clientVisible").value(false))
        .andExpect(
            jsonPath("$[?(@.code == 'FUND_INTEREST')].label")
                .value("Interest received for the Legal Practitioners' Fidelity Fund"))
        // The retired interest types are gone from the registry (646A, V16)
        .andExpect(jsonPath("$[?(@.code == 'INTEREST_CREDIT')]").isEmpty())
        .andExpect(jsonPath("$[?(@.code == 'INTEREST_LPFF')]").isEmpty());
  }

  @Test
  void aMemberWithViewTrust_canRead() throws Exception {
    mockMvc
        .perform(get(URL).with(TestJwtFactory.memberJwt(ORG_ID, MEMBER_SUBJECT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(25));
  }

  @Test
  void aCallerWithoutViewTrust_isForbidden() throws Exception {
    mockMvc
        .perform(get(URL).with(TestJwtFactory.memberJwt(ORG_ID, NO_TRUST_SUBJECT)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  @Test
  void moduleOff_isForbidden() throws Exception {
    mockMvc
        .perform(get(URL).with(TestJwtFactory.ownerJwt(NO_MODULE_ORG_ID, NO_MODULE_OWNER_SUBJECT)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Module not enabled"))
        .andExpect(jsonPath("$.moduleId").value("trust_accounting"));
  }

  // --- helpers ---

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT);
  }

  private String createCustomRole(String name, String capability) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","description":"Test role","capabilities":["%s"]}"""
                            .formatted(name, capability)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(String memberId, String roleId) throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + memberId + "/role")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
  }
}
