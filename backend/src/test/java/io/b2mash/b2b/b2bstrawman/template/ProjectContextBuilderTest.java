package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.budget.ProjectBudget;
import io.b2mash.b2b.b2bstrawman.budget.ProjectBudgetRepository;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberInfo;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService.PresignedDownloadResult;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.tag.EntityTag;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagRepository;
import io.b2mash.b2b.b2bstrawman.tag.Tag;
import io.b2mash.b2b.b2bstrawman.tag.TagRepository;
import java.math.BigDecimal;
import java.time.Instant;
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
class ProjectContextBuilderTest {

  @Mock private ProjectRepository projectRepository;
  @Mock private CustomerRepository customerRepository;
  @Mock private CustomerProjectRepository customerProjectRepository;
  @Mock private ProjectMemberRepository projectMemberRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private ProjectBudgetRepository projectBudgetRepository;
  @Mock private EntityTagRepository entityTagRepository;
  @Mock private TagRepository tagRepository;
  @Mock private OrgSettingsRepository orgSettingsRepository;
  @Mock private S3PresignedUrlService s3PresignedUrlService;

  @InjectMocks private ProjectContextBuilder builder;

  private final UUID projectId = UUID.randomUUID();
  private final UUID memberId = UUID.randomUUID();
  private final UUID customerId = UUID.randomUUID();

  @Test
  void supportsProjectEntityType() {
    assertThat(builder.supports()).isEqualTo(TemplateEntityType.PROJECT);
  }

  @Test
  void buildContextWithCustomerAndBudget() {
    var project = new Project("Test Project", "A test project", memberId);
    when(projectRepository.findOneById(projectId)).thenReturn(Optional.of(project));

    var cp = new CustomerProject(customerId, projectId, memberId);
    when(customerProjectRepository.findByProjectId(projectId)).thenReturn(List.of(cp));

    var customer = new Customer("Test Customer", "test@example.com", "123", null, null, memberId);
    when(customerRepository.findOneById(customerId)).thenReturn(Optional.of(customer));

    when(projectMemberRepository.findProjectMembersWithDetails(projectId)).thenReturn(List.of());

    var budget =
        new ProjectBudget(
            projectId, BigDecimal.valueOf(100), BigDecimal.valueOf(5000), "USD", 80, null);
    when(projectBudgetRepository.findByProjectId(projectId)).thenReturn(Optional.of(budget));

    when(entityTagRepository.findByEntityTypeAndEntityId("PROJECT", projectId))
        .thenReturn(List.of());
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());

    var member = new Member("clerk_user", "user@test.com", "Test User", null, "admin");
    when(memberRepository.findOneById(memberId)).thenReturn(Optional.of(member));

    var context = builder.buildContext(projectId, memberId);

    assertThat(context).containsKey("project");
    assertThat(context).containsKey("customer");
    assertThat(context).containsKey("budget");
    assertThat(context).containsKey("generatedAt");
    assertThat(context).containsKey("generatedBy");

    @SuppressWarnings("unchecked")
    var projectMap = (Map<String, Object>) context.get("project");
    assertThat(projectMap.get("name")).isEqualTo("Test Project");

    @SuppressWarnings("unchecked")
    var customerMap = (Map<String, Object>) context.get("customer");
    assertThat(customerMap.get("name")).isEqualTo("Test Customer");

