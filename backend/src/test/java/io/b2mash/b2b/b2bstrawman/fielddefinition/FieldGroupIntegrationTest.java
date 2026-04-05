package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FieldGroupIntegrationTest {
  private static final String ORG_ID = "org_fg_test";
  private static final String ORG_ID_B = "org_fg_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private FieldGroupRepository fieldGroupRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String tenantSchemaB;
  private UUID memberIdOwner;
  private UUID memberIdOwnerB;

  @BeforeAll
  void setup() throws Exception {
    // --- Tenant A ---
    provisioningService.provisionTenant(ORG_ID, "FG Test Org", null);

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_fg_owner", "fg_owner@test.com", "FG Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // --- Tenant B ---
    provisioningService.provisionTenant(ORG_ID_B, "FG Test Org B", null);

    memberIdOwnerB =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID_B,
                "user_fg_owner_b",
                "fg_owner_b@test.com",
                "FG Owner B",
                "owner"));

    tenantSchemaB =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_B).orElseThrow().getSchemaName();
  }

  @Test
  void shouldSaveAndRetrieveFieldGroup() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var fg = new FieldGroup(EntityType.PROJECT, "General Info", "general_info");
                  fg = fieldGroupRepository.save(fg);

                  var found = fieldGroupRepository.findById(fg.getId());
                  assertThat(found).isPresent();
                  assertThat(found.get().getSlug()).isEqualTo("general_info");
                  assertThat(found.get().isActive()).isTrue();
                }));
  }

  @Test
  void findByIdRespectsFilter() {
    var idHolder = new UUID[1];
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var fg =
                      new FieldGroup(EntityType.TASK, "Filter Test Group", "filter_test_group");
                  fg = fieldGroupRepository.save(fg);
                  idHolder[0] = fg.getId();
                }));

    // Verify accessible in same tenant
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var found = fieldGroupRepository.findById(idHolder[0]);
                  assertThat(found).isPresent();
                }));
  }

  @Test
  void findByIdRespectsFilterForCrossTenantIsolation() {
    var idHolder = new UUID[1];
    // Create in tenant A
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var fg =
                      new FieldGroup(EntityType.TASK, "Cross Tenant Group", "cross_tenant_group");
                  fg = fieldGroupRepository.save(fg);
                  idHolder[0] = fg.getId();
                }));

    // Try from tenant B — should return empty
    runInTenant(
        tenantSchemaB,
        ORG_ID_B,
        memberIdOwnerB,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var found = fieldGroupRepository.findById(idHolder[0]);
                  assertThat(found).isEmpty();
                }));
  }

  @Test
  void shouldCreateFieldGroupViaApi() throws Exception {
    mockMvc
        .perform(
            post("/api/field-groups")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fg_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "PROJECT",
                      "name": "API Created Group",
                      "description": "Created via API test",
                      "sortOrder": 1
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slug").value("api_created_group"))
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  void shouldDeactivateFieldGroupViaApi() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fg_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "TASK",
                          "name": "Deactivate Group Test",
                          "sortOrder": 0
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            delete("/api/field-groups/" + id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fg_owner")))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/field-groups/" + id).with(TestJwtFactory.ownerJwt(ORG_ID, "user_fg_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void shouldDenyMemberMutationOnFieldGroups() throws Exception {
    mockMvc
        .perform(
            post("/api/field-groups")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_fg_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "PROJECT",
                      "name": "Denied Group",
                      "sortOrder": 0
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  private void runInTenant(String schema, String orgId, UUID memberId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
