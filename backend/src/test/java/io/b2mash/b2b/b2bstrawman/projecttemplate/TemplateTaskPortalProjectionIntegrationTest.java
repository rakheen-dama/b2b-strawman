package io.b2mash.b2b.b2bstrawman.projecttemplate;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalTaskView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.InstantiateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G88-F19 (Phase 89 633A, §14.3.13, B5): a matter instantiated from a legal-za template on a
 * client-linked matter projects every template task into {@code portal.portal_tasks}, in template
 * order (the template task's {@code sortOrder}), with the matter lead's name on PROJECT_LEAD tasks.
 * At HEAD before 633A, {@code instantiateTemplate} published no portal task event, so the portal
 * showed only tasks that had since been edited ("2 of 9").
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemplateTaskPortalProjectionIntegrationTest {

  private static final String ORG_ID = "org_tmpl_task_portal_projection";
  private static final String ORG_NAME = "Template Task Portal Projection Firm";
  private static final String TEMPLATE_NAME = "Litigation (Personal Injury / General)";
  private static final String LEAD_NAME = "Projection Lead Owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProjectTemplateService templateService;
  @Autowired private ProjectTemplateRepository templateRepository;
  @Autowired private TemplateTaskRepository templateTaskRepository;
  @Autowired private CustomerService customerService;
  @Autowired private PortalReadModelRepository readModelRepo;
  @Autowired private PlatformTransactionManager transactionManager;

  private String tenantSchema;
  private UUID memberId;
  private UUID templateId;
  private List<TemplateTask> templateTasks;
  private TransactionTemplate tenantTx;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, ORG_NAME, "legal-za");
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_tmpl_task_portal_owner",
                "tmpl_task_portal_owner@test.com",
                LEAD_NAME,
                "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    tenantTx = new TransactionTemplate(transactionManager);
    templateId =
        inTenant(
            () ->
                templateRepository.findAll().stream()
                    .filter(t -> TEMPLATE_NAME.equals(t.getName()))
                    .findFirst()
                    .orElseThrow()
                    .getId());
    templateTasks =
        inTenant(() -> templateTaskRepository.findByTemplateIdOrderBySortOrder(templateId));
    assertThat(templateTasks).hasSize(9);
  }

  @Test
  void instantiatingOnAClientLinkedMatterProjectsEveryTemplateTaskInTemplateOrder() {
    UUID projectId =
        inTenant(
            () ->
                tenantTx.execute(
                    s -> {
                      var customer =
                          customerService.createCustomer(
                              "Projection Client",
                              "projection-client@test.com",
                              null,
                              null,
                              null,
                              memberId);
                      return templateService
                          .instantiateTemplate(
                              templateId,
                              new InstantiateTemplateRequest(
                                  "Projection Matter",
                                  customer.getId(),
                                  memberId,
                                  "G88-F19 reproduction",
                                  null,
                                  null,
                                  null),
                              memberId)
                          .getId();
                    }));

    // AFTER_COMMIT: PortalEventHandler.onTaskCreated has run by now.
    List<PortalTaskView> rows = readModelRepo.findTasksByProject(projectId, ORG_ID);

    assertThat(rows)
        .extracting(PortalTaskView::name)
        .containsExactlyElementsOf(templateTasks.stream().map(TemplateTask::getName).toList());
    assertThat(rows)
        .extracting(PortalTaskView::sortOrder)
        .containsExactlyElementsOf(templateTasks.stream().map(TemplateTask::getSortOrder).toList());
    assertThat(rows).extracting(PortalTaskView::status).containsOnly("OPEN");
    for (int i = 0; i < templateTasks.size(); i++) {
      String expectedAssignee =
          "PROJECT_LEAD".equals(templateTasks.get(i).getAssigneeRole()) ? LEAD_NAME : null;
      assertThat(rows.get(i).assigneeName()).isEqualTo(expectedAssignee);
    }
  }

  @Test
  void instantiatingWithoutAClientProjectsNoTasks() {
    UUID projectId =
        inTenant(
            () ->
                tenantTx.execute(
                    s ->
                        templateService
                            .instantiateTemplate(
                                templateId,
                                new InstantiateTemplateRequest(
                                    "Unlinked Projection Matter",
                                    null,
                                    memberId,
                                    null,
                                    null,
                                    null,
                                    null),
                                memberId)
                            .getId()));

    assertThat(readModelRepo.findTasksByProject(projectId, ORG_ID)).isEmpty();
  }

  private <T> T inTenant(Callable<T> action) {
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .where(RequestScopes.ORG_ID, ORG_ID)
          .where(RequestScopes.MEMBER_ID, memberId)
          .call(action::call);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
