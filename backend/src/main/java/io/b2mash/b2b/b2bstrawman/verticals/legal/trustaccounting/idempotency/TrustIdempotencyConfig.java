package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Wires {@link IdempotentTrustWrite} enforcement into Spring MVC (648B F-11). */
@Configuration
public class TrustIdempotencyConfig implements WebMvcConfigurer {

  private final TrustIdempotencyInterceptor interceptor;

  public TrustIdempotencyConfig(TrustIdempotencyInterceptor interceptor) {
    this.interceptor = interceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(interceptor).addPathPatterns("/api/**");
  }

  /**
   * Registered after the Spring Security chain (order -100), so it runs inside the tenant and
   * member scopes and never buffers a request the chain refused.
   */
  @Bean
  FilterRegistrationBean<TrustIdempotencyFilter> trustIdempotencyFilter(
      @Qualifier("requestMappingHandlerMapping")
          ObjectProvider<RequestMappingHandlerMapping> handlerMapping) {
    var registration = new FilterRegistrationBean<>(new TrustIdempotencyFilter(handlerMapping));
    registration.addUrlPatterns("/api/*");
    registration.setOrder(0);
    return registration;
  }
}
