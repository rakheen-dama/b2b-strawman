package io.b2mash.b2b.b2bstrawman.orgrole;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Spring Security {@link AuthorizationManager} that enforces {@link RequiresCapability} annotations
 * on methods. Reads the required capability from the annotation, then checks against the
 * capabilities bound in {@link RequestScopes#CAPABILITIES} by {@code MemberFilter}.
 *
 * <p>Registered as a method-security interceptor in {@code SecurityConfig} via {@link
 * org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor}.
 */
@Component
public class CapabilityAuthorizationManager implements AuthorizationManager<MethodInvocation> {

  @Override
  public AuthorizationResult authorize(
      Supplier<? extends Authentication> authentication, MethodInvocation invocation) {
    Method method = invocation.getMethod();
    RequiresCapability annotation = method.getAnnotation(RequiresCapability.class);

    if (annotation == null) {
      // No @RequiresCapability on this method — abstain, let other managers decide
      return null;
    }

    Authentication auth = authentication.get();
    if (auth == null || !auth.isAuthenticated()) {
      return new AuthorizationDecision(false);
    }

    return new AuthorizationDecision(RequestScopes.hasCapability(annotation.value()));
  }
}
