package io.b2mash.b2b.b2bstrawman.takeon.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDataset;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnParseOptions.DecimalSeparator;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The plain-decimal parser and the dedup key of the generic take-on checks (653A review). */
class GenericTakeOnDatasetHandlerTest {

  @Test
  void aPercentSignAndSurroundingSpacesAreAcceptedButNotSpacesBetweenDigits() {
    assertThat(GenericTakeOnDatasetHandler.decimal("7.50%", DecimalSeparator.POINT).value())
        .isEqualByComparingTo("7.5");
    assertThat(GenericTakeOnDatasetHandler.decimal(" 12 % ", DecimalSeparator.POINT).value())
        .isEqualByComparingTo("12");
    assertThat(GenericTakeOnDatasetHandler.decimal("7,5 %", DecimalSeparator.COMMA).value())
        .isEqualByComparingTo("7.5");

    var spaced = GenericTakeOnDatasetHandler.decimal("1 2%", DecimalSeparator.POINT);
    assertThat(spaced.ok()).isFalse();
    assertThat(spaced.errorCode()).isEqualTo(GenericTakeOnDatasetHandler.DECIMAL_INVALID);
    assertThat(spaced.message()).isEqualTo("\"1 2%\" is not a number.");
    assertThat(GenericTakeOnDatasetHandler.decimal("1 2", DecimalSeparator.POINT).ok()).isFalse();
    assertThat(GenericTakeOnDatasetHandler.decimal("1 2", DecimalSeparator.COMMA).ok()).isFalse();
  }

  @Test
  void theDedupKeyIsTheDatasetKeyFieldLowerCasedAndPrefixed() {
    var handler = new GenericTakeOnDatasetHandler(null, null);
    assertThat(handler.dedupKey(TakeOnDataset.CLIENTS, Map.of("email", "Thandi@Example.TEST")))
        .isEqualTo(new TakeOnDatasetHandler.DedupKey("email", "email:thandi@example.test"));
    assertThat(handler.dedupKey(TakeOnDataset.MATTERS, Map.of("referenceNumber", "M-1")))
        .isEqualTo(new TakeOnDatasetHandler.DedupKey("referenceNumber", "ref:m-1"));
    assertThat(handler.dedupKey(TakeOnDataset.TRUST_POSITION, Map.of("clientRef", "C-1"))).isNull();
    assertThat(handler.dedupKey(TakeOnDataset.CLIENTS, Map.of("email", BigDecimal.ONE))).isNull();
  }
}
