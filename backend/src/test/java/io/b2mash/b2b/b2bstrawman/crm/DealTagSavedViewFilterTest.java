package io.b2mash.b2b.b2bstrawman.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagService;
import io.b2mash.b2b.b2bstrawman.tag.TagFilterUtil;
import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Epic 577 (577A.5) — proves the free-form {@code "DEAL"} discriminator round-trips through the tag
 * and saved-view registries with zero new registration. Tag filtering is exercised directly against
 * {@link EntityTagService} (there is no {@code /api/deals/{id}/tags} endpoint — building one is out
 * of scope for 577A) and saved views via the existing {@code POST /api/views} HTTP path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DealTagSavedViewFilterTest {

  private static final String ORG_ID = "org_deal_tag_view_test";
  private static final String OWNER_SUBJECT = "user_deal_tv_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private EntityTagService entityTagService;

  private String customerId;
  private String tenantSchema;
  private UUID ownerMemberId;

  private JwtRequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT);
  }

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Deal Tag/View Test Org", null);
    String memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, OWNER_SUBJECT, "deal_tv_owner@test.com", "Deal TV Owner", "owner");
    ownerMemberId = UUID.fromString(memberId);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    customerId =
        TestEntityHelper.createCustomer(mockMvc, owner(), "TV Acme Corp", "tv_acme@test.com");
  }

  private String createDeal(String title) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/deals")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"customerId":"%s","title":"%s","valueAmount":1000.00}
                        """
                            .formatted(customerId, title)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  void dealCanBeTaggedAndFilteredByTagViaTheRegistry() throws Exception {
    String tagId = TestEntityHelper.createTag(mockMvc, owner(), "Hot Deal", "#F97316");
    UUID tagUuid = UUID.fromString(tagId);

    UUID taggedDeal = UUID.fromString(createDeal("Hot Tagged Deal"));
    UUID untaggedDeal = UUID.fromString(createDeal("Untagged Deal"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              entityTagService.setEntityTags("DEAL", taggedDeal, List.of(tagUuid));

              var tags = entityTagService.getEntityTags("DEAL", taggedDeal);
              assertThat(tags).extracting(TagResponse::name).contains("Hot Deal");

              var batch =
                  entityTagService.getEntityTagsBatch("DEAL", List.of(taggedDeal, untaggedDeal));
              assertThat(TagFilterUtil.matchesTagFilter(batch.get(taggedDeal), List.of("hot_deal")))
                  .isTrue();
              assertThat(
                      TagFilterUtil.matchesTagFilter(
                          batch.getOrDefault(untaggedDeal, List.of()), List.of("hot_deal")))
                  .isFalse();
            });
  }

  @Test
  void savedViewWithDealEntityTypePersistsAndLists() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/views")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "DEAL",
                          "name": "Open High-Value Deals",
                          "filters": {"status": "OPEN"},
                          "shared": false,
                          "sortOrder": 0
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.entityType").value("DEAL"))
            .andExpect(jsonPath("$.name").value("Open High-Value Deals"))
            .andReturn();
    String viewId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(get("/api/views").with(owner()).param("entityType", "DEAL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '" + viewId + "')]").exists())
        .andExpect(jsonPath("$[?(@.id == '" + viewId + "')].entityType").value("DEAL"));
  }
}
