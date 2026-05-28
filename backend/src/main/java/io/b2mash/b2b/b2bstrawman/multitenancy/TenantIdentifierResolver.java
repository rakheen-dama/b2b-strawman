package io.b2mash.b2b.b2bstrawman.multitenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

  private static final String PRIMARY_SHARD = "primary";
  private static final String DEFAULT_COMPOSITE =
      ShardAndSchema.format(PRIMARY_SHARD, RequestScopes.DEFAULT_TENANT);

  private final boolean shardingEnabled;

  public TenantIdentifierResolver(
      @Value("${kazi.sharding.enabled:false}") boolean shardingEnabled) {
    this.shardingEnabled = shardingEnabled;
  }

  @Override
  public String resolveCurrentTenantIdentifier() {
    if (shardingEnabled) {
      String shardId =
          RequestScopes.SHARD_ID.isBound() ? RequestScopes.SHARD_ID.get() : PRIMARY_SHARD;
      String schemaName =
          RequestScopes.TENANT_ID.isBound()
              ? RequestScopes.TENANT_ID.get()
              : RequestScopes.DEFAULT_TENANT;
      return ShardAndSchema.format(shardId, schemaName);
    }

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
    if (shardingEnabled) {
      return DEFAULT_COMPOSITE.equals(tenantId);
    }
    return RequestScopes.DEFAULT_TENANT.equals(tenantId);
  }
}
