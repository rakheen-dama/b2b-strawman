package io.b2mash.b2b.b2bstrawman.takeon.reader;

import static io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnParseOptions.DecimalSeparator.COMMA;
import static io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnParseOptions.DecimalSeparator.POINT;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnParseOptions.DecimalSeparator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Phase 93 / 653A (task 653.6, §17.3.2 generic row checks, FD-17): the take-on value parser. */
class TakeOnValueParserTest {

  private static BigDecimal amount(String raw, DecimalSeparator separator) {
    var parsed = TakeOnValueParser.amount(raw, separator);
    assertThat(parsed.ok()).as("%s parses: %s", raw, parsed.message()).isTrue();
    return parsed.value();
  }

  private static String amountError(String raw, DecimalSeparator separator) {
    var parsed = TakeOnValueParser.amount(raw, separator);
    assertThat(parsed.ok()).as("%s is refused", raw).isFalse();
    assertThat(parsed.message()).isNotBlank();
    return parsed.errorCode();
  }

  // --- amounts ---

  @Test
  void bracketedDecimalCommaWithSpaceGrouping_isNegative() {
    assertThat(amount("(1 234,56)", COMMA)).isEqualByComparingTo("-1234.56");
  }

  @Test
  void drSuffixIsNegative_crSuffixIsPositive() {
    assertThat(amount("1 234,56 DR", COMMA)).isEqualByComparingTo("-1234.56");
    assertThat(amount("1 234,56 dr", COMMA)).isEqualByComparingTo("-1234.56");
    assertThat(amount("500.00 CR", POINT)).isEqualByComparingTo("500.00");
  }

  @Test
  void leadingOrTrailingMinusIsNegative() {
    assertThat(amount("1234.56-", POINT)).isEqualByComparingTo("-1234.56");
    assertThat(amount("-1234.56", POINT)).isEqualByComparingTo("-1234.56");
    assertThat(amount("R -50", POINT)).isEqualByComparingTo("-50.00");
  }

  @Test
  void currencyPrefixAndSpacesAreStripped() {
    assertThat(amount("R 1 234.56", POINT)).isEqualByComparingTo("1234.56");
    assertThat(amount("ZAR1,234.56", POINT)).isEqualByComparingTo("1234.56");
    assertThat(amount("r12", POINT)).isEqualByComparingTo("12.00");
  }

  @Test
  void nonBreakingSpacesGroup() {
    assertThat(amount("1\u00A0234\u00A0567,89", COMMA)).isEqualByComparingTo("1234567.89");
    assertThat(amount("1\u202F234.5", POINT)).isEqualByComparingTo("1234.50");
  }

  @Test
  void resultHasScaleTwo() {
    assertThat(amount("1234.5", POINT)).isEqualTo(new BigDecimal("1234.50"));
    assertThat(amount("7", POINT)).isEqualTo(new BigDecimal("7.00"));
  }

  @Test
  void loneThousandsGroup_isAmbiguous_neverAGuess() {
    assertThat(amountError("1,234", POINT)).isEqualTo(TakeOnValueParser.AMOUNT_AMBIGUOUS);
    assertThat(amountError("1.234", COMMA)).isEqualTo(TakeOnValueParser.AMOUNT_AMBIGUOUS);
  }

  @Test
  void groupingAlongsideADecimal_orInSeveralGroups_isFine() {
    assertThat(amount("1,234.56", POINT)).isEqualByComparingTo("1234.56");
    assertThat(amount("1,234,567", POINT)).isEqualByComparingTo("1234567");
    assertThat(amount("1.234,56", COMMA)).isEqualByComparingTo("1234.56");
    assertThat(amount("1.234.567", COMMA)).isEqualByComparingTo("1234567");
  }

  @Test
  void precisionSizeAndGarbageAreRefused() {
    assertThat(amountError("1234.567", POINT)).isEqualTo(TakeOnValueParser.AMOUNT_TOO_PRECISE);
    assertThat(amountError("12345678901234", POINT)).isEqualTo(TakeOnValueParser.AMOUNT_TOO_LARGE);
    assertThat(amount("1234567890123", POINT)).isEqualByComparingTo("1234567890123");
    assertThat(amountError("12,34", POINT)).isEqualTo(TakeOnValueParser.AMOUNT_INVALID);
    assertThat(amountError("abc", POINT)).isEqualTo(TakeOnValueParser.AMOUNT_INVALID);
    assertThat(amountError("1.2.3", POINT)).isEqualTo(TakeOnValueParser.AMOUNT_INVALID);
  }

  @Test
  void blankIsOkAndNull() {
    var parsed = TakeOnValueParser.amount("  ", POINT);
    assertThat(parsed.ok()).isTrue();
    assertThat(parsed.value()).isNull();
  }

