package io.b2mash.b2b.b2bstrawman.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.beans.factory.annotation.Value;

/**
 * §13.6.6 (Phase 88 U11): every client-facing portal link is built by {@code PortalLinkBuilder}.
 * ArchUnit cannot see string concatenation (javac compiles it to {@code invokedynamic}), so the
 * rule removes the ingredient instead: no class other than the builder may obtain {@code
 * docteams.app.portal-base-url} via {@code @Value}, and no other class may hold a {@code
 * portalBaseUrl} field. {@code security.PortalOrigin} is the single exemption — it derives the CSP
 * {@code frame-ancestors} origin (ADR-352), not a link; a third rule stops anyone else depending on
 * {@code PortalOrigin} and building links from its value (allowlist: {@code SecurityConfig}).
 *
 * <p>Residual gaps this guard cannot see (review-time only): {@code @Value("${PORTAL_BASE_URL}")}
 * (the raw env var rather than the property key) and {@code
 * Environment.getProperty("docteams.app.portal-base-url")} (a runtime string argument, not an
 * annotation). Both bypass the property-key match in rule 1.
 */
@AnalyzeClasses(
    packages = "io.b2mash.b2b.b2bstrawman",
    importOptions = ImportOption.DoNotIncludeTests.class)
class PortalLinkBuilderArchTest {

  private static final String PROPERTY = "docteams.app.portal-base-url";
  private static final String BUILDER = "io.b2mash.b2b.b2bstrawman.portal.PortalLinkBuilder";
  private static final String PORTAL_ORIGIN = "io.b2mash.b2b.b2bstrawman.security.PortalOrigin";
  private static final String SECURITY_CONFIG = "io.b2mash.b2b.b2bstrawman.security.SecurityConfig";

  private static final ArchCondition<JavaClass> NOT_INJECT_PORTAL_BASE_URL =
      new ArchCondition<>("not inject " + PROPERTY + " via @Value") {
        @Override
        public void check(JavaClass clazz, ConditionEvents events) {
          for (JavaField field : clazz.getFields()) {
            field
                .tryGetAnnotationOfType(Value.class)
                .filter(v -> v.value().contains(PROPERTY))
                .ifPresent(
                    v -> events.add(SimpleConditionEvent.violated(field, field.getFullName())));
          }
          for (JavaCodeUnit codeUnit : clazz.getCodeUnits()) {
            for (JavaParameter parameter : codeUnit.getParameters()) {
              parameter
                  .tryGetAnnotationOfType(Value.class)
                  .filter(v -> v.value().contains(PROPERTY))
                  .ifPresent(
                      v ->
                          events.add(
                              SimpleConditionEvent.violated(
                                  parameter,
                                  codeUnit.getFullName() + " parameter #" + parameter.getIndex())));
            }
          }
        }
      };

  @ArchTest
  static final ArchRule only_the_builder_reads_the_portal_base_url =
      classes()
          .that()
          .doNotHaveFullyQualifiedName(BUILDER)
          .and()
          .doNotHaveFullyQualifiedName(PORTAL_ORIGIN)
          .should(NOT_INJECT_PORTAL_BASE_URL)
          .because(
              "client-facing portal links must come from PortalLinkBuilder so every authenticated"
                  + " link carries orgId (§13.6.6, ADR-351); PortalOrigin is exempt (CSP origin,"
                  + " ADR-352)");

  @ArchTest
  static final ArchRule only_the_builder_holds_a_portalBaseUrl_field =
      fields()
          .that()
          .haveName("portalBaseUrl")
          .should()
          .beDeclaredIn(BUILDER)
          .because("concatenating portalBaseUrl outside PortalLinkBuilder is the G-D1 drift");

  @ArchTest
  static final ArchRule only_csp_config_depends_on_portal_origin =
      noClasses()
          .that()
          .doNotHaveFullyQualifiedName(PORTAL_ORIGIN)
          .and()
          .doNotHaveFullyQualifiedName(SECURITY_CONFIG)
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName(PORTAL_ORIGIN)
          .because(
              "PortalOrigin carries the portal base URL for the CSP frame-ancestors header only"
                  + " (ADR-352); building links from its value bypasses PortalLinkBuilder"
                  + " (§13.6.6)");
}
