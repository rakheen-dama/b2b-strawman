package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.integration.accounting.CustomerSyncRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class XeroContactPayloadMapperTest {

  private final XeroContactPayloadMapper mapper = new XeroContactPayloadMapper();

  @Test
  @SuppressWarnings("unchecked")
  void map_producesCorrectXeroContactJson() {
    var request =
        new CustomerSyncRequest(
            "Acme Corp",
            "billing@acme.com",
            "123 Main St",
            "Suite 100",
            "Cape Town",
            "8001",
            "ZA",
            "KAZI-CUST-abc123");

    Map<String, Object> payload = mapper.map(request);

    assertThat(payload.get("Name")).isEqualTo("Acme Corp");
    assertThat(payload.get("EmailAddress")).isEqualTo("billing@acme.com");
    assertThat(payload.get("AccountNumber")).isEqualTo("KAZI-CUST-abc123");

    var addresses = (List<Map<String, Object>>) payload.get("Addresses");
    assertThat(addresses).hasSize(1);

    Map<String, Object> address = addresses.getFirst();
    assertThat(address.get("AddressType")).isEqualTo("POBOX");
    assertThat(address.get("AddressLine1")).isEqualTo("123 Main St");
    assertThat(address.get("AddressLine2")).isEqualTo("Suite 100");
    assertThat(address.get("City")).isEqualTo("Cape Town");
    assertThat(address.get("PostalCode")).isEqualTo("8001");
    assertThat(address.get("Country")).isEqualTo("ZA");
  }

  @Test
  void map_handlesNullAddressFields() {
    var request =
        new CustomerSyncRequest(
            "Name Only Corp", "info@nameonly.com", null, null, null, null, null, null);

    Map<String, Object> payload = mapper.map(request);

    assertThat(payload.get("Name")).isEqualTo("Name Only Corp");
    assertThat(payload.get("EmailAddress")).isEqualTo("info@nameonly.com");
    assertThat(payload).doesNotContainKey("Addresses");
    assertThat(payload).doesNotContainKey("AccountNumber");
  }

  @Test
  void map_omitsEmailWhenNull() {
    var request =
        new CustomerSyncRequest("No Email Corp", null, null, null, null, null, null, null);

    Map<String, Object> payload = mapper.map(request);

    assertThat(payload.get("Name")).isEqualTo("No Email Corp");
    assertThat(payload).doesNotContainKey("EmailAddress");
    assertThat(payload).doesNotContainKey("Addresses");
    assertThat(payload).doesNotContainKey("AccountNumber");
  }

  @Test
  @SuppressWarnings("unchecked")
  void map_includesAddressWhenPartialFieldsPresent() {
    var request =
        new CustomerSyncRequest(
            "Partial Addr Corp", null, null, null, "Johannesburg", "2000", null, null);

    Map<String, Object> payload = mapper.map(request);

    assertThat(payload.get("Name")).isEqualTo("Partial Addr Corp");
    assertThat(payload).doesNotContainKey("EmailAddress");

    var addresses = (List<Map<String, Object>>) payload.get("Addresses");
    assertThat(addresses).hasSize(1);

    Map<String, Object> address = addresses.getFirst();
    assertThat(address.get("AddressType")).isEqualTo("POBOX");
    assertThat(address).doesNotContainKey("AddressLine1");
    assertThat(address).doesNotContainKey("AddressLine2");
    assertThat(address.get("City")).isEqualTo("Johannesburg");
    assertThat(address.get("PostalCode")).isEqualTo("2000");
    assertThat(address).doesNotContainKey("Country");
  }

  @Test
  void map_omitsAccountNumberWhenExternalReferenceNull() {
    var request =
        new CustomerSyncRequest(
            "No Ref Corp", "info@noref.com", null, null, null, null, null, null);

    Map<String, Object> payload = mapper.map(request);

    assertThat(payload).doesNotContainKey("AccountNumber");
  }

  @Test
  void map_includesAccountNumberWhenExternalReferencePresent() {
    var request =
        new CustomerSyncRequest(
            "Ref Corp", null, null, null, null, null, null, "KAZI-CUST-unique-ref");

    Map<String, Object> payload = mapper.map(request);

    assertThat(payload.get("AccountNumber")).isEqualTo("KAZI-CUST-unique-ref");
  }
}
