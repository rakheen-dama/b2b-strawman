package io.b2mash.b2b.b2bstrawman.projecttemplate;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestItemRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestTemplate;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestTemplateItem;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestTemplateItemRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestTemplateRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.ResponseType;
import io.b2mash.b2b.b2bstrawman.informationrequest.TemplateSource;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.InstantiateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
class AutoDraftInformationRequestTest {
  private static final String ORG_ID = "org_autodraft_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ProjectTemplateService templateService;
  @Autowired private ProjectTemplateRepository templateRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private RequestTemplateRepository requestTemplateRepository;
  @Autowired private RequestTemplateItemRepository requestTemplateItemRepository;
  @Autowired private InformationRequestRepository informationRequestRepository;
  @Autowired private RequestItemRepository requestItemRepository;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Auto-Draft Test Org", null);
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_autodraft_owner",
                "autodraft_owner@test.com",
                "Owner",
                "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void instantiate_withRequestTemplateId_createsDraftRequest() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var requestTemplate = createRequestTemplateWithItems();
                  var customer =
                      customerRepository.saveAndFlush(
                          createActiveCustomer("Draft Customer", "draft_cust@test.com", memberId));
                  portalContactRepository.saveAndFlush(
                      new PortalContact(
                          ORG_ID,
                          customer.getId(),
                          "primary@test.com",
                          "Primary Contact",
                          PortalContact.ContactRole.PRIMARY));

