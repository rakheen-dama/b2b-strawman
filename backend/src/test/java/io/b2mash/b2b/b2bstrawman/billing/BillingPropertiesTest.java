package io.b2mash.b2b.b2bstrawman.billing;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.billing.payfast.PayFastBillingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
class BillingPropertiesTest {

  @Autowired private BillingProperties billingProperties;

  @Autowired private PayFastBillingProperties payFastBillingProperties;

  @Test
  void shouldBindBillingPropertiesFromTestConfig() {
    assertThat(billingProperties.monthlyPriceCents()).isEqualTo(49900);
    assertThat(billingProperties.trialDays()).isEqualTo(14);
    assertThat(billingProperties.gracePeriodDays()).isEqualTo(60);
    assertThat(billingProperties.currency()).isEqualTo("ZAR");
    assertThat(billingProperties.itemName()).isEqualTo("HeyKazi Professional");
    assertThat(billingProperties.maxMembers()).isEqualTo(10);
  }

  @Test
  void shouldBindPayFastBillingPropertiesFromTestConfig() {
    assertThat(payFastBillingProperties.sandbox()).isTrue();
    assertThat(payFastBillingProperties.merchantId()).isEqualTo("test-merchant-id");
    assertThat(payFastBillingProperties.merchantKey()).isEqualTo("test-merchant-key");
    assertThat(payFastBillingProperties.passphrase()).isEqualTo("test-passphrase");
  }

  @Test
  void shouldBindBillingPropertiesUrlFields() {
    assertThat(billingProperties.notifyUrl()).isNotNull();
    assertThat(billingProperties.notifyUrl())
        .isEqualTo("http://localhost:8080/api/webhooks/subscription");
    assertThat(billingProperties.returnUrl()).isNotNull();
    assertThat(billingProperties.returnUrl())
        .isEqualTo("http://localhost:3000/settings/billing?result=success");
    assertThat(billingProperties.cancelUrl()).isNotNull();
    assertThat(billingProperties.cancelUrl())
        .isEqualTo("http://localhost:3000/settings/billing?result=cancelled");
  }
}
