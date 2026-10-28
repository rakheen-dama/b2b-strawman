package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DisbursementCategoryTest {

  @ParameterizedTest
  @CsvSource({
    "SHERIFF_FEES, Sheriff fees",
    "COUNSEL_FEES, Counsel fees",
    "SEARCH_FEES, Search fees",
    "DEEDS_OFFICE_FEES, Deeds office fees",
    "COURT_FEES, Court fees",
    "ADVOCATE_FEES, Advocate fees",
    "EXPERT_WITNESS, Expert witness",
    "TRAVEL, Travel",
    "OTHER, Other"
  })
  void labelIsSentenceCase(DisbursementCategory category, String expected) {
    assertThat(category.label()).isEqualTo(expected);
  }
}
