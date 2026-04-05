package io.b2mash.b2b.b2bstrawman.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

class TagIntegrationTest extends AbstractIntegrationTest {
  private static final String ORG_ID = "org_tag_test";
  private static final String ORG_ID_B = "org_tag_test_b";

  @Autowired private TagRepository tagRepository;
  @Autowired private EntityTagRepository entityTagRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String tenantSchemaB;
  private UUID memberIdOwner;
  private UUID memberIdOwnerB;

  @BeforeAll
  void setup() throws Exception {
    // --- Tenant A ---
    provisioningService.provisionTenant(ORG_ID, "Tag Test Org", null);

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_tag_owner", "tag_owner@test.com", "Tag Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // --- Tenant B ---
    provisioningService.provisionTenant(ORG_ID_B, "Tag Test Org B", null);

    memberIdOwnerB =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID_B,
                "user_tag_owner_b",
                "tag_owner_b@test.com",
                "Tag Owner B",
                "owner"));

    tenantSchemaB =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_B).orElseThrow().getSchemaName();
  }

  @Test
  void shouldSaveAndRetrieveTagInDedicatedSchema() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var tag = new Tag("Urgent", "#EF4444");
                  tag = tagRepository.save(tag);

                  var found = tagRepository.findById(tag.getId());
                  assertThat(found).isPresent();
                  assertThat(found.get().getName()).isEqualTo("Urgent");
                  assertThat(found.get().getSlug()).isEqualTo("urgent");
                  assertThat(found.get().getColor()).isEqualTo("#EF4444");
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
                  var tag = new Tag("Isolation Test Tag", null);
                  tag = tagRepository.save(tag);
                  idHolder[0] = tag.getId();
                }));

    runInTenant(
        tenantSchemaB,
        ORG_ID_B,
        memberIdOwnerB,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var found = tagRepository.findById(idHolder[0]);
                  assertThat(found).isEmpty();
                }));
  }

  @Test
  void shouldAutoGenerateSlugFromName() {
    var slug = Tag.generateSlug("My Custom Tag");
    assertThat(slug).isEqualTo("my_custom_tag");
  }

  @Test
  void shouldAutoGenerateSlugWithHyphens() {
    var slug = Tag.generateSlug("high-priority");
    assertThat(slug).isEqualTo("high_priority");
  }

  @Test
  void shouldDeleteTagAndCascadeToEntityTags() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var tag = new Tag("Cascade Test", "#10B981");
                  tag = tagRepository.save(tag);

                  var entityTag = new EntityTag(tag.getId(), "PROJECT", UUID.randomUUID());
                  entityTagRepository.save(entityTag);

                  assertThat(entityTagRepository.countByTagId(tag.getId())).isEqualTo(1);

                  tagRepository.delete(tag);
                  tagRepository.flush();

                  assertThat(entityTagRepository.countByTagId(tag.getId())).isEqualTo(0);
                }));
  }

  @Test
  void shouldSearchTagsByPrefix() throws Exception {
    // Create tags via API
    mockMvc
        .perform(
            post("/api/tags")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tag_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Search Alpha", "color": "#EF4444"}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/tags")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tag_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Search Beta", "color": "#3B82F6"}
                    """))
        .andExpect(status().isCreated());

    // Search with prefix
    mockMvc
        .perform(
            get("/api/tags")
                .param("search", "Search")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tag_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));

    // Search non-matching prefix
    mockMvc
        .perform(
            get("/api/tags")
                .param("search", "ZZZ")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tag_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void adminCanCreateUpdateDeleteTag() throws Exception {
    // Create
    var result =
        mockMvc
            .perform(
                post("/api/tags")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tag_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "CRUD Test Tag", "color": "#F59E0B"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("CRUD Test Tag"))
            .andExpect(jsonPath("$.slug").value("crud_test_tag"))
            .andExpect(jsonPath("$.color").value("#F59E0B"))
            .andReturn();

    String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // Update
    mockMvc
        .perform(
            put("/api/tags/" + id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tag_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Updated Tag", "color": "#8B5CF6"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Tag"))
        .andExpect(jsonPath("$.color").value("#8B5CF6"));

    // Delete
    mockMvc
        .perform(delete("/api/tags/" + id).with(TestJwtFactory.ownerJwt(ORG_ID, "user_tag_owner")))
        .andExpect(status().isNoContent());

    // Verify deleted — list should not contain it
    mockMvc
        .perform(
            get("/api/tags")
                .param("search", "Updated Tag")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tag_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void memberCannotMutateTags() throws Exception {
    mockMvc
        .perform(
            post("/api/tags")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_tag_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Denied Tag", "color": "#EF4444"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void memberCanReadTags() throws Exception {
    // Create as owner
    mockMvc
        .perform(
            post("/api/tags")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tag_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Readable Tag", "color": "#06B6D4"}
                    """))
        .andExpect(status().isCreated());

    // Read as member
    mockMvc
        .perform(get("/api/tags").with(TestJwtFactory.memberJwt(ORG_ID, "user_tag_member")))
        .andExpect(status().isOk());
  }

  @Test
  void shouldResolveDuplicateSlugWithSuffix() throws Exception {
    // Create first tag with name "Duplicate"
    mockMvc
        .perform(
            post("/api/tags")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tag_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Duplicate", "color": "#EF4444"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slug").value("duplicate"));

    // Create second tag with same name — should get a suffixed slug
    mockMvc
        .perform(
            post("/api/tags")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tag_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Duplicate", "color": "#3B82F6"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slug").value("duplicate_2"));
  }

  // --- JWT Helpers ---

  // --- Helpers ---

  private void runInTenant(String schema, String orgId, UUID memberId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