                  var template =
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "Auto-Draft Template",
                              "{customer}",
                              null,
                              true,
                              "MANUAL",
                              null,
                              memberId));
                  template.setRequestTemplateId(requestTemplate.getId());
                  templateRepository.saveAndFlush(template);

                  var request = new InstantiateTemplateRequest(null, customer.getId(), null, null);
                  var project =
                      templateService.instantiateTemplate(template.getId(), request, memberId);

                  var drafts = informationRequestRepository.findByProjectId(project.getId());
                  assertThat(drafts).hasSize(1);
                  assertThat(drafts.get(0).getStatus().name()).isEqualTo("DRAFT");
                }));
  }

  @Test
  void instantiate_draftHasCorrectCustomerProjectContact() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var requestTemplate = createRequestTemplateWithItems();
                  var customer =
                      customerRepository.saveAndFlush(
                          createActiveCustomer(
                              "Verify Customer", "verify_cust@test.com", memberId));
                  var contact =
                      portalContactRepository.saveAndFlush(
                          new PortalContact(
                              ORG_ID,
                              customer.getId(),
                              "verify_primary@test.com",
                              "Verify Primary",
                              PortalContact.ContactRole.PRIMARY));

                  var template =
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "Verify Template",
                              "{customer}",
                              null,
                              true,
                              "MANUAL",
                              null,
                              memberId));
                  template.setRequestTemplateId(requestTemplate.getId());
                  templateRepository.saveAndFlush(template);

                  var request = new InstantiateTemplateRequest(null, customer.getId(), null, null);
                  var project =
                      templateService.instantiateTemplate(template.getId(), request, memberId);

                  var drafts = informationRequestRepository.findByProjectId(project.getId());
                  assertThat(drafts).hasSize(1);
                  var draft = drafts.get(0);
                  assertThat(draft.getCustomerId()).isEqualTo(customer.getId());
                  assertThat(draft.getProjectId()).isEqualTo(project.getId());
                  assertThat(draft.getPortalContactId()).isEqualTo(contact.getId());
                }));
  }

  @Test
  void instantiate_draftHasItemsCopiedFromRequestTemplate() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var requestTemplate = createRequestTemplateWithItems();
                  var customer =
                      customerRepository.saveAndFlush(
                          createActiveCustomer("Items Customer", "items_cust@test.com", memberId));
                  portalContactRepository.saveAndFlush(
                      new PortalContact(
                          ORG_ID,
                          customer.getId(),
                          "items_primary@test.com",
                          "Items Primary",
                          PortalContact.ContactRole.PRIMARY));

                  var template =
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "Items Template",
                              "{customer}",
                              null,
                              true,
                              "MANUAL",
                              null,
                              memberId));
                  template.setRequestTemplateId(requestTemplate.getId());
                  templateRepository.saveAndFlush(template);

                  var request = new InstantiateTemplateRequest(null, customer.getId(), null, null);
                  var project =
                      templateService.instantiateTemplate(template.getId(), request, memberId);

                  var drafts = informationRequestRepository.findByProjectId(project.getId());
                  assertThat(drafts).hasSize(1);
                  var items = requestItemRepository.findByRequestId(drafts.get(0).getId());
                  assertThat(items).hasSize(2);
                  var names = items.stream().map(i -> i.getName()).toList();
                  assertThat(names)
                      .containsExactlyInAnyOrder("Upload ID Document", "Provide Tax Number");
                }));
  }

  @Test
  void instantiate_withoutRequestTemplateId_noDraftCreated() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.saveAndFlush(
                          createActiveCustomer("No RT Customer", "no_rt_cust@test.com", memberId));
                  portalContactRepository.saveAndFlush(
                      new PortalContact(
                          ORG_ID,
                          customer.getId(),
                          "no_rt_primary@test.com",
                          "No RT Primary",
                          PortalContact.ContactRole.PRIMARY));

                  var template =
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "No RT Template",
                              "{customer}",
                              null,
                              true,
                              "MANUAL",
                              null,
                              memberId));
                  // No requestTemplateId set

                  var request = new InstantiateTemplateRequest(null, customer.getId(), null, null);
                  var project =
                      templateService.instantiateTemplate(template.getId(), request, memberId);

                  var drafts = informationRequestRepository.findByProjectId(project.getId());
                  assertThat(drafts).isEmpty();
                }));
  }

  @Test
  void instantiate_withoutCustomer_noDraftCreated() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var requestTemplate = createRequestTemplateWithItems();

                  var template =
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "No Customer Template",
                              "{customer}",
                              null,
                              true,
                              "MANUAL",
                              null,
                              memberId));
                  template.setRequestTemplateId(requestTemplate.getId());
                  templateRepository.saveAndFlush(template);

                  var request = new InstantiateTemplateRequest("No Cust Project", null, null, null);
                  var project =
                      templateService.instantiateTemplate(template.getId(), request, memberId);

                  var drafts = informationRequestRepository.findByProjectId(project.getId());
                  assertThat(drafts).isEmpty();
                }));
  }

  @Test
  void instantiate_customerWithoutPrimaryContact_noDraftCreated() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var requestTemplate = createRequestTemplateWithItems();
                  var customer =
                      customerRepository.saveAndFlush(
                          createActiveCustomer(
                              "No Contact Customer", "no_contact@test.com", memberId));
                  // No portal contact at all

                  var template =
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "No Contact Template",
                              "{customer}",
                              null,
                              true,
                              "MANUAL",
                              null,
                              memberId));
                  template.setRequestTemplateId(requestTemplate.getId());
                  templateRepository.saveAndFlush(template);

                  var request = new InstantiateTemplateRequest(null, customer.getId(), null, null);
                  var project =
                      templateService.instantiateTemplate(template.getId(), request, memberId);

                  var drafts = informationRequestRepository.findByProjectId(project.getId());
                  assertThat(drafts).isEmpty();
                }));
  }

  @Test
  void instantiate_customerWithNonPrimaryContact_noDraftCreated() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var requestTemplate = createRequestTemplateWithItems();
                  var customer =
                      customerRepository.saveAndFlush(
                          createActiveCustomer(
                              "General Contact Customer", "general_contact@test.com", memberId));
                  portalContactRepository.saveAndFlush(
                      new PortalContact(
                          ORG_ID,
                          customer.getId(),
                          "general@test.com",
                          "General Contact",
                          PortalContact.ContactRole.GENERAL));

                  var template =
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "General Contact Template",
                              "{customer}",
                              null,
                              true,
                              "MANUAL",
                              null,
                              memberId));
                  template.setRequestTemplateId(requestTemplate.getId());
                  templateRepository.saveAndFlush(template);

                  var request = new InstantiateTemplateRequest(null, customer.getId(), null, null);
                  var project =
                      templateService.instantiateTemplate(template.getId(), request, memberId);

                  var drafts = informationRequestRepository.findByProjectId(project.getId());
                  assertThat(drafts).isEmpty();
                }));
  }

  @Test
  void update_setsRequestTemplateId_persistedAndReturned() throws Exception {
    // Create a template via API
    var createResult =
        mockMvc
            .perform(
                post("/api/project-templates")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_autodraft_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "RT Update Test",
                          "namePattern": "{customer}",
                          "billableDefault": false,
                          "tasks": [],
                          "tagIds": []
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.requestTemplateId").isEmpty())
            .andReturn();

    String templateId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Create a request template in tenant context to get a valid UUID
    final UUID[] rtIdHolder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var rt =
                          requestTemplateRepository.saveAndFlush(
                              new RequestTemplate("Update RT Test", "desc", TemplateSource.CUSTOM));
                      rtIdHolder[0] = rt.getId();
                    }));

    // Update template with requestTemplateId
    mockMvc
        .perform(
            put("/api/project-templates/" + templateId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_autodraft_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "RT Update Test",
                      "namePattern": "{customer}",
                      "billableDefault": false,
                      "tasks": [],
                      "tagIds": [],
                      "requestTemplateId": "%s"
                    }
                    """
                        .formatted(rtIdHolder[0])))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestTemplateId").value(rtIdHolder[0].toString()));

    // GET should also return the requestTemplateId
    mockMvc
        .perform(
            get("/api/project-templates/" + templateId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_autodraft_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestTemplateId").value(rtIdHolder[0].toString()));
  }

  // --- Helpers ---

  private RequestTemplate createRequestTemplateWithItems() {
    var requestTemplate =
        requestTemplateRepository.saveAndFlush(
            new RequestTemplate("Test Info Template", "Test description", TemplateSource.CUSTOM));
    requestTemplateItemRepository.saveAndFlush(
        new RequestTemplateItem(
            requestTemplate.getId(),
            "Upload ID Document",
            "Please upload your ID",
            ResponseType.FILE_UPLOAD,
            true,
            "pdf,jpg",
            0));
    requestTemplateItemRepository.saveAndFlush(
        new RequestTemplateItem(
            requestTemplate.getId(),
            "Provide Tax Number",
            "Enter your tax registration number",
            ResponseType.TEXT_RESPONSE,
            true,
            null,
            1));
    return requestTemplate;
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
