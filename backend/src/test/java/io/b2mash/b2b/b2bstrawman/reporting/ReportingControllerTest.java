package io.b2mash.b2b.b2bstrawman.reporting;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportingControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_rc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Reporting Controller Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_rc_owner", "rc_owner@test.com", "RC Owner", "owner");
  }

  @Test
  void listReportDefinitionsReturnsThreeCategories() throws Exception {
    mockMvc
        .perform(get("/api/report-definitions").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories").isArray())
        .andExpect(jsonPath("$.categories.length()").value(3));
  }

  @Test
  void listReportDefinitionsHasCorrectCategoryLabels() throws Exception {
    mockMvc
        .perform(get("/api/report-definitions").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories[?(@.category == 'FINANCIAL')].label").value("Financial"))
        .andExpect(
            jsonPath("$.categories[?(@.category == 'TIME_ATTENDANCE')].label")
                .value("Time & Attendance"))
        .andExpect(jsonPath("$.categories[?(@.category == 'PROJECT')].label").value("Project"));
  }

  @Test
  void getReportDefinitionBySlugReturnsDetail() throws Exception {
    mockMvc
        .perform(get("/api/report-definitions/timesheet").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value("timesheet"))
        .andExpect(jsonPath("$.name").value("Timesheet Report"))
        .andExpect(jsonPath("$.category").value("TIME_ATTENDANCE"))
        .andExpect(jsonPath("$.parameterSchema").isMap())
        .andExpect(jsonPath("$.columnDefinitions").isMap())
        .andExpect(jsonPath("$.isSystem").value(true))
        .andExpect(jsonPath("$.templateBody").doesNotExist());
  }

  @Test
  void getReportDefinitionUnknownSlugReturns404() throws Exception {
    mockMvc
        .perform(get("/api/report-definitions/nonexistent").with(memberJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void listReportDefinitionsRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/report-definitions")).andExpect(status().isUnauthorized());
  }

  // --- Helpers ---

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_rc_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
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
}
