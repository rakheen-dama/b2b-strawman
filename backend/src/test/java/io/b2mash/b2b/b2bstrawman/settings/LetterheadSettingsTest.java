package io.b2mash.b2b.b2bstrawman.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pins the derived address helpers 626B's {@code TemplateContextHelper.buildOrgContext} consumes:
 * non-blank lines in print order with "city postal-code" on one line; {@code addressJoined()} is
 * those lines joined with {@code ", "}, and the empty string when nothing is set (R-8).
 */
class LetterheadSettingsTest {

  @Test
  void emptyLetterhead_hasNoLinesAndJoinsToEmptyString() {
    var letterhead = new LetterheadSettings();

    assertThat(letterhead.addressLines()).isEmpty();
    assertThat(letterhead.addressJoined()).isEmpty();
  }

  @Test
  void lineCityPostalAndCountry_printOnThreeLines() {
    var letterhead = new LetterheadSettings();
    letterhead.setAddressLine1("12 Rissik Street");
    letterhead.setCity("Johannesburg");
    letterhead.setPostalCode("2001");
    letterhead.setCountry("South Africa");

    assertThat(letterhead.addressLines())
        .containsExactly("12 Rissik Street", "Johannesburg 2001", "South Africa");
    assertThat(letterhead.addressJoined())
        .isEqualTo("12 Rissik Street, Johannesburg 2001, South Africa");
  }

  @Test
  void allAddressFields_printInOrder() {
    var letterhead = new LetterheadSettings();
    letterhead.setAddressLine1("12 Rissik Street");
    letterhead.setAddressLine2("Marshalltown");
    letterhead.setAddressLine3("Gauteng");
    letterhead.setCity("Johannesburg");
    letterhead.setPostalCode("2001");
    letterhead.setCountry("South Africa");

    assertThat(letterhead.addressLines())
        .containsExactly(
            "12 Rissik Street", "Marshalltown", "Gauteng", "Johannesburg 2001", "South Africa");
  }

  @Test
  void cityOnly_printsCityAlone() {
    var letterhead = new LetterheadSettings();
    letterhead.setCity("Johannesburg");

    assertThat(letterhead.addressLines()).containsExactly("Johannesburg");
    assertThat(letterhead.addressJoined()).isEqualTo("Johannesburg");
  }

  @Test
  void postalCodeOnly_printsPostalCodeAlone() {
    var letterhead = new LetterheadSettings();
    letterhead.setPostalCode("2001");

    assertThat(letterhead.addressLines()).containsExactly("2001");
  }

  @Test
  void blankAndNullLines_areSkipped() {
    var letterhead = new LetterheadSettings();
    letterhead.setAddressLine1("12 Rissik Street");
    letterhead.setAddressLine2("   ");
    letterhead.setAddressLine3(null);
    letterhead.setCity(" ");
    letterhead.setPostalCode("2001");
    letterhead.setCountry("");

    assertThat(letterhead.addressLines()).containsExactly("12 Rissik Street", "2001");
    assertThat(letterhead.addressJoined()).isEqualTo("12 Rissik Street, 2001");
  }

  @Test
  void updateLetterhead_replacesAllSixteenFieldsAndNullClears() {
    var letterhead = new LetterheadSettings();
    letterhead.setBankName("FNB");
    letterhead.setSignatoryName("Old Signatory");

    letterhead.updateLetterhead(
        "12 Rissik Street",
        "Marshalltown",
        "Gauteng",
        "Johannesburg",
        "2001",
        "South Africa",
        "+27 11 555 0100",
        "info@mathebula.law",
        "https://mathebula.law",
        null,
        "250655",
        "62012345678",
        "Cheque",
        "Use the fee-note number as your reference",
        "Thandi Mathebula",
        "Director");

    assertThat(letterhead.getAddressLine1()).isEqualTo("12 Rissik Street");
    assertThat(letterhead.getAddressLine2()).isEqualTo("Marshalltown");
    assertThat(letterhead.getAddressLine3()).isEqualTo("Gauteng");
    assertThat(letterhead.getCity()).isEqualTo("Johannesburg");
    assertThat(letterhead.getPostalCode()).isEqualTo("2001");
    assertThat(letterhead.getCountry()).isEqualTo("South Africa");
    assertThat(letterhead.getPhone()).isEqualTo("+27 11 555 0100");
    assertThat(letterhead.getEmail()).isEqualTo("info@mathebula.law");
    assertThat(letterhead.getWebsite()).isEqualTo("https://mathebula.law");
    assertThat(letterhead.getBankName()).isNull(); // full replace: null clears
    assertThat(letterhead.getBankBranchCode()).isEqualTo("250655");
    assertThat(letterhead.getBankAccountNumber()).isEqualTo("62012345678");
    assertThat(letterhead.getBankAccountType()).isEqualTo("Cheque");
    assertThat(letterhead.getPaymentReferenceNote())
        .isEqualTo("Use the fee-note number as your reference");
    assertThat(letterhead.getSignatoryName()).isEqualTo("Thandi Mathebula");
    assertThat(letterhead.getSignatoryDesignation()).isEqualTo("Director");
  }

  @Test
  void nonAddressFields_doNotAffectTheAddress() {
    var letterhead = new LetterheadSettings();
    letterhead.setPhone("+27 11 555 0100");
    letterhead.setBankName("FNB");
    letterhead.setSignatoryName("Thandi Mathebula");

    assertThat(letterhead.addressLines()).isEmpty();
    assertThat(letterhead.addressJoined()).isEmpty();
  }
}
