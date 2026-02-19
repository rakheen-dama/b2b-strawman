package io.b2mash.b2b.b2bstrawman.setupstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerType;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerReadinessServiceTest {

  private static final UUID CUSTOMER_ID = UUID.randomUUID();
  private static final UUID MEMBER_ID = UUID.randomUUID();

  @Mock private CustomerRepository customerRepository;
  @Mock private CustomerProjectRepository customerProjectRepository;
  @Mock private FieldDefinitionRepository fieldDefinitionRepository;
  @Mock private EntityManager entityManager;

  private CustomerReadinessService service;

  @BeforeEach
  void setUp() {
    service =
        new CustomerReadinessService(
            customerRepository,
            customerProjectRepository,
            fieldDefinitionRepository,
            entityManager);
  }

  @Test
  void getReadiness_throwsWhenCustomerNotFound() {
    when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getReadiness(CUSTOMER_ID))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void getReadiness_prospect_noProjects_needsAttention() {
    mockCustomer(LifecycleStatus.PROSPECT);
    mockNoChecklist();
    mockNoRequiredFields();
    when(customerProjectRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of());

    var result = service.getReadiness(CUSTOMER_ID);

    assertThat(result.customerId()).isEqualTo(CUSTOMER_ID);
    assertThat(result.lifecycleStatus()).isEqualTo("PROSPECT");
    assertThat(result.overallReadiness()).isEqualTo("Needs Attention");
    assertThat(result.hasLinkedProjects()).isFalse();
  }

  @Test
  void getReadiness_onboarding_checklistInProgress_inProgress() {
    mockCustomer(LifecycleStatus.ONBOARDING);
    mockChecklistProgress("SA FICA Onboarding", 3, 5);
    mockNoRequiredFields();
    when(customerProjectRepository.findByCustomerId(CUSTOMER_ID))
        .thenReturn(List.of(mock(CustomerProject.class)));

    var result = service.getReadiness(CUSTOMER_ID);

    assertThat(result.lifecycleStatus()).isEqualTo("ONBOARDING");
    assertThat(result.checklistProgress()).isNotNull();
    assertThat(result.checklistProgress().completed()).isEqualTo(3);
    assertThat(result.checklistProgress().total()).isEqualTo(5);
    assertThat(result.checklistProgress().percentComplete()).isEqualTo(60);
    assertThat(result.overallReadiness()).isEqualTo("In Progress");
  }

  @Test
  void getReadiness_active_allFilled_checklistNull_hasProjects_complete() {
    var customer = mockCustomer(LifecycleStatus.ACTIVE, Map.of("tax_number", "12345"));
    mockNoChecklist();
    mockRequiredField("Tax Number", "tax_number");
    when(customerProjectRepository.findByCustomerId(CUSTOMER_ID))
        .thenReturn(List.of(mock(CustomerProject.class)));

    var result = service.getReadiness(CUSTOMER_ID);

    assertThat(result.lifecycleStatus()).isEqualTo("ACTIVE");
    assertThat(result.checklistProgress()).isNull();
    assertThat(result.requiredFields().filled()).isEqualTo(1);
    assertThat(result.requiredFields().total()).isEqualTo(1);
    assertThat(result.hasLinkedProjects()).isTrue();
    assertThat(result.overallReadiness()).isEqualTo("Complete");
  }

  @Test
  void getReadiness_active_checklistComplete_hasProjects_complete() {
    mockCustomer(LifecycleStatus.ACTIVE);
    mockChecklistProgress("SA FICA Onboarding", 5, 5);
    mockNoRequiredFields();
    when(customerProjectRepository.findByCustomerId(CUSTOMER_ID))
        .thenReturn(List.of(mock(CustomerProject.class)));

    var result = service.getReadiness(CUSTOMER_ID);

    assertThat(result.checklistProgress().percentComplete()).isEqualTo(100);
    assertThat(result.overallReadiness()).isEqualTo("Complete");
  }

  @Test
  void getReadiness_dormant_inProgress() {
    mockCustomer(LifecycleStatus.DORMANT);
    mockNoChecklist();
    mockNoRequiredFields();
    when(customerProjectRepository.findByCustomerId(CUSTOMER_ID))
        .thenReturn(List.of(mock(CustomerProject.class)));

    var result = service.getReadiness(CUSTOMER_ID);

    assertThat(result.lifecycleStatus()).isEqualTo("DORMANT");
    assertThat(result.overallReadiness()).isEqualTo("In Progress");
  }

  @Test
  void getReadiness_noRequiredFields_zeroFilled_noProjects_needsAttention() {
    mockCustomer(LifecycleStatus.ACTIVE);
    mockNoChecklist();
    mockNoRequiredFields();
    when(customerProjectRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of());

    var result = service.getReadiness(CUSTOMER_ID);

    assertThat(result.hasLinkedProjects()).isFalse();
    assertThat(result.overallReadiness()).isEqualTo("Needs Attention");
  }

  @Test
  void getReadiness_checklistProgress_percentComplete100_complete() {
    mockCustomer(LifecycleStatus.ACTIVE);
    mockChecklistProgress("SA FICA Onboarding", 5, 5);
    mockNoRequiredFields();
    when(customerProjectRepository.findByCustomerId(CUSTOMER_ID))
        .thenReturn(List.of(mock(CustomerProject.class)));

    var result = service.getReadiness(CUSTOMER_ID);

    assertThat(result.checklistProgress()).isNotNull();
    assertThat(result.checklistProgress().checklistName()).isEqualTo("SA FICA Onboarding");
    assertThat(result.checklistProgress().completed()).isEqualTo(5);
    assertThat(result.checklistProgress().total()).isEqualTo(5);
    assertThat(result.checklistProgress().percentComplete()).isEqualTo(100);
    assertThat(result.overallReadiness()).isEqualTo("Complete");
  }

  // --- Helper methods ---

  private Customer mockCustomer(LifecycleStatus lifecycleStatus) {
    return mockCustomer(lifecycleStatus, Map.of());
  }

  private Customer mockCustomer(LifecycleStatus lifecycleStatus, Map<String, Object> customFields) {
    var customer =
        new Customer(
            "Test Customer",
            "test@test.com",
            null,
            null,
            null,
            MEMBER_ID,
            CustomerType.INDIVIDUAL,
            lifecycleStatus);
    customer.setCustomFields(customFields);
    when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
    return customer;
  }

  @SuppressWarnings("unchecked")
  private void mockNoChecklist() {
    Query query = mock(Query.class);
    when(entityManager.createNativeQuery(any(String.class), eq(Tuple.class))).thenReturn(query);
    when(query.setParameter(eq("customerId"), eq(CUSTOMER_ID))).thenReturn(query);
    when(query.getResultList()).thenReturn(List.of());
  }

  @SuppressWarnings("unchecked")
  private void mockChecklistProgress(String name, int completed, int total) {
    var tuple = mock(Tuple.class);
    when(tuple.get("checklist_name", String.class)).thenReturn(name);
    when(tuple.get("completed_required")).thenReturn((long) completed);
    when(tuple.get("total_required")).thenReturn((long) total);

    Query query = mock(Query.class);
    when(entityManager.createNativeQuery(any(String.class), eq(Tuple.class))).thenReturn(query);
    when(query.setParameter(eq("customerId"), eq(CUSTOMER_ID))).thenReturn(query);
    when(query.getResultList()).thenReturn(List.of(tuple));
  }

  private void mockNoRequiredFields() {
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.CUSTOMER))
        .thenReturn(List.of());
  }

  private void mockRequiredField(String name, String slug) {
    var fd = new FieldDefinition(EntityType.CUSTOMER, name, slug, FieldType.TEXT);
    fd.updateMetadata(name, null, true, null);
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.CUSTOMER))
        .thenReturn(List.of(fd));
  }
}
