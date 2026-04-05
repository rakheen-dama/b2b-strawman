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
class FieldDefinitionIntegrationTest {
  private static final String ORG_ID = "org_fd_test";
  private static final String ORG_ID_B = "org_fd_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
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
    provisioningService.provisionTenant(ORG_ID, "FD Test Org", null);

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_fd_owner", "fd_owner@test.com", "FD Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // --- Tenant B ---
    provisioningService.provisionTenant(ORG_ID_B, "FD Test Org B", null);

    memberIdOwnerB =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID_B,
                "user_fd_owner_b",
                "fd_owner_b@test.com",
                "FD Owner B",
                "owner"));

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
                          EntityType.PROJECT,
                          "Priority Level",
                          "priority_level",
                          FieldType.DROPDOWN);
                  fd = fieldDefinitionRepository.save(fd);

                  var found = fieldDefinitionRepository.findById(fd.getId());
                  assertThat(found).isPresent();
                  assertThat(found.get().getSlug()).isEqualTo("priority_level");
                  assertThat(found.get().getFieldType()).isEqualTo(FieldType.DROPDOWN);
                  assertThat(found.get().isActive()).isTrue();
                }));
  }

  @Test
  void findByIdRespectsFilterForCrossTenantIsolation() {
    var idHolder = new UUID[1];
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var fd =
                      new FieldDefinition(
                          EntityType.TASK, "Isolation Test", "isolation_test", FieldType.TEXT);
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
                  var found = fieldDefinitionRepository.findById(idHolder[0]);
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fd_owner"))
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
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fd_owner"))
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
        .perform(
            delete("/api/field-definitions/" + id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fd_owner")))
        .andExpect(status().isNoContent());

    // Verify deactivated — GET by ID should still return (soft delete)
    mockMvc
        .perform(
            get("/api/field-definitions/" + id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fd_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void shouldDenyMemberMutationAccess() throws Exception {
    mockMvc
        .perform(
            post("/api/field-definitions")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_fd_member"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fd_owner"))
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
        .perform(
            get("/api/field-definitions")
                .param("entityType", "CUSTOMER")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_fd_member")))
        .andExpect(status().isOk());
  }

  @Test
  void shouldRejectInvalidEntityType() throws Exception {
    mockMvc
        .perform(
            post("/api/field-definitions")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fd_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "FOOBAR",
                      "name": "Bad Type",
                      "fieldType": "TEXT",
                      "required": false,
                      "sortOrder": 0
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectInvalidFieldType() throws Exception {
    mockMvc
        .perform(
            post("/api/field-definitions")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fd_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "PROJECT",
                      "name": "Bad Field Type",
                      "fieldType": "INVALID",
                      "required": false,
                      "sortOrder": 0
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  private void runInTenant(String schema, String orgId, UUID memberId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
