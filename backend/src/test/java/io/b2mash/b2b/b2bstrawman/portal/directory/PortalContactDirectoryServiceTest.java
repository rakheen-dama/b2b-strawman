package io.b2mash.b2b.b2bstrawman.portal.directory;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Plain Mockito (no Spring context): an after-commit directory reconcile is retried once, then
 * logged at ERROR — never rethrown, because the contact write it follows has already committed.
 */
class PortalContactDirectoryServiceTest {

  private static final String ORG_ID = "org_dir_unit";
  private static final String TENANT = "tenant_0123456789ab";
  private static final String EMAIL = "Client@Example.com";
  private static final String EMAIL_LOWER = "client@example.com";

  private PortalContactDirectoryRepository directoryRepository;
  private PortalContactRepository portalContactRepository;
  private PortalContactDirectoryService service;

  @BeforeEach
  void setUp() {
    directoryRepository = mock(PortalContactDirectoryRepository.class);
    portalContactRepository = mock(PortalContactRepository.class);
    OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    when(organizationRepository.findByExternalOrgId(ORG_ID)).thenReturn(Optional.empty());
    when(portalContactRepository.existsActiveByEmailLower(EMAIL_LOWER)).thenReturn(true);
    service =
        new PortalContactDirectoryService(
            directoryRepository,
            portalContactRepository,
            organizationRepository,
            mock(PlatformTransactionManager.class),
            mock(PlatformTransactionManager.class));
  }

  @Test
  void failingTwiceIsSwallowedAfterOneRetry() {
    doThrow(new DataAccessResourceFailureException("portal down"))
        .when(directoryRepository)
        .upsert(anyString(), anyString(), anyString(), anyString());

    assertThatCode(this::reconcileAfterCommitInTenant).doesNotThrowAnyException();

    verify(directoryRepository, times(PortalContactDirectoryService.RECONCILE_ATTEMPTS))
        .upsert(EMAIL_LOWER, ORG_ID, TENANT, ORG_ID);
  }

  @Test
  void failingOnceSucceedsOnTheRetry() {
    doThrow(new DataAccessResourceFailureException("transient"))
        .doNothing()
        .when(directoryRepository)
        .upsert(anyString(), anyString(), anyString(), anyString());

    assertThatCode(this::reconcileAfterCommitInTenant).doesNotThrowAnyException();

    verify(directoryRepository, times(2)).upsert(EMAIL_LOWER, ORG_ID, TENANT, ORG_ID);
  }

  @Test
  void takesTheOrgLockBeforeReadingTheTenant() {
    doNothing()
        .when(directoryRepository)
        .upsert(anyString(), anyString(), anyString(), anyString());

    reconcileAfterCommitInTenant();

    var order = inOrder(directoryRepository, portalContactRepository);
    order.verify(directoryRepository).lockOrg(ORG_ID);
    order.verify(portalContactRepository).existsActiveByEmailLower(EMAIL_LOWER);
    order.verify(directoryRepository).upsert(EMAIL_LOWER, ORG_ID, TENANT, ORG_ID);
  }

  /** No transaction synchronization is active here, so the reconcile runs immediately. */
  private void reconcileAfterCommitInTenant() {
    ScopedValue.where(RequestScopes.TENANT_ID, TENANT)
        .run(() -> service.reconcileAfterCommit(EMAIL, ORG_ID));
  }
}
