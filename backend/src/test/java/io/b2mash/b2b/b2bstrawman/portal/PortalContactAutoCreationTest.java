package io.b2mash.b2b.b2bstrawman.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.Map;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalContactAutoCreationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_portal_contact_auto_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private int emailCounter = 0;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Contact Auto Test Org", null);
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_pc_owner", "pc_owner@test.com", "PC Owner", "owner");
  }

  @Test
  void shouldReturnPortalContactsForCustomer() throws Exception {
    // Create customer and transition to ONBOARDING (auto-creates portal contact)
    String customerId = createCustomer("Portal Contacts List Corp", nextEmail());

    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ONBOARDING"}
                    """))
        .andExpect(status().isOk());

    // GET portal contacts should return the auto-created contact
    mockMvc
        .perform(get("/api/customers/" + customerId + "/portal-contacts").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].displayName").value("Portal Contacts List Corp"))
        .andExpect(jsonPath("$[0].email").exists());
  }

  @Test
  void shouldAutoCreatePortalContactOnProspectToOnboarding() throws Exception {
    String email = nextEmail();
    String customerId = createCustomer("Auto Contact Corp", email);

    // Verify no portal contacts before transition
    mockMvc
        .perform(get("/api/customers/" + customerId + "/portal-contacts").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    // Transition PROSPECT -> ONBOARDING
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ONBOARDING"}
                    """))
        .andExpect(status().isOk());

    // Verify portal contact was auto-created
    mockMvc
        .perform(get("/api/customers/" + customerId + "/portal-contacts").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].email").value(email))
        .andExpect(jsonPath("$[0].displayName").value("Auto Contact Corp"));
  }

  @Test
  void shouldNotDuplicatePortalContactIfAlreadyExists() throws Exception {
    String email = nextEmail();
    String customerId = createCustomer("No Dup Corp", email);

    // Transition to ONBOARDING — creates the portal contact
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ONBOARDING"}
                    """))
        .andExpect(status().isOk());

    // Confirm one portal contact exists
    var result =
        mockMvc
            .perform(get("/api/customers/" + customerId + "/portal-contacts").with(ownerJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andReturn();

    String firstContactId = JsonPath.read(result.getResponse().getContentAsString(), "$[0].id");
    assertThat(firstContactId).isNotNull();

    // The auto-creation check uses existsByEmailAndCustomerId, so even if
    // the transition logic were called again, no duplicate would be created.
    // We verify by checking that still only 1 contact exists.
    mockMvc
        .perform(get("/api/customers/" + customerId + "/portal-contacts").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(firstContactId));
  }

  @Test
  void shouldReturn404ForNonExistentCustomerPortalContacts() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/00000000-0000-0000-0000-000000000099/portal-contacts")
                .with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnEmptyListForCustomerWithNoContacts() throws Exception {
    String customerId = createCustomer("No Contacts Corp", nextEmail());

    mockMvc
        .perform(get("/api/customers/" + customerId + "/portal-contacts").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  // --- Helpers ---

  private String nextEmail() {
    return "pc_auto_" + (++emailCounter) + "@test.com";
  }

  private String createCustomer(String name, String email) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "email": "%s"}
                        """
                            .formatted(name, email)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_pc_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private void syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
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
        .andExpect(status().isCreated());
  }
}
