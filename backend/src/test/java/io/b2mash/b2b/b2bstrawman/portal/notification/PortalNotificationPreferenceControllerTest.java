package io.b2mash.b2b.b2bstrawman.portal.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration coverage for {@link PortalNotificationPreferenceController} (Epic 498C). Exercises
 * the GET/PUT round-trip over the full CustomerAuthFilter → controller → service → repository
 * stack.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PortalNotificationPreferenceControllerTest {

  private static final String ORG_ID = "org_portal_notif_controller_test";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerService customerService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String portalToken;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Notif Controller Test Org", null);

    var syncResult =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "user_notif_owner",
                          "email": "notif_owner@test.com",
                          "name": "Notif Owner",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_ID)))
            .andReturn();

    String memberIdStr = JsonPath.read(syncResult.getResponse().getContentAsString(), "$.memberId");
    UUID memberId = UUID.fromString(memberIdStr);

    String tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();

    final UUID[] customerIdHolder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Notif Test Customer", "notif-customer@test.com", null, null, null, memberId);
              customerIdHolder[0] = customer.getId();

              portalContactService.createContact(
                  ORG_ID,
                  customer.getId(),
                  "notif-contact@test.com",
                  "Notif Contact",
                  PortalContact.ContactRole.PRIMARY);
            });

    portalToken = portalJwtService.issueToken(customerIdHolder[0], ORG_ID);
  }

  @Test
  @Order(1)
  void get_returnsAllDefaultsTrueForFirstCall() throws Exception {
    mockMvc
        .perform(
            get("/portal/notification-preferences")
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.digestEnabled").value(true))
        .andExpect(jsonPath("$.trustActivityEnabled").value(true))
        .andExpect(jsonPath("$.retainerUpdatesEnabled").value(true))
        .andExpect(jsonPath("$.deadlineRemindersEnabled").value(true))
        .andExpect(jsonPath("$.actionRequiredEnabled").value(true))
        .andExpect(jsonPath("$.firmDigestCadence").value("WEEKLY"));
  }

  @Test
  @Order(2)
  void put_persistsToggleChangesAndRoundTrips() throws Exception {
    String body =
        """
        {
          "digestEnabled": false,
          "trustActivityEnabled": false,
          "retainerUpdatesEnabled": true,
          "deadlineRemindersEnabled": false,
          "actionRequiredEnabled": true
        }
        """;

    mockMvc
        .perform(
            put("/portal/notification-preferences")
                .header("Authorization", "Bearer " + portalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.digestEnabled").value(false))
        .andExpect(jsonPath("$.trustActivityEnabled").value(false))
        .andExpect(jsonPath("$.retainerUpdatesEnabled").value(true))
        .andExpect(jsonPath("$.deadlineRemindersEnabled").value(false))
        .andExpect(jsonPath("$.actionRequiredEnabled").value(true));

    mockMvc
        .perform(
            get("/portal/notification-preferences")
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.digestEnabled").value(false))
        .andExpect(jsonPath("$.deadlineRemindersEnabled").value(false))
        .andExpect(jsonPath("$.actionRequiredEnabled").value(true));
  }

  @Test
  @Order(3)
  void get_returns401WithoutToken() throws Exception {
    mockMvc.perform(get("/portal/notification-preferences")).andExpect(status().isUnauthorized());
  }

  @Test
  @Order(4)
  void put_returns401WithoutToken() throws Exception {
    mockMvc
        .perform(
            put("/portal/notification-preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"digestEnabled":true,"trustActivityEnabled":true,"retainerUpdatesEnabled":true,"deadlineRemindersEnabled":true,"actionRequiredEnabled":true}
                    """))
        .andExpect(status().isUnauthorized());
  }
}
