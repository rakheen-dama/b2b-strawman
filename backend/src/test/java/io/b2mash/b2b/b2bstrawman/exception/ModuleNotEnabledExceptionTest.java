package io.b2mash.b2b.b2bstrawman.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModuleNotEnabledExceptionTest {

  private static final String EXPECTED_DETAIL =
      "This feature is not enabled for your organization. "
          + "An admin can enable it in Settings → Features.";

  @Test
  void exception_hasCorrect403Status() {
    var ex = new ModuleNotEnabledException("trust_accounting");

    assertThat(ex.getStatusCode().value()).isEqualTo(403);
  }

  @Test
  void exception_usesStaticSettingsFeaturesDetailMessage() {
    var ex = new ModuleNotEnabledException("trust_accounting");
    var problem = ex.getBody();

    assertThat(problem.getTitle()).isEqualTo("Module not enabled");
    assertThat(problem.getDetail()).isEqualTo(EXPECTED_DETAIL);
    assertThat(problem.getProperties()).containsEntry("moduleId", "trust_accounting");
  }

  @Test
  void exception_handlesNullModuleId() {
    var ex = new ModuleNotEnabledException(null);
    var problem = ex.getBody();

    assertThat(problem.getTitle()).isEqualTo("Module not enabled");
    assertThat(problem.getDetail()).isEqualTo(EXPECTED_DETAIL);
    assertThat(problem.getProperties() == null || !problem.getProperties().containsKey("moduleId"))
        .isTrue();
  }

  @Test
  void exception_handlesEmptyModuleId() {
    var ex = new ModuleNotEnabledException("");
    var problem = ex.getBody();

    assertThat(problem.getTitle()).isEqualTo("Module not enabled");
    assertThat(problem.getDetail()).isEqualTo(EXPECTED_DETAIL);
    assertThat(problem.getProperties() == null || !problem.getProperties().containsKey("moduleId"))
        .isTrue();
  }
}
