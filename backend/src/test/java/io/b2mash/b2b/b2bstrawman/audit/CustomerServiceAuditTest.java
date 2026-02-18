package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests verifying audit events produced by {@code CustomerService} and {@code
 * CustomerProjectService} operations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerServiceAuditTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_cust_audit_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private AuditService auditService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String schemaName;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Customer Audit Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    schemaName =
        provisioningService.provisionTenant(ORG_ID, "Customer Audit Test Org").schemaName();

    syncMember(ORG_ID, "user_ca_owner", "ca_owner@test.com", "CA Owner", "owner");
  }

  @Test
  void createCustomerProducesAuditEvent() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Audit Cust", "email": "audit-cust@test.com", "phone": "555-0100", "idNumber": "ID001", "notes": "test"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var customerId = UUID.fromString(extractIdFromLocation(result));

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "customer", customerId, null, "customer.created", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("customer.created");
              assertThat(event.getEntityType()).isEqualTo("customer");
              assertThat(event.getEntityId()).isEqualTo(customerId);
              assertThat(event.getDetails()).containsEntry("name", "Audit Cust");
              assertThat(event.getDetails()).containsEntry("email", "audit-cust@test.com");
            });
  }

  @Test
  void updateCustomerCapturesFieldDeltas() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Old Name", "email": "old-email@test.com", "phone": "111", "idNumber": "OLD-ID", "notes": "old notes"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var customerId = UUID.fromString(extractIdFromLocation(createResult));

    mockMvc
        .perform(
            put("/api/customers/" + customerId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "New Name", "email": "new-email@test.com", "phone": "222", "idNumber": "NEW-ID", "notes": "new notes"}
                    """))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "customer", customerId, null, "customer.updated", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("customer.updated");

              @SuppressWarnings("unchecked")
              var nameChange = (Map<String, Object>) event.getDetails().get("name");
              assertThat(nameChange).containsEntry("from", "Old Name");
              assertThat(nameChange).containsEntry("to", "New Name");

              @SuppressWarnings("unchecked")
              var emailChange = (Map<String, Object>) event.getDetails().get("email");
              assertThat(emailChange).containsEntry("from", "old-email@test.com");
              assertThat(emailChange).containsEntry("to", "new-email@test.com");
            });
  }

  @Test
  void archiveCustomerProducesAuditEvent() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Archive Me", "email": "archive@test.com", "phone": null, "idNumber": null, "notes": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var customerId = UUID.fromString(extractIdFromLocation(createResult));

    // Archive the customer (DELETE endpoint archives, doesn't truly delete)
    mockMvc
        .perform(delete("/api/customers/" + customerId).with(ownerJwt()))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "customer", customerId, null, "customer.archived", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("customer.archived");
              assertThat(event.getEntityId()).isEqualTo(customerId);
            });
  }

  @Test
  void linkCustomerToProjectProducesAuditEvent() throws Exception {
    // Create a project first
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Link Test Project", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId =
        UUID.fromString(
            projectResult
                .getResponse()
                .getHeader("Location")
                .substring(projectResult.getResponse().getHeader("Location").lastIndexOf('/') + 1));

    // Create a customer
    var customerResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Link Cust", "email": "link-cust@test.com", "phone": null, "idNumber": null, "notes": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var customerId = UUID.fromString(extractIdFromLocation(customerResult));

    // Transition customer to ACTIVE so linking is permitted by lifecycle guard
    transitionToActive(customerId);

    // Link customer to project
    mockMvc
        .perform(post("/api/customers/" + customerId + "/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isCreated());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "customer", customerId, null, "customer.linked", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("customer.linked");
              assertThat(event.getEntityId()).isEqualTo(customerId);
              assertThat(event.getDetails()).containsEntry("project_id", projectId.toString());
            });
  }

  @Test
  void unlinkCustomerFromProjectProducesAuditEvent() throws Exception {
    // Create a project
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Unlink Test Project", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId =
        UUID.fromString(
            projectResult
                .getResponse()
                .getHeader("Location")
                .substring(projectResult.getResponse().getHeader("Location").lastIndexOf('/') + 1));

    // Create a customer
    var customerResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Unlink Cust", "email": "unlink-cust@test.com", "phone": null, "idNumber": null, "notes": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var customerId = UUID.fromString(extractIdFromLocation(customerResult));

    // Transition customer to ACTIVE so linking is permitted by lifecycle guard
    transitionToActive(customerId);

    // Link then unlink
    mockMvc
        .perform(post("/api/customers/" + customerId + "/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isCreated());

    mockMvc
        .perform(delete("/api/customers/" + customerId + "/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "customer", customerId, null, "customer.unlinked", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("customer.unlinked");
              assertThat(event.getEntityId()).isEqualTo(customerId);
              assertThat(event.getDetails()).containsEntry("project_id", projectId.toString());
            });
  }

  // --- Helpers ---

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

  /** No-op: customers now default to ACTIVE lifecycle status. */
  private void transitionToActive(UUID customerId) throws Exception {
    // Customers default to ACTIVE — no transition needed
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ca_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
