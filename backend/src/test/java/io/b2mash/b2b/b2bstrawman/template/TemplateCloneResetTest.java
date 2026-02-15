package io.b2mash.b2b.b2bstrawman.template;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemplateCloneResetTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_clone_reset_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID platformTemplateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Clone Reset Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    syncMember(ORG_ID, "user_cr_owner", "cr_owner@test.com", "CR Owner", "owner");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Find a PLATFORM template to clone
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var templates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
                      platformTemplateId =
                          templates.stream()
                              .filter(t -> t.getSource() == TemplateSource.PLATFORM)
                              .findFirst()
                              .orElseThrow()
                              .getId();
                    }));
  }

  @Test
  @Order(1)
  void clonePlatformTemplate() throws Exception {
    mockMvc
        .perform(post("/api/templates/" + platformTemplateId + "/clone").with(ownerJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.source").value("ORG_CUSTOM"))
        .andExpect(jsonPath("$.sourceTemplateId").value(platformTemplateId.toString()))
        .andExpect(jsonPath("$.packId").value("common"));
  }

  @Test
  @Order(2)
  void cloneConflict() throws Exception {
    // The first test already cloned it — clone again should 409
    mockMvc
        .perform(post("/api/templates/" + platformTemplateId + "/clone").with(ownerJwt()))
        .andExpect(status().isConflict());
  }

  @Test
  @Order(3)
  void resetOrgCustom() throws Exception {
    // Find an ORG_CUSTOM clone to reset
    var cloneIdHolder = new String[1];

    // Create a second PLATFORM template and clone it for this test
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Find a platform template with a different key to clone
                      var templates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
                      var platformTemplate =
                          templates.stream()
                              .filter(
                                  t ->
                                      t.getSource() == TemplateSource.PLATFORM
                                          && "project-summary".equals(t.getPackTemplateKey()))
                              .findFirst()
                              .orElseThrow();

                      // Create an ORG_CUSTOM clone directly
                      var clone =
                          new DocumentTemplate(
                              platformTemplate.getPrimaryEntityType(),
                              platformTemplate.getName() + " (Custom Reset Test)",
                              "reset-test-clone",
                              platformTemplate.getCategory(),
                              platformTemplate.getContent());
                      clone.setSourceTemplateId(platformTemplate.getId());
                      clone.setPackId(platformTemplate.getPackId());
                      clone.setPackTemplateKey("reset-test-key");
                      clone = documentTemplateRepository.save(clone);
                      cloneIdHolder[0] = clone.getId().toString();
                    }));

    mockMvc
        .perform(post("/api/templates/" + cloneIdHolder[0] + "/reset").with(ownerJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  @Order(4)
  void resetNonClone() throws Exception {
    // Create an ORG_CUSTOM template without sourceTemplateId
    var customIdHolder = new String[1];

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var custom =
                          new DocumentTemplate(
                              TemplateEntityType.PROJECT,
                              "Non-Clone Custom",
                              "non-clone-custom",
                              TemplateCategory.OTHER,
                              "<p>Custom</p>");
                      custom = documentTemplateRepository.save(custom);
                      customIdHolder[0] = custom.getId().toString();
                    }));

    mockMvc
        .perform(post("/api/templates/" + customIdHolder[0] + "/reset").with(ownerJwt()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(5)
  void resetPlatformTemplate() throws Exception {
    mockMvc
        .perform(post("/api/templates/" + platformTemplateId + "/reset").with(ownerJwt()))
        .andExpect(status().isBadRequest());
  }

  // --- Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cr_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
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
