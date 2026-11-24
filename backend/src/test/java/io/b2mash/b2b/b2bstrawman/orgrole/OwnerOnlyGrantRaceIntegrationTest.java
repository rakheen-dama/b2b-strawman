package io.b2mash.b2b.b2bstrawman.orgrole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * FR-4 under concurrency (PR #1856 review round 2): an admin's role update or role assignment
 * decides what it "adds" from what it read; the owner taking an owner-only capability away in
 * between must not be undone by the admin's whole-list write.
 *
 * <p>Deterministic: the admin's request is paused inside its audit call — after it has read the
 * role (or member) and changed the entity, before its transaction flushes — while the owner's
 * request runs; then the admin is released. Unlocked, the owner commits first and the admin's write
 * restores the capability. Locked (the role / member row {@code FOR UPDATE} before the read), the
 * owner waits for the admin and its removal lands last.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnerOnlyGrantRaceIntegrationTest {

  private static final String ORG_ID = "org_owner_only_grant_race_test";
  private static final String OWNER = "user_ogr_owner";
  private static final String ADMIN = "user_ogr_admin";
  private static final String SIGN_OFF = "SIGN_OFF_TRUST_TAKE_ON";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @MockitoSpyBean private AuditService auditService;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Grant Race Attorneys", "legal-za");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, OWNER, "ogr_owner@test.com", "Race Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, ADMIN, "ogr_admin@test.com", "Race Admin", "admin");
  }

  @AfterEach
  void unpause() {
    reset(auditService);
  }

  @Test
  void anOwnersRemovalFromARoleIsNotUndoneByAConcurrentAdminUpdate() throws Exception {
    String role = createPartner("Partner Race Role");

    race(
        // the admin keeps SIGN_OFF (allowed: nothing owner-only added) and adds INVOICING
        () -> updateCapabilities(admin(), role, SIGN_OFF, "VIEW_TRUST", "INVOICING"),
        // the owner takes SIGN_OFF away
        () -> updateCapabilities(owner(), role, "VIEW_TRUST"));

    assertThat(roleCapabilities(role))
        .as("the owner's removal survives the admin's concurrent update")
        .doesNotContain(SIGN_OFF);
  }

  @Test
  void anOwnersRemovalFromAMemberIsNotUndoneByAConcurrentAdminAssignment() throws Exception {
    String role = createPartner("Partner Race Member");
    String memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_ogr_target", "ogr_target@test.com", "Race Target", "member");
    assign(owner(), memberId, role).andExpect(status().isOk());

    String partner2 = createPartner("Partner Race Member 2");
    String bookkeeper = createRole("Bookkeeper Race", "INVOICING");

    race(
        // the admin moves the member between two roles holding SIGN_OFF (allowed: the member it
        // read holds SIGN_OFF already, nothing is gained)
        () -> assign(admin(), memberId, partner2),
        // the owner moves the member off the Partner role (taking SIGN_OFF away)
        () -> assign(owner(), memberId, bookkeeper));

    assertThat(effectiveCapabilities(memberId))
        .as("the owner's removal survives the admin's concurrent assignment")
        .doesNotContain(SIGN_OFF);
  }

  /**
   * Runs {@code adminCall} paused in its audit call, then {@code ownerCall} (which completes,
   * unlocked, or waits on the admin's lock), then releases the admin. Both must answer 200.
   */
  private void race(Call adminCall, Call ownerCall) throws Exception {
    var adminThread = new AtomicReference<Thread>();
    var paused = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              if (Thread.currentThread() == adminThread.get() && paused.getCount() > 0) {
                paused.countDown();
                await(release);
              }
              return invocation.callRealMethod();
            })
        .when(auditService)
        .log(any());

    var adminStatus = new AtomicReference<Integer>();
    var ownerStatus = new AtomicReference<Integer>();
    var failure = new AtomicReference<Throwable>();
    Thread admin = Thread.ofVirtual().unstarted(() -> run(adminCall, adminStatus, failure));
    adminThread.set(admin);
    Thread owner = null;
    try {
      admin.start();
      assertThat(paused.await(60, TimeUnit.SECONDS)).as("the admin's request paused").isTrue();
      owner = Thread.ofVirtual().start(() -> run(ownerCall, ownerStatus, failure));
      owner.join(3_000); // unlocked it commits now; locked it waits for the admin
    } finally {
      release.countDown();
      admin.join(60_000);
      if (owner != null) {
        owner.join(60_000);
      }
    }
    assertThat(failure.get()).isNull();
    assertThat(admin.isAlive()).isFalse();
    assertThat(owner.isAlive()).isFalse();
    assertThat(adminStatus.get()).as("admin's answer").isEqualTo(200);
    assertThat(ownerStatus.get()).as("owner's answer").isEqualTo(200);
  }

  @FunctionalInterface
  private interface Call {
    ResultActions perform() throws Exception;
  }

  private static void run(
      Call call, AtomicReference<Integer> status, AtomicReference<Throwable> failure) {
    try {
      status.set(call.perform().andReturn().getResponse().getStatus());
    } catch (Throwable t) {
      failure.set(t);
    }
  }

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }

  private RequestPostProcessor admin() {
    return TestJwtFactory.adminJwt(ORG_ID, ADMIN);
  }

  private String createPartner(String name) throws Exception {
    return createRole(name, SIGN_OFF, "VIEW_TRUST");
  }

  private String createRole(String name, String... capabilities) throws Exception {
    var created =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\": \"%s\", \"description\": \"Test role\", \"capabilities\": %s}"
                            .formatted(name, json(capabilities))))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
  }

  private ResultActions updateCapabilities(
      RequestPostProcessor jwt, String roleId, String... capabilities) throws Exception {
    return mockMvc.perform(
        put("/api/org-roles/" + roleId)
            .with(jwt)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"capabilities\": %s}".formatted(json(capabilities))));
  }

  private ResultActions assign(
      RequestPostProcessor jwt, String memberId, String roleId, String... overrides)
      throws Exception {
    return mockMvc.perform(
        put("/api/members/" + memberId + "/role")
            .with(jwt)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"orgRoleId\": \"%s\", \"capabilityOverrides\": %s}"
                    .formatted(roleId, json(overrides))));
  }

  private List<String> roleCapabilities(String roleId) throws Exception {
    return JsonPath.read(
        mockMvc
            .perform(get("/api/org-roles/" + roleId).with(owner()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.capabilities");
  }

  private List<String> effectiveCapabilities(String memberId) throws Exception {
    return JsonPath.read(
        mockMvc
            .perform(get("/api/members/" + memberId + "/capabilities").with(owner()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.effectiveCapabilities");
  }

  private static String json(String... values) {
    var sb = new StringBuilder("[");
    for (int i = 0; i < values.length; i++) {
      sb.append(i == 0 ? "" : ", ").append('"').append(values[i]).append('"');
    }
    return sb.append(']').toString();
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(60, TimeUnit.SECONDS)) {
        throw new IllegalStateException("latch timed out");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
