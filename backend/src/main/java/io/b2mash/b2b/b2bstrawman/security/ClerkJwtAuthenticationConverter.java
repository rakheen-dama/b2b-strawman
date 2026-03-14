package io.b2mash.b2b.b2bstrawman.security;

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
 * baseline authority for any authenticated user with an org context. The actual role-level
 * authorization (admin/owner) is resolved from the DB by MemberFilter and bound via
 * RequestScopes.ORG_ROLE. Migration to @RequiresCapability will happen in Epic 347.
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
    // Always grant ROLE_ORG_MEMBER as baseline — real authorization is via DB role + capabilities
    return List.of(new SimpleGrantedAuthority(Roles.AUTHORITY_ORG_MEMBER));
  }
}
