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
  void nonAddressFields_doNotAffectTheAddress() {
    var letterhead = new LetterheadSettings();
    letterhead.setPhone("+27 11 555 0100");
    letterhead.setBankName("FNB");
    letterhead.setSignatoryName("Thandi Mathebula");

    assertThat(letterhead.addressLines()).isEmpty();
    assertThat(letterhead.addressJoined()).isEmpty();
  }
}
