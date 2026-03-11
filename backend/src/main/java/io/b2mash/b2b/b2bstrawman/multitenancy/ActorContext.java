package io.b2mash.b2b.b2bstrawman.multitenancy;

import io.b2mash.b2b.b2bstrawman.security.Roles;
import java.util.UUID;

/**
 * Value object carrying the authenticated member's identity and role. Replaces the {@code (UUID
 * memberId, String orgRole)} parameter pair that was previously threaded through service method
 * signatures.
 *
 * <p>Controllers create instances via {@link #fromRequestScopes()}. Tests construct directly via
 * the canonical constructor.
 */
public record ActorContext(UUID memberId, String orgRole) {

  /** Creates an ActorContext from the current request's scoped values. */
  public static ActorContext fromRequestScopes() {
    return new ActorContext(RequestScopes.requireMemberId(), RequestScopes.getOrgRole());
  }

  /** Returns true if the actor has owner or admin privileges. */
  public boolean isOwnerOrAdmin() {
    return Roles.ORG_OWNER.equals(orgRole) || Roles.ORG_ADMIN.equals(orgRole);
  }
}