    @SuppressWarnings("unchecked")
    var budgetMap = (Map<String, Object>) context.get("budget");
    assertThat(budgetMap.get("hours")).isEqualTo(BigDecimal.valueOf(100));
    assertThat(budgetMap.get("amount")).isEqualTo(BigDecimal.valueOf(5000));
    assertThat(budgetMap.get("currency")).isEqualTo("USD");
  }

  @Test
  void buildContextWithoutCustomer() {
    var project = new Project("Solo Project", "No customer", memberId);
    when(projectRepository.findOneById(projectId)).thenReturn(Optional.of(project));
    when(customerProjectRepository.findByProjectId(projectId)).thenReturn(List.of());
    when(projectMemberRepository.findProjectMembersWithDetails(projectId)).thenReturn(List.of());
    when(projectBudgetRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
    when(entityTagRepository.findByEntityTypeAndEntityId("PROJECT", projectId))
        .thenReturn(List.of());
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());
    when(memberRepository.findOneById(memberId)).thenReturn(Optional.empty());

    var context = builder.buildContext(projectId, memberId);

    assertThat(context.get("customer")).isNull();
    assertThat(context.get("budget")).isNull();
  }

  @Test
  void buildContextWithCustomFields() {
    var project = new Project("Custom Fields Project", "desc", memberId);
    project.setCustomFields(Map.of("case_number", "CN-001", "priority", "high"));
    when(projectRepository.findOneById(projectId)).thenReturn(Optional.of(project));
    when(customerProjectRepository.findByProjectId(projectId)).thenReturn(List.of());
    when(projectMemberRepository.findProjectMembersWithDetails(projectId)).thenReturn(List.of());
    when(projectBudgetRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
    when(entityTagRepository.findByEntityTypeAndEntityId("PROJECT", projectId))
        .thenReturn(List.of());
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());
    when(memberRepository.findOneById(memberId)).thenReturn(Optional.empty());

    var context = builder.buildContext(projectId, memberId);

    @SuppressWarnings("unchecked")
    var projectMap = (Map<String, Object>) context.get("project");
    @SuppressWarnings("unchecked")
    var customFields = (Map<String, Object>) projectMap.get("customFields");
    assertThat(customFields).containsEntry("case_number", "CN-001");
    assertThat(customFields).containsEntry("priority", "high");
  }

  @Test
  void buildContextWithLogoUrl() {
    var project = new Project("Logo Project", "desc", memberId);
    when(projectRepository.findOneById(projectId)).thenReturn(Optional.of(project));
    when(customerProjectRepository.findByProjectId(projectId)).thenReturn(List.of());
    when(projectMemberRepository.findProjectMembersWithDetails(projectId)).thenReturn(List.of());
    when(projectBudgetRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
    when(entityTagRepository.findByEntityTypeAndEntityId("PROJECT", projectId))
        .thenReturn(List.of());

    var settings = new OrgSettings("USD");
    settings.setLogoS3Key("org/test-org/branding/logo.png");
    settings.setBrandColor("#ff0000");
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.of(settings));
    when(s3PresignedUrlService.generateDownloadUrl("org/test-org/branding/logo.png"))
        .thenReturn(new PresignedDownloadResult("https://s3.example.com/logo.png", 300));
    when(memberRepository.findOneById(memberId)).thenReturn(Optional.empty());

    var context = builder.buildContext(projectId, memberId);

    @SuppressWarnings("unchecked")
    var orgMap = (Map<String, Object>) context.get("org");
    assertThat(orgMap.get("logoUrl")).isEqualTo("https://s3.example.com/logo.png");
    assertThat(orgMap.get("brandColor")).isEqualTo("#ff0000");
  }

  @Test
  void buildContextWithTags() {
    var project = new Project("Tagged Project", "desc", memberId);
    when(projectRepository.findOneById(projectId)).thenReturn(Optional.of(project));
    when(customerProjectRepository.findByProjectId(projectId)).thenReturn(List.of());
    when(projectMemberRepository.findProjectMembersWithDetails(projectId)).thenReturn(List.of());
    when(projectBudgetRepository.findByProjectId(projectId)).thenReturn(Optional.empty());

    var tagId = UUID.randomUUID();
    var entityTag = new EntityTag(tagId, "PROJECT", projectId);
    when(entityTagRepository.findByEntityTypeAndEntityId("PROJECT", projectId))
        .thenReturn(List.of(entityTag));

    var tag = new Tag("Urgent", "#ff0000");
    when(tagRepository.findAllByIds(List.of(tagId))).thenReturn(List.of(tag));
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());
    when(memberRepository.findOneById(memberId)).thenReturn(Optional.empty());

    var context = builder.buildContext(projectId, memberId);

    @SuppressWarnings("unchecked")
    var tags = (List<Map<String, Object>>) context.get("tags");
    assertThat(tags).hasSize(1);
    assertThat(tags.getFirst().get("name")).isEqualTo("Urgent");
    assertThat(tags.getFirst().get("color")).isEqualTo("#ff0000");
  }

  @Test
  void buildContextWithMembersAndLead() {
    var project = new Project("Team Project", "desc", memberId);
    when(projectRepository.findOneById(projectId)).thenReturn(Optional.of(project));
    when(customerProjectRepository.findByProjectId(projectId)).thenReturn(List.of());

    var leadId = UUID.randomUUID();
    var devId = UUID.randomUUID();
    var leadInfo =
        new ProjectMemberInfo(
            UUID.randomUUID(), leadId, "Lead User", "lead@test.com", null, "lead", Instant.now());
    var devInfo =
        new ProjectMemberInfo(
            UUID.randomUUID(), devId, "Dev User", "dev@test.com", null, "member", Instant.now());
    when(projectMemberRepository.findProjectMembersWithDetails(projectId))
        .thenReturn(List.of(leadInfo, devInfo));

    when(projectBudgetRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
    when(entityTagRepository.findByEntityTypeAndEntityId("PROJECT", projectId))
        .thenReturn(List.of());
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());
    when(memberRepository.findOneById(memberId)).thenReturn(Optional.empty());

    var context = builder.buildContext(projectId, memberId);

    @SuppressWarnings("unchecked")
    var members = (List<Map<String, Object>>) context.get("members");
    assertThat(members).hasSize(2);

    @SuppressWarnings("unchecked")
    var lead = (Map<String, Object>) context.get("lead");
    assertThat(lead).isNotNull();
    assertThat(lead.get("name")).isEqualTo("Lead User");
    assertThat(lead.get("email")).isEqualTo("lead@test.com");
  }

  @Test
  void throwsWhenProjectNotFound() {
    when(projectRepository.findOneById(projectId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> builder.buildContext(projectId, memberId))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
