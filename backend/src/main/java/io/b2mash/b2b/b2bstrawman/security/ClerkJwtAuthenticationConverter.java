package io.b2mash.b2b.b2bstrawman.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Converts JWT tokens (Clerk v2 or Keycloak) to Spring Security authentication tokens with proper
 * granted authorities. Extracts role from Keycloak JWT via JwtUtils.
 */
@Component
public class ClerkJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private static final Map<String, String> ROLE_MAPPING =
      Map.of(
          Roles.ORG_OWNER, Roles.AUTHORITY_ORG_OWNER,
          Roles.ORG_ADMIN, Roles.AUTHORITY_ORG_ADMIN,
          Roles.ORG_MEMBER, Roles.AUTHORITY_ORG_MEMBER);

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
    return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
  }

  private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
    // JwtUtils.extractOrgRole handles Keycloak format
    String orgRole = JwtUtils.extractOrgRole(jwt);
    if (orgRole == null) {
      return List.of();
    }
    String springRole = ROLE_MAPPING.get(orgRole);
    if (springRole == null) {
      return List.of();
    }
    return List.of(new SimpleGrantedAuthority(springRole));
  }
}
