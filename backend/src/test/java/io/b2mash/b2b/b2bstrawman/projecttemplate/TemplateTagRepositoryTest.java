package io.b2mash.b2b.b2bstrawman.projecttemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.tag.Tag;
import io.b2mash.b2b.b2bstrawman.tag.TagRepository;
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
class TemplateTagRepositoryTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_template_tag_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ProjectTemplateRepository projectTemplateRepository;
  @Autowired private TemplateTagRepository templateTagRepository;
  @Autowired private TagRepository tagRepository;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Template Tag Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_tt_owner", "tt_owner@test.com", "Template Tag Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void saveAndFindTagIds() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new ProjectTemplate(
                          "Tag Find Template",
                          "{customer} - Find",
                          null,
                          true,
                          "MANUAL",
                          null,
                          memberId);
                  template = projectTemplateRepository.saveAndFlush(template);

                  var tag = new Tag("find-tag", "#10B981");
                  tag = tagRepository.saveAndFlush(tag);

                  templateTagRepository.save(template.getId(), tag.getId());

                  var tagIds = templateTagRepository.findTagIdsByTemplateId(template.getId());
                  assertThat(tagIds).containsExactly(tag.getId());
                }));
  }

  @Test
  void deleteByTemplateIdRemovesAllRows() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new ProjectTemplate(
                          "Tag Delete All Template",
                          "{customer} - Del",
                          null,
                          true,
                          "MANUAL",
                          null,
                          memberId);
                  template = projectTemplateRepository.saveAndFlush(template);

                  var tag1 = new Tag("del-tag-1", "#F59E0B");
                  tag1 = tagRepository.saveAndFlush(tag1);
                  var tag2 = new Tag("del-tag-2", "#8B5CF6");
                  tag2 = tagRepository.saveAndFlush(tag2);

                  templateTagRepository.save(template.getId(), tag1.getId());
                  templateTagRepository.save(template.getId(), tag2.getId());
                  assertThat(templateTagRepository.findTagIdsByTemplateId(template.getId()))
                      .hasSize(2);

                  templateTagRepository.deleteByTemplateId(template.getId());

                  assertThat(templateTagRepository.findTagIdsByTemplateId(template.getId()))
                      .isEmpty();
                }));
  }

  @Test
  void deleteByTemplateIdAndTagIdRemovesOnlySpecificRow() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new ProjectTemplate(
                          "Tag Delete One Template",
                          "{customer} - DelOne",
                          null,
                          true,
                          "MANUAL",
                          null,
                          memberId);
                  template = projectTemplateRepository.saveAndFlush(template);

                  var tag1 = new Tag("keep-tag", "#06B6D4");
                  tag1 = tagRepository.saveAndFlush(tag1);
                  var tag2 = new Tag("remove-tag", "#EC4899");
                  tag2 = tagRepository.saveAndFlush(tag2);

                  templateTagRepository.save(template.getId(), tag1.getId());
                  templateTagRepository.save(template.getId(), tag2.getId());

                  templateTagRepository.deleteByTemplateIdAndTagId(template.getId(), tag2.getId());

                  var remaining = templateTagRepository.findTagIdsByTemplateId(template.getId());
                  assertThat(remaining).containsExactly(tag1.getId());
                }));
  }

  @Test
  void duplicateSaveDoesNotThrow() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new ProjectTemplate(
                          "Tag Dup Template",
                          "{customer} - Dup",
                          null,
                          true,
                          "MANUAL",
                          null,
                          memberId);
                  template = projectTemplateRepository.saveAndFlush(template);

                  var tag = new Tag("dup-tag", "#D946EF");
                  tag = tagRepository.saveAndFlush(tag);

                  templateTagRepository.save(template.getId(), tag.getId());
                  // Second save should not throw (ON CONFLICT DO NOTHING)
                  templateTagRepository.save(template.getId(), tag.getId());

                  var tagIds = templateTagRepository.findTagIdsByTemplateId(template.getId());
                  assertThat(tagIds).hasSize(1);
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
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
