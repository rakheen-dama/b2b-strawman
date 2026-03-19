package io.b2mash.b2b.b2bstrawman.datarequest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JurisdictionDefaultsTest {

  @Test
  void zaJurisdiction_returnsCorrectValues() {
    assertThat(JurisdictionDefaults.getDefaultDeadlineDays("ZA")).isEqualTo(30);
    assertThat(JurisdictionDefaults.getMaxDeadlineDays("ZA")).isEqualTo(30);
    assertThat(JurisdictionDefaults.getMinFinancialRetentionMonths("ZA")).isEqualTo(60);
    assertThat(JurisdictionDefaults.getRegulatorName("ZA"))
        .isEqualTo("Information Regulator (South Africa)");
    assertThat(JurisdictionDefaults.getMandatoryDocumentType("ZA"))
        .isEqualTo("paia_section_51_manual");
  }

  @Test
  void euJurisdiction_returnsCorrectValues() {
    assertThat(JurisdictionDefaults.getDefaultDeadlineDays("EU")).isEqualTo(30);
    assertThat(JurisdictionDefaults.getMaxDeadlineDays("EU")).isEqualTo(30);
    assertThat(JurisdictionDefaults.getMinFinancialRetentionMonths("EU")).isEqualTo(72);
    assertThat(JurisdictionDefaults.getRegulatorName("EU")).isEqualTo("Data Protection Authority");
  }

  @Test
  void brJurisdiction_returnsCorrectValues() {
    assertThat(JurisdictionDefaults.getDefaultDeadlineDays("BR")).isEqualTo(15);
    assertThat(JurisdictionDefaults.getMaxDeadlineDays("BR")).isEqualTo(15);
    assertThat(JurisdictionDefaults.getMinFinancialRetentionMonths("BR")).isEqualTo(60);
  }

  @Test
  void nullJurisdiction_returnsConservativeDefaults() {
    assertThat(JurisdictionDefaults.getDefaultDeadlineDays(null)).isEqualTo(30);
    assertThat(JurisdictionDefaults.getMaxDeadlineDays(null)).isEqualTo(90);
    assertThat(JurisdictionDefaults.getMinFinancialRetentionMonths(null)).isEqualTo(60);
    assertThat(JurisdictionDefaults.getRegulatorName(null)).isEqualTo("");
    assertThat(JurisdictionDefaults.getMandatoryDocumentType(null)).isEqualTo("");
  }
}
