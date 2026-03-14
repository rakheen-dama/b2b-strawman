package io.b2mash.b2b.b2bstrawman.member;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.multitenancy.ScopedFilterChain;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.security.ClerkJwtUtils;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
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
  private final OrgRoleService orgRoleService;
  private final MemberCacheService memberCacheService;

  public MemberFilter(
      MemberRepository memberRepository,
      OrgRoleService orgRoleService,
      MemberCacheService memberCacheService) {
    this.memberRepository = memberRepository;
    this.orgRoleService = orgRoleService;
    this.memberCacheService = memberCacheService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String tenantId = RequestScopes.getTenantIdOrNull();

    if (tenantId != null) {
      MemberCacheService.MemberInfo info = resolveMember(tenantId);
      if (info != null) {
        var carrier = ScopedValue.where(RequestScopes.MEMBER_ID, info.memberId());
        if (info.orgRole() != null) {
          carrier = carrier.where(RequestScopes.ORG_ROLE, info.orgRole());
        }

        Set<String> capabilities;
        try {
          capabilities = orgRoleService.resolveCapabilities(info.memberId());
        } catch (Exception e) {
          log.warn(
              "Failed to resolve capabilities for member {}: {}", info.memberId(), e.getMessage());
          capabilities = Collections.emptySet();
        }
        carrier = carrier.where(RequestScopes.CAPABILITIES, capabilities);

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
    memberCacheService.evict(tenantId, clerkUserId);
  }

  private MemberCacheService.MemberInfo resolveMember(String tenantId) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
      return null;
    }

    Jwt jwt = jwtAuth.getToken();
    String clerkUserId = jwt.getSubject();
    String jwtOrgRole = ClerkJwtUtils.extractOrgRole(jwt);

    if (clerkUserId == null) {
      return null;
    }

    MemberCacheService.MemberInfo info;
    try {
      info = memberCacheService.get(tenantId, clerkUserId);
      if (info == null) {
        info = resolveOrCreateMember(clerkUserId, jwtOrgRole, jwt);
        if (info != null) {
          memberCacheService.put(tenantId, clerkUserId, info);
        }
      }
    } catch (Exception e) {
      log.warn(
          "Failed to resolve/create member for user {} in tenant {}: {}",
          clerkUserId,
          tenantId,
          e.getMessage());
      return null;
    }

    if (info == null) {
      return null;
    }

    // If JWT has an explicit role (rich format, Clerk, or org_role claim), prefer it.
    // Otherwise (flat list default), trust the DB role — it was set correctly during onboarding.
    boolean jwtHasExplicitRole =
        !ClerkJwtUtils.isKeycloakFlatListFormat(jwt) || jwt.getClaimAsString("org_role") != null;
    String effectiveRole = jwtHasExplicitRole ? jwtOrgRole : info.orgRole();
    return new MemberCacheService.MemberInfo(info.memberId(), effectiveRole);
  }

  private MemberCacheService.MemberInfo resolveOrCreateMember(
      String clerkUserId, String orgRole, Jwt jwt) {
    return memberRepository
        .findByClerkUserId(clerkUserId)
        .map(m -> new MemberCacheService.MemberInfo(m.getId(), m.getOrgRole()))
        .orElseGet(() -> lazyCreateMember(clerkUserId, orgRole, jwt));
  }

  private MemberCacheService.MemberInfo lazyCreateMember(
      String clerkUserId, String orgRole, Jwt jwt) {
    // Always extract real email/name from JWT when available.
    // Keycloak JWTs include email/name directly; Clerk JWTs may not (updated via webhook).
    String jwtEmail = jwt.getClaimAsString("email");
    String jwtName = jwt.getClaimAsString("name");
    String email =
        (jwtEmail != null && jwtEmail.contains("@"))
            ? jwtEmail
            : clerkUserId + "@placeholder.internal";
    String name = jwtName;

    // First member in a newly-provisioned tenant becomes owner (founding user).
    String effectiveRole = orgRole != null ? orgRole : Roles.ORG_MEMBER;
    if (Roles.ORG_MEMBER.equals(effectiveRole) && memberRepository.count() == 0) {
      effectiveRole = Roles.ORG_OWNER;
      log.info("Promoting first member {} to owner (founding user)", clerkUserId);
    }

    try {
      var member = new Member(clerkUserId, email, name, null, effectiveRole);

      // Assign default system role based on the effective org role before persisting
      orgRoleService
          .findSystemRoleBySlug(effectiveRole)
          .ifPresent(systemRole -> member.setOrgRoleId(systemRole.getId()));

      memberRepository.save(member);

      log.info(
          "Lazy-created member {} for user {} in tenant {}",
          member.getId(),
          clerkUserId,
          RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : "unknown");
      return new MemberCacheService.MemberInfo(member.getId(), effectiveRole);
    } catch (DataIntegrityViolationException e) {
      // Race condition: another instance already created this member
      return memberRepository
          .findByClerkUserId(clerkUserId)
          .map(m -> new MemberCacheService.MemberInfo(m.getId(), m.getOrgRole()))
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Member not found after constraint violation for: " + clerkUserId));
    }
  }
}
