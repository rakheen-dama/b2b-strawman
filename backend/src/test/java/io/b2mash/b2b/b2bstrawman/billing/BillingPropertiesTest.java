package io.b2mash.b2b.b2bstrawman.billing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies {@code heykazi.billing} property binding against the real {@code application.yml} /
 * {@code application-test.yml} values (test profile), using a lightweight {@link
 * ApplicationContextRunner} slice instead of a full application boot. {@link
 * ConfigDataApplicationContextInitializer} loads the same config files Spring Boot would, so the
 * asserted literals come from the yml — not from inline test properties.
 */
class BillingPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withPropertyValues("spring.profiles.active=test")
          .withUserConfiguration(BillingPropertiesConfiguration.class);

  @Test
  void shouldBindBillingPropertiesFromTestConfig() {
    contextRunner.run(
        context -> {
          var billingProperties = context.getBean(BillingProperties.class);
          assertThat(billingProperties.monthlyPriceCents()).isEqualTo(49900);
          assertThat(billingProperties.trialDays()).isEqualTo(14);
          assertThat(billingProperties.gracePeriodDays()).isEqualTo(60);
          assertThat(billingProperties.currency()).isEqualTo("ZAR");
          assertThat(billingProperties.maxMembers()).isEqualTo(10);
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(BillingProperties.class)
  static class BillingPropertiesConfiguration {}
}
