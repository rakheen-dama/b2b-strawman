package io.b2mash.b2b.b2bstrawman.audit;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InternalAuditControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_internal_audit_test";
  private static final String EMPTY_ORG_ID = "org_internal_audit_empty";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    // Provision main org with audit events
    provisioningService.provisionTenant(ORG_ID, "Internal Audit Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    // Provision empty org -- no members/projects synced, so no audit events for this org.
    // Both orgs are on tenant_shared; ORG_ID binding enables Hibernate @Filter for isolation.
    provisioningService.provisionTenant(EMPTY_ORG_ID, "Empty Audit Org");

    // Sync members into main org
    syncMember(ORG_ID, "user_iat_owner", "iat_owner@test.com", "IAT Owner", "owner");

    // Create projects to generate audit events (project.created events include ipAddress/userAgent)
    createProject("Internal Audit Project 1");
    createProject("Internal Audit Project 2");
    createProject("Internal Audit Project 3");
  }

  @Test
  void internalApiKeyAuthSucceeds() throws Exception {
    mockMvc
        .perform(get("/internal/audit-events").header("X-API-KEY", API_KEY).param("orgId", ORG_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));
  }

  @Test
  void noApiKeyReturns401() throws Exception {
    mockMvc
        .perform(get("/internal/audit-events").param("orgId", ORG_ID))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void queryWithValidOrgIdReturnsAuditEvents() throws Exception {
    mockMvc
        .perform(get("/internal/audit-events").header("X-API-KEY", API_KEY).param("orgId", ORG_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(greaterThan(0))))
        .andExpect(jsonPath("$.content[0].id", notNullValue()))
        .andExpect(jsonPath("$.content[0].eventType", notNullValue()))
        .andExpect(jsonPath("$.content[0].entityType", notNullValue()))
        .andExpect(jsonPath("$.content[0].occurredAt", notNullValue()));
  }

  @Test
  void queryWithInvalidOrgIdReturns404() throws Exception {
    mockMvc
        .perform(
            get("/internal/audit-events")
                .header("X-API-KEY", API_KEY)
                .param("orgId", "org_nonexistent"))
        .andExpect(status().isNotFound());
  }

  @Test
  void statsEndpointReturnsEventTypeCounts() throws Exception {
    mockMvc
        .perform(
            get("/internal/audit-events/stats").header("X-API-KEY", API_KEY).param("orgId", ORG_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.eventTypeCounts", not(empty())))
        .andExpect(jsonPath("$.totalEvents", greaterThan(0)))
        .andExpect(jsonPath("$.eventTypeCounts[0].eventType", notNullValue()))
        .andExpect(jsonPath("$.eventTypeCounts[0].count", greaterThan(0)));
  }

  @Test
  void statsEndpointWithNoEventsReturnsEmptyListAndZeroTotal() throws Exception {
    mockMvc
        .perform(
            get("/internal/audit-events/stats")
                .header("X-API-KEY", API_KEY)
                .param("orgId", EMPTY_ORG_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.eventTypeCounts", empty()))
        .andExpect(jsonPath("$.totalEvents", is(0)));
  }

  @Test
  void paginationOnInternalEndpoint() throws Exception {
    mockMvc
        .perform(
            get("/internal/audit-events")
                .header("X-API-KEY", API_KEY)
                .param("orgId", ORG_ID)
                .param("page", "0")
                .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.size", is(2)))
        .andExpect(jsonPath("$.number", is(0)))
        .andExpect(jsonPath("$.totalElements", greaterThan(2)));
  }

  @Test
  void responseIncludesIpAddressAndUserAgent() throws Exception {
    // Internal endpoint includes PII fields (ipAddress, userAgent) unlike tenant-scoped endpoint.
    // MockMvc sets ipAddress to 127.0.0.1; userAgent may be null but the key is still present.
    var result =
        mockMvc
            .perform(
                get("/internal/audit-events")
                    .header("X-API-KEY", API_KEY)
                    .param("orgId", ORG_ID)
                    .param("eventType", "project."))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].ipAddress", is("127.0.0.1")))
            .andReturn();

    // Verify userAgent key is present in JSON (even if null -- proves PII field is included)
    String body = result.getResponse().getContentAsString();
    assertTrue(body.contains("\"userAgent\""), "Response should include userAgent field");
  }

  // --- Helpers ---

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
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();

    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private void createProject(String name) throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "%s", "description": "For internal audit tests"}
                    """
                        .formatted(name)))
        .andExpect(status().isCreated());
  }

  private JwtRequestPostProcessor ownerJwt() {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(j -> j.subject("user_iat_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
