package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class WorkTypesTest {

  @Test
  void nullStaysNull() {
    assertThat(WorkTypes.normalize(null)).isNull();
  }

  @Test
  void blankBecomesNull() {
    assertThat(WorkTypes.normalize("")).isNull();
    assertThat(WorkTypes.normalize("   ")).isNull();
    assertThat(WorkTypes.normalize(" \t\n ")).isNull();
  }

  @Test
  void mixedCaseIsUpperCased() {
    assertThat(WorkTypes.normalize("Conveyancing")).isEqualTo("CONVEYANCING");
    assertThat(WorkTypes.normalize("litigation")).isEqualTo("LITIGATION");
  }

  @Test
  void surroundingWhitespaceIsTrimmed() {
    assertThat(WorkTypes.normalize("  estates \t")).isEqualTo("ESTATES");
  }

  @Test
  void innerWhitespaceRunsCollapseToOneUnderscore() {
    assertThat(WorkTypes.normalize(" personal injury ")).isEqualTo("PERSONAL_INJURY");
    assertThat(WorkTypes.normalize("Road  Accident\tFund")).isEqualTo("ROAD_ACCIDENT_FUND");
  }

  @Test
  void canonicalValueIsUnchanged() {
    assertThat(WorkTypes.normalize("CONVEYANCING")).isEqualTo("CONVEYANCING");
    assertThat(WorkTypes.normalize("PERSONAL_INJURY")).isEqualTo("PERSONAL_INJURY");
  }

  @Test
  void upperCasingIsLocaleIndependent() {
    // Turkish dotted-i trap: under a tr default locale "i".toUpperCase() is "İ".
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertThat(WorkTypes.normalize("litigation")).isEqualTo("LITIGATION");
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void fiftyCharacterInputWithSpacesStillFitsFiftyCharacters() {
    String input = "a b".repeat(16) + "cc"; // 50 chars
    assertThat(input).hasSize(50);
    assertThat(WorkTypes.normalize(input)).hasSize(50);
  }
}
