package io.b2mash.b2b.b2bstrawman.setupstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnbilledTimeSummaryServiceTest {

  @Mock private EntityManager entityManager;
  @Mock private OrgSettingsRepository orgSettingsRepository;
  @Mock private ProjectRepository projectRepository;
  @Mock private CustomerRepository customerRepository;

  private UnbilledTimeSummaryService service;

  @BeforeEach
  void setUp() {
    service =
        new UnbilledTimeSummaryService(
            entityManager, orgSettingsRepository, projectRepository, customerRepository);
  }

  @Test
  void getProjectUnbilledSummary_withEntries_returnsCorrectTotals() {
    // 3 entries: 120 + 60 + 90 = 270 minutes = 4.50 hours
    // amounts: (120/60)*1500 + (60/60)*1500 + (90/60)*1500 = 3000 + 1500 + 2250 = 6750
    var projectId = UUID.randomUUID();
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(mock(Project.class)));
    var orgSettings = mock(OrgSettings.class);
    when(orgSettings.getDefaultCurrency()).thenReturn("ZAR");
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.of(orgSettings));

    var tuple = mock(Tuple.class);
    when(tuple.get("total_minutes")).thenReturn(new BigDecimal("270"));
    when(tuple.get("total_amount")).thenReturn(new BigDecimal("6750.00"));
    when(tuple.get("entry_count")).thenReturn(3L);

    @SuppressWarnings("unchecked")
    Query query = mock(Query.class);
    when(entityManager.createNativeQuery(any(String.class), eq(Tuple.class))).thenReturn(query);
    when(query.setParameter(eq("projectId"), eq(projectId))).thenReturn(query);
    when(query.getResultList()).thenReturn(List.of(tuple));

    var result = service.getProjectUnbilledSummary(projectId);

    assertThat(result.totalHours()).isEqualByComparingTo("4.50");
    assertThat(result.totalAmount()).isEqualByComparingTo("6750.00");
    assertThat(result.currency()).isEqualTo("ZAR");
    assertThat(result.entryCount()).isEqualTo(3);
    assertThat(result.byProject()).isNull();
  }

  @Test
  void getProjectUnbilledSummary_noEntries_returnsZeros() {
    var projectId = UUID.randomUUID();
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(mock(Project.class)));
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());

    // Aggregate query with no matching rows still returns one row with zeros
    var tuple = mock(Tuple.class);
    when(tuple.get("total_minutes")).thenReturn(new BigDecimal("0"));
    when(tuple.get("total_amount")).thenReturn(new BigDecimal("0"));
    when(tuple.get("entry_count")).thenReturn(0L);

    @SuppressWarnings("unchecked")
    Query query = mock(Query.class);
    when(entityManager.createNativeQuery(any(String.class), eq(Tuple.class))).thenReturn(query);
    when(query.setParameter(eq("projectId"), eq(projectId))).thenReturn(query);
    when(query.getResultList()).thenReturn(List.of(tuple));

    var result = service.getProjectUnbilledSummary(projectId);

    assertThat(result.totalHours()).isEqualByComparingTo("0");
    assertThat(result.totalAmount()).isEqualByComparingTo("0");
    assertThat(result.currency()).isEqualTo("USD");
    assertThat(result.entryCount()).isZero();
    assertThat(result.byProject()).isNull();
  }

  @Test
  void getProjectUnbilledSummary_emptyResultList_returnsZeros() {
    var projectId = UUID.randomUUID();
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(mock(Project.class)));
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());

    @SuppressWarnings("unchecked")
    Query query = mock(Query.class);
    when(entityManager.createNativeQuery(any(String.class), eq(Tuple.class))).thenReturn(query);
    when(query.setParameter(eq("projectId"), eq(projectId))).thenReturn(query);
    when(query.getResultList()).thenReturn(List.of());

    var result = service.getProjectUnbilledSummary(projectId);

    assertThat(result.totalHours()).isEqualByComparingTo("0");
    assertThat(result.totalAmount()).isEqualByComparingTo("0");
    assertThat(result.currency()).isEqualTo("USD");
    assertThat(result.entryCount()).isZero();
    assertThat(result.byProject()).isNull();
  }

  @Test
  void getCustomerUnbilledSummary_groupsByProject() {
    var customerId = UUID.randomUUID();
    var projectId1 = UUID.randomUUID();
    var projectId2 = UUID.randomUUID();

    when(customerRepository.findById(customerId)).thenReturn(Optional.of(mock(Customer.class)));
    var orgSettings = mock(OrgSettings.class);
    when(orgSettings.getDefaultCurrency()).thenReturn("ZAR");
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.of(orgSettings));

    var tuple1 = mock(Tuple.class);
    when(tuple1.get("project_id", UUID.class)).thenReturn(projectId1);
    when(tuple1.get("project_name", String.class)).thenReturn("Project Alpha");
    when(tuple1.get("total_minutes")).thenReturn(new BigDecimal("120"));
    when(tuple1.get("total_amount")).thenReturn(new BigDecimal("3000.00"));
    when(tuple1.get("entry_count")).thenReturn(2L);

    var tuple2 = mock(Tuple.class);
    when(tuple2.get("project_id", UUID.class)).thenReturn(projectId2);
    when(tuple2.get("project_name", String.class)).thenReturn("Project Beta");
    when(tuple2.get("total_minutes")).thenReturn(new BigDecimal("60"));
    when(tuple2.get("total_amount")).thenReturn(new BigDecimal("1500.00"));
    when(tuple2.get("entry_count")).thenReturn(1L);

    @SuppressWarnings("unchecked")
    Query query = mock(Query.class);
    when(entityManager.createNativeQuery(any(String.class), eq(Tuple.class))).thenReturn(query);
    when(query.setParameter(eq("customerId"), eq(customerId))).thenReturn(query);
    when(query.getResultList()).thenReturn(List.of(tuple1, tuple2));

    var result = service.getCustomerUnbilledSummary(customerId);

    assertThat(result.totalHours()).isEqualByComparingTo("3.00");
    assertThat(result.totalAmount()).isEqualByComparingTo("4500.00");
    assertThat(result.currency()).isEqualTo("ZAR");
    assertThat(result.entryCount()).isEqualTo(3);
    assertThat(result.byProject()).hasSize(2);

    var breakdown1 = result.byProject().get(0);
    assertThat(breakdown1.projectId()).isEqualTo(projectId1);
    assertThat(breakdown1.projectName()).isEqualTo("Project Alpha");
    assertThat(breakdown1.hours()).isEqualByComparingTo("2.00");
    assertThat(breakdown1.amount()).isEqualByComparingTo("3000.00");
    assertThat(breakdown1.entryCount()).isEqualTo(2);

    var breakdown2 = result.byProject().get(1);
    assertThat(breakdown2.projectId()).isEqualTo(projectId2);
    assertThat(breakdown2.projectName()).isEqualTo("Project Beta");
    assertThat(breakdown2.hours()).isEqualByComparingTo("1.00");
    assertThat(breakdown2.amount()).isEqualByComparingTo("1500.00");
    assertThat(breakdown2.entryCount()).isEqualTo(1);
  }

  @Test
  void getCustomerUnbilledSummary_noEntries_returnsZerosWithNullByProject() {
    var customerId = UUID.randomUUID();
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(mock(Customer.class)));
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());

    @SuppressWarnings("unchecked")
    Query query = mock(Query.class);
    when(entityManager.createNativeQuery(any(String.class), eq(Tuple.class))).thenReturn(query);
    when(query.setParameter(eq("customerId"), eq(customerId))).thenReturn(query);
    when(query.getResultList()).thenReturn(List.of());

    var result = service.getCustomerUnbilledSummary(customerId);

    assertThat(result.totalHours()).isEqualByComparingTo("0");
    assertThat(result.totalAmount()).isEqualByComparingTo("0");
    assertThat(result.currency()).isEqualTo("USD");
    assertThat(result.entryCount()).isZero();
    assertThat(result.byProject()).isNull();
  }

  @Test
  void resolveCurrency_fromOrgSettings() {
    var projectId = UUID.randomUUID();
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(mock(Project.class)));
    var orgSettings = mock(OrgSettings.class);
    when(orgSettings.getDefaultCurrency()).thenReturn("EUR");
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.of(orgSettings));

    var tuple = mock(Tuple.class);
    when(tuple.get("total_minutes")).thenReturn(new BigDecimal("0"));
    when(tuple.get("total_amount")).thenReturn(new BigDecimal("0"));
    when(tuple.get("entry_count")).thenReturn(0L);

    @SuppressWarnings("unchecked")
    Query query = mock(Query.class);
    when(entityManager.createNativeQuery(any(String.class), eq(Tuple.class))).thenReturn(query);
    when(query.setParameter(eq("projectId"), eq(projectId))).thenReturn(query);
    when(query.getResultList()).thenReturn(List.of(tuple));

    var result = service.getProjectUnbilledSummary(projectId);

    assertThat(result.currency()).isEqualTo("EUR");
  }

  @Test
  void getProjectUnbilledSummary_nonExistentProject_throwsResourceNotFoundException() {
    var projectId = UUID.randomUUID();
    when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getProjectUnbilledSummary(projectId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void getCustomerUnbilledSummary_nonExistentCustomer_throwsResourceNotFoundException() {
    var customerId = UUID.randomUUID();
    when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getCustomerUnbilledSummary(customerId))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
