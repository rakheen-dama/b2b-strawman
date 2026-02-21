package io.b2mash.b2b.b2bstrawman.member;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.multitenancy.ScopedFilterChain;
import io.b2mash.b2b.b2bstrawman.security.ClerkJwtUtils;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
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
  private final Cache<String, UUID> memberCache =
      Caffeine.newBuilder().maximumSize(50_000).expireAfterWrite(Duration.ofHours(1)).build();

  public MemberFilter(MemberRepository memberRepository) {
    this.memberRepository = memberRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;

    if (tenantId != null) {
      MemberInfo info = resolveMember(tenantId);
      if (info != null) {
        var carrier = ScopedValue.where(RequestScopes.MEMBER_ID, info.memberId());
        if (info.orgRole() != null) {
          carrier = carrier.where(RequestScopes.ORG_ROLE, info.orgRole());
        }
        ScopedFilterChain.runScoped(carrier, filterChain, request, response);
        return;
      }
    }

    // No tenant or member resolution failed — continue unbound
    filterChain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/internal/")
        || path.startsWith("/actuator/")
        || path.startsWith("/portal/");
  }

  public void evictFromCache(String tenantId, String clerkUserId) {
    memberCache.invalidate(tenantId + ":" + clerkUserId);
  }

  private record MemberInfo(UUID memberId, String orgRole) {}

  private MemberInfo resolveMember(String tenantId) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
      return null;
    }

    Jwt jwt = jwtAuth.getToken();
    String clerkUserId = jwt.getSubject();
    String orgRole = ClerkJwtUtils.extractOrgRole(jwt);

    if (clerkUserId == null) {
      return null;
    }

    String cacheKey = tenantId + ":" + clerkUserId;
    UUID memberId;
    try {
      memberId = memberCache.get(cacheKey, k -> resolveOrCreateMember(clerkUserId, orgRole));
    } catch (Exception e) {
      log.warn(
          "Failed to resolve/create member for user {} in tenant {}: {}",
          clerkUserId,
          tenantId,
          e.getMessage());
      return null;
    }

    return new MemberInfo(memberId, orgRole);
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
              null,
              null,
              orgRole != null ? orgRole : Roles.ORG_MEMBER);
      member = memberRepository.save(member);
      log.info(
          "Lazy-created member {} for user {} in tenant {}",
          member.getId(),
          clerkUserId,
          RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : "unknown");
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
}
