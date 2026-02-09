package io.b2mash.b2b.b2bstrawman.multitenancy;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Custom transaction manager that activates Hibernate's {@code @Filter("tenantFilter")} for
 * shared-schema tenants. Overrides {@link #doBegin} to enable the filter on the <em>same</em>
 * Session that will execute queries — this is the critical guarantee that AOP-based approaches
 * could not provide.
 *
 * <p>For dedicated-schema tenants (Pro tier), this is a no-op — schema-level isolation is
 * sufficient.
 */
public class TenantFilterTransactionManager extends JpaTransactionManager {

  private static final Logger log = LoggerFactory.getLogger(TenantFilterTransactionManager.class);

  private static final String SHARED_SCHEMA = "tenant_shared";
  private static final String FILTER_NAME = "tenantFilter";
  private static final String FILTER_PARAM = "tenantId";

  public TenantFilterTransactionManager(jakarta.persistence.EntityManagerFactory emf) {
    super(emf);
  }

  @Override
  protected void doBegin(Object transaction, TransactionDefinition definition) {
    super.doBegin(transaction, definition);

    if (RequestScopes.TENANT_ID.isBound()
        && SHARED_SCHEMA.equals(RequestScopes.TENANT_ID.get())
        && RequestScopes.ORG_ID.isBound()) {
      String orgId = RequestScopes.ORG_ID.get();
      // Get the Session from the EntityManager that super.doBegin() just bound.
      // Avoids sessionFactory.getCurrentSession() which can fail due to field shadowing
      // between our class and JpaTransactionManager's internal sessionFactory reference.
      var emHolder =
          (EntityManagerHolder)
              TransactionSynchronizationManager.getResource(getEntityManagerFactory());
      if (emHolder != null) {
        Session session = emHolder.getEntityManager().unwrap(Session.class);
        session.enableFilter(FILTER_NAME).setParameter(FILTER_PARAM, orgId);
        log.debug("Enabled tenantFilter for org {} on session {}", orgId, session.hashCode());
      }
    }
  }
}
