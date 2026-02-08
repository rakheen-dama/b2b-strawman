package io.b2mash.b2b.b2bstrawman.multitenancy;

import io.b2mash.b2b.b2bstrawman.member.MemberContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantLoggingFilter extends OncePerRequestFilter {

  private static final String MDC_TENANT_ID = "tenantId";
  private static final String MDC_USER_ID = "userId";
  private static final String MDC_MEMBER_ID = "memberId";
  private static final String MDC_REQUEST_ID = "requestId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      MDC.put(MDC_REQUEST_ID, UUID.randomUUID().toString());

      String tenantId = TenantContext.getTenantId();
      if (tenantId != null) {
        MDC.put(MDC_TENANT_ID, tenantId);
      }

      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth instanceof JwtAuthenticationToken jwtAuth) {
        MDC.put(MDC_USER_ID, jwtAuth.getToken().getSubject());
      }

      UUID memberId = MemberContext.getCurrentMemberId();
      if (memberId != null) {
        MDC.put(MDC_MEMBER_ID, memberId.toString());
      }

      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_TENANT_ID);
      MDC.remove(MDC_USER_ID);
      MDC.remove(MDC_MEMBER_ID);
      MDC.remove(MDC_REQUEST_ID);
    }
  }
}
