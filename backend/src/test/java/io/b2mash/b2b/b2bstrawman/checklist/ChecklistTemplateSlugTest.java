package io.b2mash.b2b.b2bstrawman.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import org.junit.jupiter.api.Test;

class ChecklistTemplateSlugTest {

  @Test
  void slugFromName() {
    String slug = ChecklistTemplate.generateSlug("Client Onboarding");
    assertThat(slug).isEqualTo("client-onboarding");
  }

  @Test
  void specialCharsStripped() {
    String slug = ChecklistTemplate.generateSlug("KYC & AML (2024)");
    assertThat(slug).isEqualTo("kyc-aml-2024");
  }

  @Test
  void numbersAndHyphensPreserved() {
    String slug = ChecklistTemplate.generateSlug("phase-2-checklist-v3");
    assertThat(slug).isEqualTo("phase-2-checklist-v3");
  }

  @Test
  void invalidNameThrowsInvalidStateException() {
    assertThatThrownBy(() -> ChecklistTemplate.generateSlug("###"))
        .isInstanceOf(InvalidStateException.class);
  }
}
