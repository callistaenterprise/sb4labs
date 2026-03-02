package se.magnus.sb4labs.apiconsumer.interfaceclients.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.magnus.sb4labs.api.core.product.Product;

public class ProductFallbacks {

  final static private Logger LOG = LoggerFactory.getLogger(ProductFallbacks.class);

  // TODO: This class can't be injected as a Spring Bean, construction fails when initiated from an @HttpServiceFallback annotation
  private final GetProductFallback getProductFallback = new GetProductFallback();

  // TODO: CallNotPermittedException does not seem to work?
  // public Product getProduct(CallNotPermittedException cause, int productId, int delay, int faultPercent) {

  public Product getProduct(Throwable cause, int productId, int delay, int faultPercent) {

    switch (cause) {
      case CallNotPermittedException cnpe:
        return getProductFallback.getProductFallbackValue(productId, delay, faultPercent, cnpe);

      case RuntimeException rtex:
        LOG.warn("Rethrowing a RuntimeException");
        throw rtex;

      default:
        LOG.warn("Rethrowing a checked exception wrapped in a RuntimeException {}", cause.getClass().getName());
        LOG.warn("Error message: {}", cause.getMessage());
        throw new RuntimeException(cause);
    }
  }
}