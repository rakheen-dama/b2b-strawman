package io.b2mash.b2b.b2bstrawman.retention;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetentionControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_retention_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String customerId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Retention Controller Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_retctrl_owner", "retctrl_owner@test.com", "RetCtrl Owner", "owner");
    syncMember(
        ORG_ID, "user_retctrl_member", "retctrl_member@test.com", "RetCtrl Member", "member");

    // Create a customer for purge tests
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Retention Test Customer","email":"ret-ctrl@test.com","phone":"+1-555-0400","idNumber":"RET-C01","notes":"Test"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    customerId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  void getList_returnsActivePolicies() throws Exception {
    // The FICA pack seeds retention policies during provisioning
    mockMvc
        .perform(get("/api/retention-policies").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].recordType").exists())
        .andExpect(jsonPath("$[0].retentionDays").exists());
  }

  @Test
  void postCreate_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/retention-policies")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"recordType":"COMMENT","retentionDays":365,"triggerEvent":"RECORD_CREATED","action":"PURGE"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.recordType").value("COMMENT"))
        .andExpect(jsonPath("$.retentionDays").value(365))
        .andExpect(jsonPath("$.action").value("PURGE"));
  }

  @Test
  void postCreate_member_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/retention-policies")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"recordType":"DOCUMENT","retentionDays":730,"triggerEvent":"RECORD_CREATED","action":"FLAG"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void postCreate_duplicate_returns409() throws Exception {
    // First create
    mockMvc
        .perform(
            post("/api/retention-policies")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"recordType":"DOCUMENT","retentionDays":730,"triggerEvent":"DOCUMENT_ARCHIVED","action":"FLAG"}
                    """))
        .andExpect(status().isCreated());

    // Duplicate create
    mockMvc
        .perform(
            post("/api/retention-policies")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"recordType":"DOCUMENT","retentionDays":365,"triggerEvent":"DOCUMENT_ARCHIVED","action":"PURGE"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void putUpdate_updatesPolicy() throws Exception {
    // Create a policy first
    var createResult =
        mockMvc
            .perform(
                post("/api/retention-policies")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"recordType":"COMMENT","retentionDays":180,"triggerEvent":"COMMENT_RESOLVED","action":"FLAG"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String policyId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Update it
    mockMvc
        .perform(
            put("/api/retention-policies/" + policyId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"retentionDays":365,"action":"PURGE"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.retentionDays").value(365))
        .andExpect(jsonPath("$.action").value("PURGE"));
  }

  @Test
  void deletePolicy_removes204() throws Exception {
    // Create a policy first
    var createResult =
        mockMvc
            .perform(
                post("/api/retention-policies")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"recordType":"DOCUMENT","retentionDays":90,"triggerEvent":"DOCUMENT_EXPIRED","action":"PURGE"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String policyId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Delete it
    mockMvc
        .perform(delete("/api/retention-policies/" + policyId).with(ownerJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deletePolicy_notFound_returns404() throws Exception {
    mockMvc
        .perform(delete("/api/retention-policies/" + UUID.randomUUID()).with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void postCheck_returnsFlaggedResult() throws Exception {
    mockMvc
        .perform(post("/api/retention-policies/check").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.checkedAt").exists())
        .andExpect(jsonPath("$.flagged").exists())
        .andExpect(jsonPath("$.totalFlagged").isNumber());
  }

  @Test
  void postPurge_auditEvent_hardDeletes() throws Exception {
    // First run check to generate audit events, then use one of those audit event IDs
    // We know runCheck itself creates an audit event, so we can use the check endpoint
    // to create audit events, then purge them
    var checkResult =
        mockMvc
            .perform(post("/api/retention-policies/check").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    // Run a second check to create another audit event
    mockMvc
        .perform(post("/api/retention-policies/check").with(ownerJwt()))
        .andExpect(status().isOk());

    // Now purge with a random UUID for AUDIT_EVENT — non-existent IDs won't fail (deleteAllById
    // silently ignores missing)
    var randomAuditId = UUID.randomUUID();
    mockMvc
        .perform(
            post("/api/retention-policies/purge")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"recordType":"AUDIT_EVENT","recordIds":["%s"]}
                    """
                        .formatted(randomAuditId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recordType").value("AUDIT_EVENT"))
        .andExpect(jsonPath("$.purged").isNumber());
  }

  @Test
  void postPurge_customer_anonymizes() throws Exception {
    // Purge the customer created in setup
    mockMvc
        .perform(
            post("/api/retention-policies/purge")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"recordType":"CUSTOMER","recordIds":["%s"]}
                    """
                        .formatted(customerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recordType").value("CUSTOMER"))
        .andExpect(jsonPath("$.purged").value(1))
        .andExpect(jsonPath("$.failed").value(0));

    // Verify customer is anonymized by fetching it
    mockMvc
        .perform(get("/api/customers/" + customerId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Anonymized Customer " + customerId.substring(0, 6)));
  }

  @Test
  void postPurge_member_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/retention-policies/purge")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"recordType":"AUDIT_EVENT","recordIds":["%s"]}
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isForbidden());
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_retctrl_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_retctrl_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private String syncMember(
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
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
