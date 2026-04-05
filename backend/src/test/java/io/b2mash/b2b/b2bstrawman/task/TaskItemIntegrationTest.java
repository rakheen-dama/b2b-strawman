package io.b2mash.b2b.b2bstrawman.task;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskItemIntegrationTest {
  private static final String ORG_ID = "org_task_item_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private String projectId;
  private String taskId;
  private String memberIdOwner;
  private String memberIdMember;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Task Item Test Org", null);

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_ti_owner", "ti_owner@test.com", "TI Owner", "owner");
    memberIdMember =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_ti_member", "ti_member@test.com", "TI Member", "member");

    // Create project
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ti_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "TaskItem Test Project", "description": "For task item tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = TestEntityHelper.extractIdFromLocation(projectResult);

    // Add member to project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ti_owner"))
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
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ti_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Parent Task for Items"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    taskId = TestEntityHelper.extractIdFromLocation(taskResult);
  }

  @Test
  void shouldAddItemToTask() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/items")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ti_owner"))
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
        .perform(
            get("/api/tasks/" + freshTaskId + "/items")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ti_owner")))
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
        .perform(
            put("/api/tasks/" + freshTaskId + "/items/" + itemId + "/toggle")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ti_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completed").value(true));

    // Toggle back to completed=false
    mockMvc
        .perform(
            put("/api/tasks/" + freshTaskId + "/items/" + itemId + "/toggle")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ti_owner")))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ti_owner"))
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
        .perform(
            delete("/api/tasks/" + freshTaskId + "/items/" + itemId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ti_owner")))
        .andExpect(status().isNoContent());

    // Verify item is gone
    mockMvc
        .perform(
            get("/api/tasks/" + freshTaskId + "/items")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ti_owner")))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ti_owner"))
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
            put("/api/tasks/" + freshTaskId + "/items/" + itemId + "/toggle")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_ti_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completed").value(true));
  }

  @Test
  void memberCanAddItem() throws Exception {
    var freshTaskId = createTask("Member Add Test Task");

    mockMvc
        .perform(
            post("/api/tasks/" + freshTaskId + "/items")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_ti_member"))
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

    // Member gets 403 on delete (requires PROJECT_MANAGEMENT capability)
    mockMvc
        .perform(
            delete("/api/tasks/" + freshTaskId + "/items/" + itemId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_ti_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void memberCannotUpdateItem() throws Exception {
    var freshTaskId = createTask("Member Update Forbidden Task");
    var itemId = addItem(freshTaskId, "Cannot Update", 0);

    // Member gets 403 on update (requires PROJECT_MANAGEMENT capability)
    mockMvc
        .perform(
            put("/api/tasks/" + freshTaskId + "/items/" + itemId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_ti_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Attempted Update", "sortOrder": 1}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void memberCannotReorderItems() throws Exception {
    var freshTaskId = createTask("Member Reorder Forbidden Task");
    var id1 = addItem(freshTaskId, "Reorder A", 0);
    var id2 = addItem(freshTaskId, "Reorder B", 1);

    // Member gets 403 on reorder (requires PROJECT_MANAGEMENT capability)
    mockMvc
        .perform(
            put("/api/tasks/" + freshTaskId + "/items/reorder")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_ti_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orderedIds": ["%s", "%s"]}
                    """
                        .formatted(id2, id1)))
        .andExpect(status().isForbidden());
  }

  @Test
  void itemsDeletedWithTask() throws Exception {
    var freshTaskId = createTask("Cascade Delete Task");
    addItem(freshTaskId, "Cascaded Item 1", 0);
    addItem(freshTaskId, "Cascaded Item 2", 1);

    // Verify items exist
    mockMvc
        .perform(
            get("/api/tasks/" + freshTaskId + "/items")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ti_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));

    // Delete the parent task
    mockMvc
        .perform(
            delete("/api/tasks/" + freshTaskId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ti_owner")))
        .andExpect(status().isNoContent());

    // Verify task is gone (items cascade-deleted at DB level)
    mockMvc
        .perform(
            get("/api/tasks/" + freshTaskId + "/items")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ti_owner")))
        .andExpect(status().isNotFound());
  }

  // --- Helpers ---

  private String createTask(String title) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ti_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "%s"}
                        """
                            .formatted(title)))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractIdFromLocation(result);
  }

  private String addItem(String taskId, String title, int sortOrder) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/tasks/" + taskId + "/items")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ti_owner"))
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
}
