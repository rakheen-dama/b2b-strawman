package io.b2mash.b2b.b2bstrawman.security;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.multitenancy.ScopedFilterChain;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Extracts JWT group memberships and binds them to {@link RequestScopes#GROUPS}. This enables
 * platform admin checks via {@link RequestScopes#isPlatformAdmin()} and
 * {@code @PreAuthorize("@platformSecurityService.isPlatformAdmin()")}.
 *
 * <p>Must be registered in the filter chain after {@link
 * io.b2mash.b2b.b2bstrawman.member.MemberFilter}.
 */
@Component
public class PlatformAdminFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      Jwt jwt = jwtAuth.getToken();
      Set<String> groups = JwtUtils.extractGroups(jwt);
      ScopedFilterChain.runScoped(
          ScopedValue.where(RequestScopes.GROUPS, groups), filterChain, request, response);
      return;
    }

    // No JWT authentication — continue without binding groups
    filterChain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/internal/")
        || path.startsWith("/actuator/")
        || path.startsWith("/portal/");
  }
}
