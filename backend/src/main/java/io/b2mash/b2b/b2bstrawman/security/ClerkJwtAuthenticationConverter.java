package io.b2mash.b2b.b2bstrawman.security;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Converts JWT tokens to Spring Security authentication tokens. Always grants ROLE_ORG_MEMBER as
 * baseline authority for any authenticated user with an org context. Additionally grants
 * ROLE_ORG_ADMIN and/or ROLE_ORG_OWNER based on the DB-resolved role bound via
 * RequestScopes.ORG_ROLE by MemberFilter (which runs earlier in the filter chain).
 *
 * <p>This backward-compat approach preserves existing {@code @PreAuthorize} checks (e.g., {@code
 * hasRole('ORG_OWNER')}) until migration to {@code @RequiresCapability} in Epic 347.
 */
@Component
public class ClerkJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
    return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
  }

  private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
    List<GrantedAuthority> authorities = new ArrayList<>();

    // Always grant ROLE_ORG_MEMBER as baseline — MemberFilter guarantees membership
    authorities.add(new SimpleGrantedAuthority(Roles.AUTHORITY_ORG_MEMBER));

    // Grant role-level authorities based on DB-resolved role (bound by MemberFilter)
    if (RequestScopes.ORG_ROLE.isBound()) {
      String role = RequestScopes.ORG_ROLE.get();
      if (Roles.ORG_ADMIN.equals(role)) {
        authorities.add(new SimpleGrantedAuthority(Roles.AUTHORITY_ORG_ADMIN));
      } else if (Roles.ORG_OWNER.equals(role)) {
        // Owner has all admin privileges
        authorities.add(new SimpleGrantedAuthority(Roles.AUTHORITY_ORG_ADMIN));
        authorities.add(new SimpleGrantedAuthority(Roles.AUTHORITY_ORG_OWNER));
      }
    }

    return List.copyOf(authorities);
  }
}
