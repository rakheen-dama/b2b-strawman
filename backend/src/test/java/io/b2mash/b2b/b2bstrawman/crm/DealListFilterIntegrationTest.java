package io.b2mash.b2b.b2bstrawman.crm;

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
 * Slice 574B — proves {@code GET /api/deals} narrows the dataset server-side by {@code tags} (CSV
 * of slugs, ALL/AND semantics) and {@code view} (saved-view UUID), composing them with the existing
 * direct column filters. Mirrors the customers-list mechanism (in-memory tag util semantics, native
 * saved-view executor) but the deal path keeps the composition in {@link DealService} and the tag
 * predicate SQL-side so paging totals stay correct.
 *
 * <p>The deal entity type discriminator is the free-form string {@code "DEAL"} (Epic 577A) — no new
 * registration. The only registry change 574B required is allowlisting {@code "deals"} in the
 * saved-view native executor ({@code ViewFilterService.ALLOWED_TABLES}).
 *
 * <p>Assertions use containment-by-id over the paged {@code $.content[*].id} (VIA_DTO shape) —
 * never an exact global count, since sibling methods accumulate deals in the shared {@code
 * PER_CLASS} tenant (count-bleed flake rule).
 *
 * <p><b>Shared-handler limitation (documented, not a bug):</b> because DEAL views run through the
 * same {@code ViewFilterService} handlers as the other entities, a DEAL {@code dateRange} saved
 * view is limited to the columns that exist on the {@code deals} table — namely {@code created_at}
 * and {@code updated_at}. {@code DateRangeFilterHandler.ALLOWED_FIELDS} also permits {@code
 * due_date}, but {@code deals} has no such column; a DEAL date-range view on {@code due_date} would
 * reference a non-existent column. This is a pre-existing per-entity-field gap in the shared
 * handler (out of scope for 574B — adding due_date support is intentionally NOT attempted); the
 * realistic deal date filters (created_at/updated_at) work correctly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DealListFilterIntegrationTest {

  private static final String ORG_ID = "org_deal_list_filter_test";
  private static final String OWNER_SUBJECT = "user_deal_list_filter_owner";

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
    provisioningService.provisionTenant(ORG_ID, "Deal List Filter Test Org", null);
    String memberId =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            OWNER_SUBJECT,
            "deal_list_filter_owner@test.com",
            "Deal List Filter Owner",
            "owner");
    ownerMemberId = UUID.fromString(memberId);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    customerId =
        TestEntityHelper.createCustomer(
            mockMvc, owner(), "Filter Acme Corp", "filter_acme@test.com");
  }

  // --- Helpers ---

  private UUID createDeal(String title) throws Exception {
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
    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  private UUID createTag(String name) throws Exception {
    return UUID.fromString(TestEntityHelper.createTag(mockMvc, owner(), name, "#F97316"));
  }

  /** Attach tags to a deal — must run inside tenant scope (EntityTagService is tenant-bound). */
  private void tagDeal(UUID dealId, List<UUID> tagIds) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(() -> entityTagService.setEntityTags("DEAL", dealId, tagIds));
  }

  private UUID createDealSavedView(String name, String filtersJson) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/views")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "DEAL",
                          "name": "%s",
                          "filters": %s,
                          "shared": false,
                          "sortOrder": 0
                        }
                        """
                            .formatted(name, filtersJson)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.entityType").value("DEAL"))
            .andReturn();
    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  // --- Tests ---

  @Test
  void filtersBySingleTag() throws Exception {
    UUID hot = createTag("Hot Single");
    UUID tagged = createDeal("Single-Tag Tagged");
    UUID untagged = createDeal("Single-Tag Untagged");
    tagDeal(tagged, List.of(hot));

    mockMvc
        .perform(get("/api/deals").with(owner()).param("tags", "hot_single"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id == '" + tagged + "')]").exists())
        .andExpect(jsonPath("$.content[?(@.id == '" + untagged + "')]").doesNotExist());
  }

  @Test
  void filtersByMultipleTagsWithAllSemantics() throws Exception {
    UUID alpha = createTag("Multi Alpha");
    UUID beta = createTag("Multi Beta");

    UUID both = createDeal("Multi Both Tags");
    UUID onlyAlpha = createDeal("Multi Only Alpha");
    tagDeal(both, List.of(alpha, beta));
    tagDeal(onlyAlpha, List.of(alpha));

    // ALL/AND: a deal carrying only one of the two requested slugs is excluded.
    mockMvc
        .perform(get("/api/deals").with(owner()).param("tags", "multi_alpha,multi_beta"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id == '" + both + "')]").exists())
        .andExpect(jsonPath("$.content[?(@.id == '" + onlyAlpha + "')]").doesNotExist());
  }

  @Test
  void filtersBySavedView() throws Exception {
    UUID openDeal = createDeal("View Open Deal");
    UUID wonDeal = createDeal("View Won Deal");

    // Transition one deal into a WON stage so the {"status":"OPEN"} saved view must exclude it.
    transitionToStage(wonDeal, stageIdOfType("WON"));

    // The status filter handler expects a list of status values → SQL `status IN (:statuses)`.
    UUID viewId = createDealSavedView("Open Deals View", "{\"status\": [\"OPEN\"]}");

    mockMvc
        .perform(get("/api/deals").with(owner()).param("view", viewId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id == '" + openDeal + "')]").exists())
        .andExpect(jsonPath("$.content[?(@.id == '" + wonDeal + "')]").doesNotExist());
  }

  @Test
  void composesDirectFilterWithTagFilter() throws Exception {
    UUID hot = createTag("Combo Hot");

    String openStageId = stageIdOfType("OPEN");

    UUID stageAndTag = createDeal("Combo Stage And Tag");
    UUID tagOnly = createDeal("Combo Tag No Matching Stage");
    tagDeal(stageAndTag, List.of(hot));
    tagDeal(tagOnly, List.of(hot));

    // Move tagOnly off the open stage (to WON) so the stageId filter excludes it even though it
    // carries the tag — proving the direct filter AND the tag filter intersect.
    transitionToStage(tagOnly, stageIdOfType("WON"));

    mockMvc
        .perform(
            get("/api/deals")
                .with(owner())
                .param("stageId", openStageId)
                .param("tags", "combo_hot"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id == '" + stageAndTag + "')]").exists())
        .andExpect(jsonPath("$.content[?(@.id == '" + tagOnly + "')]").doesNotExist());
  }

  @Test
  void filtersBySavedViewSearchOnTitle() throws Exception {
    // DEAL saved views with a {"search": ...} filter must narrow by the deals.title column.
    // (Regression guard: deals have a `title` column, not `name`; the search handler must map
    // DEAL → title, otherwise this view would emit `name ILIKE ...` against deals and break.)
    UUID matching = createDeal("Search Zephyrine Holdings");
    UUID nonMatching = createDeal("Search Quokka Logistics");

    UUID viewId = createDealSavedView("Zephyrine Search View", "{\"search\": \"Zephyrine\"}");

    mockMvc
        .perform(get("/api/deals").with(owner()).param("view", viewId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id == '" + matching + "')]").exists())
        .andExpect(jsonPath("$.content[?(@.id == '" + nonMatching + "')]").doesNotExist());
  }

  @Test
  void composesSavedViewWithTagFilter() throws Exception {
    // view ∩ tags: a view that matches some deals, intersected with a tag, returns only deals
    // satisfying BOTH facets (AND/intersection semantics).
    UUID warm = createTag("Compose Warm");

    UUID inViewAndTagged = createDeal("Compose ViewTag Both");
    UUID inViewNotTagged = createDeal("Compose ViewTag ViewOnly");
    UUID taggedNotInView = createDeal("Compose ViewTag TagOnly");

    tagDeal(inViewAndTagged, List.of(warm));
    tagDeal(taggedNotInView, List.of(warm));

    // Move taggedNotInView into a WON stage so the {"status":["OPEN"]} view excludes it even though
    // it carries the tag — proving the view AND the tag intersect.
    transitionToStage(taggedNotInView, stageIdOfType("WON"));

    UUID viewId = createDealSavedView("Compose Open Deals View", "{\"status\": [\"OPEN\"]}");

    mockMvc
        .perform(
            get("/api/deals")
                .with(owner())
                .param("view", viewId.toString())
                .param("tags", "compose_warm"))
        .andExpect(status().isOk())
        // satisfies BOTH (OPEN status via view AND carries the tag)
        .andExpect(jsonPath("$.content[?(@.id == '" + inViewAndTagged + "')]").exists())
        // in the view (OPEN) but missing the tag → excluded
        .andExpect(jsonPath("$.content[?(@.id == '" + inViewNotTagged + "')]").doesNotExist())
        // carries the tag but excluded by the view (WON, not OPEN) → excluded
        .andExpect(jsonPath("$.content[?(@.id == '" + taggedNotInView + "')]").doesNotExist());
  }

  /** Resolves the first non-archived stage id of the given stage type via the HTTP path. */
  private String stageIdOfType(String stageType) throws Exception {
    var result =
        mockMvc
            .perform(get("/api/pipeline/stages").with(owner()))
            .andExpect(status().isOk())
            .andReturn();
    // JsonPath filter expressions return an array; take the first matching stage's id.
    List<String> ids =
        JsonPath.read(
            result.getResponse().getContentAsString(),
            "$[?(@.stageType == '" + stageType + "' && @.archived == false)].id");
    return ids.getFirst();
  }

  private void transitionToStage(UUID dealId, String targetStageId) throws Exception {
    mockMvc
        .perform(
            post("/api/deals/" + dealId + "/transition")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStageId\":\"%s\"}".formatted(targetStageId)))
        .andExpect(status().isOk());
  }
}
