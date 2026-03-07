package io.b2mash.b2b.b2bstrawman.accessrequest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link EmailDomainValidator}. No Spring context required -- these are plain
 * logic tests.
 */
class EmailDomainValidatorTest {

  private final EmailDomainValidator validator =
      new EmailDomainValidator(
          new AccessRequestConfigProperties(
              List.of("gmail.com", "yahoo.com", "hotmail.com"), 10, 5));

  @Test
  void blockedDomain_returnsTrue() {
    assertThat(validator.isBlockedDomain("user@gmail.com")).isTrue();
  }

  @Test
  void allowedDomain_returnsFalse() {
    assertThat(validator.isBlockedDomain("user@acme-corp.com")).isFalse();
  }

  @Test
  void caseInsensitive_returnsTrue() {
    assertThat(validator.isBlockedDomain("user@Gmail.COM")).isTrue();
  }

  @Test
  void nullEmail_returnsFalse() {
    assertThat(validator.isBlockedDomain(null)).isFalse();
  }

  @Test
  void emailWithoutAtSymbol_returnsFalse() {
    assertThat(validator.isBlockedDomain("usergmail.com")).isFalse();
  }

  @Test
  void emptyEmail_returnsFalse() {
    assertThat(validator.isBlockedDomain("")).isFalse();
  }

  @Test
  void emailWithEmptyDomain_returnsFalse() {
    assertThat(validator.isBlockedDomain("user@")).isFalse();
  }

  @Test
  void emptyBlockedList_allowsAllEmails() {
    EmailDomainValidator permissiveValidator =
        new EmailDomainValidator(new AccessRequestConfigProperties(List.of(), 10, 5));
    assertThat(permissiveValidator.isBlockedDomain("user@gmail.com")).isFalse();
    assertThat(permissiveValidator.isBlockedDomain("user@anything.com")).isFalse();
  }
}
