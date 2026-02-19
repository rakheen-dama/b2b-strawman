package io.b2mash.b2b.b2bstrawman.setupstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.billingrate.BillingRate;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateRepository;
import io.b2mash.b2b.b2bstrawman.budget.ProjectBudget;
import io.b2mash.b2b.b2bstrawman.budget.ProjectBudgetRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectSetupStatusServiceTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID MEMBER_ID = UUID.randomUUID();

  @Mock private ProjectRepository projectRepository;
  @Mock private CustomerProjectRepository customerProjectRepository;
  @Mock private BillingRateRepository billingRateRepository;
  @Mock private ProjectBudgetRepository projectBudgetRepository;
  @Mock private ProjectMemberRepository projectMemberRepository;
  @Mock private FieldDefinitionRepository fieldDefinitionRepository;

  @InjectMocks private ProjectSetupStatusService service;

  @Test
  void getSetupStatus_throwsWhenProjectNotFound() {
    when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getSetupStatus(PROJECT_ID))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void getSetupStatus_allFalse_completionTwenty() {
    mockProjectExists();
    when(customerProjectRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());
    when(billingRateRepository.findByFilters(null, PROJECT_ID, null)).thenReturn(List.of());
    when(billingRateRepository.existsByProjectIdIsNullAndCustomerIdIsNull()).thenReturn(false);
    when(projectBudgetRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
    when(projectMemberRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of());

    var result = service.getSetupStatus(PROJECT_ID);

    assertThat(result.projectId()).isEqualTo(PROJECT_ID);
    assertThat(result.customerAssigned()).isFalse();
    assertThat(result.rateCardConfigured()).isFalse();
    assertThat(result.budgetConfigured()).isFalse();
    assertThat(result.teamAssigned()).isFalse();
    // No required fields = fields check passes → 1 pass → 20%
    assertThat(result.completionPercentage()).isEqualTo(20);
    assertThat(result.overallComplete()).isFalse();
  }

  @Test
  void getSetupStatus_allTrue_completionHundred() {
    mockProjectExists();
    mockCustomerAssigned();
    mockRateCardProjectLevel();
    mockBudgetExists();
    mockTeamOf2();
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of());

    var result = service.getSetupStatus(PROJECT_ID);

    assertThat(result.customerAssigned()).isTrue();
    assertThat(result.rateCardConfigured()).isTrue();
    assertThat(result.budgetConfigured()).isTrue();
    assertThat(result.teamAssigned()).isTrue();
    assertThat(result.completionPercentage()).isEqualTo(100);
    assertThat(result.overallComplete()).isTrue();
  }

  @Test
  void getSetupStatus_customerAssigned_true() {
    mockProjectExists();
    mockCustomerAssigned();
    when(billingRateRepository.findByFilters(null, PROJECT_ID, null)).thenReturn(List.of());
    when(billingRateRepository.existsByProjectIdIsNullAndCustomerIdIsNull()).thenReturn(false);
    when(projectBudgetRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
    when(projectMemberRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of());

    var result = service.getSetupStatus(PROJECT_ID);

    assertThat(result.customerAssigned()).isTrue();
    assertThat(result.completionPercentage()).isEqualTo(40); // customer + no required fields
  }

  @Test
  void getSetupStatus_rateCard_configuredViaOrgDefault() {
    mockProjectExists();
    when(customerProjectRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());
    when(billingRateRepository.findByFilters(null, PROJECT_ID, null)).thenReturn(List.of());
    when(billingRateRepository.existsByProjectIdIsNullAndCustomerIdIsNull()).thenReturn(true);
    when(projectBudgetRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
    when(projectMemberRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of());

    var result = service.getSetupStatus(PROJECT_ID);

    assertThat(result.rateCardConfigured()).isTrue();
  }

  @Test
  void getSetupStatus_rateCard_configuredViaProjectLevel() {
    mockProjectExists();
    when(customerProjectRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());
    mockRateCardProjectLevel();
    when(projectBudgetRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
    when(projectMemberRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of());

    var result = service.getSetupStatus(PROJECT_ID);

    assertThat(result.rateCardConfigured()).isTrue();
  }

  @Test
  void getSetupStatus_teamAssigned_falseWhen1Member() {
    mockProjectExists();
    when(customerProjectRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());
    when(billingRateRepository.findByFilters(null, PROJECT_ID, null)).thenReturn(List.of());
    when(billingRateRepository.existsByProjectIdIsNullAndCustomerIdIsNull()).thenReturn(false);
    when(projectBudgetRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
    when(projectMemberRepository.findByProjectId(PROJECT_ID))
        .thenReturn(List.of(mock(ProjectMember.class)));
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of());

    var result = service.getSetupStatus(PROJECT_ID);

    assertThat(result.teamAssigned()).isFalse();
  }

  @Test
  void getSetupStatus_teamAssigned_trueWhen2Members() {
    mockProjectExists();
    when(customerProjectRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());
    when(billingRateRepository.findByFilters(null, PROJECT_ID, null)).thenReturn(List.of());
    when(billingRateRepository.existsByProjectIdIsNullAndCustomerIdIsNull()).thenReturn(false);
    when(projectBudgetRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
    mockTeamOf2();
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of());

    var result = service.getSetupStatus(PROJECT_ID);

    assertThat(result.teamAssigned()).isTrue();
  }

  @Test
  void getSetupStatus_requiredFields_filledAndUnfilled() {
    var project = new Project("Test", "Desc", MEMBER_ID);
    project.setCustomFields(Map.of("engagement_type", "audit", "risk_rating", ""));
    when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
    when(customerProjectRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());
    when(billingRateRepository.findByFilters(null, PROJECT_ID, null)).thenReturn(List.of());
    when(billingRateRepository.existsByProjectIdIsNullAndCustomerIdIsNull()).thenReturn(false);
    when(projectBudgetRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
    when(projectMemberRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());

    var engagementTypeDef = makeRequiredField("Engagement Type", "engagement_type");
    var riskRatingDef = makeRequiredField("Risk Rating", "risk_rating");
    var taxNumberDef = makeRequiredField("Tax Number", "sars_tax_number");
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of(engagementTypeDef, riskRatingDef, taxNumberDef));

    var result = service.getSetupStatus(PROJECT_ID);

    assertThat(result.requiredFields().total()).isEqualTo(3);
    assertThat(result.requiredFields().filled()).isEqualTo(1); // only engagement_type is non-blank
    assertThat(result.requiredFields().fields()).hasSize(3);
    assertThat(result.requiredFields().fields().get(0).filled()).isTrue(); // engagement_type
    assertThat(result.requiredFields().fields().get(1).filled()).isFalse(); // risk_rating = ""
    assertThat(result.requiredFields().fields().get(2).filled()).isFalse(); // sars_tax_number null
  }

  @Test
  void getSetupStatus_requiredFields_allFilled_countAsPass() {
    var project = new Project("Test", "Desc", MEMBER_ID);
    project.setCustomFields(Map.of("engagement_type", "audit"));
    when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
    when(customerProjectRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());
    when(billingRateRepository.findByFilters(null, PROJECT_ID, null)).thenReturn(List.of());
    when(billingRateRepository.existsByProjectIdIsNullAndCustomerIdIsNull()).thenReturn(false);
    when(projectBudgetRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
    when(projectMemberRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of(makeRequiredField("Engagement Type", "engagement_type")));

    var result = service.getSetupStatus(PROJECT_ID);

    assertThat(result.requiredFields().filled()).isEqualTo(1);
    assertThat(result.requiredFields().total()).isEqualTo(1);
    // Fields check passes: 1 pass -> 20%
    assertThat(result.completionPercentage()).isEqualTo(20);
  }

  @Test
  void getSetupStatus_noRequiredFields_countAsPass() {
    mockProjectExists();
    when(customerProjectRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());
    when(billingRateRepository.findByFilters(null, PROJECT_ID, null)).thenReturn(List.of());
    when(billingRateRepository.existsByProjectIdIsNullAndCustomerIdIsNull()).thenReturn(false);
    when(projectBudgetRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
    when(projectMemberRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of());

    var result = service.getSetupStatus(PROJECT_ID);

    assertThat(result.requiredFields().total()).isEqualTo(0);
    assertThat(result.requiredFields().filled()).isEqualTo(0);
    assertThat(result.completionPercentage()).isEqualTo(20);
  }

  // --- Helpers ---

  private void mockProjectExists() {
    var project = new Project("Test Project", "Desc", MEMBER_ID);
    when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
  }

  private void mockCustomerAssigned() {
    var cp = mock(CustomerProject.class);
    when(customerProjectRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(cp));
  }

  private void mockRateCardProjectLevel() {
    var rate = mock(BillingRate.class);
    when(billingRateRepository.findByFilters(null, PROJECT_ID, null)).thenReturn(List.of(rate));
  }

  private void mockBudgetExists() {
    var budget = mock(ProjectBudget.class);
    when(projectBudgetRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(budget));
  }

  private void mockTeamOf2() {
    var m1 = mock(ProjectMember.class);
    var m2 = mock(ProjectMember.class);
    when(projectMemberRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(m1, m2));
  }

  private FieldDefinition makeRequiredField(String name, String slug) {
    var fd = new FieldDefinition(EntityType.PROJECT, name, slug, FieldType.TEXT);
    fd.updateMetadata(name, null, true, null);
    return fd;
  }
}
