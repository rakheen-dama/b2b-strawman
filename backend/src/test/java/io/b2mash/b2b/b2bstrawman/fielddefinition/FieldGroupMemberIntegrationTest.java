package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class FieldGroupMemberIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_fgm_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private FieldGroupRepository fieldGroupRepository;
  @Autowired private FieldGroupMemberRepository fieldGroupMemberRepository;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "FGM Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(ORG_ID, "user_fgm_owner", "fgm_owner@test.com", "FGM Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void shouldAddFieldToGroup() throws Exception {
    // Create a PROJECT group
    String groupId = createGroup("PROJECT", "Add Test Group");
    // Create a PROJECT field
    String fieldId = createField("PROJECT", "Add Test Field", "TEXT");

    // Add field to group
    mockMvc
        .perform(
            post("/api/field-groups/" + groupId + "/fields")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "fieldDefinitionId": "%s",
                      "sortOrder": 0
                    }
                    """
                        .formatted(fieldId)))
        .andExpect(status().isCreated());

    // Verify member exists
    mockMvc
        .perform(get("/api/field-groups/" + groupId + "/fields").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].fieldDefinitionId").value(fieldId))
        .andExpect(jsonPath("$[0].sortOrder").value(0));
  }

  @Test
  void shouldRemoveFieldFromGroup() throws Exception {
    String groupId = createGroup("TASK", "Remove Test Group");
    String fieldId = createField("TASK", "Remove Test Field", "TEXT");

    // Add field
    mockMvc
        .perform(
            post("/api/field-groups/" + groupId + "/fields")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "fieldDefinitionId": "%s",
                      "sortOrder": 0
                    }
                    """
                        .formatted(fieldId)))
        .andExpect(status().isCreated());

    // Remove field
    mockMvc
        .perform(delete("/api/field-groups/" + groupId + "/fields/" + fieldId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify member is gone
    mockMvc
        .perform(get("/api/field-groups/" + groupId + "/fields").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void shouldReorderFieldsInGroup() throws Exception {
    String groupId = createGroup("PROJECT", "Reorder Test Group");
    String fieldA = createField("PROJECT", "Reorder Field A", "TEXT");
    String fieldB = createField("PROJECT", "Reorder Field B", "NUMBER");
    String fieldC = createField("PROJECT", "Reorder Field C", "DATE");

    // Add fields in order A=0, B=1, C=2
    addFieldToGroup(groupId, fieldA, 0);
    addFieldToGroup(groupId, fieldB, 1);
    addFieldToGroup(groupId, fieldC, 2);

    // Reorder to C, A, B
    mockMvc
        .perform(
            put("/api/field-groups/" + groupId + "/fields/reorder")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "fieldIds": ["%s", "%s", "%s"]
                    }
                    """
                        .formatted(fieldC, fieldA, fieldB)))
        .andExpect(status().isNoContent());

    // Verify order is now C=0, A=1, B=2
    mockMvc
        .perform(get("/api/field-groups/" + groupId + "/fields").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].fieldDefinitionId").value(fieldC))
        .andExpect(jsonPath("$[0].sortOrder").value(0))
        .andExpect(jsonPath("$[1].fieldDefinitionId").value(fieldA))
        .andExpect(jsonPath("$[1].sortOrder").value(1))
        .andExpect(jsonPath("$[2].fieldDefinitionId").value(fieldB))
        .andExpect(jsonPath("$[2].sortOrder").value(2));
  }

  @Test
  void shouldRejectEntityTypeMismatch() throws Exception {
    // Create a PROJECT group and a TASK field
    String groupId = createGroup("PROJECT", "Mismatch Group");
    String fieldId = createField("TASK", "Mismatch Field", "TEXT");

    // Attempt to add TASK field to PROJECT group -- should fail
    mockMvc
        .perform(
            post("/api/field-groups/" + groupId + "/fields")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "fieldDefinitionId": "%s",
                      "sortOrder": 0
                    }
                    """
                        .formatted(fieldId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Entity type mismatch"));
  }

  @Test
  void shouldDenyMemberAddingFieldToGroup() throws Exception {
    String groupId = createGroup("CUSTOMER", "RBAC Test Group");
    String fieldId = createField("CUSTOMER", "RBAC Test Field", "TEXT");

    mockMvc
        .perform(
            post("/api/field-groups/" + groupId + "/fields")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "fieldDefinitionId": "%s",
                      "sortOrder": 0
                    }
                    """
                        .formatted(fieldId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectDuplicateFieldInGroup() throws Exception {
    String groupId = createGroup("PROJECT", "Duplicate Test Group");
    String fieldId = createField("PROJECT", "Duplicate Test Field", "TEXT");

    // Add field once
    addFieldToGroup(groupId, fieldId, 0);

    // Add same field again -- should fail
    mockMvc
        .perform(
            post("/api/field-groups/" + groupId + "/fields")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "fieldDefinitionId": "%s",
                      "sortOrder": 1
                    }
                    """
                        .formatted(fieldId)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Duplicate membership"));
  }

  @Test
  void shouldReturnMembersInSortOrder() throws Exception {
    String groupId = createGroup("TASK", "Sort Order Group");
    String fieldA = createField("TASK", "Sort A", "TEXT");
    String fieldB = createField("TASK", "Sort B", "NUMBER");

    // Add B first with sortOrder 5, then A with sortOrder 2
    addFieldToGroup(groupId, fieldB, 5);
    addFieldToGroup(groupId, fieldA, 2);

    // Should return A (sortOrder=2) before B (sortOrder=5)
    mockMvc
        .perform(get("/api/field-groups/" + groupId + "/fields").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].fieldDefinitionId").value(fieldA))
        .andExpect(jsonPath("$[0].sortOrder").value(2))
        .andExpect(jsonPath("$[1].fieldDefinitionId").value(fieldB))
        .andExpect(jsonPath("$[1].sortOrder").value(5));
  }

  @Test
  void shouldCascadeDeleteMembersWhenGroupDeleted() throws Exception {
    String groupId = createGroup("CUSTOMER", "Cascade Group");
    String fieldId = createField("CUSTOMER", "Cascade Field", "BOOLEAN");

    addFieldToGroup(groupId, fieldId, 0);

    // Verify member exists
    mockMvc
        .perform(get("/api/field-groups/" + groupId + "/fields").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    // Delete (deactivate) the group via the DB to test ON DELETE CASCADE
    // We do a hard delete in the DB to trigger CASCADE
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var group = fieldGroupRepository.findById(UUID.fromString(groupId)).orElseThrow();
                  fieldGroupRepository.delete(group);
                }));

    // Verify members are gone (group was deleted, so members should be cascaded)
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var members =
                      fieldGroupMemberRepository.findByFieldGroupIdOrderBySortOrder(
                          UUID.fromString(groupId));
                  assertThat(members).isEmpty();
                }));
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_fgm_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_fgm_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
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

  private String createGroup(String entityType, String name) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "%s",
                          "name": "%s",
                          "sortOrder": 0
                        }
                        """
                            .formatted(entityType, name)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String createField(String entityType, String name, String fieldType) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/field-definitions")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "%s",
                          "name": "%s",
                          "fieldType": "%s",
                          "required": false,
                          "sortOrder": 0
                        }
                        """
                            .formatted(entityType, name, fieldType)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void addFieldToGroup(String groupId, String fieldId, int sortOrder) throws Exception {
    mockMvc
        .perform(
            post("/api/field-groups/" + groupId + "/fields")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "fieldDefinitionId": "%s",
                      "sortOrder": %d
                    }
                    """
                        .formatted(fieldId, sortOrder)))
        .andExpect(status().isCreated());
  }
}
