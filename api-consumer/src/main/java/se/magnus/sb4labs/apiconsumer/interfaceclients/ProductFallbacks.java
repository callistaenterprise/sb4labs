package se.magnus.sb4labs.apiconsumer.interfaceclients;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import se.magnus.sb4labs.api.core.product.Product;
import se.magnus.sb4labs.api.exceptions.NotFoundException;

public class ProductFallbacks {

  final static private Logger LOG = LoggerFactory.getLogger(ProductFallbacks.class);

  // TODO: CallNotPermittedException does not seem to work?
  // public Product getProduct(CallNotPermittedException cause, int productId, int delay, int faultPercent) {
  public Product getProduct(Throwable cause, int productId, int delay, int faultPercent) {

    switch (cause) {
      case CallNotPermittedException _:
        // TODO: Extract to separate class...
        LOG.warn("getProduct({}) fallback invoked, caused by {}", productId, cause.toString());

        if (productId == 13) {
          String errMsg = "Product Id: " + productId + " not found in fallback cache!";
          LOG.warn(errMsg);
          throw new NotFoundException(errMsg);
        }

        return (new Product(productId, "Fallback product" + productId, productId));

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