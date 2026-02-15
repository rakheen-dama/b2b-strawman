package io.b2mash.b2b.b2bstrawman.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntityTagIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_entity_tag_test";
  private static final String ORG_ID_B = "org_entity_tag_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TagRepository tagRepository;
  @Autowired private EntityTagRepository entityTagRepository;
  @Autowired private EntityTagService entityTagService;
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
    provisioningService.provisionTenant(ORG_ID, "Entity Tag Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(ORG_ID, "user_et_owner", "et_owner@test.com", "ET Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    provisioningService.provisionTenant(ORG_ID_B, "Entity Tag Test Org B");
    planSyncService.syncPlan(ORG_ID_B, "pro-plan");

    memberIdOwnerB =
        UUID.fromString(
            syncMember(ORG_ID_B, "user_et_owner_b", "et_owner_b@test.com", "ET Owner B", "owner"));

    tenantSchemaB =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_B).orElseThrow().getSchemaName();
  }

  @Test
  void setEntityTagsWithEmptyListDeletesAllTags() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var tag = new Tag("ET Empty Test", "#EF4444");
                  tag = tagRepository.saveAndFlush(tag);
                  UUID entityId = UUID.randomUUID();

                  entityTagService.setEntityTags("PROJECT", entityId, List.of(tag.getId()));
                  assertThat(entityTagService.getEntityTags("PROJECT", entityId)).hasSize(1);

                  entityTagService.setEntityTags("PROJECT", entityId, List.of());
                  assertThat(entityTagService.getEntityTags("PROJECT", entityId)).isEmpty();
                }));
  }

  @Test
  void setEntityTagsWithTwoTagsCreatesTwoRows() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var tag1 = new Tag("ET Tag A", "#EF4444");
                  tag1 = tagRepository.saveAndFlush(tag1);
                  var tag2 = new Tag("ET Tag B", "#3B82F6");
                  tag2 = tagRepository.saveAndFlush(tag2);
                  UUID entityId = UUID.randomUUID();

                  entityTagService.setEntityTags(
                      "PROJECT", entityId, List.of(tag1.getId(), tag2.getId()));

                  var tags = entityTagService.getEntityTags("PROJECT", entityId);
                  assertThat(tags).hasSize(2);
                }));
  }

  @Test
  void setEntityTagsReplacesExistingTags() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var tag1 = new Tag("ET Replace A", "#EF4444");
                  tag1 = tagRepository.saveAndFlush(tag1);
                  var tag2 = new Tag("ET Replace B", "#3B82F6");
                  tag2 = tagRepository.saveAndFlush(tag2);
                  var tag3 = new Tag("ET Replace C", "#10B981");
                  tag3 = tagRepository.saveAndFlush(tag3);
                  UUID entityId = UUID.randomUUID();

                  // Set initial tags
                  entityTagService.setEntityTags(
                      "PROJECT", entityId, List.of(tag1.getId(), tag2.getId()));
                  assertThat(entityTagService.getEntityTags("PROJECT", entityId)).hasSize(2);

                  // Replace with different tag
                  entityTagService.setEntityTags("PROJECT", entityId, List.of(tag3.getId()));
                  var tags = entityTagService.getEntityTags("PROJECT", entityId);
                  assertThat(tags).hasSize(1);
                  assertThat(tags.getFirst().name()).isEqualTo("ET Replace C");
                }));
  }

  @Test
  void getEntityTagsReturnsSortedByName() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var tagZ = new Tag("Zebra Tag", "#EF4444");
                  tagZ = tagRepository.saveAndFlush(tagZ);
                  var tagA = new Tag("Alpha Tag", "#3B82F6");
                  tagA = tagRepository.saveAndFlush(tagA);
                  var tagM = new Tag("Mango Tag", "#10B981");
                  tagM = tagRepository.saveAndFlush(tagM);
                  UUID entityId = UUID.randomUUID();

                  entityTagService.setEntityTags(
                      "PROJECT", entityId, List.of(tagZ.getId(), tagA.getId(), tagM.getId()));

                  var tags = entityTagService.getEntityTags("PROJECT", entityId);
                  assertThat(tags).hasSize(3);
                  assertThat(tags.get(0).name()).isEqualTo("Alpha Tag");
                  assertThat(tags.get(1).name()).isEqualTo("Mango Tag");
                  assertThat(tags.get(2).name()).isEqualTo("Zebra Tag");
                }));
  }

  @Test
  void deleteTagCascadesToEntityTags() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var tag = new Tag("ET Cascade Test", "#10B981");
                  tag = tagRepository.saveAndFlush(tag);
                  UUID entityId = UUID.randomUUID();

                  entityTagService.setEntityTags("PROJECT", entityId, List.of(tag.getId()));
                  assertThat(entityTagRepository.countByTagId(tag.getId())).isEqualTo(1);

                  tagRepository.delete(tag);
                  tagRepository.flush();

                  assertThat(entityTagRepository.countByTagId(tag.getId())).isEqualTo(0);
                }));
  }

  @Test
  void crossTenantEntityTagAccessReturnsEmpty() {
    var tagIdHolder = new UUID[1];
    UUID entityId = UUID.randomUUID();

    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var tag = new Tag("ET Cross Tenant", "#EF4444");
                  tag = tagRepository.saveAndFlush(tag);
                  tagIdHolder[0] = tag.getId();

                  entityTagService.setEntityTags("PROJECT", entityId, List.of(tag.getId()));
                  assertThat(entityTagService.getEntityTags("PROJECT", entityId)).hasSize(1);
                }));

    runInTenant(
        tenantSchemaB,
        ORG_ID_B,
        memberIdOwnerB,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var tags = entityTagService.getEntityTags("PROJECT", entityId);
                  assertThat(tags).isEmpty();
                }));
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
    return com.jayway.jsonpath.JsonPath.read(
        result.getResponse().getContentAsString(), "$.memberId");
  }
}