  // --- dates ---

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "31/10/2026|dd/MM/yyyy",
        "2026-10-31|yyyy-MM-dd",
        "31-10-2026|dd-MM-yyyy",
        "31.10.2026|dd.MM.yyyy",
        "10/31/2026|MM/dd/yyyy",
        "31 Oct 2026|d MMM yyyy",
        "31 OCT 2026|d MMM yyyy",
        "2026-10-31|dd/MM/yyyy"
      })
  void everySupportedDateFormat(String raw, String format) {
    var parsed = TakeOnValueParser.date(raw, format);
    assertThat(parsed.ok()).as(parsed.message()).isTrue();
    assertThat(parsed.value()).isEqualTo(LocalDate.of(2026, 10, 31));
  }

  @Test
  void impossibleDateIsRefusedStrictly() {
    assertThat(TakeOnValueParser.date("2026-02-30", "yyyy-MM-dd").errorCode())
        .isEqualTo(TakeOnValueParser.DATE_INVALID);
    assertThat(TakeOnValueParser.date("30/02/2026", "dd/MM/yyyy").errorCode())
        .isEqualTo(TakeOnValueParser.DATE_INVALID);
    assertThat(TakeOnValueParser.date("31/10/2026", "yyyy-MM-dd").message())
        .isEqualTo("\"31/10/2026\" is not a date in the format yyyy-mm-dd.");
  }

  @Test
  void numericValueInADateField_isNeverAnExcelSerial() {
    var parsed = TakeOnValueParser.date("45230", "dd/MM/yyyy");
    assertThat(parsed.errorCode()).isEqualTo(TakeOnValueParser.NUMERIC_IN_DATE);
    assertThat(parsed.message())
        .isEqualTo("Format this column as a date in Excel, or as text dd/mm/yyyy");
  }

  // --- enums ---

  @Test
  void enumByNameCaseInsensitively_andByAlias() {
    var names = List.of("CLIENT_INSTRUCTION", "FIRM_DISCRETION");
    var aliases = Map.of("S86(4)", "CLIENT_INSTRUCTION", "S86(3)", "FIRM_DISCRETION");
    assertThat(TakeOnValueParser.enumValue("FIRM_DISCRETION", names, aliases).value())
        .isEqualTo("FIRM_DISCRETION");
    assertThat(TakeOnValueParser.enumValue("client instruction", names, aliases).value())
        .isEqualTo("CLIENT_INSTRUCTION");
    assertThat(TakeOnValueParser.enumValue("firm-discretion", names, aliases).value())
        .isEqualTo("FIRM_DISCRETION");
    assertThat(TakeOnValueParser.enumValue("s86(4)", names, aliases).value())
        .isEqualTo("CLIENT_INSTRUCTION");

    var refused = TakeOnValueParser.enumValue("Call account", names, aliases);
    assertThat(refused.errorCode()).isEqualTo(TakeOnValueParser.ENUM_INVALID);
    assertThat(refused.message())
        .isEqualTo("Call account is not one of: Client instruction, Firm discretion");
  }

  // --- booleans, text, email ---

  @ParameterizedTest
  @ValueSource(strings = {"true", "YES", "y", "1", "True"})
  void truthyValues(String raw) {
    assertThat(TakeOnValueParser.bool(raw).value()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"false", "No", "N", "0"})
  void falsyValues(String raw) {
    assertThat(TakeOnValueParser.bool(raw).value()).isFalse();
  }

  @Test
  void notABoolean() {
    assertThat(TakeOnValueParser.bool("maybe").errorCode())
        .isEqualTo(TakeOnValueParser.BOOLEAN_INVALID);
  }

  @Test
  void textIsTrimmedAndLengthChecked() {
    assertThat(TakeOnValueParser.text("  Zanele  ", 10).value()).isEqualTo("Zanele");
    var tooLong = TakeOnValueParser.text("x".repeat(11), 10);
    assertThat(tooLong.errorCode()).isEqualTo(TakeOnValueParser.TEXT_TOO_LONG);
    assertThat(tooLong.message()).isEqualTo("This value is longer than 10 characters.");
  }

  @Test
  void emailNeedsOneAtNoSpacesAndADottedDomain() {
    assertThat(TakeOnValueParser.email(" a.b@example.test ").value()).isEqualTo("a.b@example.test");
    for (String bad :
        List.of("a@b", "a b@example.test", "a@@example.test", "@example.test", "a@.test")) {
      assertThat(TakeOnValueParser.email(bad).errorCode())
          .as(bad)
          .isEqualTo(TakeOnValueParser.EMAIL_INVALID);
    }
  }

  // --- Excel error tokens ---

  @ParameterizedTest
  @ValueSource(
      strings = {
        "#REF!", "#VALUE!", "#DIV/0!", "#N/A", "#NAME?", "#NULL!", "#NUM!", "#SPILL!", "#CALC!"
      })
  void everyExcelErrorTokenIsRecognised(String token) {
    assertThat(TakeOnValueParser.excelErrorCode(" " + token + " ")).contains(token);
  }

  @Test
  void ordinaryTextIsNotAnErrorToken() {
    assertThat(TakeOnValueParser.excelErrorCode("#1 Client")).isEmpty();
    assertThat(TakeOnValueParser.excelErrorCode(null)).isEmpty();
  }

  @Test
  void overlongCellsAreRefusedBeforeAnyPatternRuns() {
    assertThat(TakeOnValueParser.amount("1".repeat(41), POINT).errorCode())
        .isEqualTo(TakeOnValueParser.AMOUNT_INVALID);
    assertThat(TakeOnValueParser.date("1".repeat(41), "dd/MM/yyyy").errorCode())
        .isEqualTo(TakeOnValueParser.DATE_INVALID);
    assertThat(TakeOnValueParser.email("a@" + "b".repeat(300)).errorCode())
        .isEqualTo(TakeOnValueParser.EMAIL_INVALID);
  }
}
