package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
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
class CustomerContextBuilderTest {

  @Mock private CustomerRepository customerRepository;
  @Mock private CustomerProjectRepository customerProjectRepository;
  @Mock private ProjectRepository projectRepository;
  @Mock private EntityTagRepository entityTagRepository;
  @Mock private TagRepository tagRepository;
  @Mock private OrgSettingsRepository orgSettingsRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private S3PresignedUrlService s3PresignedUrlService;

  @InjectMocks private CustomerContextBuilder builder;

  private final UUID customerId = UUID.randomUUID();
  private final UUID memberId = UUID.randomUUID();

  @Test
  void supportsCustomerEntityType() {
    assertThat(builder.supports()).isEqualTo(TemplateEntityType.CUSTOMER);
  }

  @Test
  void buildContextWithProjects() {
    var customer = new Customer("Acme Corp", "acme@example.com", "555-0100", null, null, memberId);
    when(customerRepository.findOneById(customerId)).thenReturn(Optional.of(customer));

    var projectId = UUID.randomUUID();
    var cp = new CustomerProject(customerId, projectId, memberId);
    when(customerProjectRepository.findByCustomerId(customerId)).thenReturn(List.of(cp));

    var project = new Project("Project Alpha", "desc", memberId);
    when(projectRepository.findAllByIds(List.of(projectId))).thenReturn(List.of(project));

    when(entityTagRepository.findByEntityTypeAndEntityId("CUSTOMER", customerId))
        .thenReturn(List.of());
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());
    when(memberRepository.findOneById(memberId))
        .thenReturn(Optional.of(new Member("clerk", "user@test.com", "User", null, "admin")));

    var context = builder.buildContext(customerId, memberId);

    assertThat(context).containsKey("customer");
    assertThat(context).containsKey("projects");

    @SuppressWarnings("unchecked")
    var customerMap = (Map<String, Object>) context.get("customer");
    assertThat(customerMap.get("name")).isEqualTo("Acme Corp");
    assertThat(customerMap.get("email")).isEqualTo("acme@example.com");

    @SuppressWarnings("unchecked")
    var projects = (List<Map<String, Object>>) context.get("projects");
    assertThat(projects).hasSize(1);
    assertThat(projects.getFirst().get("name")).isEqualTo("Project Alpha");
  }

  @Test
  void buildContextWithoutProjects() {
    var customer = new Customer("Solo Customer", "solo@example.com", null, null, null, memberId);
    when(customerRepository.findOneById(customerId)).thenReturn(Optional.of(customer));
    when(customerProjectRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(entityTagRepository.findByEntityTypeAndEntityId("CUSTOMER", customerId))
        .thenReturn(List.of());
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());
    when(memberRepository.findOneById(memberId)).thenReturn(Optional.empty());

    var context = builder.buildContext(customerId, memberId);

    @SuppressWarnings("unchecked")
    var projects = (List<Map<String, Object>>) context.get("projects");
    assertThat(projects).isEmpty();
  }

  @Test
  void buildContextWithCustomFields() {
    var customer =
        new Customer("Fields Customer", "fields@example.com", null, null, null, memberId);
    customer.setCustomFields(Map.of("industry", "Tech", "tier", "Gold"));
    when(customerRepository.findOneById(customerId)).thenReturn(Optional.of(customer));
    when(customerProjectRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(entityTagRepository.findByEntityTypeAndEntityId("CUSTOMER", customerId))
        .thenReturn(List.of());
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());
    when(memberRepository.findOneById(memberId)).thenReturn(Optional.empty());

    var context = builder.buildContext(customerId, memberId);

    @SuppressWarnings("unchecked")
    var customerMap = (Map<String, Object>) context.get("customer");
    @SuppressWarnings("unchecked")
    var customFields = (Map<String, Object>) customerMap.get("customFields");
    assertThat(customFields).containsEntry("industry", "Tech");
    assertThat(customFields).containsEntry("tier", "Gold");
  }

  @Test
  void buildContextWithTags() {
    var customer = new Customer("Tagged Customer", "tag@example.com", null, null, null, memberId);
    when(customerRepository.findOneById(customerId)).thenReturn(Optional.of(customer));
    when(customerProjectRepository.findByCustomerId(customerId)).thenReturn(List.of());

    var tagId = UUID.randomUUID();
    when(entityTagRepository.findByEntityTypeAndEntityId("CUSTOMER", customerId))
        .thenReturn(List.of(new EntityTag(tagId, "CUSTOMER", customerId)));
    when(tagRepository.findAllByIds(List.of(tagId))).thenReturn(List.of(new Tag("VIP", "#gold")));
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());
    when(memberRepository.findOneById(memberId)).thenReturn(Optional.empty());

    var context = builder.buildContext(customerId, memberId);

    @SuppressWarnings("unchecked")
    var tags = (List<Map<String, Object>>) context.get("tags");
    assertThat(tags).hasSize(1);
    assertThat(tags.getFirst().get("name")).isEqualTo("VIP");
  }

  @Test
  void buildContextWithLogoUrl() {
    var customer = new Customer("Logo Customer", "logo@example.com", null, null, null, memberId);
    when(customerRepository.findOneById(customerId)).thenReturn(Optional.of(customer));
    when(customerProjectRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(entityTagRepository.findByEntityTypeAndEntityId("CUSTOMER", customerId))
        .thenReturn(List.of());

    var settings = new OrgSettings("ZAR");
    settings.setLogoS3Key("org/o1/branding/logo.png");
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.of(settings));
    when(s3PresignedUrlService.generateDownloadUrl("org/o1/branding/logo.png"))
        .thenReturn(new PresignedDownloadResult("https://s3.example.com/logo.png", 300));
    when(memberRepository.findOneById(memberId)).thenReturn(Optional.empty());

    var context = builder.buildContext(customerId, memberId);

    @SuppressWarnings("unchecked")
    var orgMap = (Map<String, Object>) context.get("org");
    assertThat(orgMap.get("logoUrl")).isEqualTo("https://s3.example.com/logo.png");
  }

  @Test
  void throwsWhenCustomerNotFound() {
    when(customerRepository.findOneById(customerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> builder.buildContext(customerId, memberId))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
