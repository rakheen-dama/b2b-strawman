package io.b2mash.b2b.b2bstrawman.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModuleNotEnabledExceptionTest {

  @Test
  void exception_hasCorrect403Status() {
    var ex = new ModuleNotEnabledException("trust_accounting");

    assertThat(ex.getStatusCode().value()).isEqualTo(403);
  }

  @Test
  void exception_humanizesModuleNameInDetail() {
    var ex = new ModuleNotEnabledException("trust_accounting");
    var problem = ex.getBody();

    assertThat(problem.getTitle()).isEqualTo("Module not enabled");
    assertThat(problem.getDetail()).contains("Trust accounting");
    assertThat(problem.getDetail()).contains("Contact your administrator");
  }
}
