package io.b2mash.b2b.b2bstrawman.invoice;

public interface PaymentProvider {
  PaymentResult recordPayment(PaymentRequest request);
}
