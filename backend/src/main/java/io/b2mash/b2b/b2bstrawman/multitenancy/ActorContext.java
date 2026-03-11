package io.b2mash.b2b.b2bstrawman.multitenancy;

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
    return "ORG_OWNER".equals(orgRole) || "ORG_ADMIN".equals(orgRole);
  }
}
