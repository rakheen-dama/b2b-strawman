package io.b2mash.b2b.b2bstrawman.orgrole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR #1856 review round 3 (security delta review, HIGH): an override set holding both {@code +X}
 * and {@code -X}. Overrides used to be applied in set-iteration order, whichever came last winning
 * — and the request's set (checked for owner-only gains) need not iterate like the set reloaded
 * from {@code member_capability_overrides} (what the member's requests resolve). Now such a set is
 * refused as malformed (400, for anyone), and resolution applies every {@code +} then every {@code
 * -} ({@code -} wins), whatever the order — so stored data holding both resolves without X.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContradictoryOverrideIntegrationTest {

  private static final String ORG_ID = "org_contradictory_override_test";
  private static final String OWNER = "user_cov_owner";
  private static final String ADMIN = "user_cov_admin";

  /**
   * 13 elements: the request's HashSet is resized to 32 buckets, where "-MANAGE_COMPLIANCE_
   * DESTRUCTIVE" iterates after "+…", so the old gain check saw nothing gained.
   */
  private static final String[] CONTRADICTORY_WITH_FILLER = {
    "+MANAGE_COMPLIANCE_DESTRUCTIVE",
    "-MANAGE_COMPLIANCE_DESTRUCTIVE",
    "+INVOICING",
    "+PROJECT_MANAGEMENT",
    "+CUSTOMER_MANAGEMENT",
    "+AUTOMATIONS",
    "+VIEW_LEGAL",
    "+MANAGE_LEGAL",
    "+MANAGE_TRUST",
    "+CLOSE_MATTER",
    "+AI_EXECUTE",
    "+AI_REVIEW",
    "+AI_MANAGE"
  };

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository schemaMappings;
  @Autowired private JdbcTemplate jdbc;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Contradictory Override Attorneys", "legal-za");
    TestMemberHelper.syncMember(mockMvc, ORG_ID, OWNER, "cov_owner@test.com", "Cov Owner", "owner");
    TestMemberHelper.syncMember(mockMvc, ORG_ID, ADMIN, "cov_admin@test.com", "Cov Admin", "admin");
  }

  @Test
  void anAdminCannotSendAContradictoryOverridePair() throws Exception {
    String memberId = syncMember("user_cov_target_a");
    String bookkeeper = createRole(admin(), "Bookkeeper Contra A");

    var assigned = assign(admin(), memberId, bookkeeper, CONTRADICTORY_WITH_FILLER).andReturn();

    assertThat(myCapabilities("user_cov_target_a"))
        .as("what the member holds after the request (HTTP %s)", assigned.getResponse().getStatus())
        .doesNotContain("MANAGE_COMPLIANCE_DESTRUCTIVE");
    assertContradictory(assigned, "MANAGE_COMPLIANCE_DESTRUCTIVE");
  }

  @Test
  void theOwnerCannotSendAContradictoryOverridePairEither() throws Exception {
    String memberId = syncMember("user_cov_target_b");
    String bookkeeper = createRole(owner(), "Bookkeeper Contra B");

    assertContradictory(
        assign(owner(), memberId, bookkeeper, "+INVOICING", "-INVOICING").andReturn(), "INVOICING");
  }

  @Test
  void storedOverridesHoldingBothSignsResolveWithTheMinusWinning() throws Exception {
    String memberId = syncMember("user_cov_target_c");
    String bookkeeper = createRole(owner(), "Bookkeeper Contra C");
    assign(owner(), memberId, bookkeeper).andExpect(status().isOk());
    // data from before the validation (or any other path): both signs stored for one capability
    String schema = schemaMappings.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    for (String override : List.of("+SIGN_OFF_TRUST_TAKE_ON", "-SIGN_OFF_TRUST_TAKE_ON")) {
      jdbc.update(
          "INSERT INTO \"%s\".member_capability_overrides (member_id, override_value) VALUES (?, ?)"
              .formatted(schema),
          UUID.fromString(memberId),
          override);
    }

    // what MemberFilter binds for the member's own requests, and the owner's view
    assertThat(myCapabilities("user_cov_target_c"))
        .contains("INVOICING")
        .doesNotContain("SIGN_OFF_TRUST_TAKE_ON");
    assertThat(effectiveCapabilities(memberId)).doesNotContain("SIGN_OFF_TRUST_TAKE_ON");
  }

  // --- helpers ---

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }

  private RequestPostProcessor admin() {
    return TestJwtFactory.adminJwt(ORG_ID, ADMIN);
  }

  private String syncMember(String subject) throws Exception {
    return TestMemberHelper.syncMember(
        mockMvc, ORG_ID, subject, subject + "@test.com", subject, "member");
  }

  private String createRole(RequestPostProcessor jwt, String name) throws Exception {
    var created =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\": \"%s\", \"description\": \"Test role\", \"capabilities\":"
                                .formatted(name)
                            + " [\"INVOICING\"]}"))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
  }

  private ResultActions assign(
      RequestPostProcessor jwt, String memberId, String roleId, String... overrides)
      throws Exception {
    return mockMvc.perform(
        put("/api/members/" + memberId + "/role")
            .with(jwt)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"orgRoleId\": \"%s\", \"capabilityOverrides\": %s}"
                    .formatted(
                        roleId,
                        JsonMapper.builder().build().writeValueAsString(List.of(overrides)))));
  }

  private List<String> myCapabilities(String subject) throws Exception {
    return JsonPath.read(
        mockMvc
            .perform(
                get("/api/me/capabilities").with(TestJwtFactory.customRoleJwt(ORG_ID, subject)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.capabilities");
  }

  private List<String> effectiveCapabilities(String memberId) throws Exception {
    return JsonPath.read(
        mockMvc
            .perform(get("/api/members/" + memberId + "/capabilities").with(owner()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.effectiveCapabilities");
  }

  private static void assertContradictory(MvcResult result, String capability) throws Exception {
    var response = result.getResponse();
    String body = response.getContentAsString();
    assertThat(response.getStatus()).as("status of %s", body).isEqualTo(400);
    assertThat((Integer) JsonPath.read(body, "$.status")).isEqualTo(400);
    assertThat((String) JsonPath.read(body, "$.title"))
        .isEqualTo("Contradictory capability override");
    assertThat((String) JsonPath.read(body, "$.detail"))
        .isEqualTo(
            "An override cannot both add and remove the same capability: " + capability + ".");
  }
}
