package io.b2mash.b2b.b2bstrawman.multitenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

  @Override
  public String resolveCurrentTenantIdentifier() {
    return RequestScopes.TENANT_ID.isBound()
        ? RequestScopes.TENANT_ID.get()
        : RequestScopes.DEFAULT_TENANT;
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return true;
  }

  @Override
  public boolean isRoot(String tenantId) {
    return RequestScopes.DEFAULT_TENANT.equals(tenantId);
  }
}
