package se.magnus.sb4labs.apiconsumer.interfaceclients.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.magnus.sb4labs.api.core.product.Product;
import se.magnus.sb4labs.api.exceptions.NotFoundException;

public class GetProductFallback {

  private static final Logger LOG = LoggerFactory.getLogger(GetProductFallback.class);

  public Product getProductFallbackValue(int productId, int delay, int faultPercent, CallNotPermittedException ex) {

    LOG.warn("getProduct({}) fail-fast fallback invoked, caused by {}", productId, ex.toString());

    if (productId == 13) {
      String errMsg = "Product Id: " + productId + " not found in fallback cache!";
      LOG.warn(errMsg);
      throw new NotFoundException(errMsg);
    }

    return new Product(productId, "Fallback product" + productId, productId);
  }

}
