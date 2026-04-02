package io.b2mash.b2b.b2bstrawman.billing.payfast;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PayFastBillingProperties.class)
public class PayFastBillingConfig {}
