package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * 648B F-11 guard: every mutating HTTP mapping (POST, PUT, PATCH, DELETE — or a {@code
 * RequestMapping} with no method, which matches them all) of a controller under {@code
 * verticals.legal.trustaccounting} carries {@link IdempotentTrustWrite}, so a new trust endpoint
 * cannot skip the {@code Idempotency-Key} protocol. Scans the production classpath; no Spring
 * context.
 */
class TrustIdempotencyCoverageTest {

  private static final String TRUST_PACKAGE =
      "io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting";

  private static final Set<RequestMethod> MUTATING =
      Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

  @Test
  void everyMutatingTrustMappingIsAnIdempotentTrustWrite() throws Exception {
    var mutating = new TreeSet<String>();
    var missing = new ArrayList<String>();
    for (Method method : mappedMethods()) {
      var mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
      boolean writes =
          mapping.method().length == 0
              || Arrays.stream(mapping.method()).anyMatch(MUTATING::contains);
      String name = method.getDeclaringClass().getSimpleName() + "." + method.getName();
      if (writes) {
        mutating.add(name);
        if (!method.isAnnotationPresent(IdempotentTrustWrite.class)) {
          missing.add(name);
        }
      } else {
        assertThat(method.isAnnotationPresent(IdempotentTrustWrite.class))
            .as("%s is read-only and must not require an Idempotency-Key", name)
            .isFalse();
      }
    }
    assertThat(missing).as("mutating trust mappings without @IdempotentTrustWrite").isEmpty();
    // Not vacuous: the 28 trust writes of 648B F-11 (notes.md lists them).
    assertThat(mutating).hasSizeGreaterThanOrEqualTo(28);
  }

  private static List<Method> mappedMethods() throws ClassNotFoundException {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
    var methods = new ArrayList<Method>();
    for (var candidate : scanner.findCandidateComponents(TRUST_PACKAGE)) {
      Class<?> type = ClassUtils.forName(candidate.getBeanClassName(), null);
      for (Method method : type.getDeclaredMethods()) {
        if (AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
          methods.add(method);
        }
      }
    }
    assertThat(methods).as("trust controllers found on the classpath").isNotEmpty();
    return methods;
  }
}
