package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FieldDefinitionIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_fd_test";
  private static final String ORG_ID_B = "org_fd_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String tenantSchemaB;
  private UUID memberIdOwner;
  private UUID memberIdOwnerB;

  @BeforeAll
  void setup() throws Exception {
    // --- Tenant A ---
    provisioningService.provisionTenant(ORG_ID, "FD Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(ORG_ID, "user_fd_owner", "fd_owner@test.com", "FD Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // --- Tenant B ---
    provisioningService.provisionTenant(ORG_ID_B, "FD Test Org B");
    planSyncService.syncPlan(ORG_ID_B, "pro-plan");

    memberIdOwnerB =
        UUID.fromString(
            syncMember(ORG_ID_B, "user_fd_owner_b", "fd_owner_b@test.com", "FD Owner B", "owner"));

    tenantSchemaB =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_B).orElseThrow().getSchemaName();
  }

  @Test
  void shouldSaveAndRetrieveFieldDefinitionInDedicatedSchema() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var fd =
                      new FieldDefinition(
                          "PROJECT", "Priority Level", "priority_level", "DROPDOWN");
                  fd = fieldDefinitionRepository.save(fd);

                  var found = fieldDefinitionRepository.findOneById(fd.getId());
                  assertThat(found).isPresent();
                  assertThat(found.get().getSlug()).isEqualTo("priority_level");
                  assertThat(found.get().getFieldType()).isEqualTo("DROPDOWN");
                  assertThat(found.get().isActive()).isTrue();
                }));
  }

  @Test
  void findOneByIdRespectsFilterForCrossTenantIsolation() {
    var idHolder = new UUID[1];
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var fd = new FieldDefinition("TASK", "Isolation Test", "isolation_test", "TEXT");
                  fd = fieldDefinitionRepository.save(fd);
                  idHolder[0] = fd.getId();
                }));

    runInTenant(
        tenantSchemaB,
        ORG_ID_B,
        memberIdOwnerB,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var found = fieldDefinitionRepository.findOneById(idHolder[0]);
                  assertThat(found).isEmpty();
                }));
  }

  @Test
  void shouldAutoGenerateSlug() {
    var slug = FieldDefinition.generateSlug("My Custom Field");
    assertThat(slug).isEqualTo("my_custom_field");
  }

  @Test
  void shouldAutoGenerateSlugWithHyphens() {
    var slug = FieldDefinition.generateSlug("start-date");
    assertThat(slug).isEqualTo("start_date");
  }

  @Test
  void shouldCreateFieldDefinitionViaApi() throws Exception {
    mockMvc
        .perform(
            post("/api/field-definitions")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "PROJECT",
                      "name": "Budget Code",
                      "fieldType": "TEXT",
                      "description": "Internal budget code",
                      "required": false,
                      "sortOrder": 1
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slug").value("budget_code"))
        .andExpect(jsonPath("$.fieldType").value("TEXT"))
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  void shouldDeactivateFieldDefinitionViaApi() throws Exception {
    // Create
    var result =
        mockMvc
            .perform(
                post("/api/field-definitions")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "TASK",
                          "name": "Deactivate Test",
                          "fieldType": "BOOLEAN",
                          "required": false,
                          "sortOrder": 0
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // Deactivate
    mockMvc
        .perform(delete("/api/field-definitions/" + id).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify deactivated — GET by ID should still return (soft delete)
    mockMvc
        .perform(get("/api/field-definitions/" + id).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void shouldDenyMemberMutationAccess() throws Exception {
    mockMvc
        .perform(
            post("/api/field-definitions")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "PROJECT",
                      "name": "Denied Field",
                      "fieldType": "TEXT",
                      "required": false,
                      "sortOrder": 0
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void memberCanReadFieldDefinitions() throws Exception {
    // Create as owner
    mockMvc
        .perform(
            post("/api/field-definitions")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "CUSTOMER",
                      "name": "Read Test Field",
                      "fieldType": "EMAIL",
                      "required": false,
                      "sortOrder": 0
                    }
                    """))
        .andExpect(status().isCreated());

    // Read as member
    mockMvc
        .perform(get("/api/field-definitions").param("entityType", "CUSTOMER").with(memberJwt()))
        .andExpect(status().isOk());
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_fd_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_fd_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  // --- Helpers ---

  private void runInTenant(String schema, String orgId, UUID memberId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
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
