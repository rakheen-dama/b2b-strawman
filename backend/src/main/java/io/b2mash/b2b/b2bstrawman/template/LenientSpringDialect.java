package io.b2mash.b2b.b2bstrawman.template;

import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.standard.expression.IStandardVariableExpressionEvaluator;

/**
 * Extends the standard Spring Thymeleaf dialect to use lenient expression evaluation. Unresolvable
 * expressions (e.g. missing custom fields) produce a placeholder instead of an error.
 */
public class LenientSpringDialect extends SpringStandardDialect {

  @Override
  public IStandardVariableExpressionEvaluator getVariableExpressionEvaluator() {
    return LenientSpELEvaluator.INSTANCE;
  }
}
