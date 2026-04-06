package io.b2mash.b2b.b2bstrawman.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;

@AnalyzeClasses(
    packages = "io.b2mash.b2b.b2bstrawman",
    importOptions = ImportOption.DoNotIncludeTests.class)
class LayerDependencyRulesTest {

  @ArchTest
  static final ArchRule controllers_should_not_depend_on_repositories =
      noClasses()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .and()
          .doNotHaveSimpleName("DevPortalController")
          .and()
          .doNotHaveSimpleName("InternalAuditController")
          .and()
          .doNotHaveSimpleName("PaymentWebhookController")
          .should()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("Repository")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule controllers_should_not_import_jpa =
      noClasses()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("jakarta.persistence..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule no_field_injection =
      fields().should().notBeAnnotatedWith(Autowired.class).allowEmptyShould(true);
}
