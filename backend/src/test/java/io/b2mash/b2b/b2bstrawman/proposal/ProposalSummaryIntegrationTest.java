package io.b2mash.b2b.b2bstrawman.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProposalSummaryIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_proposal_summary_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private MemberRepository memberRepository;

  private String memberIdOwner;
  private String customerId;
  private String schema;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Proposal Summary Test Org", null);

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_sum_owner", "sum_owner@test.com", "Summary Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_sum_member", "sum_member@test.com", "Summary Member", "member");

    customerId = createCustomer(TestJwtFactory.ownerJwt(ORG_ID, "user_sum_owner"));

    schema = SchemaNameGenerator.generateSchemaName(ORG_ID);

    // Assign system owner role to owner member for capability-based auth
    UUID ownerMemberUuid = UUID.fromString(memberIdOwner);
    String tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberUuid)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var ownerRole =
                  orgRoleRepository.findAll().stream()
                      .filter(r -> r.isSystem() && "owner".equals(r.getSlug()))
                      .findFirst()
                      .orElseThrow();
              var ownerMember = memberRepository.findById(ownerMemberUuid).orElseThrow();
              ownerMember.setOrgRoleEntity(ownerRole);
              memberRepository.save(ownerMember);
            });
  }

  // --- 362.4: Test 1 — byStatus counts ---

  @Test
  void getProposalSummary_variousStates_returnsCorrectByStatusCounts() throws Exception {
    // Capture baseline counts before creating test data
    var baseline = getSummaryBaseline();
    int baseDraft = baseline.draft();
    int baseSent = baseline.sent();
    int baseAccepted = baseline.accepted();
    int baseDeclined = baseline.declined();
    int baseExpired = baseline.expired();
    int baseTotal = baseline.total();

    String draftId = createProposal("Draft Summary Proposal");
    String sentId = createProposal("Sent Summary Proposal");
    String acceptedId = createProposal("Accepted Summary Proposal");
    String declinedId = createProposal("Declined Summary Proposal");
    String expiredId = createProposal("Expired Summary Proposal");

    setSent(sentId);
    setAccepted(acceptedId);
    setDeclined(declinedId);
    setExpired(expiredId);

    mockMvc
        .perform(
            get("/api/proposals/summary").with(TestJwtFactory.ownerJwt(ORG_ID, "user_sum_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(baseTotal + 5))
        .andExpect(jsonPath("$.byStatus.DRAFT").value(baseDraft + 1))
        .andExpect(jsonPath("$.byStatus.SENT").value(baseSent + 1))
        .andExpect(jsonPath("$.byStatus.ACCEPTED").value(baseAccepted + 1))
        .andExpect(jsonPath("$.byStatus.DECLINED").value(baseDeclined + 1))
        .andExpect(jsonPath("$.byStatus.EXPIRED").value(baseExpired + 1));
  }

  // --- 362.4: Test 2 — avgDaysToAcceptance ---

  @Test
  void getProposalSummary_acceptedProposals_calculatesAvgDaysToAcceptance() throws Exception {
    String p1 = createProposal("Accepted 4 Days");
    String p2 = createProposal("Accepted 6 Days");

    Instant now = Instant.now();
    Instant sentAt1 = now.minus(Duration.ofDays(10));
    Instant acceptedAt1 = sentAt1.plus(Duration.ofDays(4));
    Instant sentAt2 = now.minus(Duration.ofDays(20));
    Instant acceptedAt2 = sentAt2.plus(Duration.ofDays(6));

    setAcceptedWithTimestamps(p1, sentAt1, acceptedAt1);
    setAcceptedWithTimestamps(p2, sentAt2, acceptedAt2);

    mockMvc
        .perform(
            get("/api/proposals/summary").with(TestJwtFactory.ownerJwt(ORG_ID, "user_sum_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.avgDaysToAcceptance").value(org.hamcrest.Matchers.greaterThan(0.0)));
  }

  // --- 362.4: Test 3 — pendingOverdue ---

  @Test
  void getProposalSummary_overdueProposals_returnsSortedByDaysDesc() throws Exception {
    String p10days = createProposal("Overdue 10 days");
    String p7days = createProposal("Overdue 7 days");
    String p1day = createProposal("Not overdue 1 day");

    setSentDaysAgo(p10days, 10);
    setSentDaysAgo(p7days, 7);
    setSentDaysAgo(p1day, 1);

    var result =
        mockMvc
            .perform(
                get("/api/proposals/summary")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sum_owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pendingOverdue").isArray())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    var overdueList = JsonPath.<List<Map<String, Object>>>read(body, "$.pendingOverdue");

    // Must contain at least 2 entries (the 10-day and 7-day overdue ones)
    assertThat(overdueList.size()).isGreaterThanOrEqualTo(2);

    // Verify sorted desc by daysSinceSent
    for (int i = 0; i < overdueList.size() - 1; i++) {
      long current = ((Number) overdueList.get(i).get("daysSinceSent")).longValue();
      long next = ((Number) overdueList.get(i + 1).get("daysSinceSent")).longValue();
      assertThat(current).isGreaterThanOrEqualTo(next);
    }

    // None should have daysSinceSent <= 5
    for (var item : overdueList) {
      long days = ((Number) item.get("daysSinceSent")).longValue();
      assertThat(days).isGreaterThan(5);
    }
  }

  // --- 362.5: Permission enforcement ---

  @Test
  void getProposalSummary_memberWithoutInvoicingCapability_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/proposals/summary").with(TestJwtFactory.memberJwt(ORG_ID, "user_sum_member")))
        .andExpect(status().isForbidden());
  }

  // --- 362.6: Tenant isolation ---

  @Test
  void getProposalSummary_secondTenant_returnsZeroCounts() throws Exception {
    // Provision a separate tenant with no proposals
    String isolatedOrgId = "org_proposal_isolation_test";
    provisioningService.provisionTenant(isolatedOrgId, "Isolation Test Org", null);

    String isolatedMemberId =
        syncMemberForOrg(
            isolatedOrgId, "user_iso_owner", "iso_owner@test.com", "Isolation Owner", "owner");

    // Assign system owner role
    UUID isoMemberUuid = UUID.fromString(isolatedMemberId);
    String isoSchema =
        orgSchemaMappingRepository.findByClerkOrgId(isolatedOrgId).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, isoSchema)
        .where(RequestScopes.ORG_ID, isolatedOrgId)
        .where(RequestScopes.MEMBER_ID, isoMemberUuid)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var ownerRole =
                  orgRoleRepository.findAll().stream()
                      .filter(r -> r.isSystem() && "owner".equals(r.getSlug()))
                      .findFirst()
                      .orElseThrow();
              var member = memberRepository.findById(isoMemberUuid).orElseThrow();
              member.setOrgRoleEntity(ownerRole);
              memberRepository.save(member);
            });

    // The second tenant should see zero proposals
    JwtRequestPostProcessor isoJwt =
        jwt()
            .jwt(
                j ->
                    j.subject("user_iso_owner")
                        .claim("o", Map.of("id", isolatedOrgId, "rol", "owner")));

    mockMvc
        .perform(get("/api/proposals/summary").with(isoJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.byStatus.DRAFT").value(0))
        .andExpect(jsonPath("$.byStatus.SENT").value(0))
        .andExpect(jsonPath("$.byStatus.ACCEPTED").value(0))
        .andExpect(jsonPath("$.byStatus.DECLINED").value(0))
        .andExpect(jsonPath("$.byStatus.EXPIRED").value(0))
        .andExpect(jsonPath("$.avgDaysToAcceptance").value(0.0))
        .andExpect(jsonPath("$.conversionRate").value(0.0))
        .andExpect(jsonPath("$.pendingOverdue").isArray())
        .andExpect(jsonPath("$.pendingOverdue").isEmpty());
  }

  private String createCustomer(JwtRequestPostProcessor jwt) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Summary Test Customer", "email": "summary-customer@test.com", "type": "INDIVIDUAL"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private String createProposal(String title) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/proposals")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sum_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "%s", "customerId": "%s", "feeModel": "HOURLY"}
                        """
                            .formatted(title, customerId)))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractIdFromLocation(result);
  }

  private void setSent(String proposalId) {
    jdbcTemplate.update(
        "UPDATE \"%s\".proposals SET status = 'SENT', sent_at = now() WHERE id = ?::uuid"
            .formatted(schema),
        proposalId);
  }

  private void setAccepted(String proposalId) {
    jdbcTemplate.update(
        "UPDATE \"%s\".proposals SET status = 'ACCEPTED', sent_at = now() - interval '3 days', accepted_at = now() WHERE id = ?::uuid"
            .formatted(schema),
        proposalId);
  }

  private void setAcceptedWithTimestamps(String proposalId, Instant sentAt, Instant acceptedAt) {
    jdbcTemplate.update(
        "UPDATE \"%s\".proposals SET status = 'ACCEPTED', sent_at = ?, accepted_at = ? WHERE id = ?::uuid"
            .formatted(schema),
        Timestamp.from(sentAt),
        Timestamp.from(acceptedAt),
        proposalId);
  }

  private void setDeclined(String proposalId) {
    jdbcTemplate.update(
        "UPDATE \"%s\".proposals SET status = 'DECLINED', declined_at = now() WHERE id = ?::uuid"
            .formatted(schema),
        proposalId);
  }

  private void setExpired(String proposalId) {
    jdbcTemplate.update(
        "UPDATE \"%s\".proposals SET status = 'EXPIRED' WHERE id = ?::uuid".formatted(schema),
        proposalId);
  }

  private void setSentDaysAgo(String proposalId, int days) {
    jdbcTemplate.update(
        "UPDATE \"%s\".proposals SET status = 'SENT', sent_at = now() - interval '%d days' WHERE id = ?::uuid"
            .formatted(schema, days),
        proposalId);
  }

  private record SummaryBaseline(
      int total, int draft, int sent, int accepted, int declined, int expired) {}

  private SummaryBaseline getSummaryBaseline() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/api/proposals/summary")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sum_owner")))
            .andExpect(status().isOk())
            .andReturn();
    String body = result.getResponse().getContentAsString();
    int total = JsonPath.<Integer>read(body, "$.total");
    int draft = JsonPath.<Integer>read(body, "$.byStatus.DRAFT");
    int sent = JsonPath.<Integer>read(body, "$.byStatus.SENT");
    int accepted = JsonPath.<Integer>read(body, "$.byStatus.ACCEPTED");
    int declined = JsonPath.<Integer>read(body, "$.byStatus.DECLINED");
    int expired = JsonPath.<Integer>read(body, "$.byStatus.EXPIRED");
    return new SummaryBaseline(total, draft, sent, accepted, declined, expired);
  }

  private String syncMemberForOrg(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s", "clerkUserId": "%s", "email": "%s",
                          "name": "%s", "avatarUrl": null, "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
