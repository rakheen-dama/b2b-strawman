package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.ProblemDetailAssertions;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * G88-M13 (Phase 89 §14.3.18): {@code GET /api/matters/{matterId}/trust-balance} — VIEW_TRUST +
 * matter visibility (404 off-matter), the per-matter RECORDED/APPROVED sum, and 200 + zero when the
 * trust module is off (the frontend hides the tile) — including a tenant that recorded trust money
 * under legal-za and then cleared its profile. Deposits go through the real API write path in
 * dedicated legal-za tenants.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatterTrustBalanceEndpointTest {

  /** legal-za → trust_accounting enabled, ZAR. */
  private static final String ORG_ID = "org_matter_trust_balance";

  /** No vertical profile + "ZA" → trust module off, ZAR default currency. */
  private static final String OFF_ORG_ID = "org_matter_trust_balance_off";

  private static final String OWNER = "user_mtb_owner";

  /** Default member role HAS VIEW_TRUST; on matterShared only. */
  private static final String MEMBER = "user_mtb_member";

  /** Member with the capability override {@code -VIEW_TRUST}. */
  private static final String NO_TRUST = "user_mtb_notrust";

  private static final String OFF_OWNER = "user_mtb_off_owner";

  /** legal-za at deposit time, then the owner clears the vertical profile → trust module off. */
  private static final String FLIPPED_ORG_ID = "org_matter_trust_balance_flipped";

  private static final String FLIPPED_OWNER = "user_mtb_flipped_owner";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private TrustTransactionRepository trustTransactionRepository;

  private UUID matterA;
  private UUID matterB;
  private UUID matterEmpty;
  private UUID matterShared;
  private UUID matterOff;
  private UUID matterFlipped;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Matter Trust Balance Org", "legal-za");
    String schema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    UUID ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "mtb_owner@test.com", "MTB Owner", "owner"));
    UUID memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, MEMBER, "mtb_member@test.com", "MTB Member", "member"));
    UUID noTrustId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, NO_TRUST, "mtb_notrust@test.com", "MTB No Trust", "member"));

    UUID[] client = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var noTrust = memberRepository.findById(noTrustId).orElseThrow();
              noTrust.setCapabilityOverrides(Set.of("-VIEW_TRUST"));
              memberRepository.save(noTrust);

              client[0] =
                  customerRepository
                      .saveAndFlush(
                          TestCustomerFactory.createActiveCustomer(
                              "Trust Balance Client", "mtb_client@test.com", ownerId))
                      .getId();
              matterA = saveMatter("Trust Matter A", client[0], ownerId);
              matterB = saveMatter("Trust Matter B", client[0], ownerId);
              matterEmpty = saveMatter("Trust Matter Empty", client[0], ownerId);
              matterShared = saveMatter("Trust Matter Shared", client[0], ownerId);
            });

    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    String trustAccountId = createTrustAccount(ownerJwt, "62000000634");

    deposit(ownerJwt, trustAccountId, client[0], matterA, "50000.00", "DEP/634B/A");
    deposit(ownerJwt, trustAccountId, client[0], matterB, "12000.00", "DEP/634B/B");
    // Legacy client money not tagged to any matter — counts on no matter's balance. Recording one
    // through the API is refused since G89-F28, so the pre-F28 row is written directly (D6: legacy
    // rows are not backfilled).
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () ->
                trustTransactionRepository.save(
                    new TrustTransaction(
                        UUID.fromString(trustAccountId),
                        "DEPOSIT",
                        new java.math.BigDecimal("3000.00"),
                        client[0],
                        null,
                        null,
                        "DEP/634B/NONE",
                        "634B legacy unmattered deposit",
                        java.time.LocalDate.of(2026, 4, 1),
                        "RECORDED",
                        ownerId)));
    deposit(ownerJwt, trustAccountId, client[0], matterShared, "750.00", "DEP/634B/SHARED");

    mockMvc
        .perform(
            post("/api/matters/" + matterShared + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberId)))
        .andExpect(status().isCreated());

    provisioningService.provisionTenant(OFF_ORG_ID, "Matter Trust Off Org", null, "ZA");
    TestMemberHelper.syncMember(
        mockMvc, OFF_ORG_ID, OFF_OWNER, "mtb_off_owner@test.com", "MTB Off Owner", "owner");
    matterOff =
        UUID.fromString(
            TestEntityHelper.createProject(
                mockMvc, TestJwtFactory.ownerJwt(OFF_ORG_ID, OFF_OWNER), "No Trust Matter"));

    setupFlippedTenant();
  }

  /**
   * Records trust money on a matter while legal-za is active, then clears the profile through the
   * real owner endpoint — the trust rows stay, the module goes off.
   */
  private void setupFlippedTenant() throws Exception {
    provisioningService.provisionTenant(FLIPPED_ORG_ID, "Matter Trust Flipped Org", "legal-za");
    String schema =
        orgSchemaMappingRepository.findByClerkOrgId(FLIPPED_ORG_ID).orElseThrow().getSchemaName();
    UUID ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                FLIPPED_ORG_ID,
                FLIPPED_OWNER,
                "mtb_flipped_owner@test.com",
                "MTB Flipped Owner",
                "owner"));
    UUID[] client = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, FLIPPED_ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              client[0] =
                  customerRepository
                      .saveAndFlush(
                          TestCustomerFactory.createActiveCustomer(
                              "Flipped Trust Client", "mtb_flipped_client@test.com", ownerId))
                      .getId();
              matterFlipped = saveMatter("Flipped Trust Matter", client[0], ownerId);
            });

    var ownerJwt = TestJwtFactory.ownerJwt(FLIPPED_ORG_ID, FLIPPED_OWNER);
    String trustAccountId = createTrustAccount(ownerJwt, "62000000635");
    deposit(ownerJwt, trustAccountId, client[0], matterFlipped, "4200.00", "DEP/634B/FLIP");

    mockMvc
        .perform(
            patch("/api/settings/vertical-profile")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"verticalProfile\": null}"))
        .andExpect(status().isOk());
  }

  @Test
  void owner_getsOnlyThatMattersBalance() throws Exception {
    JsonNode body = getOk(matterA, TestJwtFactory.ownerJwt(ORG_ID, OWNER));
    assertThat(body.get("matterId").asText()).isEqualTo(matterA.toString());
    assertThat(body.get("balance").decimalValue()).isEqualByComparingTo("50000.00");
    assertThat(body.get("currency").asText()).isEqualTo("ZAR");

    JsonNode b = getOk(matterB, TestJwtFactory.ownerJwt(ORG_ID, OWNER));
    assertThat(b.get("balance").decimalValue()).isEqualByComparingTo("12000.00");
  }

  @Test
  void matterWithoutTransactions_isZero() throws Exception {
    JsonNode body = getOk(matterEmpty, TestJwtFactory.ownerJwt(ORG_ID, OWNER));
    assertThat(body.get("matterId").asText()).isEqualTo(matterEmpty.toString());
    assertThat(body.get("balance").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(body.get("currency").asText()).isEqualTo("ZAR");
  }

  @Test
  void memberOnTheMatter_seesIt() throws Exception {
    JsonNode body = getOk(matterShared, TestJwtFactory.memberJwt(ORG_ID, MEMBER));
    assertThat(body.get("balance").decimalValue()).isEqualByComparingTo("750.00");
  }

  @Test
  void nonMember_gets404ProblemDetail() throws Exception {
    ProblemDetailAssertions.assertProblem(
        mockMvc.perform(trustBalance(matterA).with(TestJwtFactory.memberJwt(ORG_ID, MEMBER))),
        HttpStatus.NOT_FOUND,
        "Matter not found");
  }

  @Test
  void unknownMatter_gets404ProblemDetail() throws Exception {
    ProblemDetailAssertions.assertProblem(
        mockMvc.perform(
            trustBalance(UUID.randomUUID()).with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))),
        HttpStatus.NOT_FOUND,
        "Matter not found");
  }

  @Test
  void withoutViewTrust_gets403() throws Exception {
    mockMvc
        .perform(trustBalance(matterShared).with(TestJwtFactory.memberJwt(ORG_ID, NO_TRUST)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  /**
   * G89-F64: the client Trust tab's first call. Without VIEW_TRUST it is a 403 ProblemDetail, never
   * a 500 — the 500 the walk saw was the Next server action rethrowing this 403.
   */
  @Test
  void trustAccountsList_withoutViewTrust_gets403NotA500() throws Exception {
    mockMvc
        .perform(get("/api/trust-accounts").with(TestJwtFactory.memberJwt(ORG_ID, NO_TRUST)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  @Test
  void trustModuleOff_still200WithZero() throws Exception {
    JsonNode body = getOk(matterOff, TestJwtFactory.ownerJwt(OFF_ORG_ID, OFF_OWNER));
    assertThat(body.get("matterId").asText()).isEqualTo(matterOff.toString());
    assertThat(body.get("balance").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(body.get("currency").asText()).isEqualTo("ZAR");
  }

  @Test
  void trustModuleTurnedOffAfterDeposits_still200WithZero() throws Exception {
    JsonNode body = getOk(matterFlipped, TestJwtFactory.ownerJwt(FLIPPED_ORG_ID, FLIPPED_OWNER));
    assertThat(body.get("matterId").asText()).isEqualTo(matterFlipped.toString());
    assertThat(body.get("balance").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(body.get("currency").asText()).isEqualTo("ZAR");
  }

  // --- helpers ---

  private UUID saveMatter(String name, UUID clientId, UUID ownerId) {
    var project = new Project(name, "634B trust balance", ownerId);
    project.setCustomerId(clientId);
    return projectRepository.saveAndFlush(project).getId();
  }

  private String createTrustAccount(RequestPostProcessor ownerJwt, String accountNumber)
      throws Exception {
    return TestEntityHelper.extractId(
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "634B Trust Account",
                          "bankName": "Test Bank",
                          "branchCode": "250655",
                          "accountNumber": "%s",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-15"
                        }
                        """
                            .formatted(accountNumber)))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private void deposit(
      RequestPostProcessor ownerJwt,
      String trustAccountId,
      UUID clientId,
      UUID matterId,
      String amount,
      String reference)
      throws Exception {
    String matterField = matterId == null ? "" : "\"matterId\": \"%s\",".formatted(matterId);
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      %s
                      "amount": %s,
                      "reference": "%s",
                      "description": "634B matter trust balance deposit",
                      "transactionDate": "2026-04-01"
                    }
                    """
                        .formatted(clientId, matterField, amount, reference)))
        .andExpect(status().isCreated());
  }

  private MockHttpServletRequestBuilder trustBalance(UUID matterId) {
    return get("/api/matters/" + matterId + "/trust-balance");
  }

  private JsonNode getOk(UUID matterId, RequestPostProcessor jwt) throws Exception {
    return objectMapper.readTree(
        mockMvc
            .perform(trustBalance(matterId).with(jwt))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }
}
