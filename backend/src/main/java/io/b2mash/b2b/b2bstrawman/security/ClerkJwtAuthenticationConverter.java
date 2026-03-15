package io.b2mash.b2b.b2bstrawman.security;

import java.util.Collections;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Converts JWT tokens to Spring Security authentication tokens for identity purposes. Authorization
 * is handled by {@code @RequiresCapability} annotations, with capabilities resolved from the
 * member's {@code OrgRole} entity by {@code MemberFilter}.
 *
 * <p>No Spring Security authorities are granted — the converter only extracts the JWT subject as
 * the principal name.
 */
@Component
public class ClerkJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    return new JwtAuthenticationToken(jwt, Collections.emptyList(), jwt.getSubject());
  }
}
