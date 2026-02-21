package io.b2mash.b2b.b2bstrawman.task;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskItemIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_task_item_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String projectId;
  private String taskId;
  private String memberIdOwner;
  private String memberIdMember;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Task Item Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner = syncMember(ORG_ID, "user_ti_owner", "ti_owner@test.com", "TI Owner", "owner");
    memberIdMember =
        syncMember(ORG_ID, "user_ti_member", "ti_member@test.com", "TI Member", "member");

    // Create project
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "TaskItem Test Project", "description": "For task item tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = extractIdFromLocation(projectResult);

    // Add member to project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memberIdMember)))
        .andExpect(status().isCreated());

    // Create a task
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Parent Task for Items"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    taskId = extractIdFromLocation(taskResult);
  }

  @Test
  void shouldAddItemToTask() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/items")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Collect IRP5s", "sortOrder": 0}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Collect IRP5s"))
        .andExpect(jsonPath("$.completed").value(false))
        .andExpect(jsonPath("$.sortOrder").value(0))
        .andExpect(jsonPath("$.taskId").value(taskId))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists());
  }

  @Test
  void shouldListItemsOrderedBySortOrder() throws Exception {
    // Create a fresh task for this test
    var freshTaskId = createTask("List Order Test Task");

    // Add items with different sort orders (added out of order)
    addItem(freshTaskId, "Third", 2);
    addItem(freshTaskId, "First", 0);
    addItem(freshTaskId, "Second", 1);

    mockMvc
        .perform(get("/api/tasks/" + freshTaskId + "/items").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].title").value("First"))
        .andExpect(jsonPath("$[0].sortOrder").value(0))
        .andExpect(jsonPath("$[1].title").value("Second"))
        .andExpect(jsonPath("$[1].sortOrder").value(1))
        .andExpect(jsonPath("$[2].title").value("Third"))
        .andExpect(jsonPath("$[2].sortOrder").value(2));
  }

  @Test
  void shouldToggleItem() throws Exception {
    var freshTaskId = createTask("Toggle Test Task");
    var itemId = addItem(freshTaskId, "Toggle Me", 0);

    // Toggle to completed=true
    mockMvc
        .perform(put("/api/tasks/" + freshTaskId + "/items/" + itemId + "/toggle").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completed").value(true));

    // Toggle back to completed=false
    mockMvc
        .perform(put("/api/tasks/" + freshTaskId + "/items/" + itemId + "/toggle").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completed").value(false));
  }

  @Test
  void shouldUpdateItem() throws Exception {
    var freshTaskId = createTask("Update Test Task");
    var itemId = addItem(freshTaskId, "Before Update", 0);

    mockMvc
        .perform(
            put("/api/tasks/" + freshTaskId + "/items/" + itemId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "After Update", "sortOrder": 5}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("After Update"))
        .andExpect(jsonPath("$.sortOrder").value(5));
  }

  @Test
  void shouldDeleteItem() throws Exception {
    var freshTaskId = createTask("Delete Test Task");
    var itemId = addItem(freshTaskId, "To Delete", 0);

    mockMvc
        .perform(delete("/api/tasks/" + freshTaskId + "/items/" + itemId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify item is gone
    mockMvc
        .perform(get("/api/tasks/" + freshTaskId + "/items").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void shouldReorderItems() throws Exception {
    var freshTaskId = createTask("Reorder Test Task");
    var id1 = addItem(freshTaskId, "Item A", 0);
    var id2 = addItem(freshTaskId, "Item B", 1);
    var id3 = addItem(freshTaskId, "Item C", 2);

    // Reverse the order
    mockMvc
        .perform(
            put("/api/tasks/" + freshTaskId + "/items/reorder")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orderedIds": ["%s", "%s", "%s"]}
                    """
                        .formatted(id3, id2, id1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("Item C"))
        .andExpect(jsonPath("$[0].sortOrder").value(0))
        .andExpect(jsonPath("$[1].title").value("Item B"))
        .andExpect(jsonPath("$[1].sortOrder").value(1))
        .andExpect(jsonPath("$[2].title").value("Item A"))
        .andExpect(jsonPath("$[2].sortOrder").value(2));
  }

  @Test
  void memberCanToggleItem() throws Exception {
    var freshTaskId = createTask("Member Toggle Test Task");
    var itemId = addItem(freshTaskId, "Member Toggle", 0);

    // Member (not admin/owner) can toggle
    mockMvc
        .perform(
            put("/api/tasks/" + freshTaskId + "/items/" + itemId + "/toggle").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completed").value(true));
  }

  @Test
  void memberCanAddItem() throws Exception {
    var freshTaskId = createTask("Member Add Test Task");

    mockMvc
        .perform(
            post("/api/tasks/" + freshTaskId + "/items")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Member Added Item"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Member Added Item"));
  }

  @Test
  void memberCannotDeleteItem() throws Exception {
    var freshTaskId = createTask("Member Delete Forbidden Task");
    var itemId = addItem(freshTaskId, "Cannot Delete", 0);

    // Member gets 403 on delete (ADMIN+ required by @PreAuthorize)
    mockMvc
        .perform(delete("/api/tasks/" + freshTaskId + "/items/" + itemId).with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void itemsDeletedWithTask() throws Exception {
    var freshTaskId = createTask("Cascade Delete Task");
    addItem(freshTaskId, "Cascaded Item 1", 0);
    addItem(freshTaskId, "Cascaded Item 2", 1);

    // Verify items exist
    mockMvc
        .perform(get("/api/tasks/" + freshTaskId + "/items").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));

    // Delete the parent task
    mockMvc
        .perform(delete("/api/tasks/" + freshTaskId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify task is gone (items cascade-deleted at DB level)
    mockMvc
        .perform(get("/api/tasks/" + freshTaskId + "/items").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  // --- Helpers ---

  private String createTask(String title) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "%s"}
                        """
                            .formatted(title)))
            .andExpect(status().isCreated())
            .andReturn();
    return extractIdFromLocation(result);
  }

  private String addItem(String taskId, String title, int sortOrder) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/tasks/" + taskId + "/items")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "%s", "sortOrder": %d}
                        """
                            .formatted(title, sortOrder)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
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

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ti_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ti_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
