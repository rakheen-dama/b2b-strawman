package io.b2mash.b2b.b2bstrawman.member;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class MemberFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(MemberFilter.class);

  private final MemberRepository memberRepository;
  private final Map<String, UUID> memberCache = new ConcurrentHashMap<>();

  public MemberFilter(MemberRepository memberRepository) {
    this.memberRepository = memberRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String tenantId = TenantContext.getTenantId();
      if (tenantId != null) {
        resolveMember(tenantId);
      }

      filterChain.doFilter(request, response);
    } finally {
      MemberContext.clear();
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/internal/") || path.startsWith("/actuator/");
  }

  public void evictFromCache(String tenantId, String clerkUserId) {
    memberCache.remove(tenantId + ":" + clerkUserId);
  }

  private void resolveMember(String tenantId) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
      return;
    }

    Jwt jwt = jwtAuth.getToken();
    String clerkUserId = jwt.getSubject();
    String orgRole = extractOrgRole(jwt);

    if (clerkUserId == null) {
      return;
    }

    String cacheKey = tenantId + ":" + clerkUserId;
    UUID memberId =
        memberCache.computeIfAbsent(cacheKey, k -> resolveOrCreateMember(clerkUserId, orgRole));

    MemberContext.setCurrentMemberId(memberId);
    if (orgRole != null) {
      MemberContext.setOrgRole(orgRole);
    }
  }

  private UUID resolveOrCreateMember(String clerkUserId, String orgRole) {
    return memberRepository
        .findByClerkUserId(clerkUserId)
        .map(Member::getId)
        .orElseGet(() -> lazyCreateMember(clerkUserId, orgRole));
  }

  private UUID lazyCreateMember(String clerkUserId, String orgRole) {
    try {
      var member =
          new Member(
              clerkUserId,
              clerkUserId + "@placeholder.internal",
              clerkUserId,
              null,
              orgRole != null ? orgRole : "member");
      member = memberRepository.save(member);
      log.info(
          "Lazy-created member {} for user {} in tenant {}",
          member.getId(),
          clerkUserId,
          TenantContext.getTenantId());
      return member.getId();
    } catch (DataIntegrityViolationException e) {
      // Race condition: another instance already created this member
      return memberRepository
          .findByClerkUserId(clerkUserId)
          .map(Member::getId)
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Member not found after constraint violation for: " + clerkUserId));
    }
  }

  @SuppressWarnings("unchecked")
  private String extractOrgRole(Jwt jwt) {
    Map<String, Object> orgClaim = jwt.getClaim("o");
    if (orgClaim != null) {
      return (String) orgClaim.get("rol");
    }
    return null;
  }
}
