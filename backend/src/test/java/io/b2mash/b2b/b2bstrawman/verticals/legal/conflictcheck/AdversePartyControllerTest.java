package io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdversePartyControllerTest {
  private static final String ORG_ID = "org_adverse_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private AdversePartyRepository adversePartyRepository;
  @Autowired private AdversePartyLinkRepository adversePartyLinkRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Adverse Controller Test Org", null)
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_adverse_ctrl_owner",
                "adverse_ctrl@test.com",
                "Adverse Ctrl Owner",
                "owner"));
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_adverse_ctrl_member",
        "adverse_ctrl_member@test.com",
        "Adverse Ctrl Member",
        "member");

    // Enable the conflict_check module
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("conflict_check"));
                      orgSettingsRepository.save(settings);
                    }));

    // Create test customer and project
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          customerRepository.saveAndFlush(
                              createActiveCustomer(
                                  "Adverse Ctrl Corp", "adverse_ctrl_test@test.com", memberId));
                      customerId = customer.getId();

                      var project =
                          new Project("Controller Test Matter", "Controller test", memberId);
                      project.setCustomerId(customerId);
                      project = projectRepository.saveAndFlush(project);
                      projectId = project.getId();
                    }));
  }

  @Test
  void postAdverseParty_returns201WithCreatedParty() throws Exception {
    mockMvc
        .perform(
            post("/api/adverse-parties")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_adverse_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Jane Smith",
                      "idNumber": "9001015800083",
                      "partyType": "NATURAL_PERSON",
                      "aliases": "J Smith",
                      "notes": "Created via controller test"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Jane Smith"))
        .andExpect(jsonPath("$.partyType").value("NATURAL_PERSON"))
        .andExpect(jsonPath("$.idNumber").value("9001015800083"))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  void getAdverseParties_withSearch_returnsFuzzyMatches() throws Exception {
    // Seed a party with a known name
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx ->
                        adversePartyRepository.save(
                            new AdverseParty(
                                "Springbok Industries (Pty) Ltd",
                                null,
                                "2020/654321/07",
                                "COMPANY",
                                null,
                                null))));

    mockMvc
        .perform(
            get("/api/adverse-parties")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_adverse_ctrl_owner"))
                .param("search", "Springbok Industries"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].name").value("Springbok Industries (Pty) Ltd"));
  }

  @Test
  void deleteAdverseParty_withActiveLinks_returns409() throws Exception {
    // Seed a party with a link
    var partyId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var party =
                          adversePartyRepository.save(
                              new AdverseParty("Linked Corp", null, null, "COMPANY", null, null));
                      partyId[0] = party.getId();

                      adversePartyLinkRepository.save(
                          new AdversePartyLink(
                              party.getId(), projectId, customerId, "OPPOSING_PARTY", "Test link"));
                    }));

    mockMvc
        .perform(
            delete("/api/adverse-parties/" + partyId[0])
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_adverse_ctrl_owner")))
        .andExpect(status().isConflict());
  }

  @Test
  void postLink_createsRelationship() throws Exception {
    // Seed a party
    var partyId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var party =
                          adversePartyRepository.save(
                              new AdverseParty("Linkable Corp", null, null, "COMPANY", null, null));
                      partyId[0] = party.getId();
                    }));

    mockMvc
        .perform(
            post("/api/adverse-parties/" + partyId[0] + "/links")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_adverse_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "projectId": "%s",
                      "customerId": "%s",
                      "relationship": "WITNESS",
                      "description": "Key witness in matter"
                    }
                    """
                        .formatted(projectId, customerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.adversePartyName").value("Linkable Corp"))
        .andExpect(jsonPath("$.relationship").value("WITNESS"))
        .andExpect(jsonPath("$.projectName").value("Controller Test Matter"));
  }

  @Test
  void getProjectAdverseParties_returnsLinkedParties() throws Exception {
    // Seed a party with a link
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var party =
                          adversePartyRepository.save(
                              new AdverseParty(
                                  "Project Linked Corp", null, null, "COMPANY", null, null));

                      adversePartyLinkRepository.save(
                          new AdversePartyLink(
                              party.getId(), projectId, customerId, "GUARANTOR", "Surety"));
                    }));

    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/adverse-parties")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_adverse_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[?(@.relationship == 'GUARANTOR')]").isNotEmpty());
  }

  @Test
  void deleteAdverseParty_withMemberRole_returns403() throws Exception {
    // Seed a party (no links — to confirm it's auth, not conflict)
    var partyId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var party =
                          adversePartyRepository.save(
                              new AdverseParty(
                                  "Auth Test Corp", null, null, "COMPANY", null, null));
                      partyId[0] = party.getId();
                    }));

    mockMvc
        .perform(
            delete("/api/adverse-parties/" + partyId[0])
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_adverse_ctrl_member")))
        .andExpect(status().isForbidden());
  }
}
