package io.b2mash.b2b.b2bstrawman.setupstatus;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocumentGenerationReadinessControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_doc_readiness_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID projectWithCustomerId;
  private UUID projectNoCustomerId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Doc Readiness Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_doc_readiness_owner",
                "doc_readiness_owner@test.com",
                "Doc Readiness Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Save a PROJECT template
                      var template =
                          new DocumentTemplate(
                              TemplateEntityType.PROJECT,
                              "Engagement Letter",
                              "engagement-letter",
                              TemplateCategory.ENGAGEMENT_LETTER,
                              "<p>Hello [[${project.name}]]</p>");
                      documentTemplateRepository.save(template);

                      // Project WITH linked customer
                      var projectWithCust =
                          new Project("Project With Customer", null, memberIdOwner);
                      projectWithCust = projectRepository.save(projectWithCust);
                      var customer =
                          new Customer(
                              "Acme Corp", "acme@test.com", null, null, null, memberIdOwner);
                      customer = customerRepository.save(customer);
                      entityManager.flush();
                      var link =
                          new CustomerProject(
                              customer.getId(), projectWithCust.getId(), memberIdOwner);
                      customerProjectRepository.save(link);
                      projectWithCustomerId = projectWithCust.getId();

                      // Project WITHOUT linked customer
                      var projectNoCust = new Project("No Customer Project", null, memberIdOwner);
                      projectNoCust = projectRepository.save(projectNoCust);
                      projectNoCustomerId = projectNoCust.getId();

                      entityManager.flush();
                    }));
  }

  @Test
  @Order(1)
  void getReadiness_returns200_withExpectedShape() throws Exception {
    mockMvc
        .perform(
            get("/api/templates/readiness")
                .param("entityType", "PROJECT")
                .param("entityId", projectWithCustomerId.toString())
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].templateId").exists())
        .andExpect(jsonPath("$[0].templateName").value("Engagement Letter"))
        .andExpect(jsonPath("$[0].templateSlug").value("engagement-letter"))
        .andExpect(jsonPath("$[0].ready").isBoolean())
        .andExpect(jsonPath("$[0].missingFields").isArray());
  }

  @Test
  @Order(2)
  void getReadiness_projectWithCustomer_templateReady() throws Exception {
    mockMvc
        .perform(
            get("/api/templates/readiness")
                .param("entityType", "PROJECT")
                .param("entityId", projectWithCustomerId.toString())
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].ready").value(true))
        .andExpect(jsonPath("$[0].missingFields").isEmpty());
  }

  @Test
  @Order(3)
  void getReadiness_projectWithoutCustomer_templateNotReady() throws Exception {
    mockMvc
        .perform(
            get("/api/templates/readiness")
                .param("entityType", "PROJECT")
                .param("entityId", projectNoCustomerId.toString())
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].ready").value(false))
        .andExpect(jsonPath("$[0].missingFields[0]").value("Customer"));
  }

  @Test
  @Order(4)
  void getReadiness_withoutAuth_returns401() throws Exception {
    mockMvc
        .perform(
            get("/api/templates/readiness")
                .param("entityType", "PROJECT")
                .param("entityId", projectWithCustomerId.toString()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @Order(5)
  void getReadiness_invalidEntityType_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/templates/readiness")
                .param("entityType", "INVALID_TYPE")
                .param("entityId", projectWithCustomerId.toString())
                .with(ownerJwt()))
        .andExpect(status().isBadRequest());
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_doc_readiness_owner")
                    .claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
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
