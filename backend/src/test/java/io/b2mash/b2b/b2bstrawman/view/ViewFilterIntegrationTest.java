package io.b2mash.b2b.b2bstrawman.view;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.tag.EntityTag;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagRepository;
import io.b2mash.b2b.b2bstrawman.tag.Tag;
import io.b2mash.b2b.b2bstrawman.tag.TagRepository;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
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
class ViewFilterIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_vf_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private EntityTagRepository entityTagRepository;
  @Autowired private SavedViewRepository savedViewRepository;

  private String tenantSchema;
  private UUID memberIdOwner;

  // Seeded entity IDs
  private UUID projectActiveId;
  private UUID projectOnHoldId;
  private UUID projectArchivedId;
  private UUID customerActiveId;
  private UUID customerArchivedId;
  private UUID taskOpenId;
  private UUID taskDoneId;
  private UUID tagVipId;
  private UUID tagUrgentId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "View Filter Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(ORG_ID, "user_vf_owner", "vf_owner@test.com", "VF Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Seed test data
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        "owner",
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Create tags
                  var vipTag = new Tag("VIP Client", "#EF4444");
                  vipTag = tagRepository.saveAndFlush(vipTag);
                  tagVipId = vipTag.getId();

                  var urgentTag = new Tag("Urgent", "#F59E0B");
                  urgentTag = tagRepository.saveAndFlush(urgentTag);
                  tagUrgentId = urgentTag.getId();

                  // Create projects with custom fields
                  var p1 = new Project("Acme Corp Case", "Active project", memberIdOwner);
                  p1.setCustomFields(Map.of("court", "high_court_gauteng"));
                  p1 = projectRepository.saveAndFlush(p1);
                  projectActiveId = p1.getId();

                  var p2 = new Project("Beta Inc Case", "On hold project", memberIdOwner);
                  p2.setCustomFields(Map.of("court", "magistrate_court"));
                  p2 = projectRepository.saveAndFlush(p2);
                  projectOnHoldId = p2.getId();

                  var p3 = new Project("Gamma Ltd Case", "Archived project", memberIdOwner);
                  p3.setCustomFields(Map.of("court", "high_court_gauteng"));
                  p3 = projectRepository.saveAndFlush(p3);
                  projectArchivedId = p3.getId();

                  // Tag project 1 with VIP and Urgent
                  entityTagRepository.saveAndFlush(
                      new EntityTag(tagVipId, "PROJECT", projectActiveId));
                  entityTagRepository.saveAndFlush(
                      new EntityTag(tagUrgentId, "PROJECT", projectActiveId));

                  // Tag project 2 with VIP only
                  entityTagRepository.saveAndFlush(
                      new EntityTag(tagVipId, "PROJECT", projectOnHoldId));

                  // Create customers
                  var c1 =
                      new Customer(
                          "Active Customer", "active_vf@test.com", null, null, null, memberIdOwner);
                  c1 = customerRepository.saveAndFlush(c1);
                  customerActiveId = c1.getId();

                  var c2 =
                      new Customer(
                          "Archived Customer",
                          "archived_vf@test.com",
                          null,
                          null,
                          null,
                          memberIdOwner);
                  c2.archive();
                  c2 = customerRepository.saveAndFlush(c2);
                  customerArchivedId = c2.getId();

                  // Create tasks under project 1
                  var t1 =
                      new Task(projectActiveId, "Open Task", null, null, null, null, memberIdOwner);
                  t1 = taskRepository.saveAndFlush(t1);
                  taskOpenId = t1.getId();

                  var t2 =
                      new Task(projectActiveId, "Done Task", null, null, null, null, memberIdOwner);
                  t2.update("Done Task", null, "MEDIUM", "DONE", null, null, null);
                  t2 = taskRepository.saveAndFlush(t2);
                  taskDoneId = t2.getId();
                }));
  }

  // --- Project filter tests ---

  @Test
  void projectSearchFilterReturnsMatchingNames() throws Exception {
    // Create a saved view with search filter
    var viewId = createSavedView("PROJECT", "Search Acme", Map.of("search", "Acme"));

    mockMvc
        .perform(get("/api/projects").param("view", viewId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Acme Corp Case"));
  }

  @Test
  void projectCustomFieldFilterReturnsMatching() throws Exception {
    var viewId =
        createSavedView(
            "PROJECT",
            "Court Filter",
            Map.of(
                "customFields",
                Map.of("court", Map.of("op", "eq", "value", "high_court_gauteng"))));

    mockMvc
        .perform(get("/api/projects").param("view", viewId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void projectTagFilterWithAndLogicReturnsOnlyFullMatches() throws Exception {
    // Find the tag slugs
    var vipSlug = new String[1];
    var urgentSlug = new String[1];
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        "owner",
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  vipSlug[0] = tagRepository.findById(tagVipId).orElseThrow().getSlug();
                  urgentSlug[0] = tagRepository.findById(tagUrgentId).orElseThrow().getSlug();
                }));

    // Both tags — only project 1 has both
    var viewId =
        createSavedView(
            "PROJECT", "Both Tags Filter", Map.of("tags", List.of(vipSlug[0], urgentSlug[0])));

    mockMvc
        .perform(get("/api/projects").param("view", viewId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Acme Corp Case"));
  }

  @Test
  void projectViewWithEmptyFiltersUsesExistingLogic() throws Exception {
    // View with empty filters — should return all projects via fallback
    var viewId = createSavedView("PROJECT", "Empty Filter", Map.of());

    // Empty filters → whereClause is empty → falls through to existing logic
    mockMvc
        .perform(get("/api/projects").param("view", viewId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
  }

  @Test
  void projectViewNotFoundReturns404() throws Exception {
    var nonExistentId = UUID.randomUUID().toString();

    mockMvc
        .perform(get("/api/projects").param("view", nonExistentId).with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  // --- Customer filter tests ---

  @Test
  void customerStatusFilterReturnsMatching() throws Exception {
    var viewId =
        createSavedView("CUSTOMER", "Active Customers", Map.of("status", List.of("ACTIVE")));

    mockMvc
        .perform(get("/api/customers").param("view", viewId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Active Customer"));
  }

  @Test
  void customerSearchFilterReturnsMatching() throws Exception {
    var viewId = createSavedView("CUSTOMER", "Search Archived", Map.of("search", "Archived"));

    mockMvc
        .perform(get("/api/customers").param("view", viewId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Archived Customer"));
  }

  // --- Task filter tests ---

  @Test
  void taskStatusFilterReturnsMatchingInProject() throws Exception {
    var viewId = createSavedView("TASK", "Open Tasks Only", Map.of("status", List.of("OPEN")));

    mockMvc
        .perform(
            get("/api/projects/{projectId}/tasks", projectActiveId)
                .param("view", viewId)
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].title").value("Open Task"));
  }

  @Test
  void taskSearchFilterReturnsMatchingInProject() throws Exception {
    var viewId = createSavedView("TASK", "Search Done", Map.of("search", "Done"));

    mockMvc
        .perform(
            get("/api/projects/{projectId}/tasks", projectActiveId)
                .param("view", viewId)
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].title").value("Done Task"));
  }

  // --- Combined filter test ---

  @Test
  void combinedSearchAndCustomFieldFiltersNarrowResults() throws Exception {
    var viewId =
        createSavedView(
            "PROJECT",
            "Combined Filter",
            Map.of(
                "search",
                "Case",
                "customFields",
                Map.of("court", Map.of("op", "eq", "value", "magistrate_court"))));

    mockMvc
        .perform(get("/api/projects").param("view", viewId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Beta Inc Case"));
  }

  // --- Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_vf_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private String createSavedView(String entityType, String name, Map<String, Object> filters) {
    var idHolder = new String[1];
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        "owner",
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var view =
                      new SavedView(entityType, name, filters, null, false, memberIdOwner, 0);
                  view = savedViewRepository.saveAndFlush(view);
                  idHolder[0] = view.getId().toString();
                }));
    return idHolder[0];
  }

  private void runInTenant(
      String schema, String orgId, UUID memberId, String orgRole, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, orgRole)
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
