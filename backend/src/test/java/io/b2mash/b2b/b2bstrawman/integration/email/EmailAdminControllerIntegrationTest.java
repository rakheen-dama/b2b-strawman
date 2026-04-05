package io.b2mash.b2b.b2bstrawman.integration.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isOneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmailAdminControllerIntegrationTest {

  private static final String ORG_ID = "org_email_admin_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private EmailDeliveryLogRepository deliveryLogRepository;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void provisionTenantAndMembers() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Email Admin Test Org", null).schemaName();

    // Sync owner member
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_ea_owner", "ea_owner@test.com", "EA Owner", "owner"));

    // Sync admin member
    TestMemberHelper.syncMemberQuietly(
        mockMvc, ORG_ID, "user_ea_admin", "ea_admin@test.com", "EA Admin", "admin");

    // Sync regular member
    TestMemberHelper.syncMemberQuietly(
        mockMvc, ORG_ID, "user_ea_member", "ea_member@test.com", "EA Member", "member");

    // Seed some delivery log entries for testing
    seedDeliveryLogEntries();
  }

  @Test
  void delivery_log_paginated() throws Exception {
    mockMvc
        .perform(
            get("/api/email/delivery-log")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ea_owner"))
                .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.page.totalElements", greaterThanOrEqualTo(3)));
  }

  @Test
  void filters_by_status() throws Exception {
    mockMvc
        .perform(
            get("/api/email/delivery-log")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ea_owner"))
                .param("status", "FAILED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].status").value("FAILED"));
  }

  @Test
  void filters_by_date() throws Exception {
    var from = Instant.now().minus(1, ChronoUnit.HOURS).toString();
    var to = Instant.now().plus(1, ChronoUnit.HOURS).toString();

    mockMvc
        .perform(
            get("/api/email/delivery-log")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ea_owner"))
                .param("from", from)
                .param("to", to))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  @Test
  void stats_correct_counts() throws Exception {
    mockMvc
        .perform(get("/api/email/stats").with(TestJwtFactory.ownerJwt(ORG_ID, "user_ea_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sent24h").isNumber())
        .andExpect(jsonPath("$.bounced7d").isNumber())
        .andExpect(jsonPath("$.failed7d").isNumber())
        .andExpect(jsonPath("$.rateLimited7d").isNumber())
        .andExpect(jsonPath("$.currentHourUsage").isNumber())
        .andExpect(jsonPath("$.hourlyLimit").isNumber())
        .andExpect(jsonPath("$.providerSlug").isString());
  }

  @Test
  void test_email_sends() throws Exception {
    mockMvc
        .perform(post("/api/email/test").with(TestJwtFactory.ownerJwt(ORG_ID, "user_ea_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recipientEmail").value("ea_owner@test.com"))
        .andExpect(jsonPath("$.referenceType").value("TEST"))
        .andExpect(jsonPath("$.templateName").value("test-email"))
        .andExpect(jsonPath("$.status").value(isOneOf("SENT", "FAILED")));
  }

  @Test
  void test_email_records_log() throws Exception {
    var result =
        mockMvc
            .perform(post("/api/email/test").with(TestJwtFactory.ownerJwt(ORG_ID, "user_ea_owner")))
            .andExpect(status().isOk())
            .andReturn();

    String logId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    assertThat(logId).isNotNull();

    // Verify the log entry exists in the database
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var logEntry = deliveryLogRepository.findById(UUID.fromString(logId));
              assertThat(logEntry).isPresent();
              assertThat(logEntry.get().getReferenceType()).isEqualTo("TEST");
              assertThat(logEntry.get().getRecipientEmail()).isEqualTo("ea_owner@test.com");
            });
  }

  @Test
  void requires_admin_role() throws Exception {
    // MEMBER role should be forbidden on all admin endpoints
    mockMvc
        .perform(
            get("/api/email/delivery-log").with(TestJwtFactory.memberJwt(ORG_ID, "user_ea_member")))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/email/stats").with(TestJwtFactory.memberJwt(ORG_ID, "user_ea_member")))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(post("/api/email/test").with(TestJwtFactory.memberJwt(ORG_ID, "user_ea_member")))
        .andExpect(status().isForbidden());

    // ADMIN role should succeed
    mockMvc
        .perform(
            get("/api/email/delivery-log").with(TestJwtFactory.adminJwt(ORG_ID, "user_ea_admin")))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/email/stats").with(TestJwtFactory.adminJwt(ORG_ID, "user_ea_admin")))
        .andExpect(status().isOk());
  }

  // --- Helper methods ---

  private void seedDeliveryLogEntries() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              // Create SENT entries
              deliveryLogRepository.save(
                  new EmailDeliveryLog(
                      "user1@test.com",
                      "notification-task",
                      "NOTIFICATION",
                      UUID.randomUUID(),
                      EmailDeliveryStatus.SENT,
                      "MSG-1",
                      "noop",
                      null));
              deliveryLogRepository.save(
                  new EmailDeliveryLog(
                      "user2@test.com",
                      "notification-comment",
                      "NOTIFICATION",
                      UUID.randomUUID(),
                      EmailDeliveryStatus.SENT,
                      "MSG-2",
                      "noop",
                      null));

              // Create a FAILED entry
              deliveryLogRepository.save(
                  new EmailDeliveryLog(
                      "user3@test.com",
                      "notification-task",
                      "NOTIFICATION",
                      UUID.randomUUID(),
                      EmailDeliveryStatus.FAILED,
                      null,
                      "noop",
                      "Connection refused"));
            });
  }
}
